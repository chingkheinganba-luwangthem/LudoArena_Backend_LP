package com.ludoarena.model;

/**
 * Enum representing the state of a game.
 *
 * Lifecycle: WAITING → IN_PROGRESS → COMPLETED
 *                                  → ABANDONED (if players leave)
 */
public enum GameState {
    WAITING,        // Game created but not yet started
    IN_PROGRESS,    // Game is actively being played
    COMPLETED,      // Game finished with a winner
    ABANDONED       // Game was abandoned (all players left)
}
