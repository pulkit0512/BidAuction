package com.biddingSystem.BidAuction.authentication;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationService {

    private JwtTokenUtil jwtTokenUtil;

    private SecretManagerUtil secretManagerUtil;

    public String login(String userName, String password) throws Exception {
        authenticate(userName, password);
        String payload = userName+"@"+password;
        return jwtTokenUtil.generateToken(payload);
    }

    public String getUserNameFromValidToken(String tokenHeader) {
        // JWT Token is in the form "Bearer token". Remove Bearer word and get only the Token
        if (tokenHeader != null && tokenHeader.startsWith("Bearer ")) {
            String jwtToken = tokenHeader.substring(7);
            String[] userInfo = jwtTokenUtil.getPayloadFromToken(jwtToken).split("@");
            if (jwtTokenUtil.validateToken(jwtToken, userInfo)) {
                return userInfo[0];
            } else {
                return null;
            }
        }
        return null;
    }

    private void authenticate(String userName, String password) throws Exception {
        String storedPassword = secretManagerUtil.getUserPassword(userName);
        if (!password.equals(storedPassword)) {
            throw new Exception("INVALID_CREDENTIALS");
        }
    }

    @Autowired
    public void setJwtTokenUtil(JwtTokenUtil jwtTokenUtil) {
        this.jwtTokenUtil = jwtTokenUtil;
    }

    @Autowired
    public void setSecretManagerUtil(SecretManagerUtil secretManagerUtil) {
        this.secretManagerUtil = secretManagerUtil;
    }
}
