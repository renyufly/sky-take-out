# 05 Service 业务层与事务

## 1. Service 不是 Mapper 转发器

Service 的价值是**表达业务规则**、组织多个数据操作并划定事务边界。

项目中的好例子：删除分类前检查关联关系。

```java
public void deleteById(Long id) {
    if (dishMapper.countByCategoryId(id) > 0) {
        throw new DeletionNotAllowedException("分类关联了菜品");
    }
    if (setmealMapper.countByCategoryId(id) > 0) {
        throw new DeletionNotAllowedException("分类关联了套餐");
    }
    categoryMapper.deleteById(id);
}
```

这段逻辑表达了“不满足前置条件则拒绝操作”，比把三个 Mapper 调用直接写进 Controller 更容易复用和测试。

## 2. 一对多写入与事务

`DishServiceImpl.saveWithFlavor` 完成两个表的写入：

```java
@Transactional
public void saveWithFlavor(DishDTO dto) {
    Dish dish = new Dish();
    BeanUtils.copyProperties(dto, dish);
    dishMapper.insert(dish);

    for (DishFlavor flavor : dto.getFlavors()) {
        flavor.setDishId(dish.getId());
    }
    dishFlavorMapper.insertBatch(dto.getFlavors());
}
```

如果菜品插入成功、口味插入失败，事务会让前一个 INSERT 回滚，从而保证“全部成功或全部失败”。

**事务通常应该放在 Service 公共方法上**，因为 Service 最清楚完整业务操作的边界。

## 3. 用户下单的业务编排

`OrderServiceImpl.submitOrder` 是复杂业务的典型：

1. 查询并校验收货地址。
2. 查询当前用户购物车并校验非空。
3. 从 DTO、地址、当前用户构造订单主表数据。
4. 插入订单并取得主键。
5. 将购物车快照转换为订单明细并批量插入。
6. 清空当前用户购物车。
7. 构造 `OrderSubmitVO`。
8. 发送 WebSocket 消息。

前三个数据库写操作必须位于同一事务中：

```text
INSERT orders
INSERT order_detail
DELETE shopping_cart
```

任何一步失败都应回滚。

## 4. Spring 事务原理

`@Transactional` 通常基于 Spring AOP 代理：调用代理对象的公开方法时，代理先开启事务，**方法成功后提交，抛出指定异常时回滚**。

默认传播行为 `REQUIRED`：当前有事务则加入，没有则新建。

常用属性：

```java
@Transactional(
    rollbackFor = Exception.class,
    propagation = Propagation.REQUIRED,
    isolation = Isolation.DEFAULT,
    timeout = 5
)
```

默认情况下，Spring 对 `RuntimeException` 和 `Error` 回滚，对受检异常不一定回滚。涉及受检异常时应明确 `rollbackFor`，或合理转换为业务运行时异常。

## 5. 事务失效的常见场景

这是面试高频题：

1. 同一个类内部用 `this.method()` 调用带事务的方法，绕过代理。
2. 方法不是可代理的公开方法，例如 private 方法。
3. 对象不是 Spring Bean，而是手动 `new` 出来的。
4. 异常被 catch 后吞掉，代理认为方法正常结束。
5. 抛出受检异常，但没有配置回滚。
6. 数据库引擎不支持事务。
7. 多数据源没有使用正确的事务管理器。
8. 异步线程中的数据库操作不自动继承原线程事务。

## 6. 本项目中的事务审查

`DishServiceImpl.updateWithFlavor` 会：

```text
更新 dish → 删除旧 dish_flavor → 插入新 dish_flavor
```

该方法目前没有 `@Transactional`。若插入新口味失败，菜品已经更新且旧口味已删除，会产生不一致。面试中可以指出应给该方法增加事务。

`OrderServiceImpl.submitOrder` 在事务内发送 WebSocket。即使数据库最终回滚，消息也可能已经发出；反过来，消息失败也可能让数据库事务回滚。生产系统常用“事务提交后发送”、事务消息或 Outbox 表处理数据库与外部消息的一致性。

## 7. 状态机思维

订单不是任意修改 status，而是**有限状态流转**：

```text
待付款 → 待接单 → 已接单 → 派送中 → 已完成
   └──────────────→ 已取消
```

**每个动作应校验当前状态**，例如只有“待接单”才能接单。并发场景最好使用带旧状态条件的更新：

```sql
UPDATE orders
SET status = 3
WHERE id = ? AND status = 2;
```

再检查影响行数。相比“先查状态再更新”，这种方式能避免两个请求同时通过校验。

## 8. 幂等性

**幂等表示同一个请求执行多次**，最终业务**效果与执行一次相同**。

必须重点考虑：

- 创建订单重复点击；
- 支付回调重复通知；
- 接单、取消等状态操作重试；
- 定时任务重复执行。

常用方案：唯一业务号、唯一索引、状态条件更新、幂等记录表、分布式锁。支付回调至少应根据订单号查询当前支付状态；已经支付则直接返回成功，而不是重复更新或重复触发后续动作。

## 9. 业务层代码质量

- 一个方法只处理一个清晰用例，**过长时拆分**私有方法或领域服务。
- **先校验，再修改数据**；失败尽早返回。
- 常量或枚举代替魔法数字。
- 不使用 double 计算金额。
- 数据库查询结果可能为空，要明确处理。
- 对外部服务设置超时、重试、熔断策略；重试必须结合幂等。
- 日志记录业务标识，如 orderId，而非泄漏隐私信息。

## 10. 自测题

1. 为什么事务通常写在 Service 层？
2. Spring 声明式事务基于什么原理？
3. 默认哪些异常会触发回滚？
4. 同类内部调用为什么可能使事务失效？
5. 数据库事务能保证 WebSocket 消息和数据库同时成功吗？
6. 如何让订单状态更新在并发下更安全？
7. 支付回调如何保证幂等？

