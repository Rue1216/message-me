# ---------- 建置階段 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 先只複製 pom.xml 以便相依套件層可被 Docker 快取
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ---------- 執行階段 ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 不以 root 執行，降低容器逃逸的影響範圍
RUN addgroup -S app && adduser -S -G app app \
    && mkdir -p /app/uploads && chown -R app:app /app

COPY --from=build --chown=app:app /build/target/*.jar app.jar

USER app
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
