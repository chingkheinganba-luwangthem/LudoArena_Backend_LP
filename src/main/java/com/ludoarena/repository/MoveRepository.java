package com.ludoarena.repository;

import com.ludoarena.model.Move;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Move Repository - Data access for game move history.
 */
@Repository
public interface MoveRepository extends JpaRepository<Move, Long> {

    /** Find all moves in a game, ordered by creation time */
    List<Move> findByGameIdOrderByCreatedAtAsc(Long gameId);

    /** Find moves by a specific player in a game */
    List<Move> findByGameIdAndUserId(Long gameId, Long userId);
}
