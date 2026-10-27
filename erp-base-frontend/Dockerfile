FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src src
RUN mvn -q package

FROM tomcat:10.1-jdk21-temurin
RUN rm -rf /usr/local/tomcat/webapps/ROOT
COPY --from=build /workspace/target/erp-base-frontend.war /usr/local/tomcat/webapps/ROOT.war
EXPOSE 8080
