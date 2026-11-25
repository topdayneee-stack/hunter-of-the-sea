package com.game.hunter.dto;

public class ScoreSubmitRequest {
    private Integer userId;
    private Integer characterId;
    private Integer score;
    private String gameMode; // "time_limit" hoặc "endless"

    public ScoreSubmitRequest() {
    }

    public ScoreSubmitRequest(Integer userId, Integer characterId, Integer score, String gameMode) {
        this.userId = userId;
        this.characterId = characterId;
        this.score = score;
        this.gameMode = gameMode;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getCharacterId() {
        return characterId;
    }

    public void setCharacterId(Integer characterId) {
        this.characterId = characterId;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public String getGameMode() {
        return gameMode;
    }

    public void setGameMode(String gameMode) {
        this.gameMode = gameMode;
    }
}
