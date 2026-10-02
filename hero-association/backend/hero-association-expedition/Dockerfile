FROM maven:3.9.16-eclipse-temurin-25 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY hero-association-bff/pom.xml ./hero-association-bff/pom.xml
COPY hero-association-core/pom.xml ./hero-association-core/pom.xml
COPY hero-association-lib ./hero-association-lib
COPY hero-association-expedition/pom.xml ./hero-association-expedition/pom.xml
COPY hero-association-assets/pom.xml ./hero-association-assets/pom.xml
COPY hero-association-market/pom.xml ./hero-association-market/pom.xml
COPY hero-association-expedition/src ./hero-association-expedition/src
RUN mvn --batch-mode -pl hero-association-expedition -am package -DskipTests

FROM eclipse-temurin:25-jre-alpine

WORKDIR /work

COPY --from=build /workspace/hero-association-expedition/target/quarkus-app/lib/ /work/lib/
COPY --from=build /workspace/hero-association-expedition/target/quarkus-app/*.jar /work/
COPY --from=build /workspace/hero-association-expedition/target/quarkus-app/app/ /work/app/
COPY --from=build /workspace/hero-association-expedition/target/quarkus-app/quarkus/ /work/quarkus/

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
