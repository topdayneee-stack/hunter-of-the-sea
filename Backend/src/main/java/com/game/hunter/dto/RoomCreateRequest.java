package com.game.hunter.dto;

public class RoomCreateRequest {
    private String username;
    private Integer characterId;

    public RoomCreateRequest() {}

    public RoomCreateRequest(String username, Integer characterId) {
        this.username = username;
        this.characterId = characterId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Integer getCharacterId() {
        return characterId;
    }

    public void setCharacterId(Integer characterId) {
        this.characterId = characterId;
    }
}
