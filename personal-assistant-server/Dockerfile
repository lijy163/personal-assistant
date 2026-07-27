FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Shanghai JAVA_OPTS=""
RUN apt-get update \
    && apt-get install -y --no-install-recommends postgresql-client ca-certificates tzdata \
    && rm -rf /var/lib/apt/lists/*
COPY --from=builder /workspace/target/*.jar /app/app.jar
COPY scripts /app/scripts
RUN chmod 750 /app/scripts/*.sh \
    && mkdir -p /app/files /app/logs /app/backup
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]