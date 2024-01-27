package com.biddingSystem.BidAuction.controller;

import com.biddingSystem.BidAuction.authentication.AuthenticationService;
import com.biddingSystem.BidAuction.service.impl.BidServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BidController {
    private static final Logger LOGGER = LoggerFactory.getLogger(BidController.class);
    private static final String TOKEN_NOT_VALID = "Token Not Valid";
    private static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    private static final String SUCCESS = "Success";

    private AuthenticationService authenticationService;
    private BidServiceImpl bidService;

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestParam String username, @RequestParam String password) {
        String modifiedUserName = username.replace('@','_').replace('.','-');
        try {
            String jwtToken = authenticationService.login(modifiedUserName, password);
            return new ResponseEntity<>(jwtToken, HttpStatus.OK);
        } catch (Exception ex) {
            LOGGER.warn(INVALID_CREDENTIALS);
            return new ResponseEntity<>(INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED);
        }
    }

    @PostMapping("/placeBid")
    public ResponseEntity<String> placeBid(@RequestParam String auctionId, @RequestParam double bidPrice,
                                           @RequestHeader("Authorization") String token) {
        LOGGER.info("Placing bid for the given auctionId: {}", auctionId);
        try {
            String userEmail = authenticationService.getUserNameFromValidToken(token);
            if (userEmail == null) {
                LOGGER.warn(TOKEN_NOT_VALID);
                return new ResponseEntity<>(TOKEN_NOT_VALID, HttpStatus.UNAUTHORIZED);
            }
            userEmail = userEmail.replace('_', '@').replace('-','.');
            String response = bidService.placeBid(auctionId, bidPrice, userEmail);

            if (response.contains(SUCCESS)) {
                return new ResponseEntity<>(response, HttpStatus.OK);
            } else {
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }
        } catch (Exception ex) {
            LOGGER.error("Error while placing bid for auction: {} with message {}", auctionId, ex.getMessage());
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Autowired
    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @Autowired
    public void setBidService(BidServiceImpl bidService) {
        this.bidService = bidService;
    }
}
