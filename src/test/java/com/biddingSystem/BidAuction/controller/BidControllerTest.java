package com.biddingSystem.BidAuction.controller;

import com.biddingSystem.BidAuction.authentication.AuthenticationService;
import com.biddingSystem.BidAuction.dao.impl.SpannerBidDAOImpl;
import com.biddingSystem.BidAuction.service.impl.BidServiceImpl;
import com.google.cloud.Timestamp;
import com.google.cloud.spanner.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;

public class BidControllerTest {
    private static final String DELETE_AUCTION_SQL = "DELETE FROM AUCTION WHERE AUCTION_ID = '1234'";
    private static final String DELETE_BID_SQL = "DELETE FROM BID WHERE AUCTION_ID = '1234'";

    private static final String READ_SQL = "SELECT COUNT(BID_ID) AS CNT FROM BID " +
            "WHERE AUCTION_ID = '1234' AND C_USER_ID = (SELECT C_USER_ID FROM C_USER WHERE EMAIL = 'arorapulkit2@gmail.com')";
    private static final DatabaseClient databaseClient;
    private static final Jedis jedis;

    static {
        Spanner spanner = SpannerOptions.newBuilder()
                .setProjectId("biddingsystem-411900")
                .build()
                .getService();

        databaseClient =  spanner.getDatabaseClient(DatabaseId.of("biddingsystem-411900", "biddingsystemdb", "bidding_system"));

        try (JedisPool jedisPool = new JedisPool("127.0.0.1", 6379)) {
            jedis = jedisPool.getResource();
        }
    }

    private BidController bidController;

    @Mock
    private AuthenticationService authenticationService;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        bidController = new BidController();
        BidServiceImpl bidService = new BidServiceImpl();
        bidService.setJedisRead(jedis);
        bidService.setJedisWrite(jedis);

        SpannerBidDAOImpl spannerBidDAO = new SpannerBidDAOImpl();
        spannerBidDAO.setDatabaseClient(databaseClient);
        bidService.setSpannerBidDAO(spannerBidDAO);

        bidController.setBidService(bidService);
        bidController.setAuthenticationService(authenticationService);

        Mockito.when(authenticationService.getUserNameFromValidToken("token")).thenReturn("arorapulkit2_gmail-com");
    }

    @Test
    public void testHello() {
        String response = bidController.hello();
        Assert.assertEquals("Service for placing bids for auction items.", response);
    }

    @Test
    public void testSuccessLogin() throws Exception {
        String user = "username@gmail.com";
        String modifiedUser = "username_gmail-com";
        String password = "password";
        Mockito.when(authenticationService.login(modifiedUser, password)).thenReturn("token");
        ResponseEntity<String> response = bidController.login(user, password);
        Assert.assertEquals(200, response.getStatusCode().value());
    }

    @Test
    public void testFailLogin() throws Exception {
        String user = "username@gmail.com";
        String modifiedUser = "username_gmail-com";
        String password = "password";
        Mockito.when(authenticationService.login(modifiedUser, password)).thenThrow(new Exception("INVALID_CREDENTIALS"));
        ResponseEntity<String> response = bidController.login(user, password);
        Assert.assertEquals(401, response.getStatusCode().value());
    }

    @Test
    public void testFailurePlaceBidTokenNotValid() {
        Mockito.when(authenticationService.getUserNameFromValidToken("token")).thenReturn(null);
        ResponseEntity<String> response = bidController.placeBid("1234", 1200.00, "token");
        Assert.assertEquals(401, response.getStatusCode().value());
    }

    @Test
    public void testFailurePlaceBid() {
        BidServiceImpl bidService = Mockito.mock(BidServiceImpl.class);
        bidController.setBidService(bidService);

        Mockito.when(bidService.placeBid("1234", 1200.00, "arorapulkit2@gmail.com")).thenReturn(null);
        ResponseEntity<String> response = bidController.placeBid("1234", 1200.00, "token");
        Assert.assertEquals(500, response.getStatusCode().value());
    }

    @Test
    public void testPlaceBidAuctionCompleted() {
        ResponseEntity<String> response = bidController.placeBid("1234", 1200, "token");
        Assert.assertEquals(400, response.getStatusCode().value());
        Assert.assertEquals("Auction Completed, Bid can't be placed.", response.getBody());
    }

    @Test
    public void testPlaceBidBasePriceLarger() {
        Mutation mutation = Mutation.newInsertBuilder("AUCTION")
                .set("AUCTION_ID").to("1234")
                .set("CATEGORY").to("Car")
                .set("BASE_PRICE").to(1400.00)
                .set("AUCTION_CREATION_TIME").to(Timestamp.now())
                .set("AUCTION_EXPIRY_TIME").to(Timestamp.ofTimeSecondsAndNanos(LocalDateTime.now().plusDays(4).toEpochSecond(ZoneOffset.UTC), 0))
                .build();
        databaseClient.write(Collections.singleton(mutation));

        ResponseEntity<String> response = bidController.placeBid("1234", 1200, "token");
        Assert.assertEquals(400, response.getStatusCode().value());
        Assert.assertEquals("Bid Price should be greater then Base Price.", response.getBody());

        databaseClient.readWriteTransaction()
                .run(transaction -> {
                    transaction.executeUpdate(Statement.of(DELETE_AUCTION_SQL));
                    return null;
                });
    }

    @Test
    public void testPlaceBidMaxBidPriceLarger() {
        Mutation mutation = Mutation.newInsertBuilder("AUCTION")
                .set("AUCTION_ID").to("1234")
                .set("CATEGORY").to("Car")
                .set("BASE_PRICE").to(1400.00)
                .set("MAX_BID_PRICE").to(1600.00)
                .set("AUCTION_CREATION_TIME").to(Timestamp.now())
                .set("AUCTION_EXPIRY_TIME").to(Timestamp.ofTimeSecondsAndNanos(LocalDateTime.now().plusDays(4).toEpochSecond(ZoneOffset.UTC), 0))
                .build();
        databaseClient.write(Collections.singleton(mutation));

        ResponseEntity<String> response = bidController.placeBid("1234", 1500, "token");
        Assert.assertEquals(400, response.getStatusCode().value());
        Assert.assertEquals("Already a higher bid is placed for this auction, Re-Shop and place bid again.", response.getBody());

        databaseClient.readWriteTransaction()
                .run(transaction -> {
                    transaction.executeUpdate(Statement.of(DELETE_AUCTION_SQL));
                    return null;
                });
    }

    @Test
    public void testPlaceBidFirstTimeSuccess() {
        Mutation mutation = Mutation.newInsertBuilder("AUCTION")
                .set("AUCTION_ID").to("1234")
                .set("CATEGORY").to("Car")
                .set("BASE_PRICE").to(1400.00)
                .set("AUCTION_CREATION_TIME").to(Timestamp.now())
                .set("AUCTION_EXPIRY_TIME").to(Timestamp.ofTimeSecondsAndNanos(LocalDateTime.now().plusDays(4).toEpochSecond(ZoneOffset.UTC), 0))
                .build();
        databaseClient.write(Collections.singleton(mutation));

        ResultSet resultSet = databaseClient.singleUse().executeQuery(Statement.of(READ_SQL));
        if (resultSet.next()) {
            Assert.assertEquals(0, resultSet.getLong("CNT"));
        }

        ResponseEntity<String> response = bidController.placeBid("1234", 1500, "token");
        Assert.assertEquals(200, response.getStatusCode().value());
        Assert.assertEquals("Success, Bid Placed.", response.getBody());

        ResultSet resultSet1 = databaseClient.singleUse().executeQuery(Statement.of(READ_SQL));
        if (resultSet1.next()) {
            Assert.assertEquals(1, resultSet1.getLong("CNT"));
        }

        databaseClient.readWriteTransaction()
                .run(transaction -> {
                    transaction.executeUpdate(Statement.of(DELETE_BID_SQL));
                    transaction.executeUpdate(Statement.of(DELETE_AUCTION_SQL));
                    return null;
                });

        jedis.del("1234");
    }

    @Test
    public void testPlaceBidSecondTimeFailureFromCache() {
        Mutation mutation = Mutation.newInsertBuilder("AUCTION")
                .set("AUCTION_ID").to("1234")
                .set("CATEGORY").to("Car")
                .set("BASE_PRICE").to(1400.00)
                .set("AUCTION_CREATION_TIME").to(Timestamp.now())
                .set("AUCTION_EXPIRY_TIME").to(Timestamp.ofTimeSecondsAndNanos(LocalDateTime.now().plusDays(4).toEpochSecond(ZoneOffset.UTC), 0))
                .build();
        databaseClient.write(Collections.singleton(mutation));

        ResultSet resultSet = databaseClient.singleUse().executeQuery(Statement.of(READ_SQL));
        if (resultSet.next()) {
            Assert.assertEquals(0, resultSet.getLong("CNT"));
        }

        ResponseEntity<String> response = bidController.placeBid("1234", 1500, "token");
        Assert.assertEquals(200, response.getStatusCode().value());
        Assert.assertEquals("Success, Bid Placed.", response.getBody());

        ResultSet resultSet1 = databaseClient.singleUse().executeQuery(Statement.of(READ_SQL));
        if (resultSet1.next()) {
            Assert.assertEquals(1, resultSet1.getLong("CNT"));
        }

        ResponseEntity<String> response1 = bidController.placeBid("1234", 1450, "token");
        Assert.assertEquals(400, response1.getStatusCode().value());
        Assert.assertEquals("User bid price is lesser then current max bid, Re-shop auction and place bid again", response1.getBody());

        databaseClient.readWriteTransaction()
                .run(transaction -> {
                    transaction.executeUpdate(Statement.of(DELETE_BID_SQL));
                    transaction.executeUpdate(Statement.of(DELETE_AUCTION_SQL));
                    return null;
                });

        jedis.del("1234");
    }

    @Test
    public void testPlaceBidSecondTimeSuccess() {
        Mutation mutation = Mutation.newInsertBuilder("AUCTION")
                .set("AUCTION_ID").to("1234")
                .set("CATEGORY").to("Car")
                .set("BASE_PRICE").to(1400.00)
                .set("AUCTION_CREATION_TIME").to(Timestamp.now())
                .set("AUCTION_EXPIRY_TIME").to(Timestamp.ofTimeSecondsAndNanos(LocalDateTime.now().plusDays(4).toEpochSecond(ZoneOffset.UTC), 0))
                .build();
        databaseClient.write(Collections.singleton(mutation));

        ResultSet resultSet = databaseClient.singleUse().executeQuery(Statement.of(READ_SQL));
        if (resultSet.next()) {
            Assert.assertEquals(0, resultSet.getLong("CNT"));
        }

        ResponseEntity<String> response = bidController.placeBid("1234", 1500, "token");
        Assert.assertEquals(200, response.getStatusCode().value());
        Assert.assertEquals("Success, Bid Placed.", response.getBody());

        ResultSet resultSet1 = databaseClient.singleUse().executeQuery(Statement.of(READ_SQL));
        if (resultSet1.next()) {
            Assert.assertEquals(1, resultSet1.getLong("CNT"));
        }

        ResponseEntity<String> response1 = bidController.placeBid("1234", 1550, "token");
        Assert.assertEquals(200, response1.getStatusCode().value());
        Assert.assertEquals("Success, Bid Placed.", response1.getBody());

        databaseClient.readWriteTransaction()
                .run(transaction -> {
                    transaction.executeUpdate(Statement.of(DELETE_BID_SQL));
                    transaction.executeUpdate(Statement.of(DELETE_AUCTION_SQL));
                    return null;
                });

        jedis.del("1234");
    }
}