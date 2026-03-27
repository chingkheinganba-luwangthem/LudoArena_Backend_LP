package com.ludoarena.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * User Entity - Represents a registered or guest user in LudoArena.
 *
 * This entity stores authentication details, profile info, and game stats.
 * Users can authenticate via Email/Password, Google OAuth, Facebook OAuth, or Guest mode.
 *
 * TABLE: users
 * INDEXES: email (unique), providerId
 *
 * THREAD SAFETY NOTE:
 * - User instances are managed by JPA/Hibernate within a transactional context.
 * - Each HTTP request thread gets its own EntityManager (thread-local),
 *   so concurrent requests modifying the same user are handled by DB-level
 *   optimistic/pessimistic locking when needed (e.g., coin updates).
 */
@Entity
@Table(name = "users", uniqueConstraints = {
    @UniqueConstraint(columnNames = "email")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Display name shown in game and profile */
    @Column(nullable = false, length = 100)
    private String name;

    /** Email address - unique for non-guest users, can be null for guests */
    @Column(length = 150)
    private String email;

    /** BCrypt hashed password - null for OAuth and guest users */
    @Column(length = 255)
    private String password;

    /** User's avatar URL or path */
    @Column(length = 500)
    private String avatarUrl;

    /** In-game currency - used for room entry fees */
    @Column(nullable = false)
    @Builder.Default
    private Long coins = 2000L;

    /** Total games played by this user */
    @Column(nullable = false)
    @Builder.Default
    private Integer gamesPlayed = 0;

    /** Total games won by this user */
    @Column(nullable = false)
    @Builder.Default
    private Integer gamesWon = 0;

    /** Authentication provider (LOCAL, GOOGLE, FACEBOOK, GUEST) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider authProvider;

    /** OAuth provider's user ID (for Google/Facebook logins) */
    @Column(length = 255)
    private String providerId;

    /** Account creation timestamp - auto-populated */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /** Last update timestamp - auto-updated */
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /** Whether this is a guest account with limited features */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isGuest = false;
}
