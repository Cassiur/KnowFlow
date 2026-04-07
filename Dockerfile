# KnowFlow 项目 Dockerfile
# 多阶段构建：阶段1编译打包，阶段2运行应用
# 使用 Maven 3.9 + Eclipse Temurin JDK 17 作为构建环境

# ============================================
# 阶段1: 构建阶段 (Builder Stage)
# ============================================
FROM maven:3.9-eclipse-temurin-17 AS builder

# 设置工作目录
WORKDIR /build

# 先复制 pom.xml 和依赖相关文件，利用 Docker 缓存层
# 这样只要 pom.xml 不变，依赖就不会重新下载
COPY pom.xml .

# 下载依赖（离线模式，加快后续构建）
RUN mvn dependency:go-offline -B

# 复制源代码
COPY src ./src

# 编译打包，跳过测试以加快构建速度
# 使用 -DskipTests 而不是 -Dmaven.test.skip=true，这样仍然编译测试代码但不执行
RUN mvn clean package -DskipTests -B

# ============================================
# 阶段2: 运行阶段 (Runtime Stage)
# ============================================
FROM eclipse-temurin:17-jre-alpine

# 维护者信息
LABEL maintainer="KnowFlow Team"
LABEL description="KnowFlow Knowledge Management System"

# 安装必要的工具（用于健康检查等）
RUN apk add --no-cache curl

# 创建非 root 用户运行应用（安全最佳实践）
# -S: 创建系统用户，-G: 指定用户组
RUN addgroup -S knowflow && adduser -S knowflow -G knowflow

# 设置工作目录
WORKDIR /app

# 从构建阶段复制生成的 JAR 文件
# 使用 --from=builder 从第一阶段复制文件
COPY --from=builder /build/target/KnowFlow-0.0.1-SNAPSHOT.jar app.jar

# 更改文件所有者为非 root 用户
RUN chown -R knowflow:knowflow /app

# 切换到非 root 用户
USER knowflow

# 暴露应用端口（与 application.yml 中的 server.port 一致）
EXPOSE 8081

# 配置 JVM 参数（可根据实际需求调整）
# -Xms512m: 初始堆内存
# -Xmx1024m: 最大堆内存（与 K8s limits.memory 配合）
# -XX:+UseContainerSupport: 启用容器支持（Java 17 默认开启）
# -XX:MaxRAMPercentage=75.0: 使用容器内存的 75%
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"

# 健康检查配置
# --interval: 检查间隔
# --timeout: 超时时间
# --start-period: 启动宽限期（应用启动需要时间）
# --retries: 重试次数
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

# 启动应用
# 使用 exec 形式确保 Java 进程接收 SIGTERM 信号（优雅关闭）
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
