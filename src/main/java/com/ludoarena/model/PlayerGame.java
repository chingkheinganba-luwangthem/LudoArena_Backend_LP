package com.ludoarena.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * PlayerGame Entity - Junction table linking a User to a Game with game-specific data.
 *
 * Stores:
 * - Which color the player chose (RED, BLUE, GREEN, YELLOW)
 * - Position of all 4 tokens on the board
 * - Turn order in the game
 * - Whether the player has finished (all tokens home)
 *
 * Token Position Encoding:
 *   -1       = Token is in BASE (not yet entered the board)
 *    0 - 51  = Position on the main track (52 cells total)
 *   52 - 57  = Position on the HOME COLUMN (6 cells before home)
 *   58       = Token has reached HOME (finished)
 *
 * TABLE: player_games
 *
 * THREAD SAFETY NOTE:
 * - Token positions are updated within the GameEngine's ReentrantLock.
 * - Each game has its own lock, so moves in different games don't block each other.
 * - The token positions are stored as comma-separated string for simplicity;
 *   parsed into int[] in the service layer.
 */
@Entity
@Table(name = "player_games")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PlayerGame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The user playing in this game */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The game this player is part of (nullable - set when game starts, not when room is created) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = true)
    private Game game;

    /** The room this player joined */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    /** Player's chosen color */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PlayerColor color;

    /**
     * Positions of the player's 4 tokens, stored as comma-separated values.
     * Example: "-1,-1,-1,-1" means all tokens are in base.
     * Example: "5,12,-1,58" means token 0 at pos 5, token 1 at pos 12,
     *          token 2 in base, token 3 has reached home.
     *
     * THREAD: Updated only within GameEngine's ReentrantLock per game.
     */
    @Column(nullable = false, length = 50)
    @Builder.Default
    private String tokenPositions = "-1,-1,-1,-1";

    /** Player's turn order (0 = first, 1 = second, etc.) */
    @Column(nullable = false)
    @Builder.Default
    private Integer turnOrder = 0;

    /** Whether this player has finished (all 4 tokens reached home) */
    @Column(nullable = false)
    @Builder.Default
    private Boolean finished = false;

    /** Whether this player is an AI/computer player */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isBot = false;

    /** Timestamp when this player joined */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime joinedAt;

    // ========== HELPER METHODS ==========

    /**
     * Parse token positions string into int array.
     * @return int[4] with each token's position
     */
    public int[] getTokenPositionArray() {
        String[] parts = tokenPositions.split(",");
        int[] positions = new int[4];
        for (int i = 0; i < 4; i++) {
            positions[i] = Integer.parseInt(parts[i].trim());
        }
        return positions;
    }

    /**
     * Update token positions from int array.
     * @param positions int[4] with updated positions
     */
    public void setTokenPositionArray(int[] positions) {
        this.tokenPositions = positions[0] + "," + positions[1] + "," + positions[2] + "," + positions[3];
    }
}
