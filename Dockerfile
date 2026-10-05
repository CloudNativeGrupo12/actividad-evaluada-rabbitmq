FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
RUN mvn -B -DskipTests package
FROM eclipse-temurin:21-jre
ARG MODULE
WORKDIR /app
COPY --from=build /build/${MODULE}/target/${MODULE}-1.0.0.jar /app/app.jar
RUN mkdir /data && chown -R 10001:10001 /data /app
USER 10001
ENTRYPOINT ["java","-jar","/app/app.jar"]
