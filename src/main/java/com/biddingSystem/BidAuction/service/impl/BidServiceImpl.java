package com.biddingSystem.BidAuction.service.impl;

import com.biddingSystem.BidAuction.dao.impl.SpannerBidDAOImpl;
import com.biddingSystem.BidAuction.dto.BidResponse;
import com.biddingSystem.BidAuction.service.BidService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

@Component
public class BidServiceImpl implements BidService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BidServiceImpl.class);

    private SpannerBidDAOImpl spannerBidDAO;
    private Jedis jedisWrite;
    private Jedis jedisRead;
    @Override
    public String placeBid(String auctionId, double bidPrice, String userEmail) {
        LOGGER.info("Placing bid for auctionId: {}, by user: {}", auctionId, userEmail);
        String cacheValue = jedisRead.get(auctionId);
        if (StringUtils.isNotEmpty(cacheValue)) {
            LOGGER.info("Checking in Cache if we already have a bid higher then the bid price.");
            double maxBidPrice = Double.parseDouble(cacheValue);
            if (maxBidPrice >= bidPrice) {
                return "User bid price is lesser then current max bid, Re-shop auction and place bid again";
            }
        }

        BidResponse bidResponse = spannerBidDAO.placeBid(auctionId, bidPrice, userEmail);
        if (bidResponse.isBidPlaced()) {
            LOGGER.info("Writing the bidPrice {} as new max bid for auction {} in cache.", bidPrice, auctionId);
            jedisWrite.set(auctionId, String.valueOf(bidPrice), SetParams.setParams().exAt(bidResponse.getExpireAtInSeconds()));
        }
        return bidResponse.getMessage();
    }

    @Autowired
    @Qualifier("writeCache")
    public void setJedisWrite(Jedis jedisWrite) {
        this.jedisWrite = jedisWrite;
    }

    @Autowired
    @Qualifier("readCache")
    public void setJedisRead(Jedis jedisRead) {
        this.jedisRead = jedisRead;
    }

    @Autowired
    public void setSpannerBidDAO(SpannerBidDAOImpl spannerBidDAO) {
        this.spannerBidDAO = spannerBidDAO;
    }
}
