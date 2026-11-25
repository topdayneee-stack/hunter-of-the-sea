package com.game.hunter.repository;

import com.game.hunter.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    // Tìm người chơi theo username (dùng khi đăng nhập hoặc kiểm tra trùng tên)
    Optional<User> findByUsername(String username);
}
