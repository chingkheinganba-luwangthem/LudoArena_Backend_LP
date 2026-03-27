package com.ludoarena.repository;

import com.ludoarena.model.Game;
import com.ludoarena.model.GameState;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Game Repository - Data access layer for Game entity.
 */
@Repository
public interface GameRepository extends JpaRepository<Game, Long> {

    @Override
    @Cacheable(value = "gameStates", key = "#id")
    Optional<Game> findById(Long id);

    @Override
    @CachePut(value = "gameStates", key = "#entity.id")
    <S extends Game> S save(S entity);

    @Override
    @CacheEvict(value = "gameStates", key = "#id")
    void deleteById(Long id);

    /** Find game by its room ID */
    Optional<Game> findByRoomId(Long roomId);

    /** Find all games with a specific state */
    List<Game> findByState(GameState state);

    /** Find games a specific user played in */
    List<Game> findByPlayersUserId(Long userId);
}
