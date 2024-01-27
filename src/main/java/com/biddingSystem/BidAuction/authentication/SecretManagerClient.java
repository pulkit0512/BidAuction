package com.biddingSystem.BidAuction.authentication;

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class SecretManagerClient {

    @Bean
    public SecretManagerServiceClient getSecretManagerClient() throws IOException {
        return SecretManagerServiceClient.create();
    }
}
