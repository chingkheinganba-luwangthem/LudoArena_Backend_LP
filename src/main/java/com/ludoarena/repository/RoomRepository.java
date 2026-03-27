package com.ludoarena.repository;

import com.ludoarena.model.Room;
import com.ludoarena.model.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Room Repository - Data access layer for Room entity.
 */
@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    /** Find room by its unique code (for joining) */
    Optional<Room> findByRoomCode(String roomCode);

    /** Find all rooms with a specific status */
    List<Room> findByStatus(RoomStatus status);

    /** Find rooms created by a specific user */
    List<Room> findByAdminId(Long adminId);

    /** Check if a room code already exists */
    Boolean existsByRoomCode(String roomCode);
}
