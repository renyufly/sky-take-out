# 06 认证、异常、AOP 与通用设计

## 1. JWT 登录链路

管理端登录流程：

```text
POST /admin/employee/login
  → EmployeeService 校验用户名、密码、账号状态
  → Controller 使用员工 ID 生成 JWT
  → 客户端保存 token
  → 后续请求把 token 放入请求头
```

项目生成 token 的核心：

```java
Map<String, Object> claims = new HashMap<>();
claims.put("empId", employee.getId());
String token = JwtUtil.createJWT(secretKey, ttl, claims);
```

JWT 通常由 Header、Payload、Signature 三部分组成。Payload 只是 Base64URL 编码，不是加密，不能放密码等敏感数据。签名用于防篡改。

## 2. 拦截器校验

`JwtTokenAdminInterceptor`：

```java
String token = request.getHeader(tokenName);
Claims claims = JwtUtil.parseJWT(secretKey, token);
Long empId = Long.valueOf(claims.get("empId").toString());
BaseContext.setCurrentId(empId);
```

`WebMvcConfiguration` 注册拦截规则：

```java
registry.addInterceptor(jwtTokenAdminInterceptor)
        .addPathPatterns("/admin/**")
        .excludePathPatterns("/admin/employee/login");
```

认证回答“你是谁”；授权回答“你能做什么”。本项目主要实现了认证，尚没有基于角色/权限的细粒度授权。

## 3. Filter、Interceptor、AOP 的区别

| 机制 | 工作层次 | 常见用途 |
| --- | --- | --- |
| Filter | Servlet 容器 | 编码、CORS、通用请求包装、安全过滤 |
| Interceptor | Spring MVC | 登录校验、Controller 前后处理 |
| AOP | Spring 方法调用 | 事务、日志、审计、公共字段填充 |

选择原则：与 HTTP/Servlet 相关用 Filter；与 MVC Handler 相关用 Interceptor；与业务方法调用相关用 AOP。

## 4. ThreadLocal 保存当前用户

`BaseContext` 使用：

```java
private static final ThreadLocal<Long> threadLocal = new ThreadLocal<>();
```

同一个 HTTP 请求通常由同一线程处理，因此拦截器写入的用户 ID 能在 Service、AOP 中读取，而不必层层传参。

必须清理：Web 线程会在线程池中复用。如果请求结束后不 `remove()`，旧值可能污染后续请求并造成内存问题。建议在拦截器中：

```java
@Override
public void afterCompletion(HttpServletRequest req,
                            HttpServletResponse resp,
                            Object handler,
                            Exception ex) {
    BaseContext.removeCurrentId();
}
```

异步任务切换线程后，普通 ThreadLocal 不会自动传递。

## 5. 密码安全

项目员工密码使用 MD5 思路。生产系统不建议直接 MD5：速度太快且抗暴力破解能力弱。应使用带随机盐和工作因子的密码哈希，例如 BCrypt、scrypt 或 Argon2。

登录安全还应考虑：失败次数限制、验证码或 MFA、审计日志、token 过期、刷新机制、密钥轮换和 HTTPS。

JWT 的典型取舍：

- 优点：服务端可无状态校验，适合横向扩容。
- 缺点：签发后难以立即撤销；权限变更不能立刻反映。
- 解决：短有效期 + refresh token、黑名单或 token 版本号。

## 6. 全局异常处理

项目使用：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler
    public Result exceptionHandler(BaseException ex) {
        return Result.error(ex.getMessage());
    }
}
```

业务层只需要：

```java
throw new DeletionNotAllowedException("启售中的菜品不能删除");
```

异常会沿调用栈向上传递，由全局处理器统一转换为响应，避免每个 Controller 重复 try/catch。

生产增强建议：

- 精确声明异常类型：`@ExceptionHandler(BaseException.class)`；
- 处理参数校验、类型转换、认证、资源不存在和未知异常；
- 未知异常对外返回通用文案，对内记录完整堆栈；
- 为错误定义稳定错误码；
- 不依赖数据库英文异常字符串拆分出业务信息，唯一性冲突最好映射为明确的领域错误。

## 7. AOP 自动填充

项目用自定义注解标记 Mapper 方法：

```java
@AutoFill(OperationType.INSERT)
void insert(Category category);
```

切面在方法执行前读取注解和第一个参数，通过反射设置：

```text
INSERT: createTime, createUser, updateTime, updateUser
UPDATE: updateTime, updateUser
```

AOP 概念：

- Aspect：切面，封装横切逻辑。
- Pointcut：切点，决定拦截哪些方法。
- Advice：通知，决定何时执行，如 Before、After、Around。
- Join Point：被拦截的方法调用点。

适合 AOP 的是跨多个业务重复的横切关注点，不适合把核心业务流程隐藏在切面中。

项目切面捕获反射异常后只 `printStackTrace()`，可能导致公共字段未填充却继续执行。更稳妥的做法是记录日志并抛出异常，让数据库操作失败并回滚。

## 8. 配置属性绑定

`JwtProperties`、`WeChatProperties` 使用配置属性类，把 YAML 映射为 Java 对象。这比在各处写 `@Value` 更集中、类型更安全，也便于校验。

典型写法：

```java
@Component
@ConfigurationProperties(prefix = "sky.jwt")
public class JwtProperties {
    private String adminSecretKey;
    private long adminTtl;
    private String adminTokenName;
}
```

敏感属性应由环境变量或密钥管理系统提供。

## 9. 支付回调安全审查

支付回调是面试中展示工程意识的好例子，应检查：

1. 验证平台签名和时间戳，防止伪造与重放。
2. 解密通知并核对商户号、订单号、金额、币种。
3. 不依赖登录用户上下文；回调是微信服务器发起，不携带用户 JWT。
4. 按订单号查询订单，并校验订单归属和金额。
5. 幂等处理重复回调。
6. 数据库提交成功后才向平台返回成功。

当前 `paySuccess` 业务读取 `BaseContext.getCurrentId()`，但 `/notify/**` 不经过用户 JWT 拦截器，因此回调线程通常没有用户 ID。这是应修复的设计点：回调应仅根据受验证的商户订单号定位订单。

## 10. 高频面试题

1. JWT 与 Session 有什么区别？
2. JWT 为什么不能保存密码？如何实现主动失效？
3. Filter、Interceptor、AOP 分别适合什么场景？
4. ThreadLocal 原理和风险是什么？为什么必须 remove？
5. `@ControllerAdvice` 如何统一处理异常？
6. Spring AOP 默认代理机制是什么？
7. 认证和授权有什么区别？
8. 为什么支付回调不能依赖当前登录用户？

