FROM openjdk:8-alpine

COPY target/uberjar/dp2.jar /dp2/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/dp2/app.jar"]
