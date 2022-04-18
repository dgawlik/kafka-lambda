package org.example;


import com.google.gson.Gson;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class App {


    private static class Config {
        private String name;
        private List<Item> inventory;

        public List<Item> getInventory() {
            return inventory;
        }

        public void setInventory(List<Item> inventory) {
            this.inventory = inventory;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    private static class Item {
        private String name;
        private Integer quantityMin;
        private Integer quantityMax;
        private Integer intervalSeconds;
        private Integer price;

        public Integer getPrice() {
            return price;
        }

        public void setPrice(Integer price) {
            this.price = price;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getQuantityMin() {
            return quantityMin;
        }

        public void setQuantityMin(Integer quantityMin) {
            this.quantityMin = quantityMin;
        }

        public Integer getQuantityMax() {
            return quantityMax;
        }

        public void setQuantityMax(Integer quantityMax) {
            this.quantityMax = quantityMax;
        }

        public Integer getIntervalSeconds() {
            return intervalSeconds;
        }

        public void setIntervalSeconds(Integer intervalSeconds) {
            this.intervalSeconds = intervalSeconds;
        }
    }

    private static class ItemDownstream {
        private String service;
        private String itemName;
        private Integer quantity;
        private LocalDateTime timestamp;
        private Integer price;

        public String getService() {
            return service;
        }

        public void setService(String service) {
            this.service = service;
        }

        public String getItemName() {
            return itemName;
        }

        public void setItemName(String itemName) {
            this.itemName = itemName;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
        }

        public Integer getPrice() {
            return price;
        }

        public void setPrice(Integer price) {
            this.price = price;
        }
    }

    private static Gson gson;
    private static ScheduledExecutorService scheduler;
    private static Config config;
    private static Random random;
    private static KafkaProducer<String, String> kafkaProducer;
    private static String divisionName;
   
    public static void main(String[] args) throws Exception {
        gson = new Gson();
        scheduler = Executors.newScheduledThreadPool(4);

        config = gson.fromJson(Files.readString(Path.of(args[0])), Config.class);
        random = new Random();

        // "kafka-broker-svc.default.svc.cluster.local:9092"
        String bootstrapUrl = System.getenv("PRODUCER_BOOTSTRAP_URL");

        Properties props = new Properties();
        props.put("client.id", config.getName());
        props.put("bootstrap.server",bootstrapUrl);
        props.put("acks", "all");

        kafkaProducer = new KafkaProducer<>(props, new StringSerializer(), new StringSerializer());

        divisionName = config.getName();

        System.out.printf("%s started.\n", divisionName);

        for (Item i : config.getInventory()) {
            scheduler.schedule(() -> schedule(i), i.intervalSeconds, TimeUnit.SECONDS);
        }
    }

    private static void schedule(Item i) {
        int qnt = i.quantityMin + random.nextInt(i.quantityMax + 1 -i.quantityMin );

        String key = divisionName;
        ItemDownstream i2 = new ItemDownstream();
        i2.setService(divisionName);
        i2.setPrice(i.getPrice());
        i2.setQuantity(qnt);
        i2.setTimestamp(LocalDateTime.now());
        i2.setItemName(i.getName());
        String value = gson.toJson(i2);

        System.out.printf("Sending %s:%s to %s", key, value, divisionName);

        kafkaProducer.send(new ProducerRecord<>(divisionName, key, value));

        scheduler.schedule(() -> schedule(i), i.intervalSeconds, TimeUnit.SECONDS);
    }

}
