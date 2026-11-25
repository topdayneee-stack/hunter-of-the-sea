package com.game.hunter.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "User_Characters")
public class UserCharacter {

    @EmbeddedId
    private UserCharacterId id;

    @ManyToOne
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @MapsId("characterId")
    @JoinColumn(name = "character_id")
    private GameCharacter character;

    @Column(name = "unlocked_at")
    private LocalDateTime unlockedAt = LocalDateTime.now();

    public UserCharacter() {
    }

    public UserCharacter(User user, GameCharacter character) {
        this.user = user;
        this.character = character;
        this.id = new UserCharacterId(user.getUserId(), character.getCharacterId());
        this.unlockedAt = LocalDateTime.now();
    }

    public UserCharacterId getId() {
        return id;
    }

    public void setId(UserCharacterId id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public GameCharacter getCharacter() {
        return character;
    }

    public void setCharacter(GameCharacter character) {
        this.character = character;
    }

    public LocalDateTime getUnlockedAt() {
        return unlockedAt;
    }

    public void setUnlockedAt(LocalDateTime unlockedAt) {
        this.unlockedAt = unlockedAt;
    }
}
