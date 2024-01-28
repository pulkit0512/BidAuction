package com.biddingSystem.BidAuction.authentication;

import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SecretManagerUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecretManagerUtil.class);

    @Value("${projectId}")
    private String gsmProjectId;

    private SecretManagerServiceClient secretManagerServiceClient;

    public String getUserPassword(String secretName) {
        SecretVersionName secretVersionName = SecretVersionName.of(gsmProjectId, secretName, "latest");
        LOGGER.info("Fetching credentials from GSM for secretName: {}", secretName);
        AccessSecretVersionResponse response = secretManagerServiceClient.accessSecretVersion(secretVersionName);
        LOGGER.info("Completed fetching credentials from GSM");

        return response.getPayload().getData().toStringUtf8();
    }

    @Autowired
    public void setSecretManagerServiceClient(SecretManagerServiceClient secretManagerServiceClient) {
        this.secretManagerServiceClient = secretManagerServiceClient;
    }
}
