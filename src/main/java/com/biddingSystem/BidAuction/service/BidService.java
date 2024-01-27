package com.biddingSystem.BidAuction.service;

public interface BidService {
    String placeBid(String auctionId, double bidPrice, String userEmail);
}
