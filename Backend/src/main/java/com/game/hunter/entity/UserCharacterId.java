package com.game.hunter.entity;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class UserCharacterId implements Serializable {

    private Integer userId;
    private Integer characterId;

    public UserCharacterId() {
    }

    public UserCharacterId(Integer userId, Integer characterId) {
        this.userId = userId;
        this.characterId = characterId;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserCharacterId that = (UserCharacterId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(characterId, that.characterId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, characterId);
    }
}
