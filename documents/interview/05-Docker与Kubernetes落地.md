# 05 Docker 与 Kubernetes 落地

## 1. Docker 的项目亮点

“写了 Dockerfile”不是完整亮点。容器化应解决：

- 构建环境一致；
- 镜像可复现、体积合理、非 root 运行；
- 配置与镜像分离；
- 进程能接收终止信号并优雅退出；
- 健康检查和资源限制明确。

## 2. 多阶段镜像

示意 Dockerfile：

```dockerfile
FROM maven:3-eclipse-temurin-8 AS build
WORKDIR /workspace
COPY pom.xml .
COPY sky-common sky-common
COPY sky-pojo sky-pojo
COPY sky-server sky-server
RUN mvn -pl sky-server -am clean package -DskipTests

FROM eclipse-temurin:8-jre
RUN addgroup --system app && adduser --system --ingroup app app
WORKDIR /app
COPY --from=build /workspace/sky-server/target/sky-server-*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

实际镜像标签应固定到经过验证的版本或 digest，不要长期使用浮动 `latest`。CI 中不应随意跳过测试；示例中的 `-DskipTests` 只适用于测试已在前置阶段运行的流水线。

进一步优化：Spring Boot layered jar、`.dockerignore`、依赖缓存、漏洞扫描、SBOM、签名和最小化基础镜像。

## 3. 本地编排

Docker Compose 适合开发环境组合应用、MySQL、Redis、Kafka 和监控组件。数据库初始化脚本、网络和持久卷应显式配置。

不要把数据库密码写入镜像或提交到仓库；本地用 `.env`，生产用 Secret 或外部密钥管理。

## 4. Kubernetes Deployment

核心示例：

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: booking-api
spec:
  replicas: 3
  strategy:
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
  selector:
    matchLabels:
      app: booking-api
  template:
    metadata:
      labels:
        app: booking-api
    spec:
      containers:
        - name: app
          image: registry.example/booking-api:${IMAGE_TAG}
          ports:
            - containerPort: 8080
          resources:
            requests:
              cpu: 500m
              memory: 768Mi
            limits:
              cpu: "2"
              memory: 1536Mi
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 20
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 60
```

资源值只是起点，必须通过压测和 JVM 指标调整。

## 5. 三种探针

- startup probe：应用启动慢时避免过早触发存活失败。
- readiness probe：决定 Pod 是否接收流量；依赖未准备好时应摘流。
- liveness probe：判断进程是否需要重启，不能把短暂外部依赖故障简单视为进程死亡。

探针设计错误会造成流量打到未就绪实例，或依赖短暂抖动时不断重启。

## 6. 优雅停机与滚动发布

Pod 收到 SIGTERM 后应：

1. readiness 变为失败，停止接收新请求；
2. 等待在途 HTTP 请求完成；
3. 停止拉取 Kafka 新消息并提交已处理 offset；
4. 关闭 WebSocket 或通知客户端重连；
5. 在 `terminationGracePeriodSeconds` 内退出。

滚动发布设置 `maxUnavailable: 0` 只能减少容量下降，不能替代向后兼容和健康检查。

## 7. HPA 与弹性扩容

API Pod 可按 CPU、请求速率或并发数扩容；Kafka 消费者更适合按 lag/最老消息年龄扩容，例如使用自定义指标或 KEDA。

```text
API 扩容信号：CPU + RPS + 在途请求
消费者扩容信号：lag + 消费延迟
```

只按 CPU 扩容可能错过 IO 型瓶颈。扩容也不是无限的：MySQL 连接数、Kafka 分区数、Redis 连接数都是下游上限。

扩容亮点必须用实验验证：从 2 Pod 扩到 6 Pod 后，吞吐是否接近线性增长，数据库连接池是否成为瓶颈，P99 是否稳定。

## 8. 配置与 Secret

- ConfigMap：非敏感配置；
- Secret：敏感值的 Kubernetes 对象，但仅 Base64 不是加密，应结合 etcd 加密和外部 Secret 管理；
- 环境变量或挂载文件：注入应用；
- 配置变更：需要版本、审计和回滚。

JWT 密钥、数据库密码、Kafka 凭据、微信/支付证书不得写入镜像。

## 9. 可观测性

建议加入 Spring Boot Actuator、Micrometer、Prometheus 和 Grafana：

```text
HTTP：RPS、P50/P95/P99、4xx/5xx、在途请求
JVM：堆、GC pause、线程、类加载
DB：连接池 active/pending、SQL 延迟、慢查询
Redis：命中率、命令延迟、连接、内存、eviction
Kafka：生产/消费速率、lag、最老消息年龄、重试/DLT
业务：资格成功率、落单成功率、超卖数、补偿数、核销率
K8s：Pod 重启、CPU throttling、内存、调度失败
```

业务指标比单纯 CPU 更能反映系统是否正常。

## 10. 中间件部署边界

简历项目可以在 K8s 内部署 MySQL/Redis/Kafka 进行演示，但生产环境更常使用托管服务或专业集群。不要把单副本 StatefulSet 描述为“高可用”。

应用无状态化后更容易水平扩容；WebSocket 会引入长连接路由问题，可以使用负载均衡粘性会话，或将消息通过 Redis/Kafka 分发到持有连接的实例。

## 11. 可量化亮点

- 多阶段构建后镜像从 `[前值] MB` 降至 `[后值] MB`；
- 冷启动时间 `[实测] s`，readiness 在 `[实测] s` 内就绪；
- 3 个 Pod 滚动发布期间错误率低于 `[实测]%`；
- HPA 从 2 扩至 6 Pod 用时 `[实测] s`，P99 保持在 `[实测] ms`；
- 人为删除一个 Pod 后恢复时间 `[实测] s`；
- 消费者按 lag 扩容后，积压在 `[实测] min` 内清空。

这些数字都需要脚本和监控截图支撑。

