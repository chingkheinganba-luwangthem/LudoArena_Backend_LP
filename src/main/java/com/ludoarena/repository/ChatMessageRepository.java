package com.ludoarena.repository;

import com.ludoarena.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ChatMessage Repository - Data access for in-game chat messages.
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** Find all chat messages in a game, ordered by time */
    List<ChatMessage> findByGameIdOrderByCreatedAtAsc(Long gameId);
}
