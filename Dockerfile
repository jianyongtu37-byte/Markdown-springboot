# 使用 JDK 17 作为基础镜像 (如果你的项目是 JDK 8，请改成 openjdk:8-jdk-alpine)
FROM eclipse-temurin:17-jre-alpine

# 设置工作目录
WORKDIR /app

# 将你打包好的 jar 包复制进容器 (假设你的 jar 包在 target 目录下)
COPY target/*.jar app.jar

# 暴露端口
EXPOSE 8080

# 启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]