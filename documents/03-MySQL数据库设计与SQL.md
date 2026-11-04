# 03 MySQL 数据库设计与 SQL

## 1. 从业务需求推导表结构

本项目的核心关系：

```text
category 1 ── N dish 1 ── N dish_flavor
category 1 ── N setmeal N ── N dish
user     1 ── N address_book
user     1 ── N shopping_cart
user     1 ── N orders 1 ── N order_detail
```

数据库设计步骤：

1. 找实体：用户、菜品、订单。
2. 找属性：名称、金额、状态、时间。
3. 找关系：一对一、一对多、多对多。
4. 决定约束和索引。
5. 考虑数据生命周期、并发和查询场景。

多对多通常拆为中间表。本项目套餐与菜品通过 `setmeal_dish` 表关联。

## 2. 以分类表为例理解字段设计

根据 `Category` 实体和 SQL，可推导出近似结构：

```sql
CREATE TABLE category (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    type        INT NOT NULL COMMENT '1菜品分类 2套餐分类',
    name        VARCHAR(32) NOT NULL,
    sort        INT NOT NULL DEFAULT 0,
    status      INT NOT NULL DEFAULT 0 COMMENT '0禁用 1启用',
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    create_user BIGINT,
    update_user BIGINT,
    UNIQUE KEY uk_category_name (name),
    KEY idx_category_type_status (type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

这是学习用推导，实际应以真实建表脚本为准；当前仓库没有 SQL 初始化文件。

字段选型要点：

- 主键常用 `BIGINT`，不要把有业务含义的字段直接当主键。
- 金额使用 `DECIMAL`，Java 对应 `BigDecimal`，不要用 float/double。
- 时间使用 `DATETIME` 或 `TIMESTAMP`，明确时区策略。
- 状态字段可用 `TINYINT/INT`，代码中使用常量或枚举，避免魔法数字。
- 字符集优先 `utf8mb4`。

## 3. 一对多和多对多

### 菜品与口味：一对多

`dish_flavor.dish_id` 指向 `dish.id`。新增菜品时先插入主表获得菜品 ID，再批量插入口味。

### 套餐与菜品：多对多

中间表通常包含：

```sql
setmeal_dish(id, setmeal_id, dish_id, name, price, copies)
```

建议为外键列建立索引：

```sql
CREATE INDEX idx_setmeal_dish_setmeal_id ON setmeal_dish(setmeal_id);
CREATE INDEX idx_setmeal_dish_dish_id ON setmeal_dish(dish_id);
```

是否使用数据库外键要按团队规范决定。外键能保证引用完整性，但会增加写入约束和迁移复杂度；不使用外键时必须在应用层严格维护一致性。

## 4. CRUD SQL

### 插入

```sql
INSERT INTO category(type, name, sort, status, create_time, update_time)
VALUES (1, '热销', 1, 0, NOW(), NOW());
```

### 查询

```sql
SELECT *
FROM category
WHERE status = 1 AND type = 1
ORDER BY sort ASC, create_time DESC;
```

### 修改

```sql
UPDATE category
SET name = '热门菜品', update_time = NOW()
WHERE id = 1;
```

必须有明确的 `WHERE`；无条件 UPDATE/DELETE 是严重事故来源。

### 删除

项目使用物理删除。业务系统也常使用逻辑删除字段 `deleted`，保留历史数据。选择取决于合规、审计、数据量和查询复杂度。

## 5. JOIN 与项目实例

`DishMapper.xml` 的分页查询通过左连接获取分类名：

```sql
SELECT d.*, c.name AS categoryName
FROM dish d
LEFT JOIN category c ON d.category_id = c.id
WHERE d.status = 1
ORDER BY d.create_time DESC;
```

- `INNER JOIN`：只保留两边能匹配的行。
- `LEFT JOIN`：保留左表全部数据，右表无匹配时为 NULL。

面试中要能解释执行逻辑、连接字段上的索引，以及为什么不能随意 `SELECT *`。

## 6. 索引基础

索引适合：高频查询条件、连接字段、排序字段，以及选择性较好的列。

联合索引遵循最左前缀。例如 `(type, status, sort)` 可支持以 `type` 开头的条件组合，但通常不能高效支持只按 `status` 查询。

索引并非越多越好：每个索引都占空间，并增加 INSERT/UPDATE/DELETE 的维护成本。

慢 SQL 排查流程：

1. 从慢查询日志或监控找到 SQL。
2. 使用 `EXPLAIN` 查看访问类型、使用的索引、扫描行数、额外操作。
3. 检查是否索引失效、返回过多列、深分页、N+1 查询。
4. 修改索引或 SQL 后重新测量，不凭感觉优化。

## 7. 事务和隔离级别

事务具有 ACID：原子性、一致性、隔离性、持久性。

MySQL InnoDB 常见隔离级别：读未提交、读已提交、可重复读、串行化。InnoDB 默认通常是可重复读，并通过 MVCC 和锁处理并发。

常见并发问题：脏读、不可重复读、幻读、丢失更新。业务还要考虑超卖：仅“先查库存再更新”可能竞争，应使用带条件的原子更新或合适的锁。

```sql
UPDATE product
SET stock = stock - 1
WHERE id = ? AND stock > 0;
```

检查影响行数是否为 1，即可判断扣减是否成功。

## 8. 项目 SQL 的可审查点

在 `DishMapper.xml` 的 `list` 查询中存在：

```sql
name like contact('%', #{name}, '%')
```

MySQL 正确函数应为 `concat`。这类拼写问题说明：动态分支如果测试没有覆盖，错误可能长期隐藏。

另一个常见问题是 N+1：`DishServiceImpl.listWithFlavor` 先查菜品列表，再循环为每个菜品查一次口味。菜品数为 N 时产生 N+1 次查询。可通过一次批量查询、JOIN 或结果映射优化。

## 9. 高频面试题

1. `VARCHAR` 与 `CHAR` 的区别是什么？
2. 为什么金额使用 `DECIMAL` 和 `BigDecimal`？
3. 联合索引的最左前缀是什么？
4. 什么情况会导致索引失效？
5. `LEFT JOIN` 与 `INNER JOIN` 的区别？
6. InnoDB 为什么适合事务业务？
7. MVCC 解决什么问题？
8. 如何发现并优化慢 SQL？
9. 如何防止库存超卖？
10. 逻辑删除和物理删除各有什么取舍？

