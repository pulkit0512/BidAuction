package com.biddingSystem.BidAuction.dao.impl;

import com.biddingSystem.BidAuction.dao.SpannerBidDAO;
import com.biddingSystem.BidAuction.dto.BidResponse;
import com.google.cloud.Timestamp;
import com.google.cloud.spanner.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

@Repository
public class SpannerBidDAOImpl implements SpannerBidDAO {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpannerBidDAOImpl.class);
    private static final String BASE_PRICE = "BASE_PRICE";
    private static final String MAX_BID_PRICE = "MAX_BID_PRICE";
    private static final String PREV_BID_PRICE = "PREV_BID_PRICE";
    private static final String AUCTION_EXPIRY_TIME = "AUCTION_EXPIRY_TIME";
    private static final String AUCTION_ID = "auctionId";
    private static final String EMAIL = "email";
    private static final String BID_PRICE = "bidPrice";

    private static final String READ_CURRENT_AUCTION_DATA_SQL = "SELECT BASE_PRICE, MAX_BID_PRICE FROM AUCTION " +
            "WHERE AUCTION_ID = @auctionId AND AUCTION_EXPIRY_TIME > CURRENT_TIMESTAMP";
    private static final String READ_CURRENT_BID_DATA_SQL = "SELECT MAX_BID_PRICE FROM BID " +
            "WHERE AUCTION_ID = @auctionId AND C_USER_ID = (SELECT C_USER_ID FROM C_USER WHERE EMAIL = @email)";
    private static final String UPDATE_AUCTION_SQL = "UPDATE AUCTION SET MAX_BID_PRICE = @bidPrice, C_USER_ID = (SELECT C_USER_ID FROM C_USER WHERE EMAIL = @email) " +
            "WHERE AUCTION_ID = @auctionID THEN RETURN AUCTION_EXPIRY_TIME";
    private static final String UPDATE_BID_SQL = "UPDATE BID SET MAX_BID_PRICE = @bidPrice WHERE AUCTION_ID = @auctionID " +
            "AND C_USER_ID = (SELECT C_USER_ID FROM C_USER WHERE EMAIL = @email)";
    private static final String INSERT_BID_SQL = "INSERT INTO BID(AUCTION_ID, C_USER_ID, MAX_BID_PRICE, BID_TIME) " +
            "VALUES (@auctionID, (SELECT C_USER_ID FROM C_USER WHERE EMAIL = @email), @bidPrice, CURRENT_TIMESTAMP)";
    private DatabaseClient databaseClient;
    @Override
    public BidResponse placeBid(String auctionId, double bidPrice, String userEmail) {
        LOGGER.info("Inside SpannerBidDao, placing bid.");

        return databaseClient.readWriteTransaction().run(transaction -> {
            BidResponse bidResponse;
            Map<String, Double> currentDataMap = getCurrentData(transaction, auctionId, userEmail);
            String preCheckFailedMessage = preChecks(currentDataMap, bidPrice);
            if (StringUtils.isNotEmpty(preCheckFailedMessage)) {
                bidResponse = new BidResponse();
                bidResponse.setBidPlaced(false);
                bidResponse.setMessage(preCheckFailedMessage);
                LOGGER.info(preCheckFailedMessage);
                return bidResponse;
            }

            long expiryInSeconds = updateAuctionAndGetExpiry(transaction, auctionId, bidPrice, userEmail);
            if (currentDataMap.get(PREV_BID_PRICE) == null) {
                updateBidInfo(transaction, auctionId, bidPrice, userEmail, INSERT_BID_SQL);
            } else {
                updateBidInfo(transaction, auctionId, bidPrice, userEmail, UPDATE_BID_SQL);
            }

            bidResponse = new BidResponse();
            bidResponse.setBidPlaced(true);
            bidResponse.setExpireAtInSeconds(expiryInSeconds);
            bidResponse.setMessage("Success, Bid Placed.");
            LOGGER.info(bidResponse.getMessage());
            return bidResponse;
        });
    }

    private String preChecks(Map<String, Double> currentDataMap, double bidPrice) {
        LOGGER.info("Applying pre-checks for placing bid");
        if (currentDataMap.get(BASE_PRICE) == null) {
            return "Auction Completed, Bid can't be placed.";
        }

        if (currentDataMap.get(BASE_PRICE) > bidPrice) {
            return "Bid Price should be greater then Base Price.";
        }

        if (currentDataMap.get(MAX_BID_PRICE) != null && currentDataMap.get(MAX_BID_PRICE) >= bidPrice) {
            return "Already a higher bid is placed for this auction, Re-Shop and place bid again.";
        }

        return null;
    }

    private Map<String, Double> getCurrentData(TransactionContext transaction, String auctionId, String userEmail) {
        Map<String, Double> currentData = new HashMap<>();
        LOGGER.info("Getting current data for auction.");
        Statement readStatement = Statement.newBuilder(READ_CURRENT_AUCTION_DATA_SQL)
                .bind(AUCTION_ID)
                .to(auctionId)
                .build();

        try(ResultSet resultSet = transaction.executeQuery(readStatement)) {
            if (resultSet.next()) {
                currentData.put(BASE_PRICE, resultSet.getDouble(BASE_PRICE));
                Value maxBidValue = resultSet.getValue(MAX_BID_PRICE);

                if (!maxBidValue.isNull()) {
                    currentData.put(MAX_BID_PRICE, maxBidValue.getFloat64());
                }
            }
        }
        if (currentData.get(BASE_PRICE) != null) {
            Statement bidStatement = Statement.newBuilder(READ_CURRENT_BID_DATA_SQL)
                    .bind(AUCTION_ID)
                    .to(auctionId)
                    .bind(EMAIL)
                    .to(userEmail)
                    .build();
            try (ResultSet resultSet = transaction.executeQuery(bidStatement)) {
                if (resultSet.next()) {
                    Value prevBidValue = resultSet.getValue(MAX_BID_PRICE);
                    if (!prevBidValue.isNull()) {
                        currentData.put(PREV_BID_PRICE, prevBidValue.getFloat64());
                    }
                }
            }
        }
        return currentData;
    }

    private long updateAuctionAndGetExpiry(TransactionContext transaction, String auctionId, double bidPrice, String userEmail) {
        LOGGER.info("Updating auction details with new max bid.");
        Statement updateAuctionStatement = Statement.newBuilder(UPDATE_AUCTION_SQL)
                .bind(AUCTION_ID)
                .to(auctionId)
                .bind(BID_PRICE)
                .to(bidPrice)
                .bind(EMAIL)
                .to(userEmail)
                .build();

        try(ResultSet resultSet = transaction.executeQuery(updateAuctionStatement)) {
            if (resultSet.next()) {
                Timestamp expiryTime = resultSet.getTimestamp(AUCTION_EXPIRY_TIME);
                return expiryTime.getSeconds();
            }
        }
        return 0;
    }

    private void updateBidInfo(TransactionContext transaction, String auctionId, double bidPrice, String userEmail, String sql) {
        LOGGER.info("Update Bid info for user");
        Statement updateBidStatement = Statement.newBuilder(sql)
                .bind(BID_PRICE)
                .to(bidPrice)
                .bind(AUCTION_ID)
                .to(auctionId)
                .bind(EMAIL)
                .to(userEmail)
                .build();
        transaction.executeUpdate(updateBidStatement);
    }

    @Autowired
    public void setDatabaseClient(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }
}
