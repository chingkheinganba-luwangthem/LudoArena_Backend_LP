package com.ludoarena.service;

import com.ludoarena.dto.GameDTO;
import com.ludoarena.dto.MoveDTO;
import com.ludoarena.model.*;
import com.ludoarena.repository.*;
import com.ludoarena.model.GameScore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Game Engine - Rebuilt with LudoMaster's Absolute Coordinate System.
 *
 * COORDINATE SYSTEM:
 * - Main Track: 0-51 (Shared)
 * - Home Columns (Entering from turning points):
 *     RED: 100-104 (Turning point 50 -> 100)
 *     YELLOW: 200-204 (Turning point 24 -> 200)
 *     GREEN: 300-304 (Turning point 11 -> 300)
 *     BLUE: 400-404 (Turning point 37 -> 400)
 * - Home (Finished): 105, 205, 305, 405
 * - Base (Starting): 500-503 (RED), 600-603 (YELLOW), 700-703 (GREEN), 800-803 (BLUE)
 *
 * START POSITIONS:
 *   RED    = 0
 *   GREEN  = 13
 *   YELLOW = 26
 *   BLUE   = 39
 */
@Service
public class GameEngine {

    private final GameRepository gameRepository;
    private final PlayerGameRepository playerGameRepository;
    private final MoveRepository moveRepository;
    private final GameScoreRepository gameScoreRepository;
    private final GameService gameService;

    private static final int TRACK_SIZE = 52;
    private static final SecureRandom RANDOM = new SecureRandom();

    public GameEngine(GameRepository gameRepository,
                      PlayerGameRepository playerGameRepository,
                      MoveRepository moveRepository,
                      GameScoreRepository gameScoreRepository,
                      GameService gameService) {
        this.gameRepository = gameRepository;
        this.playerGameRepository = playerGameRepository;
        this.moveRepository = moveRepository;
        this.gameScoreRepository = gameScoreRepository;
        this.gameService = gameService;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        gameService.setTimeoutCallback(this::skipTurn);
    }

    @Transactional
    public int rollDice(Long gameId, Long userId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found with ID: " + gameId));

        PlayerGame currentPlayer = getCurrentTurnPlayer(game);
        if (!currentPlayer.getUser().getId().equals(userId)) {
            throw new RuntimeException("Not your turn!");
        }

        // Reset watchdog on dice roll
        gameService.resetTurnTimeout(gameId);

        return RANDOM.nextInt(6) + 1;
    }

    @Transactional
    public MoveDTO processMove(Long gameId, Long userId, int diceValue, int tokenIndex) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found with ID: " + gameId));

        if (game.getState() != GameState.IN_PROGRESS) {
            throw new RuntimeException("Game is not in progress");
        }

        PlayerGame currentPlayer = getCurrentTurnPlayer(game);
        if (!currentPlayer.getUser().getId().equals(userId)) {
            throw new RuntimeException("Not your turn!");
        }

        int[] positions = currentPlayer.getTokenPositionArray();
        int fromPos = positions[tokenIndex];

        if (!isValidMove(game, currentPlayer, tokenIndex, diceValue)) {
            throw new RuntimeException("Invalid move for this token");
        }

        // Calculate new position using Master logic
        int toPos = calculateNewPosition(currentPlayer, fromPos, diceValue);

        // Check for kill (only on track 0-51)
        boolean isKill = false;
        String killedPlayerName = null;
        if (toPos >= 0 && toPos < TRACK_SIZE && !isSafePosition(toPos)) {
            for (PlayerGame opponent : game.getPlayers()) {
                if (opponent.getId().equals(currentPlayer.getId())) continue;

                int[] oppPositions = opponent.getTokenPositionArray();
                for (int i = 0; i < 4; i++) {
                    if (oppPositions[i] == toPos) {
                        // Special check: captures only allowed if it's NOT a block (2+ tokens)
                        // LudoMaster doesn't have blocks, but Arena does. Let's keep Arena's block rule.
                        if (hasBlockAt(game, toPos, opponent.getColor())) {
                            continue;
                        }

                        // KILL!
                        oppPositions[i] = getBasePosition(opponent.getColor(), i);
                        opponent.setTokenPositionArray(oppPositions);
                        playerGameRepository.save(opponent);
                        isKill = true;
                        killedPlayerName = opponent.getUser().getName();
                    }
                }
            }
        }

        // Update token position
        positions[tokenIndex] = toPos;
        currentPlayer.setTokenPositionArray(positions);

        // Check win condition
        boolean isHome = isHome(toPos);
        boolean allHome = true;
        for (int pos : positions) {
            if (!isHome(pos)) {
                allHome = false;
                break;
            }
        }

        if (allHome) {
            currentPlayer.setFinished(true);
            game.setWinnerId(userId);
            game.setState(GameState.COMPLETED);
            
            long prizePool = game.getRoom().getCoinAmount() * game.getPlayers().size();
            if (prizePool > 0) {
                User user = currentPlayer.getUser();
                user.setCoins(user.getCoins() + prizePool);
            }

            // Record scores for all players (ported from LudoMaster)
            recordGameScores(game, userId);
            
            // Check if game should end immediately (e.g. 2 player game, or only 1 left)
            long activePlayers = game.getPlayers().stream().filter(p -> !p.getFinished()).count();
            if (activePlayers <= 1) {
                game.setState(GameState.COMPLETED);
            }
        }

        playerGameRepository.save(currentPlayer);

        // Record the move
        Move move = Move.builder()
                .game(game)
                .user(currentPlayer.getUser())
                .diceValue(diceValue)
                .tokenIndex(tokenIndex)
                .fromPosition(fromPos)
                .toPosition(toPos)
                .isKill(isKill)
                .isHome(isHome)
                .extraTurn(diceValue == 6 || isKill)
                .build();
        moveRepository.save(move);

        boolean extraTurn = false;
        if (diceValue == 6) {
            game.setConsecutiveSixes(game.getConsecutiveSixes() + 1);
            if (game.getConsecutiveSixes() >= 3) {
                game.setConsecutiveSixes(0);
                advanceTurn(game);
            } else {
                extraTurn = true;
            }
        } else {
            game.setConsecutiveSixes(0);
            if (isKill) {
                extraTurn = true;
            } else {
                advanceTurn(game);
            }
        }

        gameRepository.save(game);
        
        // Reset watchdog on turn advance
        gameService.resetTurnTimeout(gameId);

        return MoveDTO.builder()
                .userId(userId)
                .playerName(currentPlayer.getUser().getName())
                .diceValue(diceValue)
                .tokenIndex(tokenIndex)
                .fromPosition(fromPos)
                .toPosition(toPos)
                .isKill(isKill)
                .isHome(isHome)
                .extraTurn(extraTurn)
                .killedPlayerName(killedPlayerName)
                .build();
    }

    @Transactional(readOnly = true)
    public List<Integer> getValidMoves(Long gameId, Long userId, int diceValue) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        PlayerGame player = game.getPlayers().stream()
                .filter(p -> p.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Player not in game"));

        List<Integer> validTokens = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (isValidMove(game, player, i, diceValue)) {
                validTokens.add(i);
            }
        }
        return validTokens;
    }

    private void advanceTurn(Game game) {
        int numPlayers = game.getPlayers().size();
        int nextTurn = (game.getCurrentTurnIndex() + 1) % numPlayers;

        // 2-Player Shortcut: If only 1 remains, game ends
        long activeCount = game.getPlayers().stream().filter(p -> !p.getFinished()).count();
        if (activeCount <= 1) {
            game.setState(GameState.COMPLETED);
            return;
        }

        int attempts = 0;
        while (attempts < numPlayers) {
            final int currentCheckTurn = nextTurn;
            PlayerGame nextPlayer = game.getPlayers().stream()
                    .filter(p -> p.getTurnOrder().equals(currentCheckTurn))
                    .findFirst().orElse(null);

            if (nextPlayer != null && !nextPlayer.getFinished()) {
                break;
            }
            nextTurn = (nextTurn + 1) % numPlayers;
            attempts++;
        }
        game.setCurrentTurnIndex(nextTurn);
    }

    private boolean isValidMove(Game game, PlayerGame player, int tokenIdx, int dice) {
        int pos = player.getTokenPositionArray()[tokenIdx];
        
        // Base: Needs 6
        if (isBase(pos)) return dice == 6;

        // Already Home
        if (isHome(pos)) return false;

        // Calculate steps needed for home
        int homeStart = getHomeEntranceStart(player.getColor());
        int homeEnd = getHomePosition(player.getColor());

        // In Home Column: Need exact count
        if (pos >= homeStart && pos < homeEnd) {
            return pos + dice <= homeEnd;
        }

        // On Track: Check path for blocks and overshoot
        int current = pos;
        int turningPoint = getTurningPoint(player.getColor());
        
        for (int i = 1; i <= dice; i++) {
            if (current == turningPoint) {
                current = homeStart;
            } else if (current == 51) {
                current = 0;
            } else if (current >= homeStart) {
                current++;
            } else {
                current++;
            }
            
            // No Overshoot
            if (current > homeEnd) return false;

            // Note: LudoMaster doesn't have blocks, removing block check to match exactly
        }

        return true;
    }

    private int calculateNewPosition(PlayerGame player, int currentPos, int dice) {
        if (isBase(currentPos)) return getStartPoint(player.getColor());

        int current = currentPos;
        int turningPoint = getTurningPoint(player.getColor());
        int homeStart = getHomeEntranceStart(player.getColor());

        for (int i = 0; i < dice; i++) {
            if (current == turningPoint) current = homeStart;
            else if (current == 51) current = 0;
            else current++;
        }
        return current;
    }

    // --- LOGIC CONSTANTS BASED ON LUDO MASTER ---

    private int getStartPoint(PlayerColor color) {
        return switch (color) {
            case RED -> 39;
            case GREEN -> 26;
            case YELLOW -> 13;
            case BLUE -> 0;
        };
    }

    private int getTurningPoint(PlayerColor color) {
        return switch (color) {
            case RED -> 37;
            case GREEN -> 24;
            case YELLOW -> 11;
            case BLUE -> 50;
        };
    }

    private int getHomeEntranceStart(PlayerColor color) {
        return switch (color) {
            case RED -> 400;
            case YELLOW -> 300;
            case GREEN -> 200;
            case BLUE -> 100;
        };
    }

    private int getHomePosition(PlayerColor color) {
        return switch (color) {
            case RED -> 405;
            case YELLOW -> 305;
            case GREEN -> 205;
            case BLUE -> 105;
        };
    }

    private int getBasePosition(PlayerColor color, int tokenIdx) {
        int offset = switch (color) {
            case BLUE -> 500;
            case YELLOW -> 600;
            case RED -> 700;
            case GREEN -> 800;
        };
        return offset + tokenIdx;
    }

    private boolean isBase(int pos) { return pos >= 500 || pos == -1; }
    private boolean isHome(int pos) { return pos >= 105 && pos <= 405 && pos % 100 == 5; }
    private boolean isSafePosition(int pos) {
        for (int safe : new int[]{0, 8, 13, 21, 26, 34, 39, 47}) {
            if (pos == safe) return true;
        }
        return false;
    }

    private boolean hasAnyOpponentBlockAt(Game game, int pos, Long currentPlayerId) {
        for (PlayerGame p : game.getPlayers()) {
            if (p.getId().equals(currentPlayerId)) continue;
            if (hasBlockAt(game, pos, p.getColor())) return true;
        }
        return false;
    }

    private boolean hasBlockAt(Game game, int pos, PlayerColor color) {
        for (PlayerGame p : game.getPlayers()) {
            if (p.getColor() == color) {
                int count = 0;
                for (int tokenPos : p.getTokenPositionArray()) {
                    if (tokenPos == pos) count++;
                }
                return count >= 2;
            }
        }
        return false;
    }

    private PlayerGame getCurrentTurnPlayer(Game game) {
        return game.getPlayers().stream()
                .filter(p -> p.getTurnOrder().equals(game.getCurrentTurnIndex()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Current turn player not found"));
    }

    @Transactional
    public void skipTurn(Long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));
        game.setConsecutiveSixes(0);
        advanceTurn(game);
        gameRepository.save(game);
        
        // Reset watchdog on skip
        gameService.resetTurnTimeout(gameId);
        
        gameService.broadcastGameState(game);
    }

    /**
     * Record game scores for all players when a game completes.
     * Winner gets 100 points, others get 10 per token that reached home.
     * Also updates User gamesPlayed/gamesWon counters.
     */
    private void recordGameScores(Game game, Long winnerId) {
        for (PlayerGame pg : game.getPlayers()) {
            User u = pg.getUser();
            boolean isWinner = u.getId().equals(winnerId);

            // Calculate score: 100 for win, 10 per token home otherwise
            int score = 0;
            if (isWinner) {
                score = 100;
            } else {
                int homePos = getHomePosition(pg.getColor());
                for (int pos : pg.getTokenPositionArray()) {
                    if (pos == homePos) score += 10;
                }
            }

            GameScore gs = GameScore.builder()
                    .user(u)
                    .game(game)
                    .score(score)
                    .isWin(isWinner)
                    .color(pg.getColor())
                    .build();
            gameScoreRepository.save(gs);

            // Update user stats (ported from LudoMaster UserService)
            u.setGamesPlayed(u.getGamesPlayed() + 1);
            if (isWinner) {
                u.setGamesWon(u.getGamesWon() + 1);
            }
        }
    }
    
    @Transactional
    public void endGame(Long gameId, Long requestingUserId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));
                
        if (game.getState() == GameState.COMPLETED) {
            return;
        }
        
        // Find the user who requested
        PlayerGame requestingPlayer = game.getPlayers().stream()
                .filter(p -> p.getUser().getId().equals(requestingUserId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Player not in game"));
                
        System.out.println("Game " + gameId + " forcefully ended by user " + requestingPlayer.getUser().getName());
                
        game.setState(GameState.COMPLETED);
        gameRepository.save(game);
    }
}
