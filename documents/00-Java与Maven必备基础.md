# 00 Java 与 Maven 必备基础

本章只讲阅读和编写当前项目必需的 Java 基础。学习时请直接在项目中搜索对应类。

## 1. 类、接口和实现

项目用接口定义能力，用实现类完成逻辑：

```java
public interface CategoryService {
    void save(CategoryDTO dto);
}

@Service
public class CategoryServiceImpl implements CategoryService {
    @Override
    public void save(CategoryDTO dto) { ... }
}
```

面向接口的好处是**调用方依赖抽象**，便于替换实现、单元测试和 AOP 代理。只有一个简单实现时接口不是绝对必要，但在典型分层项目中有助于明确边界。

## 2. 封装、继承、多态

- 封装：对象**隐藏内部状态**，通过方法提供能力。
- 继承：**子类复用**父类行为，本项目业务异常继承 `BaseException`。
- 多态：父类型引用可以**指向不同子类型**，**异常处理器可统一捕获** `BaseException` 的子类。

不要为了复用几行代码滥用继承；组合通常更灵活。

## 3. 泛型

`Result<T>` 中的 `T` 表示响应数据类型：

```java
Result<EmployeeLoginVO>
Result<List<Category>>
Result<PageResult>
```

**泛型提供编译期类型安全，避免到处强制转换**。Java 泛型主要通过类型擦除实现，运行时通常不能直接获得完整的 `T`。

通配符：

- `? extends T`：适合**读取 T** 的子类型，通常不便写入。
- `? super T`：适合**写入 T**，读取只能按 Object。
- 记忆 PECS：Producer Extends，Consumer Super。

## 4. 集合

项目大量使用：

```java
List<ShoppingCart> shoppingCartList;
Map<String, Object> claims = new HashMap<>();
```

常见集合：

| 类型 | 特点 | 常见场景 |
| --- | --- | --- |
| ArrayList | 数组结构，随机访问快 | 查询结果、DTO 列表 |
| LinkedList | 链表结构 | 更适合特定头尾操作，业务中较少直接需要 |
| HashMap | 哈希表，平均 O(1) 查找 | JWT claims、临时映射 |
| HashSet | **去重**，平均 O(1) 判断包含 | ID 去重 |
| ConcurrentHashMap | 线程**安全并发** Map | 多线程共享状态 |

`ArrayList`、`HashMap` 不是线程安全的。局部变量通常属于当前请求线程，可以安全使用；共享可变字段需要并发控制。

## 5. Lambda 与 Stream

项目给口味设置菜品 ID：

```java
flavors.forEach(flavor -> flavor.setDishId(id));
```

常见 Stream：

```java
List<Long> ids = dishes.stream()
        .filter(d -> d.getStatus() == 1)
        .map(Dish::getId)
        .distinct()
        .collect(Collectors.toList());
```

Stream 适合声明式集合转换，但复杂业务循环使用普通 `for` 往往更容易调试。避免在 Stream 中隐藏数据库调用，否则容易产生 N+1 查询。

## 6. 异常

Java 异常分为：

- 受检异常：编译器要求捕获或声明，例如部分 IO 异常。
- 运行时异常：`RuntimeException` 子类，通常表示业务失败或程序错误。
- Error：严重 JVM 问题，通常不由业务捕获。

项目的业务异常继承运行时异常，便于统一处理并触发事务回滚。

原则：**只捕获你能处理的异常**；不要空 catch；记录日志时保留堆栈；对外不要泄露数据库和服务器内部信息。

## 7. BigDecimal 与金额

`DishDTO.price`、订单金额使用 `BigDecimal`。浮点数不能精确表示许多十进制小数：

```java
BigDecimal a = new BigDecimal("0.1");
BigDecimal b = new BigDecimal("0.2");
BigDecimal sum = a.add(b);
```

优先使用字符串构造或 `BigDecimal.valueOf`，不要 `new BigDecimal(0.1)`。比较数值用 `compareTo`，不要只依赖 `equals`，因为 `1.0` 与 `1.00` 的 scale 不同。

## 8. 日期时间

项目使用 Java 8 时间 API：

- `LocalDate`：日期；
- `LocalTime`：时间；
- `LocalDateTime`：不含时区的日期时间；
- `Instant`：UTC 时间线上的瞬间；
- `ZonedDateTime`：带时区。

数据库、服务器、JSON 和客户端必须约定时区。**跨地区系统更适合存 UTC 时间**，展示时转换为用户时区。

## 9. 注解与反射

注解是元数据，例如 `@RestController`、`@Transactional`、`@AutoFill`。框架在运行时扫描注解并执行相应逻辑。

`AutoFillAspect` 使用反射调用实体 setter：

```java
Method method = entity.getClass()
        .getDeclaredMethod("setUpdateTime", LocalDateTime.class);
method.invoke(entity, now);
```

反射灵活但缺少编译期检查、性能更低且调试更难。框架和通用基础设施适合使用，普通业务逻辑优先直接调用。

## 10. Lombok

本项目常见注解：

```java
@Data               // getter/setter、toString、equals/hashCode
@Builder            // 建造者
@NoArgsConstructor  // 无参构造器
@AllArgsConstructor // 全参构造器
@Slf4j              // log 字段
```

Lombok **通过编译期注解处理器生成代码**。使用 `@Data` 时注意实体关联、敏感字段打印以及可变对象的 equals/hashCode。

## 11. Maven 多模块

根 POM 的 packaging 是 `pom`，声明三个 module，并在 `dependencyManagement` 中统一版本。

区别：

- `dependencyManagement` 只管理版本，不会自动引入依赖。
- `dependencies` 才真正引入依赖。
- 子模块继承父 POM，可省略已管理的版本号。

常用命令：

```bash
mvn clean             # 删除 target
mvn test              # 编译并运行测试
mvn package           # 打包
mvn clean package     # 清理后重新打包
mvn -pl sky-server -am package  # 构建 server 及其依赖模块
```

Maven 生命周期主要包含 validate、compile、test、package、verify、install、deploy。后一个阶段会执行前面的阶段。

## 12. JVM 与并发面试基础

至少能解释：

- **堆主要存对象**；线程栈存方法调用帧和局部变量；方法区/元空间存类元数据。
- **GC 回收不可达对象**；静态集合、未清理 ThreadLocal 等可能造成内存泄漏。
- `synchronized` 提供互斥和可见性；`volatile` 提供可见性和一定有序性，但不能保证复合操作原子性。
- **线程池复用线程**，减少创建开销，但需合理设置核心线程数、队列、最大线程数和拒绝策略。
- `ThreadLocal` 提供线程隔离，不等于线程安全的共享变量方案。

## 13. 自测题

1. 接口与抽象类有什么区别？
2. `ArrayList` 与 `LinkedList` 如何选择？
3. HashMap 为什么平均查询是 O(1)，Java 8 冲突严重时如何处理？
4. 受检异常与运行时异常有什么区别？
5. `BigDecimal` 为什么适合金额？
6. `dependencyManagement` 与 `dependencies` 有什么区别？
7. `volatile` 能保证 `count++` 原子吗？为什么？

