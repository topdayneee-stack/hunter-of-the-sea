package com.game.hunter.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Characters")
public class GameCharacter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "character_id")
    private Integer characterId;

    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "base_size_width", nullable = false)
    private Integer baseSizeWidth;

    @Column(name = "base_size_height", nullable = false)
    private Integer baseSizeHeight;

    @Column(name = "base_speed", nullable = false, precision = 4, scale = 2)
    private BigDecimal baseSpeed;

    @Column(name = "skill_name", nullable = false, length = 100)
    private String skillName;

    @Column(name = "skill_description", columnDefinition = "NVARCHAR(MAX)")
    private String skillDescription;

    @Column(name = "mana_cost")
    private Integer manaCost = 5;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public GameCharacter() {
    }

    public GameCharacter(String name, Integer baseSizeWidth, Integer baseSizeHeight, BigDecimal baseSpeed, String skillName, String skillDescription, Integer manaCost) {
        this.name = name;
        this.baseSizeWidth = baseSizeWidth;
        this.baseSizeHeight = baseSizeHeight;
        this.baseSpeed = baseSpeed;
        this.skillName = skillName;
        this.skillDescription = skillDescription;
        this.manaCost = manaCost;
        this.createdAt = LocalDateTime.now();
    }

    public Integer getCharacterId() {
        return characterId;
    }

    public void setCharacterId(Integer characterId) {
        this.characterId = characterId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getBaseSizeWidth() {
        return baseSizeWidth;
    }

    public void setBaseSizeWidth(Integer baseSizeWidth) {
        this.baseSizeWidth = baseSizeWidth;
    }

    public Integer getBaseSizeHeight() {
        return baseSizeHeight;
    }

    public void setBaseSizeHeight(Integer baseSizeHeight) {
        this.baseSizeHeight = baseSizeHeight;
    }

    public BigDecimal getBaseSpeed() {
        return baseSpeed;
    }

    public void setBaseSpeed(BigDecimal baseSpeed) {
        this.baseSpeed = baseSpeed;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public String getSkillDescription() {
        return skillDescription;
    }

    public void setSkillDescription(String skillDescription) {
        this.skillDescription = skillDescription;
    }

    public Integer getManaCost() {
        return manaCost;
    }

    public void setManaCost(Integer manaCost) {
        this.manaCost = manaCost;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
