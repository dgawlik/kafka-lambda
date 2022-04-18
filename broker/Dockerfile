FROM debian:buster-slim

RUN apt-get update

RUN apt-get install -y wget openjdk-11-jdk gettext-base

RUN wget https://dlcdn.apache.org/kafka/3.1.0/kafka_2.13-3.1.0.tgz

RUN tar -xvf kafka_2.13-3.1.0.tgz

RUN mv kafka_2.13-3.1.0 /kafka

RUN mkdir /var/kafka

WORKDIR /kafka

COPY ./entrypoint.sh .

COPY ./server.properties .

CMD ./entrypoint.sh