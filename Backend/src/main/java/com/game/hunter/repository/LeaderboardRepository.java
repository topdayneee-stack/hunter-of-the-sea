package com.game.hunter.repository;

import com.game.hunter.entity.Leaderboard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeaderboardRepository extends JpaRepository<Leaderboard, Integer> {
    // Lấy top 10 điểm cao nhất theo chế độ chơi (time_limit hoặc endless)
    List<Leaderboard> findTop10ByGameModeOrderByScoreDesc(String gameMode);

    // Lấy toàn bộ điểm theo chế độ chơi sắp xếp giảm dần
    List<Leaderboard> findByGameModeOrderByScoreDesc(String gameMode);

    // Lấy các ván đấu của 1 người chơi cụ thể
    List<Leaderboard> findByUser_UserIdOrderByScoreDesc(Integer userId);
}
