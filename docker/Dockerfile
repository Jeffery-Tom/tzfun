
# 通用应用镜像：从预构建的 Spring Boot jar 打一个 JRE 运行镜像。
# 用 BuildKit 的 ARG-in-COPY 特性，让每个服务共用这一个 Dockerfile，
# 构建时通过 build args 传入各自 jar 的相对路径（相对仓库根）。
FROM eclipse-temurin:21-jre

WORKDIR /app

# JARFILE 由 docker-compose 的 args 传入，如 user-service/target/user-service-0.0.1-SNAPSHOT.jar
ARG JARFILE
COPY ${JARFILE} app.jar

# 入口固定，JVM 内存等通过容器环境变量 JAVA_TOOL_OPTIONS 在 compose 里调
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
