package com.ludoarena.websocket;

import com.ludoarena.dto.*;
import com.ludoarena.model.ChatMessage;
import com.ludoarena.model.Game;
import com.ludoarena.model.GameState;
import com.ludoarena.model.User;
import com.ludoarena.repository.ChatMessageRepository;
import com.ludoarena.repository.GameRepository;
import com.ludoarena.repository.UserRepository;
import com.ludoarena.service.GameEngine;
import com.ludoarena.service.GameService;
import com.ludoarena.service.AIPlayer;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * WebSocket Game Controller - Handles real-time game actions via STOMP.
 *
 * THREAD ARCHITECTURE:
 * - Messages arrive on Spring's clientInboundChannel thread pool.
 * - Multiple dice rolls/moves can arrive simultaneously for the same game.
 * - We acquire the game's ReentrantLock to serialize these operations.
 * - After processing, we broadcast via SimpMessagingTemplate (thread-safe).
 *
 * Message Flow:
 *   Client sends to /app/game/{id}/roll-dice
 *   → clientInboundChannel thread picks up the message
 *   → This handler acquires ReentrantLock for game {id}
 *   → GameEngine processes the dice roll + move (thread-safe)
 *   → SimpMessagingTemplate broadcasts to /topic/game/{id}
 *   → All subscribed clients receive the update
 *   → Lock is released
 */
@Controller
public class GameWebSocketController {

    private final GameEngine gameEngine;
    private final AIPlayer aiPlayer;
    private final GameService gameService;
    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public GameWebSocketController(GameEngine gameEngine,
                                    AIPlayer aiPlayer,
                                    GameService gameService,
                                    GameRepository gameRepository,
                                    UserRepository userRepository,
                                    ChatMessageRepository chatMessageRepository,
                                    SimpMessagingTemplate messagingTemplate) {
        this.gameEngine = gameEngine;
        this.aiPlayer = aiPlayer;
        this.gameService = gameService;
        this.gameRepository = gameRepository;
        this.userRepository = userRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Handle dice roll from a player.
     *
     * Client sends: { "userId": 1 }
     * Server broadcasts dice result + valid moves to all players.
     *
     * THREAD: Acquires game lock → rolls dice → finds valid moves → broadcasts → releases lock
     */
    @MessageMapping("/game/{gameId}/roll-dice")
    public void rollDice(@DestinationVariable Long gameId,
                         @Payload Map<String, Object> payload) {
        Long userId = Long.valueOf(payload.get("userId").toString());

        // THREAD: Acquire the ReentrantLock for this specific game
        // Only one dice roll or move can process at a time per game
        ReentrantLock lock = gameService.getGameLock(gameId);
        lock.lock();
        try {
            System.out.println("[WS] rollDice call. gameId: " + gameId + ", userId: " + userId);
            
            // Roll the dice
            int diceValue = gameEngine.rollDice(gameId, userId);

            // Get valid moves for this dice value
            List<Integer> validMoves = gameEngine.getValidMoves(gameId, userId, diceValue);

            // Broadcast dice result to all players
            Map<String, Object> diceResult = Map.of(
                    "type", "DICE_ROLL",
                    "userId", userId,
                    "diceValue", diceValue,
                    "validMoves", validMoves
            );
            messagingTemplate.convertAndSend("/topic/game/" + gameId, diceResult);

            // If no valid moves, auto-advance turn after a short delay for visibility
            if (validMoves.isEmpty()) {
                new Thread(() -> {
                    try {
                        Thread.sleep(1500); // 1.5 second delay
                        lock.lock(); 
                        try {
                            gameEngine.skipTurn(gameId);
                            Long botId = gameService.getNextTurnBotId(gameId);
                            if (botId != null) {
                                aiPlayer.makeMove(gameId, botId);
                            }
                        } finally {
                            lock.unlock();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            }
        } finally {
            lock.unlock(); // THREAD: Always release the lock
        }
    }

    /**
     * Handle token move from a player.
     *
     * Client sends: { "userId": 1, "diceValue": 5, "tokenIndex": 2 }
     * Server processes move, checks kills, updates state, broadcasts.
     *
     * THREAD: Acquires game lock → processes move → broadcasts → releases lock
     */
    @MessageMapping("/game/{gameId}/move")
    public void moveToken(@DestinationVariable Long gameId,
                          @Payload Map<String, Object> payload) {
        Long userId = Long.valueOf(payload.get("userId").toString());
        int diceValue = Integer.parseInt(payload.get("diceValue").toString());
        int tokenIndex = Integer.parseInt(payload.get("tokenIndex").toString());

        // THREAD: Acquire game lock for thread-safe state mutation
        ReentrantLock lock = gameService.getGameLock(gameId);
        lock.lock();
        try {
            System.out.println("[WS] moveToken call. gameId: " + gameId + ", userId: " + userId + ", tokenIndex: " + tokenIndex);
            
            // Process the move (includes kill check, home check, turn advance)
            MoveDTO moveResult = gameEngine.processMove(gameId, userId, diceValue, tokenIndex);

            // Broadcast move result
            messagingTemplate.convertAndSend("/topic/game/" + gameId + "/move", moveResult);

            // Broadcast updated game state
            gameService.broadcastGameStateById(gameId);
            
            Long botId = gameService.getNextTurnBotId(gameId);
            if (botId != null) {
                aiPlayer.makeMove(gameId, botId);
            }
        } catch (Exception e) {
            // Send error to the specific user
            messagingTemplate.convertAndSend("/topic/game/" + gameId + "/error",
                    Map.of("error", e.getMessage(), "userId", userId));
        } finally {
            lock.unlock(); // THREAD: Always release the lock
        }
    }

    // checkAndTriggerBotTurn moved to GameService for transactional safety

    @MessageMapping("/game/{gameId}/skip-turn")
    public void skipTurn(@DestinationVariable Long gameId,
                         @Payload Map<String, Object> payload) {
        Long userId = Long.valueOf(payload.get("userId").toString());
        
        ReentrantLock lock = gameService.getGameLock(gameId);
        lock.lock();
        try {
            System.out.println("[WS] skipTurn call. gameId: " + gameId + ", userId: " + userId);
            
            // Advance turn via gameEngine
            gameEngine.skipTurn(gameId);
            
            // Broadcast updated game state
            gameService.broadcastGameStateById(gameId);
            
            // Trigger bot turn if next player is a bot
            Long botId = gameService.getNextTurnBotId(gameId);
            if (botId != null) {
                aiPlayer.makeMove(gameId, botId);
            }
        } finally {
            lock.unlock();
        }
    }

    @MessageMapping("/game/{gameId}/end-game")
    public void endGame(@DestinationVariable Long gameId,
                        @Payload Map<String, Object> payload) {
        Long userId = Long.valueOf(payload.get("userId").toString());
        
        ReentrantLock lock = gameService.getGameLock(gameId);
        lock.lock();
        try {
            System.out.println("[WS] endGame call. gameId: " + gameId + ", userId: " + userId);
            
            // Force end the game
            gameEngine.endGame(gameId, userId);
            
            // Broadcast updated game state
            gameService.broadcastGameStateById(gameId);
        } catch (Exception e) {
            System.err.println("Error ending game: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Handle chat message from a player.
     *
     * Client sends: { "userId": 1, "message": "Hello!", "messageType": "TEXT" }
     * Server persists and broadcasts to all players.
     *
     * THREAD: Chat messages don't need game lock (independent of game state).
     * Database INSERT and broadcast are both thread-safe operations.
     */
    @MessageMapping("/game/{gameId}/chat")
    public void sendChatMessage(@DestinationVariable Long gameId,
                                @Payload Map<String, Object> payload) {
        try {
            Long userId = Long.valueOf(payload.get("userId").toString());
            String message = payload.get("message").toString();
            String messageType = payload.getOrDefault("messageType", "TEXT").toString();

            User user = userRepository.findById(userId).orElse(null);
            Game game = gameRepository.findById(gameId).orElse(null);

            if (user == null || game == null) {
                System.out.println("Chat error: User or Game not found. userId=" + userId + ", gameId=" + gameId);
                return;
            }

            // Persist chat message
            ChatMessage chatMsg = ChatMessage.builder()
                    .game(game)
                    .user(user)
                    .message(message)
                    .messageType(messageType)
                    .build();
            chatMessageRepository.save(chatMsg);

            // Broadcast to all players in the game
            ChatMessageDTO chatDTO = ChatMessageDTO.builder()
                    .userId(userId)
                    .userName(user.getName())
                    .avatarUrl(user.getAvatarUrl())
                    .message(message)
                    .messageType(messageType)
                    .timestamp(LocalDateTime.now())
                    .build();

            messagingTemplate.convertAndSend("/topic/game/" + gameId + "/chat", chatDTO);
            System.out.println("Chat sent for game " + gameId + " by user " + userId + ": " + message);
        } catch (Exception e) {
            System.err.println("Error in sendChatMessage: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
