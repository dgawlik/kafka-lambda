package org.example;

import com.google.gson.Gson;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.bson.Document;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class App {

    private static class Item {
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

    private static class BatchOrder {
        private LocalDateTime lastOrderTime;
        private Integer quantity;
        private String name;
        private Integer unitPrice;

        public LocalDateTime getLastOrderTime() {
            return lastOrderTime;
        }

        public void setLastOrderTime(LocalDateTime lastOrderTime) {
            this.lastOrderTime = lastOrderTime;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(Integer unitPrice) {
            this.unitPrice = unitPrice;
        }
    }


    public static void main(String[] args) {

        String bootstrapUrl = System.getenv("STREAMS_BOOTSTRAP_URL");
        String mongoUrl = System.getenv("MONGO_URL");
        String mongoUser = System.getenv("MONGO_USERNAME");
        String mongoPasswd = System.getenv("MONGO_PASSWORD");

        MongoClient mongoClient = MongoClients.create("mongodb://" + mongoUser + ":"
                + mongoPasswd + "@" + mongoUrl);
        MongoDatabase db = mongoClient.getDatabase("orders");

        Map<String, Object> electronicsProps = new HashMap<>();
        electronicsProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapUrl);
        electronicsProps.put(ConsumerConfig.GROUP_ID_CONFIG, "streams");
        electronicsProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        electronicsProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);


        Map<String, Object> groceryProps = new HashMap<>();
        groceryProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapUrl);
        groceryProps.put(ConsumerConfig.GROUP_ID_CONFIG, "streams2");
        groceryProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        groceryProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        Map<String, Object> senderProps = new HashMap<>();
        senderProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapUrl);
        senderProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        senderProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        senderProps.put("acks", "all");


        ReceiverOptions<String, String> electronicsOpt =
                ReceiverOptions.<String, String>create(electronicsProps)
                        .subscription(Collections.singleton("electronics"));

        ReceiverOptions<String, String> groceryOpt =
                ReceiverOptions.<String, String>create(groceryProps)
                        .subscription(Collections.singleton("grocery"));

        Sinks.Many<String> all = Sinks.many().multicast().directBestEffort();
        Sinks.Many<String> outbound = Sinks.many().multicast().directBestEffort();


        SenderOptions<String, String> senderOptions =
                SenderOptions.create(senderProps);

        KafkaSender<String, String> sender = KafkaSender.create(senderOptions);

        sender.createOutbound()
                .send(outbound
                        .asFlux()
                        .map(val -> new ProducerRecord<>("realtime", "outbound", val)))
                .then()
                .subscribe();


        KafkaReceiver.create(electronicsOpt)
                .receive()
                .doOnNext(record -> {
                    System.out.println("Received electronics record");
                    all.emitNext(record.value(), Sinks.EmitFailureHandler.busyLooping(Duration.ofSeconds(1)));
                    record.receiverOffset().commit();
                })
                .subscribe();

        KafkaReceiver.create(groceryOpt)
                .receive()
                .doOnNext(record -> {
                    System.out.println("Received grocery record");
                    all.emitNext(record.value(), Sinks.EmitFailureHandler.busyLooping(Duration.ofSeconds(1)));
                    record.receiverOffset().commit();
                })
                .subscribe();

        var flux = all.asFlux();
        flux
                .map(value -> new Gson().fromJson(value, Item.class))
                .window(Duration.ofMinutes(1))
                .doOnNext(items -> {
                    Map<String, BatchOrder> orders = new HashMap<>();

                    items.subscribe(
                            item -> {
                                System.out.printf("Realtime, %s\n", new Gson().toJson(item));

                                outbound.tryEmitNext(new Gson().toJson(item));

                                if (!orders.containsKey(item.getItemName())) {
                                    var neww = new BatchOrder();
                                    neww.setName(item.getItemName());
                                    neww.setUnitPrice(item.getPrice());
                                    neww.setQuantity(item.getQuantity());
                                    neww.setLastOrderTime(item.getTimestamp());
                                    orders.put(item.getItemName(), neww);
                                } else {
                                    var old = orders.get(item.getItemName());
                                    var neww = new BatchOrder();
                                    neww.setName(item.getItemName());
                                    neww.setQuantity(old.getQuantity() + item.getQuantity());
                                    neww.setUnitPrice(old.getUnitPrice());
                                    neww.setLastOrderTime(item.getTimestamp());
                                    orders.put(item.getItemName(), neww);
                                }
                            },
                            err -> System.out.println("<ERR>"),
                            () -> {
                                var order = new Gson().toJson(orders);
                                System.out.printf("Batch, %s\n", order);
                                db.getCollection("orders").insertOne(Document.parse(order));

                                outbound.tryEmitNext("<END>");
                            });

                    sender.createOutbound()
                            .send(items
                                    .map(val -> new ProducerRecord<>("realtime", "outbound",
                                            new Gson().toJson(val))))
                            .then()
                            .subscribe();

                })
                .subscribe();

    }
}
