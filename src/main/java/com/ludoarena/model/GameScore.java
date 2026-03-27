package com.ludoarena.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * GameScore Entity - Records individual game results for leaderboard & history.
 * Ported from LudoMaster and adapted for LudoArena's User model.
 *
 * TABLE: game_scores
 */
@Entity
@Table(name = "game_scores")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class GameScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The user who played */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The game this score belongs to */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id")
    private Game game;

    /** Score earned in this game */
    @Column(nullable = false)
    @Builder.Default
    private Integer score = 0;

    /** Whether the player won this game */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isWin = false;

    /** Player's color in this game */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private PlayerColor color;

    /** Timestamp when the game was played */
    private LocalDateTime playedAt;

    @PrePersist
    protected void onCreate() {
        playedAt = LocalDateTime.now();
    }
}
