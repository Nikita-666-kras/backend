FROM amazoncorretto:21 AS builder
WORKDIR /app

RUN yum install -y unzip tar gzip findutils \
    && curl -fsSL https://services.gradle.org/distributions/gradle-8.9-bin.zip -o /tmp/gradle.zip \
    && unzip -q /tmp/gradle.zip -d /opt \
    && ln -s /opt/gradle-8.9/bin/gradle /usr/local/bin/gradle \
    && rm -f /tmp/gradle.zip \
    && yum clean all

COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY common-library ./common-library
COPY proposal-service ./proposal-service
RUN gradle :proposal-service:bootJar --no-daemon -x test

FROM amazoncorretto:21
WORKDIR /opt/app
COPY --from=builder /app/proposal-service/build/libs/*.jar app.jar
EXPOSE 9007
ENTRYPOINT ["java", "-jar", "app.jar"]
