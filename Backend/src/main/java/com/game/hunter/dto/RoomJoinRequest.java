package com.game.hunter.dto;

public class RoomJoinRequest {
    private String roomCode;
    private String username;
    private Integer characterId;

    public RoomJoinRequest() {}

    public RoomJoinRequest(String roomCode, String username, Integer characterId) {
        this.roomCode = roomCode;
        this.username = username;
        this.characterId = characterId;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public void setRoomCode(String roomCode) {
        this.roomCode = roomCode;
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
