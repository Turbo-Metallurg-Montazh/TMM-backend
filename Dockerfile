# ---------- STAGE 1: Build ----------
FROM maven:4.0.0-rc-5-eclipse-temurin-26 AS build

WORKDIR /app

COPY pom.xml .
COPY .openapi-generator-ignore .

RUN mvn -B -q dependency:go-offline

COPY src ./src

RUN mvn -B -q -Dmaven.test.skip=true package

# ---------- STAGE 2: Runtime ----------
FROM eclipse-temurin:26-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

ENV JAVA_OPTS="\
-XX:+UseContainerSupport \
-XX:MaxRAMPercentage=75.0 \
-XX:InitialRAMPercentage=25.0 \
-XX:+ExitOnOutOfMemoryError \
-Djava.security.egd=file:/dev/urandom \
"

ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]