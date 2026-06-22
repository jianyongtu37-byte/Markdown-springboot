FROM openjdk:17-jdk-slim

WORKDIR /app

COPY target/Markdown-1.0.0.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-Xmx1024m -Xms512m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]