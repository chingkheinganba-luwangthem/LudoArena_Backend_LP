package com.ludoarena.model;

/**
 * Enum representing the possible states of a game room.
 *
 * Lifecycle: WAITING → PLAYING → FINISHED
 *                              → CANCELLED (if admin cancels)
 */
public enum RoomStatus {
    WAITING,    // Room created, waiting for players to join
    PLAYING,    // Game has started
    FINISHED,   // Game completed normally
    CANCELLED   // Room was cancelled by admin
}
