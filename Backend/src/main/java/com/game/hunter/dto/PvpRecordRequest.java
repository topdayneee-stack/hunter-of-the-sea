package com.game.hunter.dto;

public class PvpRecordRequest {
    private Integer player1Id;
    private Integer player1CharacterId;
    private Integer player2Id;
    private Integer player2CharacterId;
    private Integer winnerId;
    private Integer matchDurationSeconds;

    public PvpRecordRequest() {
    }

    public Integer getPlayer1Id() {
        return player1Id;
    }

    public void setPlayer1Id(Integer player1Id) {
        this.player1Id = player1Id;
    }

    public Integer getPlayer1CharacterId() {
        return player1CharacterId;
    }

    public void setPlayer1CharacterId(Integer player1CharacterId) {
        this.player1CharacterId = player1CharacterId;
    }

    public Integer getPlayer2Id() {
        return player2Id;
    }

    public void setPlayer2Id(Integer player2Id) {
        this.player2Id = player2Id;
    }

    public Integer getPlayer2CharacterId() {
        return player2CharacterId;
    }

    public void setPlayer2CharacterId(Integer player2CharacterId) {
        this.player2CharacterId = player2CharacterId;
    }

    public Integer getWinnerId() {
        return winnerId;
    }

    public void setWinnerId(Integer winnerId) {
        this.winnerId = winnerId;
    }

    public Integer getMatchDurationSeconds() {
        return matchDurationSeconds;
    }

    public void setMatchDurationSeconds(Integer matchDurationSeconds) {
        this.matchDurationSeconds = matchDurationSeconds;
    }
}
