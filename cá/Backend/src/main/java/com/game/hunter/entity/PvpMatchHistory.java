package com.game.hunter.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "Pvp_Match_History")
public class PvpMatchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "match_id")
    private Integer matchId;

    @ManyToOne
    @JoinColumn(name = "player1_id", nullable = false)
    private User player1;

    @ManyToOne
    @JoinColumn(name = "player1_character_id", nullable = false)
    private GameCharacter player1Character;

    @ManyToOne
    @JoinColumn(name = "player2_id", nullable = false)
    private User player2;

    @ManyToOne
    @JoinColumn(name = "player2_character_id", nullable = false)
    private GameCharacter player2Character;

    @ManyToOne
    @JoinColumn(name = "winner_id", nullable = false)
    private User winner;

    @Column(name = "match_duration_seconds", nullable = false)
    private Integer matchDurationSeconds;

    @Column(name = "played_at")
    private LocalDateTime playedAt = LocalDateTime.now();

    public PvpMatchHistory() {
    }

    public PvpMatchHistory(User player1, GameCharacter player1Character, User player2, GameCharacter player2Character, User winner, Integer matchDurationSeconds) {
        this.player1 = player1;
        this.player1Character = player1Character;
        this.player2 = player2;
        this.player2Character = player2Character;
        this.winner = winner;
        this.matchDurationSeconds = matchDurationSeconds;
        this.playedAt = LocalDateTime.now();
    }

    public Integer getMatchId() {
        return matchId;
    }

    public void setMatchId(Integer matchId) {
        this.matchId = matchId;
    }

    public User getPlayer1() {
        return player1;
    }

    public void setPlayer1(User player1) {
        this.player1 = player1;
    }

    public GameCharacter getPlayer1Character() {
        return player1Character;
    }

    public void setPlayer1Character(GameCharacter player1Character) {
        this.player1Character = player1Character;
    }

    public User getPlayer2() {
        return player2;
    }

    public void setPlayer2(User player2) {
        this.player2 = player2;
    }

    public GameCharacter getPlayer2Character() {
        return player2Character;
    }

    public void setPlayer2Character(GameCharacter player2Character) {
        this.player2Character = player2Character;
    }

    public User getWinner() {
        return winner;
    }

    public void setWinner(User winner) {
        this.winner = winner;
    }

    public Integer getMatchDurationSeconds() {
        return matchDurationSeconds;
    }

    public void setMatchDurationSeconds(Integer matchDurationSeconds) {
        this.matchDurationSeconds = matchDurationSeconds;
    }

    public LocalDateTime getPlayedAt() {
        return playedAt;
    }

    public void setPlayedAt(LocalDateTime playedAt) {
        this.playedAt = playedAt;
    }
}
