package com.ludoarena.service;

import com.ludoarena.dto.GameDTO;
import com.ludoarena.model.*;
import com.ludoarena.repository.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Game Service - Business logic for game management and state transitions.
 *
 * THREAD SAFETY:
 * - Uses a ConcurrentHashMap of ReentrantLocks (one lock per game).
 * - When a dice roll or move comes in via WebSocket, the game's lock is acquired
 *   to ensure only one mutation happens at a time.
 * - Different games are completely independent (no cross-game locking).
 * - SimpMessagingTemplate.convertAndSend() is thread-safe for broadcasting.
 *
 * LOCK ARCHITECTURE:
 * ┌──────────────────────────────────────────────┐
 * │  ConcurrentHashMap<Long, ReentrantLock>       │
 * │  gameLocks                                    │
 * │  ├─ Game 1 → ReentrantLock (fair=true)       │
 * │  ├─ Game 2 → ReentrantLock (fair=true)       │
 * │  └─ Game N → ReentrantLock (fair=true)       │
 * └──────────────────────────────────────────────┘
 * Fair locks ensure FIFO ordering of concurrent move requests.
 */
@Service
public class GameService {

    private final GameRepository gameRepository;
    private final RoomRepository roomRepository;
    private final PlayerGameRepository playerGameRepository;
    private final MoveRepository moveRepository;
    private final SimpMessagingTemplate messagingTemplate;
    // We must use lazy injection for AIPlayer to avoid circular dependency
    // GameService -> AIPlayer -> GameService
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private AIPlayer aiPlayer;

    /**
     * THREAD: ConcurrentHashMap stores one ReentrantLock per active game.
     * - ConcurrentHashMap is thread-safe for concurrent put/get operations.
     * - Each ReentrantLock serializes moves within a single game.
     * - computeIfAbsent() is atomic, preventing duplicate lock creation.
     */
    private final ConcurrentHashMap<Long, ReentrantLock> gameLocks = new ConcurrentHashMap<>();
    private final java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newScheduledThreadPool(2);
    private final ConcurrentHashMap<Long, java.util.concurrent.ScheduledFuture<?>> turnTimeouts = new ConcurrentHashMap<>();
    private java.util.function.Consumer<Long> timeoutCallback;

    public GameService(GameRepository gameRepository,
                       RoomRepository roomRepository,
                       PlayerGameRepository playerGameRepository,
                       MoveRepository moveRepository,
                       SimpMessagingTemplate messagingTemplate) {
        this.gameRepository = gameRepository;
        this.roomRepository = roomRepository;
        this.playerGameRepository = playerGameRepository;
        this.moveRepository = moveRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public void setTimeoutCallback(java.util.function.Consumer<Long> callback) {
        this.timeoutCallback = callback;
    }

    @jakarta.annotation.PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    /**
     * Resets the turn timeout for a specific game.
     * If the timeout expires, skipTurn is automatically called server-side.
     */
    public void resetTurnTimeout(Long gameId) {
        // Cancel existing timeout
        java.util.concurrent.ScheduledFuture<?> existing = turnTimeouts.remove(gameId);
        if (existing != null) existing.cancel(false);

        // Schedule new timeout (45 seconds allows for latency + 30s UI timer)
        java.util.concurrent.ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                if (timeoutCallback != null) {
                    System.out.println("[TIMEOUT-WATCHDOG] Auto-skipping turn for game " + gameId);
                    timeoutCallback.accept(gameId);
                }
            } catch (Exception e) {
                System.err.println("Error in timeout watchdog: " + e.getMessage());
            }
        }, 45, java.util.concurrent.TimeUnit.SECONDS);
        
        turnTimeouts.put(gameId, future);
    }

    /**
     * Start a game from a room. Only the room admin can start.
     *
     * @param roomCode the room to start
     * @param userId the user requesting start (must be admin)
     * @return GameDTO with initial game state
     */
    @Transactional
    public GameDTO startGame(String roomCode, Long userId) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        // Only admin can start
        if (!room.getAdmin().getId().equals(userId)) {
            throw new RuntimeException("Only the room admin can start the game");
        }

        // Need at least 2 players
        if (room.getPlayers().size() < 2) {
            throw new RuntimeException("Need at least 2 players to start");
        }

        // Check room status
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new RuntimeException("Game has already started or room is closed");
        }

        // Create game
        Game game = Game.builder()
                .room(room)
                .state(GameState.IN_PROGRESS)
                .currentTurnIndex(0)
                .consecutiveSixes(0)
                .build();

        game = gameRepository.saveAndFlush(game);
        room.setGame(game);
        roomRepository.save(room);

        // Assign game reference to players and set turn order
        List<PlayerGame> players = room.getPlayers();
        for (int i = 0; i < players.size(); i++) {
            PlayerGame pg = players.get(i);
            pg.setGame(game);
            pg.setTurnOrder(i);
            int baseOffset = switch (pg.getColor()) {
                case BLUE -> 500;
                case YELLOW -> 600;
                case RED -> 700;
                case GREEN -> 800;
            };
            pg.setTokenPositions(baseOffset + "," + (baseOffset + 1) + "," + (baseOffset + 2) + "," + (baseOffset + 3));
            playerGameRepository.save(pg);
        }
        game.getPlayers().addAll(players);

        // Update room status
        room.setStatus(RoomStatus.PLAYING);
        roomRepository.save(room);

        // Create lock for this game
        // THREAD: computeIfAbsent is atomic on ConcurrentHashMap
        gameLocks.computeIfAbsent(game.getId(), k -> new ReentrantLock(true));

        // Start server-side turn watchdog
        resetTurnTimeout(game.getId());

        GameDTO gameDTO = toGameDTO(game);
        final String fRoomCode = roomCode;
        final GameDTO fGameDTO = gameDTO;
        final Long fGameId = game.getId();

        // Broadcast game start to all players via WebSocket ONLY after commit
        // This prevents the race condition where rollDice starts before game is in DB
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                messagingTemplate.convertAndSend("/topic/room/" + fRoomCode, fGameDTO);
                
                // If the first player is a bot, trigger their turn
                Long initialBotId = getNextTurnBotId(fGameId);
                if (initialBotId != null && aiPlayer != null) {
                    // Delay slightly to allow frontend to set up
                    scheduler.schedule(() -> {
                        aiPlayer.makeMove(fGameId, initialBotId);
                    }, 1500, java.util.concurrent.TimeUnit.MILLISECONDS);
                }
            }
        });

        return gameDTO;
    }

    /**
     * Get current game state.
     */
    @Transactional(readOnly = true)
    public GameDTO getGameState(Long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found with ID: " + gameId));
        return toGameDTO(game);
    }

    /**
     * Get the ReentrantLock for a specific game.
     * THREAD: Used by GameEngine to serialize dice rolls and moves.
     */
    public ReentrantLock getGameLock(Long gameId) {
        return gameLocks.computeIfAbsent(gameId, k -> new ReentrantLock(true));
    }

    /**
     * Broadcast updated game state to all players.
     * THREAD: Can be called from any thread safely.
     */
    public void broadcastGameState(Game game) {
        GameDTO gameDTO = toGameDTO(game);
        messagingTemplate.convertAndSend("/topic/game/" + game.getId(), gameDTO);
    }

    @Transactional(readOnly = true)
    public void broadcastGameStateById(Long gameId) {
        Game game = gameRepository.findById(gameId).orElse(null);
        if (game != null) {
            broadcastGameState(game);
        }
    }

    @Transactional(readOnly = true)
    public Long getNextTurnBotId(Long gameId) {
        Game game = gameRepository.findById(gameId).orElse(null);
        if (game != null && game.getState() == GameState.IN_PROGRESS) {
            int currentTurn = game.getCurrentTurnIndex();
            PlayerGame nextPlayer = game.getPlayers().stream()
                    .filter(p -> p.getTurnOrder().equals(currentTurn))
                    .findFirst().orElse(null);
            if (nextPlayer != null && nextPlayer.getIsBot()) {
                return nextPlayer.getUser().getId();
            }
        }
        return null;
    }

    // ========== HELPER METHODS ==========

    /**
     * Convert Game entity to GameDTO with all player positions.
     */
    public GameDTO toGameDTO(Game game) {
        List<GameDTO.GamePlayerDTO> players = game.getPlayers().stream()
                .map(pg -> GameDTO.GamePlayerDTO.builder()
                        .userId(pg.getUser().getId())
                        .name(pg.getUser().getName())
                        .avatarUrl(pg.getUser().getAvatarUrl())
                        .color(pg.getColor().name())
                        .tokenPositions(pg.getTokenPositionArray())
                        .turnOrder(pg.getTurnOrder())
                        .finished(pg.getFinished())
                        .isBot(pg.getIsBot())
                        .build())
                .collect(Collectors.toList());

        // Get current turn player info
        PlayerGame currentPlayer = game.getPlayers().stream()
                .filter(p -> p.getTurnOrder().equals(game.getCurrentTurnIndex()))
                .findFirst().orElse(null);

        // Get winner info
        String winnerName = null;
        if (game.getWinnerId() != null) {
            winnerName = game.getPlayers().stream()
                    .filter(p -> p.getUser().getId().equals(game.getWinnerId()))
                    .map(p -> p.getUser().getName())
                    .findFirst().orElse(null);
        }

        return GameDTO.builder()
                .id(game.getId())
                .state(game.getState().name())
                .currentTurnIndex(game.getCurrentTurnIndex())
                .currentTurnUserId(currentPlayer != null ? currentPlayer.getUser().getId() : null)
                .currentTurnPlayerName(currentPlayer != null ? currentPlayer.getUser().getName() : null)
                .winnerId(game.getWinnerId())
                .winnerName(winnerName)
                .consecutiveSixes(game.getConsecutiveSixes())
                .roomCode(game.getRoom() != null ? game.getRoom().getRoomCode() : null)
                .entryFee(game.getRoom() != null ? game.getRoom().getCoinAmount() : null)
                .players(players)
                .build();
    }
}
