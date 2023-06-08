FROM eclipse-temurin:20-jre

WORKDIR /function

ADD target/fn-test-standalone.jar /function/func.jar

CMD ["java", "-jar", "func.jar"]

