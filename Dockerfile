# Image dùng cho Railway. Build hai tầng để image cuối chỉ chứa JRE và file jar.

# ---------- Tầng build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy riêng pom trước để Docker cache được lớp tải dependency:
# chỉ khi pom.xml đổi thì bước tải mới chạy lại.
COPY pom.xml .
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY src ./src
# Bỏ test và spotless ở đây vì CI đã chạy cả hai trước khi merge vào main.
RUN mvn --batch-mode --no-transfer-progress clean package \
        -DskipTests -Dspotless.check.skip=true \
    && mv target/*.jar /build/app.jar

# ---------- Tầng chạy ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Chạy bằng user thường, không phải root.
RUN useradd --system --create-home --uid 10001 spring
USER spring

COPY --from=build --chown=spring:spring /build/app.jar app.jar

# MaxRAMPercentage để JVM tự co giãn theo RAM Railway cấp,
# thay vì đoán một con số -Xmx cố định.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
