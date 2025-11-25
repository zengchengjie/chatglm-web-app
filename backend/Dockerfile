# 运行时镜像
FROM eclipse-temurin:17-jre

# 设置工作目录
WORKDIR /app

# 复制构建好的JAR文件
COPY target/chatglm-backend-1.0.0.jar app.jar

# 创建应用用户
RUN groupadd -r spring && useradd -r -g spring spring

# 创建日志目录
RUN mkdir -p /app/logs && chown -R spring:spring /app

# 切换到应用用户
USER spring:spring

# 暴露端口
EXPOSE 8080

# JVM参数 - 优化内存使用
ENV JAVA_OPTS="-XX:+UseContainerSupport -Xmx256m -Xms128m -Djava.security.egd=file:/dev/./urandom"

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]