# 苍穹外卖 Java 后端学习手册

这套文档以当前 `sky-take-out` 项目为教材，目标不是背 API，而是建立一套可以复用到其他项目的 Java 后端开发思路：

```text
需求分析 → 接口设计 → 数据库建模 → DTO/Entity/VO
       → Controller → Service → Mapper → MySQL
       → 事务、异常、认证、缓存 → 测试与排错
```

## 推荐学习顺序

| 顺序 | 文档 | 学完后应能做到 |
| --- | --- | --- |
| 1 | [00-Java与Maven必备基础.md](./00-Java与Maven必备基础.md) | 掌握阅读本项目所需的 Java、集合、异常、泛型和 Maven 基础 |
| 2 | [01-项目全景与开发流程.md](./01-项目全景与开发流程.md) | 说清一个 Spring Boot 请求如何走完整个后端 |
| 3 | [02-SpringBoot与Web接口开发.md](./02-SpringBoot与Web接口开发.md) | 独立编写 REST CRUD 接口，理解 IOC、分层和参数绑定 |
| 4 | [03-MySQL数据库设计与SQL.md](./03-MySQL数据库设计与SQL.md) | 设计常见业务表、索引和关联关系，写常用 SQL |
| 5 | [04-MyBatis数据访问.md](./04-MyBatis数据访问.md) | 使用注解和 XML 完成动态 SQL、批量操作、分页和主键回填 |
| 6 | [05-Service业务层与事务.md](./05-Service业务层与事务.md) | 编排多表业务，正确使用事务并解释失效场景 |
| 7 | [06-认证异常AOP与通用设计.md](./06-认证异常AOP与通用设计.md) | 实现 JWT 登录、拦截器、统一响应、异常处理和公共字段填充 |
| 8 | [07-Redis缓存WebSocket与定时任务.md](./07-Redis缓存WebSocket与定时任务.md) | 掌握常见中间件和异步/实时能力的基本写法 |
| 9 | [08-从零开发功能与面试复习.md](./08-从零开发功能与面试复习.md) | 按标准步骤开发新功能，并回答常见 Java 后端面试题 |
| 10 | [09-测试调试构建与交付.md](./09-测试调试构建与交付.md) | 为功能编写测试、定位问题并完成构建交付 |

## 学习方法

每章建议执行四步：

1. 先读“核心概念”，用自己的话复述。
2. 打开文档列出的项目源码，对照调用链逐行阅读。
3. 不复制原代码，自己实现一个类似功能，例如“优惠券分类 CRUD”。
4. 回答章末自测题，并将答案说出声，模拟面试表达。

不要只记注解。面试官更关心：为什么分层、数据怎样流动、失败时如何保证一致性、并发和缓存会带来什么问题。

## 项目中的三条核心练习链路

### 链路一：分类 CRUD（基础）

```text
CategoryController
  → CategoryService / CategoryServiceImpl
  → CategoryMapper.java + CategoryMapper.xml
  → category 表
```

你会学到参数绑定、DTO、统一响应、动态 SQL、分页和业务异常。

### 链路二：新增菜品及口味（一对多＋事务）

```text
DishController.save
  → DishServiceImpl.saveWithFlavor
  → dishMapper.insert（主键回填）
  → dishFlavorMapper.insertBatch（批量写入口味）
```

你会学到一对多关系、生成主键、批量插入和事务原子性。

### 链路三：用户下单（业务编排）

```text
校验地址与购物车
  → 写入 orders
  → 批量写入 order_detail
  → 清空 shopping_cart
  → 返回 OrderSubmitVO
  → WebSocket 通知管理端
```

你会学到复杂业务拆解、状态机、多表事务和外部副作用。

## 阅读源码时的约定

- `DTO`：接收客户端输入或在层之间传递数据。
- `Entity`：与数据库表字段接近的数据对象。
- `VO`：专门返回给客户端的展示对象。
- `Result<T>`：统一接口响应包装。
- `PageResult`：统一分页结果，包含总数和当前页记录。
- `BaseContext`：用 `ThreadLocal` 保存当前请求的用户 ID。

## 完成标准

学完后，你应当能在白板或纸上画出本项目架构，并在 10 分钟内讲清：

1. 一个 HTTP 请求从 Controller 到 MySQL 的全过程。
2. MyBatis 的 `#{}`、动态 SQL、分页和主键回填。
3. 为什么多表写操作需要事务，事务可能怎样失效。
4. JWT 登录与拦截器如何配合，为什么需要 `ThreadLocal`。
5. 缓存的读写策略、缓存一致性和常见风险。
6. 如何设计统一响应、业务异常和全局异常处理。
7. 如何从需求开始独立完成一个后端功能。

## 进阶：简历与系统设计

完成基础教程后，继续阅读 [interview/README.md](./interview/README.md)。该部分讲解如何把外卖教学项目真实改造成“限量活动与场馆预约平台”，并围绕 Redis 秒杀库存、Kafka 异步落单、Docker/Kubernetes、P95/P99 压测和系统扩展形成可验证的简历项目。

