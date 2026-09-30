FROM maven:3.9.16-eclipse-temurin-25 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY hero-association-bff/pom.xml ./hero-association-bff/pom.xml
COPY hero-association-core/pom.xml ./hero-association-core/pom.xml
COPY hero-association-lib ./hero-association-lib
COPY hero-association-combat/pom.xml ./hero-association-combat/pom.xml
COPY hero-association-expedition/pom.xml ./hero-association-expedition/pom.xml
COPY hero-association-combat/src ./hero-association-combat/src
RUN mvn --batch-mode -pl hero-association-combat -am package -DskipTests

FROM eclipse-temurin:25-jre-alpine

WORKDIR /work

COPY --from=build /workspace/hero-association-combat/target/quarkus-app/lib/ /work/lib/
COPY --from=build /workspace/hero-association-combat/target/quarkus-app/*.jar /work/
COPY --from=build /workspace/hero-association-combat/target/quarkus-app/app/ /work/app/
COPY --from=build /workspace/hero-association-combat/target/quarkus-app/quarkus/ /work/quarkus/

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
