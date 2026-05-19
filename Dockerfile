# ---------- STAGE 1: Build ----------
FROM eclipse-temurin:26-jdk AS build

WORKDIR /app

# Кэш зависимостей (максимально эффективно)
COPY pom.xml .
COPY mvnw .
COPY .mvn ./.mvn
COPY .openapi-generator-ignore .
RUN ./mvnw -B -q dependency:go-offline

# Копируем исходники и собираем JAR
COPY src ./src
RUN ./mvnw -B -q -Dmaven.test.skip=true package

# ---------- STAGE 2: Runtime ----------
FROM eclipse-temurin:26-jre

WORKDIR /app

# Копируем fat jar
COPY --from=build /app/target/*.jar app.jar

# JVM-настройки для Kubernetes
ENV JAVA_OPTS="\
-XX:+UseContainerSupport \
-XX:MaxRAMPercentage=75.0 \
-XX:InitialRAMPercentage=25.0 \
-XX:+ExitOnOutOfMemoryError \
-Djava.security.egd=file:/dev/urandom \
"

# Spring Boot best-practices
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

# Корректный PID 1 (важно для Kubernetes)
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
