FROM amazoncorretto:21 AS builder
WORKDIR /app

RUN yum install -y unzip tar gzip curl \
    && curl -fsSL https://services.gradle.org/distributions/gradle-8.9-bin.zip -o /tmp/gradle.zip \
    && unzip -q /tmp/gradle.zip -d /opt \
    && ln -s /opt/gradle-8.9/bin/gradle /usr/local/bin/gradle \
    && rm -f /tmp/gradle.zip \
    && yum clean all

COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY api-gateway ./api-gateway
RUN gradle :api-gateway:bootJar --no-daemon -x test

FROM amazoncorretto:21
WORKDIR /opt/app
COPY --from=builder /app/api-gateway/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
