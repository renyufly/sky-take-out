# 04 Kafka 事件驱动设计

## 1. Kafka 解决什么问题

热门预约的请求峰值可能远大于 MySQL 持续写入能力。Kafka 的核心价值是：

- **削峰**：快速接收事件，消费者按数据库可承受速度处理；
- **异步**：资格申请接口无需等待通知、报表等下游；
- **解耦**：预约服务发布事件，不直接调用所有下游；
- **可回放**：保留期内可重新消费，便于恢复和重建派生数据。

Kafka 不会自动让业务更快或强一致。引入后必须处理重复、乱序、积压、丢失和最终一致性。

## 2. Topic 设计

```text
reservation-requested-v1    已取得 Redis 资格，等待落单
reservation-confirmed-v1    MySQL 预约成功
reservation-failed-v1       预约失败，需要补偿或通知
reservation-cancelled-v1    用户/系统取消
reservation-expired-v1      超时未支付
reservation-notification-v1 通知任务
reservation-requested-retry-v1
reservation-requested-dlt-v1
```

事件使用过去式描述已发生事实。Topic 名带版本，便于 schema 演进。

事件示例：

```json
{
  "eventId": "01H...",
  "eventType": "ReservationRequested",
  "occurredAt": "2026-11-04T10:00:00Z",
  "requestId": "req-...",
  "userId": 1001,
  "sessionId": 2001,
  "quantity": 1,
  "traceId": "trace-...",
  "schemaVersion": 1
}
```

不要把手机号、身份证等敏感信息随意放入消息。

## 3. 分区键与顺序

Kafka 只保证单分区内有序，不保证整个 Topic 全局有序。

预约请求可以使用 `sessionId` 作为 key，使同一场次进入同一分区，便于按场次顺序处理。代价是超级热门场次可能形成热点分区。

替代思路：Redis 已原子分配资格后，数据库消费者只需幂等落单，可使用 `userId` 或 `requestId` 分散负载。选择 key 取决于真正需要的顺序约束。

面试时要说清：为什么需要顺序，以及热点 key 是否会限制扩容。

## 4. 生产者可靠性

重要配置思路：

```text
acks=all
enable.idempotence=true
合理的 retries、delivery.timeout.ms、request.timeout.ms
Broker replication.factor >= 3
min.insync.replicas >= 2
```

这些配置降低消息丢失风险，但应用仍要处理发送结果。不能在 Kafka 发送失败时仍向用户返回“排队成功”。

Redis 预扣与 Kafka 发送之间存在双写一致性问题。可选方案：

1. 发送失败立即执行幂等补偿；
2. 将待发送记录写入可靠存储，由后台重试；
3. 对数据库主链路使用 Transactional Outbox，再通过 CDC/后台发布到 Kafka。

## 5. 消费者幂等

Kafka 常见语义是至少一次：消息可能重复到达。消费者应依赖业务幂等，而不是期待消息永不重复。

防重复层次：

- `request_id` 唯一索引；
- `user_id + session_id` 唯一索引；
- `processed_event` 表记录 eventId；
- 订单状态条件更新；
- 外部通知使用业务幂等键。

处理流程：

```text
开始数据库事务
→ 检查/插入 eventId
→ 条件扣减最终库存
→ 插入 reservation
→ 提交事务
→ 成功后提交 Kafka offset
```

若数据库提交后、offset 提交前宕机，消息会被重新消费；唯一索引或 eventId 让重复处理安全返回。

## 6. 重试与死信

错误分为：

- 瞬时错误：数据库超时、短暂网络故障，可退避重试；
- 永久错误：数据格式错误、业务约束失败，不应无限重试。

推荐：主 Topic → 延迟重试 Topic → 多次失败后 DLT。重试使用指数退避并设置上限，避免故障时重试风暴。

死信不能只堆积，必须有告警、查询工具、人工处理和安全重放流程。

## 7. 消息积压

重点指标：

- consumer lag；
- 最老消息等待时间；
- 每秒生产/消费速率；
- 消费成功、重试、死信数量；
- 单条处理 P95/P99；
- 分区负载是否倾斜。

处理积压：

1. 先确认下游数据库是否已是瓶颈，不能盲目加消费者。
2. 若分区足够且下游有容量，增加消费者实例。
3. 批量拉取、批量写数据库。
4. 临时关闭非核心消费者或降级功能。
5. 热点分区需重新设计 key 或拆分 Topic。

消费者组内实例数超过分区数，多出的实例不会消费。

## 8. Exactly Once 的正确表述

Kafka 的幂等生产者和事务可以在 Kafka 内部读写链路实现更强的 Exactly Once 语义，但无法自动覆盖“Kafka + MySQL + Redis + 第三方通知”的端到端业务。

跨系统通常采用至少一次投递 + 幂等消费 + 对账补偿，实现业务上的 effectively-once。面试中这样表达更准确。

## 9. Kafka 的简历亮点写法

不建议：

> 使用 Kafka 提高系统性能，保证消息不丢失。

建议：

> 将热门场次资格校验与 MySQL 落单解耦，以 Kafka 缓冲瞬时请求；使用 sessionId 分区键维护场次内顺序，配置可靠生产，并通过 requestId 唯一索引、事务内事件去重、手动提交 offset、重试与死信主题处理至少一次投递。以 consumer lag 和最老消息年龄驱动告警及扩容。

## 10. 必做故障测试

- 生产者发送超时；
- 消费者数据库提交前宕机；
- 数据库提交后、offset 提交前宕机；
- 同一事件重复投递 10 次；
- 消费者处理毒消息；
- Kafka 一个 Broker 不可用；
- 消费者暂停导致 lag 增长，恢复后能否追平；
- 补偿事件重复执行是否安全。

