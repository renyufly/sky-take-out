# 03 Redis 与秒杀预约设计

## 1. Redis 加入项目的亮点是什么

只写“使用 Redis 缓存数据”亮点有限。热门场次预约中，Redis 可以同时解决：

- 活动详情热点读取；
- 瞬时流量下的库存预扣；
- 同一用户重复预约；
- 接口幂等；
- 限流和排队状态；
- 短期预约结果查询。

亮点必须包含正确性：原子操作、防超卖、最终一致性和异常恢复。

## 2. Key 设计

```text
booking:activity:{activityId}             活动详情
booking:session:{sessionId}               场次详情
booking:stock:{sessionId}                 可预约库存
booking:users:{sessionId}                 已获得资格的用户集合
booking:request:{requestId}               幂等请求及状态
booking:result:{userId}:{sessionId}        用户预约结果
booking:rate:user:{userId}                 用户限流
```

Key 应包含业务前缀、实体、ID，避免无规则命名。所有临时 key 必须设计 TTL。

Redis Cluster 下，多 key Lua 脚本要求 key 位于同一 hash slot，可使用 hash tag：

```text
booking:{session-100}:stock
booking:{session-100}:users
```

## 3. Lua 原子预扣

目标是在一个原子操作中完成：

1. requestId 是否已处理；
2. 用户是否已经预约；
3. 库存是否充足；
4. 扣减库存；
5. 记录用户与请求。

伪 Lua：

```lua
if redis.call('EXISTS', requestKey) == 1 then
    return 2 -- duplicate request
end

if redis.call('SISMEMBER', userSetKey, userId) == 1 then
    return 3 -- duplicate booking
end

local stock = tonumber(redis.call('GET', stockKey) or '-1')
if stock < quantity then
    return 0 -- sold out
end

redis.call('DECRBY', stockKey, quantity)
redis.call('SADD', userSetKey, userId)
redis.call('SET', requestKey, 'QUEUING', 'EX', 900)
return 1
```

Java 将返回码转换为明确业务状态。Lua 保证脚本执行期间的原子性，但不能自动保证 Redis 与 MySQL 一致。

## 4. 为什么不用普通 GET 再 DECR

两个请求可能同时读取库存 1，都判断可用，然后各扣一次，产生负库存或超卖。Lua、Redis 单命令 `DECR` 后判断，或合适的数据结构可以避免竞态。Lua 更适合把重复预约和库存判断组合成一个原子业务动作。

## 5. Redis 与 MySQL 一致性

推荐流程：

```text
Redis 只发放预约资格
→ Kafka 异步落 MySQL
→ MySQL 唯一索引和库存条件更新做最终校验
→ 成功写 result=SUCCESS
→ 失败补偿 Redis 库存并移除用户资格
```

MySQL 是最终事实源。Redis 预扣成功不等于预约最终成功。

需要处理的失败：

- Redis 成功，Kafka 发送失败；
- Kafka 成功，消费者处理失败；
- MySQL 已成功，消费者在提交 offset 前宕机；
- 补偿动作重复执行；
- Redis 重启或主从切换造成最近写丢失。

可采用本地消息表/Outbox、可靠生产、幂等消费者和定期对账。补偿操作本身也必须幂等。

## 6. 库存预热与恢复

秒杀开始前将 MySQL 可用库存加载到 Redis，并记录活动版本。不能在请求高峰临时回源数据库初始化库存，否则容易击穿。

恢复策略：

- 活动未开始：允许从 MySQL 重建 Redis；
- 活动进行中：结合已发资格、已落单和消息积压对账；
- Redis 不可用：热门预约快速失败或降级为排队，避免直接打穿 MySQL。

## 7. 热点缓存

活动详情使用 Cache Aside：

```text
Redis 命中 → 返回
未命中 → 互斥重建/逻辑过期 → 写 Redis
```

优化：

- TTL 加随机值，避免雪崩；
- 不存在 ID 缓存短期空值，防穿透；
- 极热点使用逻辑过期，由后台线程重建；
- 缓存值包含版本号，便于灰度和失效；
- 监控 hit/miss、内存、淘汰、慢命令、连接数。

## 8. 限流

可以用 Redis Lua 实现固定窗口、滑动窗口或令牌桶。限流应分层：

- 网关按 IP/用户进行粗粒度限流；
- 应用按用户、场次进行业务限流；
- Redis/Kafka/数据库分别设置容量保护。

限流返回明确错误和重试建议，不应让所有失败请求进入后端重试风暴。

## 9. 分布式锁不是万能方案

对每次预约加一个全局分布式锁会串行化整个热点，吞吐很低。库存预扣更适合 Lua 原子操作。

如果必须使用锁，要考虑：唯一 token、原子释放、租约过期、业务执行超过租约、主从切换和可重入。优先使用成熟客户端并理解其假设。

## 10. 可验证指标

Redis 方案至少验证：

- 初始库存 N，最终成功预约数不超过 N；
- 同一用户重复请求只成功一次；
- 同一 requestId 重放结果一致；
- 高并发下 Redis Lua P95/P99；
- 缓存命中率；
- Redis 故障时系统是否限流降级；
- Kafka 落单失败后 Redis 库存能否正确补偿；
- 对账任务能否发现 Redis/MySQL 差异。

简历亮点不是“用了 Redis”，而是“在故障和并发下仍能解释库存为什么正确”。

