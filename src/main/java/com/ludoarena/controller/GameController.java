package com.ludoarena.controller;

import com.ludoarena.dto.*;
import com.ludoarena.model.GameScore;
import com.ludoarena.model.User;
import com.ludoarena.repository.GameScoreRepository;
import com.ludoarena.repository.UserRepository;
import com.ludoarena.service.GameService;
import com.ludoarena.service.AIPlayer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Game Controller - REST endpoints for game management.
 *
 * Endpoints:
 *   POST /api/games/start/{roomCode}  → Start game (admin only, protected)
 *   GET  /api/games/{id}              → Get game state (protected)
 */
@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;
    private final AIPlayer aiPlayer;
    private final GameScoreRepository gameScoreRepository;
    private final UserRepository userRepository;

    public GameController(GameService gameService, AIPlayer aiPlayer,
                          GameScoreRepository gameScoreRepository,
                          UserRepository userRepository) {
        this.gameService = gameService;
        this.aiPlayer = aiPlayer;
        this.gameScoreRepository = gameScoreRepository;
        this.userRepository = userRepository;
    }

    /**
     * POST /api/games/start/{roomCode}
     * Start the game (room admin only).
     *
     * Headers: Authorization: Bearer {token}
     * Response: Full game state with player positions
     */
    @PostMapping("/start/{roomCode}")
    public ResponseEntity<?> startGame(@AuthenticationPrincipal User user,
                                       @PathVariable String roomCode) {
        try {
            GameDTO game = gameService.startGame(roomCode, user.getId());
            
            // If first player is a bot, trigger its move
            if (game.getCurrentTurnUserId() != null) {
                game.getPlayers().stream()
                    .filter(p -> p.getUserId().equals(game.getCurrentTurnUserId()) && p.getIsBot())
                    .findFirst()
                    .ifPresent(bot -> aiPlayer.makeMove(game.getId(), bot.getUserId()));
            }

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Game started!", game));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * GET /api/games/{id}
     * Get current game state.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getGameState(@PathVariable Long id) {
        try {
            GameDTO game = gameService.getGameState(id);
            return ResponseEntity.ok(ApiResponse.success("Game state", game));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * GET /api/games/leaderboard
     * Get top 10 recent winners (ported from LudoMaster).
     */
    @GetMapping("/leaderboard")
    public ResponseEntity<?> getLeaderboard() {
        try {
            var scores = gameScoreRepository.findTop10ByIsWinTrueOrderByPlayedAtDesc();
            return ResponseEntity.ok(ApiResponse.success("Leaderboard", scores.stream().map(gs -> {
                var map = new java.util.LinkedHashMap<String, Object>();
                map.put("playerName", gs.getUser().getName());
                map.put("score", gs.getScore());
                map.put("color", gs.getColor() != null ? gs.getColor().name() : null);
                map.put("playedAt", gs.getPlayedAt());
                return map;
            }).toList()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * GET /api/games/scores/{userId}
     * Get match history for a specific user (ported from LudoMaster).
     */
    @GetMapping("/scores/{userId}")
    public ResponseEntity<?> getUserScores(@PathVariable Long userId) {
        try {
            var scores = gameScoreRepository.findByUserIdOrderByPlayedAtDesc(userId);
            return ResponseEntity.ok(ApiResponse.success("Match history", scores.stream().map(gs -> {
                var map = new java.util.LinkedHashMap<String, Object>();
                map.put("score", gs.getScore());
                map.put("isWin", gs.getIsWin());
                map.put("color", gs.getColor() != null ? gs.getColor().name() : null);
                map.put("playedAt", gs.getPlayedAt());
                return map;
            }).toList()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
