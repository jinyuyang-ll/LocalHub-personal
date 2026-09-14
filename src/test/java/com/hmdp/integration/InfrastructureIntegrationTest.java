package com.hmdp.integration;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class InfrastructureIntegrationTest {

    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("hmdp").withUsername("root").withPassword("123456")
            .withInitScript("db/hmdp.sql");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);
    @Container static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Test
    void schemaIsRepeatablyInitialized() throws Exception {
        try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             ResultSet result = connection.createStatement().executeQuery("select count(*) from tb_shop")) {
            assertTrue(result.next());
            assertTrue(result.getInt(1) > 0);
        }
    }

    @Test
    void seckillLuaPreventsOversellAndDuplicateOrder() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        StringRedisTemplate redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        redis.opsForValue().set("seckill:stock:99", "1");
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("seckill_kafka.lua"));
        script.setResultType(Long.class);
        assertEquals(0L, redis.execute(script, java.util.Arrays.asList("seckill:stock:99", "seckill:order:99",
                "seckill:order:status:1001", "seckill:reservation:1001", "seckill:order:owner:1001",
                "seckill:reservation:audit", "seckill:reservation:audit:payload"), "99", "7", "1001", "1800"));
        assertEquals(1L, redis.execute(script, java.util.Arrays.asList("seckill:stock:99", "seckill:order:99",
                "seckill:order:status:1002", "seckill:reservation:1002", "seckill:order:owner:1002",
                "seckill:reservation:audit", "seckill:reservation:audit:payload"), "99", "8", "1002", "1800"));
        assertEquals(3L, redis.execute(script, java.util.Arrays.asList("seckill:stock:missing", "seckill:order:100",
                "seckill:order:status:1003", "seckill:reservation:1003", "seckill:order:owner:1003",
                "seckill:reservation:audit", "seckill:reservation:audit:payload"), "100", "8", "1003", "1800"));
        assertEquals("0", redis.opsForValue().get("seckill:stock:99"));
        factory.destroy();
    }

    @Test
    void kafkaMessageCanBeProducedAndConsumed() throws Exception {
        String topic = "localhub.integration." + UUID.randomUUID();
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(topic, "order-1", "PROCESSING")).get();
        }
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList(topic));
            long deadline = System.currentTimeMillis() + 15_000;
            boolean found = false;
            while (!found && System.currentTimeMillis() < deadline) {
                found = StreamSupport.stream(consumer.poll(Duration.ofMillis(500)).records(topic).spliterator(), false)
                        .anyMatch(record -> "PROCESSING".equals(record.value()));
            }
            assertTrue(found, "Kafka message was not consumed before timeout");
        }
    }

    @Test
    void seckillCompensationIsAtomicAndIdempotent() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        StringRedisTemplate redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        redis.opsForValue().set("seckill:stock:88", "0");
        redis.opsForSet().add("seckill:order:88", "7");
        redis.opsForValue().set("seckill:reservation:1001", "88:7");
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("seckill_compensate.lua"));
        script.setResultType(Long.class);

        assertEquals(1L, redis.execute(script, java.util.Arrays.asList("seckill:reservation:1001", "seckill:order:88",
                "seckill:stock:88", "seckill:order:status:1001", "seckill:reservation:audit",
                "seckill:reservation:audit:payload"),
                "88:7", "7", "1800", "FAILED", "0", "1001"));
        assertEquals("1", redis.opsForValue().get("seckill:stock:88"));
        assertEquals(false, redis.opsForSet().isMember("seckill:order:88", "7"));
        assertEquals("FAILED", redis.opsForValue().get("seckill:order:status:1001"));
        assertEquals(0L, redis.execute(script, java.util.Arrays.asList("seckill:reservation:1001", "seckill:order:88",
                "seckill:stock:88", "seckill:order:status:1001", "seckill:reservation:audit",
                "seckill:reservation:audit:payload"),
                "88:7", "7", "1800", "FAILED", "0", "1001"));
        assertEquals("1", redis.opsForValue().get("seckill:stock:88"));
        factory.destroy();
    }

    @Test
    void duplicateCompensationKeepsUserMarker() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        StringRedisTemplate redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        redis.opsForValue().set("seckill:stock:89", "0");
        redis.opsForSet().add("seckill:order:89", "7");
        redis.opsForValue().set("seckill:reservation:1002", "89:7");
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("seckill_compensate.lua"));
        script.setResultType(Long.class);

        assertEquals(1L, redis.execute(script, java.util.Arrays.asList("seckill:reservation:1002", "seckill:order:89",
                "seckill:stock:89", "seckill:order:status:1002", "seckill:reservation:audit",
                "seckill:reservation:audit:payload"),
                "89:7", "7", "1800", "DUPLICATE", "1", "1002"));
        assertEquals("1", redis.opsForValue().get("seckill:stock:89"));
        assertEquals(true, redis.opsForSet().isMember("seckill:order:89", "7"));
        assertEquals("DUPLICATE", redis.opsForValue().get("seckill:order:status:1002"));
        factory.destroy();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void kafkaPublishRecoveryClaimIsAtomicAndLeaseCanExpire() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        StringRedisTemplate redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        String retry = "it:retry";
        String processing = "it:processing";
        redis.opsForZSet().add(retry, "1001", 1);
        redis.opsForZSet().add(retry, "1002", 1);
        DefaultRedisScript<List> claim = new DefaultRedisScript<>();
        claim.setLocation(new ClassPathResource("seckill_retry_claim.lua"));
        claim.setResultType(List.class);

        List first = redis.execute(claim, java.util.Arrays.asList(retry, processing), "10", "100", "10", "worker-a");
        List second = redis.execute(claim, java.util.Arrays.asList(retry, processing), "10", "100", "10", "worker-b");
        assertEquals(2, first.size());
        assertTrue(second.isEmpty(), "a claimed message must not be claimed by another instance");

        List reclaimed = redis.execute(claim, java.util.Arrays.asList(retry, processing), "101", "200", "10", "worker-b");
        assertEquals(2, reclaimed.size(), "expired leases must be requeued and claimable");
        redis.delete(java.util.Arrays.asList(retry, processing));
        factory.destroy();
    }
}
