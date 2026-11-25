package com.game.hunter.repository;

import com.game.hunter.entity.PvpMatchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PvpMatchHistoryRepository extends JpaRepository<PvpMatchHistory, Integer> {
    // Lấy lịch sử đấu theo thứ tự trận mới nhất
    List<PvpMatchHistory> findAllByOrderByPlayedAtDesc();

    // Lấy lịch sử đấu của một người chơi (ở vị trí player 1 hoặc player 2)
    List<PvpMatchHistory> findByPlayer1_UserIdOrPlayer2_UserIdOrderByPlayedAtDesc(Integer p1Id, Integer p2Id);
}
