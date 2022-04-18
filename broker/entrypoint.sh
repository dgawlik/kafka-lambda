
bin/zookeeper-server-start.sh config/zookeeper.properties &

sleep 2

bin/kafka-server-start.sh server.properties &

sleep 10

bin/kafka-topics.sh --create --topic electronics --replication-factor 1 --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic grocery --replication-factor 1 --bootstrap-server localhost:9092
bin/kafka-topics.sh --create --topic realtime --replication-factor 1 --bootstrap-server localhost:9092

sleep infinity