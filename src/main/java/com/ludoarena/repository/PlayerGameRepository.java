package com.ludoarena.repository;

import com.ludoarena.model.PlayerGame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * PlayerGame Repository - Data access for the player-game junction table.
 */
@Repository
public interface PlayerGameRepository extends JpaRepository<PlayerGame, Long> {

    /** Find all players in a specific game */
    List<PlayerGame> findByGameId(Long gameId);

    /** Find a specific player's entry in a game */
    Optional<PlayerGame> findByGameIdAndUserId(Long gameId, Long userId);

    /** Find all players in a room */
    List<PlayerGame> findByRoomId(Long roomId);

    /** Check if user is already in a room */
    Boolean existsByRoomIdAndUserId(Long roomId, Long userId);
}
