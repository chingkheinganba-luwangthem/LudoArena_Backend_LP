package com.ludoarena.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Room Entity - Represents a game room/lobby where players gather before a game starts.
 *
 * Lifecycle: WAITING → PLAYING → FINISHED/CANCELLED
 *
 * Features:
 * - Room code for easy sharing (6-char alphanumeric)
 * - Configurable max players (2-4)
 * - Coin entry fee (optional)
 * - Admin controls (only room creator can start the game)
 *
 * TABLE: rooms
 * INDEXES: roomCode (unique)
 *
 * THREAD SAFETY NOTE:
 * - Room join/leave operations use synchronized blocks in the RoomService
 *   to prevent race conditions when multiple players try to join simultaneously.
 * - The @Version field provides optimistic locking at the DB level, so concurrent
 *   updates to the same room will throw OptimisticLockException, which the service
 *   layer can retry.
 */
@Entity
@Table(name = "rooms", uniqueConstraints = {
    @UniqueConstraint(columnNames = "roomCode")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique 6-character room code for players to join.
     * Generated using SecureRandom for unpredictability.
     */
    @Column(nullable = false, unique = true, length = 10)
    private String roomCode;

    /** Maximum number of players allowed (2, 3, or 4) */
    @Column(nullable = false)
    @Builder.Default
    private Integer maxPlayers = 4;

    /** Coin entry fee for the room (0 = free game) */
    @Column(nullable = false)
    @Builder.Default
    private Long coinAmount = 0L;

    /** Current room status */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RoomStatus status = RoomStatus.WAITING;

    /**
     * Room admin (creator) - only this user can start the game.
     * ManyToOne because a user can create multiple rooms over time.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "admin_id", nullable = false)
    private User admin;

    /**
     * Players currently in room.
     * CascadeType.ALL ensures PlayerGame entries are managed with Room lifecycle.
     * orphanRemoval removes PlayerGame when a player leaves.
     */
    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PlayerGame> players = new ArrayList<>();

    /** The game associated with this room (created when game starts) */
    @OneToOne(mappedBy = "room", cascade = CascadeType.ALL)
    private Game game;

    /** Room creation timestamp */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /**
     * Optimistic locking version field.
     * THREAD: Hibernate uses this to detect concurrent modifications.
     * If two threads try to update the same room simultaneously,
     * the second one gets an OptimisticLockException.
     */
    @Version
    private Integer version;
}
