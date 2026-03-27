package com.ludoarena.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Move Entity - Records a single move made in a game.
 *
 * Captures the complete state of a move for:
 * - Game history replay
 * - Auditing
 * - Undo functionality (future feature)
 *
 * TABLE: moves
 *
 * THREAD SAFETY NOTE:
 * - Moves are created within the GameEngine's ReentrantLock, ensuring
 *   sequential ordering even when multiple WebSocket threads submit moves.
 * - The @OrderBy annotation on Game.moves ensures consistent ordering when loaded.
 */
@Entity
@Table(name = "moves")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Move {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The game this move belongs to */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    /** The user who made this move */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The dice value rolled (1-6) */
    @Column(nullable = false)
    private Integer diceValue;

    /** Which token was moved (0-3), null if no valid move was possible */
    @Column
    private Integer tokenIndex;

    /** Token's position before the move */
    @Column
    private Integer fromPosition;

    /** Token's position after the move */
    @Column
    private Integer toPosition;

    /** Whether this move resulted in killing an opponent's token */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isKill = false;

    /** Whether this move got the token to home */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isHome = false;

    /** Whether the player got an extra turn from this move */
    @Column(nullable = false)
    @Builder.Default
    private Boolean extraTurn = false;

    /** Move timestamp */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
