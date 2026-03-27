package com.ludoarena.repository;

import com.ludoarena.model.GameScore;
import com.ludoarena.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * GameScore Repository - DB access for game scores and leaderboard.
 * Ported from LudoMaster with additional leaderboard query.
 */
public interface GameScoreRepository extends JpaRepository<GameScore, Long> {

    /** Get a user's game history, most recent first */
    List<GameScore> findByUserOrderByPlayedAtDesc(User user);

    /** Get a user's game history by user ID */
    List<GameScore> findByUserIdOrderByPlayedAtDesc(Long userId);

    /** Leaderboard: top players by score descending */
    List<GameScore> findTop10ByIsWinTrueOrderByPlayedAtDesc();
}
