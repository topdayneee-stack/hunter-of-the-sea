package com.game.hunter.dto;

public class UserLoginRequest {
    private String username;

    public UserLoginRequest() {
    }

    public UserLoginRequest(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
