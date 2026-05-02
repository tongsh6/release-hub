FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build
COPY pom.xml ./
COPY releasehub-common/pom.xml ./releasehub-common/
COPY releasehub-domain/pom.xml ./releasehub-domain/
COPY releasehub-application/pom.xml ./releasehub-application/
COPY releasehub-infrastructure/pom.xml ./releasehub-infrastructure/
COPY releasehub-interfaces/pom.xml ./releasehub-interfaces/
COPY releasehub-bootstrap/pom.xml ./releasehub-bootstrap/
RUN mvn dependency:go-offline -q
COPY . .
RUN mvn package -pl releasehub-bootstrap -am -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/releasehub-bootstrap/target/releasehub-bootstrap-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=gitlab-e2e"]
