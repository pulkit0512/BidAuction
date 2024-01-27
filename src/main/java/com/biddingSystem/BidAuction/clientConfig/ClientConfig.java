package com.biddingSystem.BidAuction.clientConfig;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.DatabaseId;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.io.IOException;

@Configuration
public class ClientConfig {

    @Value("${projectId}")
    private String PROJECT_NAME;

    @Value("${spanner.instanceId}")
    private String INSTANCE_ID;

    @Value("${spanner.databaseId}")
    private String DATABASE_ID;

    @Value("${redis.port}")
    private int REDIS_PORT;

    @Value("${redis.write.host}")
    private String REDIS_WRITE_HOST;

    @Value("${redis.read.host}")
    private String REDIS_READ_HOST;

    @Bean
    public DatabaseClient databaseClient() throws IOException {
        Spanner spanner = SpannerOptions.newBuilder()
                .setProjectId(PROJECT_NAME)
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .build()
                .getService();

        return spanner.getDatabaseClient(DatabaseId.of(PROJECT_NAME, INSTANCE_ID, DATABASE_ID));
    }

    @Bean
    @Qualifier("writeCache")
    public Jedis jedisWrite() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        // Default : 8, consider how many concurrent connections into Redis you will need under load
        poolConfig.setMaxTotal(128);

        try (JedisPool jedisPool = new JedisPool(poolConfig, REDIS_WRITE_HOST, REDIS_PORT)) {
            return jedisPool.getResource();
        }
    }

    @Bean
    @Qualifier("readCache")
    public Jedis jedisRead() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        // Default : 8, consider how many concurrent connections into Redis you will need under load
        poolConfig.setMaxTotal(256);

        try (JedisPool jedisPool = new JedisPool(poolConfig, REDIS_READ_HOST, REDIS_PORT)) {
            return jedisPool.getResource();
        }
    }
}
