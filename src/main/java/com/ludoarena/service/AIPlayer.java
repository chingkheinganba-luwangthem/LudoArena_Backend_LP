package com.ludoarena.service;

import com.ludoarena.dto.MoveDTO;
import com.ludoarena.model.*;
import com.ludoarena.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;

/**
 * AI Player Service - Handles computer opponent logic.
 *
 * THREAD ARCHITECTURE:
 * - AI moves execute on Spring's async task executor thread pool.
 * - @Async causes the method to run on a separate thread from the TaskExecutor.
 * - The async thread pool is configured in application.properties:
 * spring.task.execution.pool.core-size=4
 * spring.task.execution.pool.max-size=8
 * spring.task.execution.thread-name-prefix=ludoarena-async-
 *
 * - When it's the AI's turn:
 * 1. WebSocket controller detects it's a bot's turn
 * 2. Calls aiPlayer.makeMove() which runs @Async on the task executor pool
 * 3. AI rolls dice, picks best move (with a small delay for realism)
 * 4. Acquires the game's ReentrantLock for thread-safe state mutation
 * 5. Processes the move through GameEngine
 * 6. Broadcasts the result via WebSocket
 *
 * AI STRATEGY (Simple):
 * Priority order:
 * 1. Kill an opponent's token if possible
 * 2. Move a token to home if possible
 * 3. Move the token that is furthest along (closest to home)
 * 4. Take a token out of base if dice is 6
 */
@Service
public class AIPlayer {

    private static final Logger log = LoggerFactory.getLogger(AIPlayer.class);

    // ✅ FIX: Added missing fields (this was causing your error)
    private final GameEngine gameEngine;
    private final GameService gameService;
    private final GameRepository gameRepository;
    private final PlayerGameRepository playerGameRepository;

    private final SimpMessagingTemplate messagingTemplate;

    public AIPlayer(GameEngine gameEngine,
            GameService gameService,
            GameRepository gameRepository,
            PlayerGameRepository playerGameRepository,
            SimpMessagingTemplate messagingTemplate) {

        // ✅ Inject dependencies
        this.gameEngine = gameEngine;
        this.gameService = gameService;
        this.gameRepository = gameRepository;
        this.playerGameRepository = playerGameRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Make an AI move asynchronously.
     *
     * THREAD: @Async causes this to run on the Spring TaskExecutor thread pool.
     * We use a loop to handle multiple moves (extra turns from 6s or kills).
     */
    @Async
    public void makeMove(Long gameId, Long botUserId) {
        try {
            boolean hasMoreTurns = true;
            while (hasMoreTurns) {

                // Add a small delay for realism (thinking time)
                Thread.sleep(1500 + ThreadLocalRandom.current().nextInt(1000));

                ReentrantLock lock = gameService.getGameLock(gameId);
                lock.lock();
                try {
                    Game game = gameRepository.findById(gameId).orElse(null);
                    if (game == null || game.getState() != GameState.IN_PROGRESS)
                        return;

                    // Verify it's still this bot's turn
                    final int currentTurnIndex = game.getCurrentTurnIndex();
                    PlayerGame botPlayer = game.getPlayers().stream()
                            .filter(p -> p.getUser().getId().equals(botUserId)
                                    && p.getTurnOrder().equals(currentTurnIndex))
                            .findFirst().orElse(null);

                    if (botPlayer == null)
                        return;

                    // 1. Roll dice
                    int diceValue = ThreadLocalRandom.current().nextInt(1, 7);

                    // Broadcast dice roll for animation
                    messagingTemplate.convertAndSend("/topic/game/" + gameId, Map.of(
                            "type", "DICE_ROLL",
                            "userId", botUserId,
                            "diceValue", diceValue,
                            "validMoves", List.of()));

                    // Delay for dice animation
                    Thread.sleep(1000);

                    // 2. Get valid moves
                    List<Integer> validMoves = gameEngine.getValidMoves(gameId, botUserId, diceValue);

                    if (validMoves.isEmpty()) {
                        System.out.println("[AI] No valid moves for bot " + botUserId + ", skipping...");
                        gameEngine.skipTurn(gameId);
                        hasMoreTurns = false;

                        // If next player is also a bot, trigger them (handled in GameService/Controller normally, 
                        // but doing it here as a fallback)
                        Long nextBotId = gameService.getNextTurnBotId(gameId);
                        if (nextBotId != null && !nextBotId.equals(botUserId)) {
                            hasMoreTurns = false; // exit current while loop
                            // Launch next bot turn async
                            makeMove(gameId, nextBotId);
                        }

                    } else {

                        // 3. Pick and process move
                        int bestToken = pickBestMove(botPlayer, validMoves, diceValue);
                        MoveDTO moveResult = gameEngine.processMove(gameId, botUserId, diceValue, bestToken);

                        // BROADCAST MOVE DTO (Critical for animation)
                        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/move", moveResult);

                        // Broadcast state update
                        gameService.broadcastGameStateById(gameId);

                        // 4. Check if we get an extra turn
                        if (Boolean.TRUE.equals(moveResult.getExtraTurn())) {
                            System.out.println("[AI] Bot " + botUserId + " got an extra turn!");
                            hasMoreTurns = true;
                        } else {
                            hasMoreTurns = false;

                            // If turn moved to another bot, trigger them
                            Long nextBotId = gameService.getNextTurnBotId(gameId);
                            if (nextBotId != null && !nextBotId.equals(botUserId)) {
                                makeMove(gameId, nextBotId);
                            }
                        }
                    }

                } finally {
                    lock.unlock();
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("AI move interrupted for game {}", gameId);
        } catch (Exception e) {
            log.error("AI move error for game {}: {}", gameId, e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Pick the best token to move using simple AI strategy.
     *
     * Priority:
     * 1. Move token closest to home
     * 2. Take token out of base on a 6
     */
    private int pickBestMove(PlayerGame botPlayer, List<Integer> validMoves, int diceValue) {

        int[] positions = botPlayer.getTokenPositionArray();
        int bestToken = validMoves.get(0);
        int bestScore = -1;

        for (int tokenIdx : validMoves) {

            int pos = positions[tokenIdx];
            int score = 0;

            // Priority 1: Taking tokens out of base
            if (pos >= 500 && diceValue == 6) {
                score = 500;
            }
            // Priority 2: Tokens close to finishing (Home Column)
            else if (pos >= 100 && pos < 405) {
                score = 100 + (pos % 100);
            }
            // Priority 3: Progress along the track (0-51)
            else if (pos >= 0 && pos < 52) {
                score = pos + 10;
            }

            if (score > bestScore) {
                bestScore = score;
                bestToken = tokenIdx;
            }
        }

        return bestToken;
    }
}