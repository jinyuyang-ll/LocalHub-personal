package com.hmdp.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class SeckillHttpKafkaIntegrationTest {

    private static final long USER_ID = 1L;
    private static final long VOUCHER_ID = 9001L;
    private static final String TOKEN = "integration-token";

    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("hmdp").withUsername("root").withPassword("123456")
            .withInitScript("db/hmdp.sql");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);
    @Container static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.redis.host", REDIS::getHost);
        registry.add("spring.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("localhub.kafka.enabled", () -> true);
        registry.add("localhub.seckill.queue", () -> "kafka");
        registry.add("localhub.canal.enabled", () -> false);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;

    @BeforeEach
    void arrange() {
        jdbc.update("delete from tb_voucher_order where voucher_id = ?", VOUCHER_ID);
        jdbc.update("delete from tb_seckill_voucher where voucher_id = ?", VOUCHER_ID);
        jdbc.update("delete from tb_voucher where id = ?", VOUCHER_ID);
        jdbc.update("insert into tb_voucher(id, shop_id, title, pay_value, actual_value, type, status) values (?,1,'integration seckill',100,200,1,1)", VOUCHER_ID);
        jdbc.update("insert into tb_seckill_voucher(voucher_id, stock, begin_time, end_time) values (?,1,date_sub(now(), interval 1 minute),date_add(now(), interval 10 minute))", VOUCHER_ID);
        redis.delete("seckill:order:" + VOUCHER_ID);
        redis.opsForValue().set("seckill:stock:" + VOUCHER_ID, "1");
        Map<String, String> user = new HashMap<>();
        user.put("id", String.valueOf(USER_ID));
        user.put("nickName", "integration-user");
        redis.opsForHash().putAll("login:token:" + TOKEN, user);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from tb_voucher_order where voucher_id = ?", VOUCHER_ID);
        jdbc.update("delete from tb_outbox_event where aggregate_type = 'VoucherOrder'");
        jdbc.update("delete from tb_seckill_voucher where voucher_id = ?", VOUCHER_ID);
        jdbc.update("delete from tb_voucher where id = ?", VOUCHER_ID);
    }

    @Test
    void httpRequestIsConsumedFromKafkaAndPersistedExactlyOnce() throws Exception {
        String body = mockMvc.perform(post("/api/seckill-vouchers/{voucherId}/orders", VOUCHER_ID)
                        .header("authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(body);
        long orderId = response.path("data").asLong();
        assertTrue(orderId > 0);

        await(Duration.ofSeconds(20), () -> jdbc.queryForObject(
                "select count(*) from tb_voucher_order where id = ?", Integer.class, orderId) == 1);
        assertEquals(0, jdbc.queryForObject(
                "select stock from tb_seckill_voucher where voucher_id = ?", Integer.class, VOUCHER_ID));
        assertEquals("SUCCESS", redis.opsForValue().get("seckill:order:status:" + orderId));

        mockMvc.perform(post("/api/seckill-vouchers/{voucherId}/orders", VOUCHER_ID)
                        .header("authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from tb_voucher_order where user_id = ? and voucher_id = ?",
                Integer.class, USER_ID, VOUCHER_ID));
    }

    private void await(Duration timeout, Condition condition) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.evaluate()) return;
            Thread.sleep(200);
        }
        throw new AssertionError("Condition was not met within " + timeout);
    }

    private interface Condition {
        boolean evaluate() throws Exception;
    }
}
