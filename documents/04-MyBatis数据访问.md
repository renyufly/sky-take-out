# 04 MyBatis 数据访问

## 1. MyBatis 的角色

MyBatis 位于 Java 对象和关系数据库之间：

```text
Mapper 方法 + 参数 → SQL → JDBC → MySQL
MySQL ResultSet → Java Entity / VO
```

与纯 JDBC 相比，它减少连接、参数绑定、结果映射等样板代码；与全自动 ORM 相比，它保留 SQL 控制能力。

配置位于 `application.yml`：

```yaml
mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.sky.entity
  configuration:
    map-underscore-to-camel-case: true
```

驼峰映射会把 `create_time` 映射为 `createTime`。

## 2. Mapper 接口和代理对象

项目写法：

```java
@Mapper
public interface CategoryMapper {
    @Insert("insert into category(...) values (...)")
    void insert(Category category);

    Page<Category> pageQuery(CategoryPageQueryDTO dto);
}
```

接口没有实现类，因为 MyBatis 在运行时创建代理对象。调用方法时，代理根据方法签名找到注解 SQL 或 XML 中的 statement。

XML 必须满足：

```xml
<mapper namespace="com.sky.mapper.CategoryMapper">
    <select id="pageQuery" resultType="com.sky.entity.Category">
        ...
    </select>
</mapper>
```

`namespace + id` 对应 Mapper 接口全限定名和方法名。

## 3. `#{}` 与 `${}`

```sql
where id = #{id}
```

`#{}` 使用预编译参数，占位符最终类似 `where id = ?`，能够正确处理类型并降低 SQL 注入风险。

`${}` 是字符串直接拼接，可能导致 SQL 注入。只有列名、排序方向等无法参数化的位置才可能使用，并且必须通过白名单控制，不能直接接收客户端文本。

面试标准答案：业务值优先使用 `#{}`；`${}` 仅用于可信、受控的 SQL 片段。

## 4. 注解 SQL 与 XML SQL

简单 SQL 适合注解：

```java
@Delete("delete from category where id = #{id}")
void deleteById(Long id);
```

复杂、动态、批量 SQL 适合 XML。统一使用 XML 也便于集中维护，具体取决于团队规范。

## 5. 动态 SQL

分类分页查询：

```xml
<select id="pageQuery" resultType="Category">
    select * from category
    <where>
        <if test="name != null and name != ''">
            and name like concat('%', #{name}, '%')
        </if>
        <if test="type != null">
            and type = #{type}
        </if>
    </where>
    order by sort asc, create_time desc
</select>
```

- `<where>` 只在内部有条件时生成 WHERE，并自动处理开头的 AND/OR。
- `<if>` 根据 Java 参数决定是否拼接片段。

动态更新：

```xml
<update id="update">
    update category
    <set>
        <if test="name != null">name = #{name},</if>
        <if test="status != null">status = #{status},</if>
    </set>
    where id = #{id}
</update>
```

`<set>` 会生成 SET 并处理末尾逗号。注意必须保证 `id` 非空，否则可能更新错误或无数据。

## 6. 批量操作与 foreach

项目批量删除菜品：

```xml
<delete id="deleteByIds">
    delete from dish where id in
    <foreach collection="ids" item="id" separator="," open="(" close=")">
        #{id}
    </foreach>
</delete>
```

Java：

```java
void deleteByIds(List<Long> ids);
```

批量操作比循环发送 N 次 SQL 网络往返更高效。注意空集合会生成非法 SQL，应在 Service 层提前校验。

## 7. 主键回填

新增菜品后需要得到数据库生成的 ID，再插入口味：

```xml
<insert id="insert" useGeneratedKeys="true" keyProperty="id">
    insert into dish (...) values (...)
</insert>
```

调用 `dishMapper.insert(dish)` 后，`dish.getId()` 已被赋值。`keyProperty` 是 Java 属性名，不是数据库列名。

## 8. 结果映射

简单字段可用 `resultType` 和驼峰映射。字段名不一致时可以使用别名：

```sql
select d.*, c.name as categoryName
```

复杂的一对多可以使用 `<resultMap>` 和 `<collection>`，但要注意 JOIN 导致主表重复行、分页失真等问题。很多项目会选择分两次批量查询后在 Service 组装。

## 9. 分页原理

PageHelper 写法：

```java
PageHelper.startPage(page, pageSize);
Page<DishVO> result = dishMapper.pageQuery(dto);
```

插件拦截紧随其后的查询，通常执行 count SQL 和带 limit 的数据 SQL。注意：

- `startPage` 与查询之间不要插入其他查询；
- 页码和页大小要限制范围；
- 大偏移量分页会越来越慢，可考虑基于游标/最后 ID 的分页。

## 10. MyBatis 常见问题

- `BindingException`：接口方法与 XML 的 namespace/id/参数不匹配。
- 字段全是 null：列名与属性名未映射，检查别名和驼峰配置。
- 动态 SQL 语法错误：逗号、AND、空集合或某个 `<if>` 分支未覆盖。
- 多参数方法：建议用 `@Param("name")` 明确参数名。
- 懒加载或循环序列化：返回对象关联过深。
- N+1 查询：循环内不断调用 Mapper，应改为批量查询。

## 11. 自测题

1. MyBatis Mapper 为什么没有实现类也能执行？
2. `#{}` 和 `${}` 有什么区别？
3. `<where>`、`<set>`、`<foreach>` 分别解决什么问题？
4. `useGeneratedKeys` 有什么用途？
5. PageHelper 为什么要求查询紧跟在 `startPage` 后面？
6. 怎样解决 N+1 查询？

