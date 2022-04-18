FROM maven:3-jdk-11

WORKDIR /app

COPY src ./src
COPY pom.xml .

RUN mvn package spring-boot:repackage

CMD java -jar target/streams-1.0-SNAPSHOT.jar