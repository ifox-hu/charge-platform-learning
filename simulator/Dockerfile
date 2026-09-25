FROM centos7-jdk17:latest
WORKDIR /app
COPY target/charge-platform-simulator-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 9100
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
