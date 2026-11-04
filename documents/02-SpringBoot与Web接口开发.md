# 02 Spring Boot 与 Web 接口开发

## 1. IOC、Bean 与依赖注入

Spring 的核心思想是：对象不由业务代码到处 `new`，而是交给容器创建和管理。

本项目中的常见组件：

```java
@RestController  // Controller Bean
@Service         // Service Bean
@Component       // 普通组件 Bean
@Configuration   // 配置类 Bean
@Mapper          // MyBatis 生成代理对象
```

项目使用字段注入：

```java
@Autowired
private CategoryService categoryService;
```

更推荐构造器注入，因为依赖明确、便于测试、字段可设为 `final`：

```java
@RestController
@RequestMapping("/admin/category")
public class CategoryController {
    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }
}
```

如果只有一个构造器，现代 Spring 可省略 `@Autowired`。

## 2. REST 接口的基本写法

本项目分类接口完整展示了 CRUD：

| 需求 | HTTP 方法 | 路径 | 注解 |
| --- | --- | --- | --- |
| 新增分类 | POST | `/admin/category` | `@PostMapping` |
| 查询分页 | GET | `/admin/category/page` | `@GetMapping("/page")` |
| 修改分类 | PUT | `/admin/category` | `@PutMapping` |
| 删除分类 | DELETE | `/admin/category?id=1` | `@DeleteMapping` |
| 改状态 | POST | `/admin/category/status/1?id=1` | `@PathVariable` + 查询参数 |

Controller 示例：

```java
@RestController
@RequestMapping("/admin/category")
public class CategoryController {
    @PostMapping
    public Result<String> save(@RequestBody CategoryDTO dto) {
        categoryService.save(dto);
        return Result.success();
    }
}
```

`@RestController` 等价于 `@Controller + @ResponseBody`，方法返回值会被消息转换器序列化为 JSON。

## 3. 四种常见参数绑定

### JSON 请求体：`@RequestBody`

```java
public Result save(@RequestBody CategoryDTO categoryDTO)
```

请求：

```json
{"name":"热销菜品","type":1,"sort":1}
```

### 查询参数

```java
public Result deleteById(Long id)
```

请求：`DELETE /admin/category?id=10`。简单参数可按名称自动绑定。

### 路径参数：`@PathVariable`

```java
@PostMapping("/status/{status}")
public Result startOrStop(@PathVariable Integer status, Long id)
```

### 对象参数

```java
public Result<PageResult> page(CategoryPageQueryDTO dto)
```

请求中的 `page`、`pageSize`、`name`、`type` 会绑定到对象同名属性。

## 4. 应当补上的参数校验

当前项目 DTO 基本没有校验注解。实际开发建议引入 Bean Validation：

```java
public class CategoryDTO {
    @NotBlank(message = "分类名称不能为空")
    private String name;

    @NotNull
    @Min(1)
    @Max(2)
    private Integer type;
}
```

Controller 使用：

```java
public Result save(@Valid @RequestBody CategoryDTO dto)
```

然后在全局异常处理器中处理 `MethodArgumentNotValidException`。校验分两类：

- 格式校验放 DTO，例如非空、长度、邮箱格式；
- 业务校验放 Service，例如“启售菜品不能删除”。

## 5. DTO、Entity 和 VO

本项目 `DishDTO` 接收菜品及口味；`Dish` 对应菜品表；`DishVO` 返回分类名称和口味列表。

```text
DishDTO: name, categoryId, price, flavors ...
Dish:    dish 表字段
DishVO:  dish 字段 + categoryName + flavors
```

不要直接用 Entity 接收所有请求，否则客户端可能修改 `createUser`、`createTime` 等不应开放的字段。

项目使用 `BeanUtils.copyProperties(source, target)` 做同名属性复制。它适合简单对象，但要注意：

- 只是浅拷贝；
- 同名字段类型必须兼容；
- 复杂映射建议使用显式转换或 MapStruct；
- 修改接口要防止 null 覆盖已有字段。

## 6. 统一响应

项目的 `Result<T>`：

```java
class Result<T> {
    private Integer code;
    private String msg;
    private T data;
}
```

成功时：

```json
{"code":1,"msg":null,"data":{"id":1,"name":"热销"}}
```

统一响应的价值：前端可以用相同逻辑判断成功、显示错误和读取数据。

生产项目常见增强：请求追踪 ID、时间戳、更细的错误码，并让 HTTP 状态码表达协议层结果，例如参数错误返回 400、未认证返回 401。

## 7. 分页接口

本项目使用 PageHelper：

```java
PageHelper.startPage(dto.getPage(), dto.getPageSize());
Page<Category> page = categoryMapper.pageQuery(dto);
return new PageResult(page.getTotal(), page.getResult());
```

关键点：`startPage` 必须紧邻需要分页的查询；它通过 MyBatis 插件拦截下一条查询并添加分页 SQL。

接口响应一般包含：

```json
{"total":53,"records":[...]}
```

## 8. 接口设计面试要点

- GET 查询应尽量无副作用；POST 新增；PUT 完整或语义明确的修改；DELETE 删除。
- 资源名尽量使用名词，版本化可使用 `/api/v1/...`。
- 列表必须考虑分页，不能无限返回。
- 新增操作应考虑重复提交；支付、回调必须实现幂等。
- 不在日志中打印密码、完整 token、身份证等敏感数据。
- 接口变更要考虑向后兼容。

## 9. 动手练习

参考 `CategoryController` 独立实现“公告 CRUD”：

1. 设计 `NoticeDTO`、`Notice`、`NoticeVO`。
2. 编写新增、分页、修改、删除、发布接口。
3. 添加参数校验。
4. 使用构造器注入。
5. 为“已发布公告不能直接删除”编写 Service 业务校验。

