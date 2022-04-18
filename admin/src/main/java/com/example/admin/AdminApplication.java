package com.example.admin;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.*;

@SpringBootApplication
public class AdminApplication {

    @Configuration
    public static class Config {

        @Value("${kafka.bootstrap.url}")
        String kafkaBootstrap;

        @Value("${mongo.url}")
        String mongoUrl;

        @Value("${mongo.username}")
        String mongoUsername;

        @Value("${mongo.password}")
        String mongoPassword;

        @Bean
        public MongoClient mongoClient() {
            return MongoClients.create("mongodb://" + mongoUsername + ":"
                    + mongoPassword + "@" + mongoUrl);
        }

        @Bean
        public KafkaConsumer<String, String> kafkaConsumer() {
            Properties config = new Properties();
            config.put("client.id", "admin");
            config.put("group.id", "admin");
            config.put("bootstrap.servers", kafkaBootstrap);
            config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            var consumer = new KafkaConsumer<String, String>(config);
            consumer.subscribe(Collections.singleton("realtime"));
            return consumer;
        }
    }

    @RestController
    public static class Controller {

        private List<String> updates = new ArrayList<>();

        @Autowired
        KafkaConsumer<String, String> kafkaConsumer;

        @Autowired
        MongoClient mongoClient;

        @GetMapping(value = "/check", produces = MediaType.APPLICATION_JSON_VALUE)
        public Map<String, Object> check() {
            var records = kafkaConsumer.poll(Duration.ofSeconds(10));

            for (var record : records) {
                if (record.value().equals("<END>")) {
                    updates.clear();
                } else updates.add(record.value());
            }

            List<String> batches = new ArrayList<>();
            mongoClient.getDatabase("orders").getCollection("orders")
                    .find()
                    .cursor()
                    .forEachRemaining(doc -> {
                        batches.add(doc.toJson());
                    });

            return Map.of("batches", batches, "updates", updates);
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }

}
