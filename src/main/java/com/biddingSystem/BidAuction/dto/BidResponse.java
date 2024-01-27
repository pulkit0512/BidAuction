package com.biddingSystem.BidAuction.dto;

public class BidResponse {
    private boolean bidPlaced;
    private long expireAtInSeconds;
    private String message;

    public boolean isBidPlaced() {
        return bidPlaced;
    }

    public void setBidPlaced(boolean bidPlaced) {
        this.bidPlaced = bidPlaced;
    }

    public long getExpireAtInSeconds() {
        return expireAtInSeconds;
    }

    public void setExpireAtInSeconds(long expireAtInSeconds) {
        this.expireAtInSeconds = expireAtInSeconds;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
