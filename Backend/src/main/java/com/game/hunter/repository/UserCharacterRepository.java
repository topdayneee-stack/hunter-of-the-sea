package com.game.hunter.repository;

import com.game.hunter.entity.UserCharacter;
import com.game.hunter.entity.UserCharacterId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserCharacterRepository extends JpaRepository<UserCharacter, UserCharacterId> {
    // Lấy danh sách các nhân vật mà một người chơi đã mở khóa
    List<UserCharacter> findByUser_UserId(Integer userId);
}
