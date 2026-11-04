# 07 Redis、缓存、WebSocket 与定时任务

## 1. Redis 在项目中的用途

本项目有三个典型场景：

- `SHOP_STATUS`：保存店铺营业状态；
- `dish_{categoryId}`：缓存分类下的在售菜品；
- `setmealCache::{categoryId}`：通过 Spring Cache **缓存套餐**。

Redis 是内存数据库，常用于缓存、计数、会话、分布式锁、排行榜和消息场景，但不能把它简单理解为“更快的 MySQL”。

## 2. Cache Aside 模式

用户查询菜品的逻辑：

```text
读 Redis
  ├─ 命中 → 直接返回
  └─ 未命中 → 查 MySQL → 写 Redis → 返回
```

项目代码：

```java
String key = "dish_" + categoryId;
List<DishVO> list = (List<DishVO>) redisTemplate.opsForValue().get(key);
if (list != null && !list.isEmpty()) return Result.success(list);

list = dishService.listWithFlavor(dish);
redisTemplate.opsForValue().set(key, list);
```

写操作通常采用“先更新数据库，再删除缓存”。下次读请求会重新加载最新数据。项目管理端新增/修改/删除菜品后会清理 `dish_*`。

## 3. 缓存一致性与风险

### 缓存穿透

**查询不存在的数据，每次都打到数据库**。方案：参数校验、缓存空值、布隆过滤器。缓存空值必须设置较短 TTL。

本项目用 `list != null && list.size() > 0` 判断命中，因此空列表即使缓存了仍会查询数据库，存在穿透风险。

### 缓存击穿

单个热点 key 失效，**大量请求同时查询数据库**。方案：互斥锁、逻辑过期、热点预热。

### 缓存雪崩

**大量 key 同时过期或 Redis 故障**。方案：过期时间增加随机值、多级缓存、限流降级、高可用 Redis。

### 一致性

数据库与缓存无法靠简单操作做到强一致。常见做法是更新数据库后删除缓存，并结合重试、消息队列或延迟双删处理特殊竞争窗口。是否需要更强方案取决于业务一致性要求。

## 4. TTL 与序列化

项目写缓存时没有设置过期时间：

```java
redisTemplate.opsForValue().set(key, list);
```

生产中通常设置 TTL，避免陈旧数据永久存在：

```java
redisTemplate.opsForValue().set(key, list, 30, TimeUnit.MINUTES);
```

还应明确序列化方式。JDK 序列化可读性和跨版本能力较弱，**常用 JSON 序列化**，但要控制类型信息和安全配置。

`KEYS dish_*` 会扫描整个 key 空间，数据量大时可能阻塞 Redis。生产清理模式 key 应使用 `SCAN`，或维护版本号/集合以精确删除。

## 5. Spring Cache

套餐查询使用：

```java
@Cacheable(cacheNames = "setmealCache", key = "#categoryId")
```

语义：调用方法前先查缓存，命中则不执行方法；未命中则执行并缓存返回值。

管理端修改后使用：

```java
@CacheEvict(cacheNames = "setmealCache", allEntries = true)
```

常见注解：

- `@Cacheable`：查缓存，没有则执行并写入。
- `@CachePut`：总是执行方法并更新缓存。
- `@CacheEvict`：删除缓存。

Spring Cache 基于代理，同类内部调用同样可能绕过缓存代理。

## 6. WebSocket

HTTP 通常是客户端发起、服务端响应；**WebSocket 建立长连接后，服务端也能主动推送消息**。

项目端点：

```java
@ServerEndpoint("/ws/{sid}")
public class WebSocketServer { ... }
```

应用场景：

- 新订单通知管理端；
- 用户催单通知管理端。

生产注意点：

- 连接需要认证，不能只相信 sid；
- 做**心跳、超时、断线重连和连接数限制**；
- **多实例部署**时，本机内存中的连接只属于单个实例，需**借助 Redis Pub/Sub 或消息队列广播**；
- 消息要有类型、业务 ID、时间和版本；
- WebSocket 发送失败不能破坏数据库核心事务。

## 7. 定时任务

项目 `OrderTask`：

```java
@Scheduled(cron = "0 * * * * ?")
public void processTimeoutOrder() { ... }
```

每分钟处理超过 15 分钟未支付的订单。另一个任务每天凌晨 1 点处理派送中订单。

Cron 六段常见格式：

```text
秒 分 时 日 月 星期
0  *  *  *  *  ?     每分钟整点
0  0  1  *  *  ?     每天 01:00
```

单机 `@Scheduled` 很简单，但多实例部署时每个实例都会执行。常用方案：分布式锁、数据库抢占、Quartz、XXL-JOB 等调度平台。

任务必须可重入、幂等，并记录扫描数量、成功数、失败数和耗时。大量数据要分页处理，避免一次加载全部记录。

## 8. 项目定时任务审查

`processDeliveryOrder` 计算了“一小时前”的 `time`，但查询时实际传入 `LocalDateTime.now()`，导致条件与注释不一致。代码审查时要检查“计算出来的变量是否真正使用”，并为时间边界编写测试。

状态更新也应加入旧状态条件，确保重复执行不产生副作用。

## 9. 什么时候使用消息队列

本项目没有消息队列，但面试常问。适合 MQ 的场景：

- 异步：**下单后异步发通知**；
- 解耦：订单服务不直接依赖所有下游；
- 削峰：**瞬时请求进入队列后平稳消费**。

代价是引入最终一致性、重复消费、消息丢失、顺序和积压问题。消费者必须幂等，关键消息需考虑可靠投递。

## 10. 高频面试题

1. Redis 为什么快？
2. Cache Aside 的读写流程是什么？
3. 穿透、击穿、雪崩分别是什么？
4. 为什么更新数据库后通常删除缓存，而不是直接更新缓存？
5. Redis 分布式锁需要注意什么？
6. WebSocket 与 HTTP 有什么区别？
7. 多实例下 WebSocket 广播如何实现？
8. `@Scheduled` 多实例重复执行怎样处理？
9. 为什么定时任务和消息消费者必须幂等？

