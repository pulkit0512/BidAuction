package com.biddingSystem.BidAuction.dao;

import com.biddingSystem.BidAuction.dto.BidResponse;

public interface SpannerBidDAO {
    BidResponse placeBid(String auctionId, double bidPrice, String userEmail);
}
