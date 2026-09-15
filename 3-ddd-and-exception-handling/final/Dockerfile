FROM maven:3.9.16-eclipse-temurin-25 AS build

WORKDIR /workspace

COPY pom.xml .
RUN mvn --batch-mode dependency:go-offline

COPY src src
RUN mvn --batch-mode package -DskipTests

FROM eclipse-temurin:25-jre-alpine

WORKDIR /work

COPY --from=build /workspace/target/quarkus-app/lib/ /work/lib/
COPY --from=build /workspace/target/quarkus-app/*.jar /work/
COPY --from=build /workspace/target/quarkus-app/app/ /work/app/
COPY --from=build /workspace/target/quarkus-app/quarkus/ /work/quarkus/

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
