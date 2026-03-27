package com.ludoarena.controller;

import com.ludoarena.dto.*;
import com.ludoarena.model.User;
import com.ludoarena.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Room Controller - REST endpoints for room management.
 *
 * THREAD NOTE:
 * - Each endpoint runs on a separate Tomcat thread.
 * - @AuthenticationPrincipal extracts User from ThreadLocal SecurityContext.
 * - RoomService handles thread-safe room joining via synchronized blocks.
 *
 * Endpoints:
 *   POST   /api/rooms/create         → Create new room (protected)
 *   POST   /api/rooms/join/{code}    → Join room by code (protected)
 *   GET    /api/rooms/{code}         → Get room details (protected)
 *   DELETE /api/rooms/{code}/leave   → Leave room (protected)
 *   GET    /api/rooms/available      → List available rooms (protected)
 */
@RestController
@RequestMapping("/api/rooms")
@CrossOrigin(origins = "http://localhost:3000")
public class RoomController {

    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;

    public RoomController(RoomService roomService, SimpMessagingTemplate messagingTemplate) {
        this.roomService = roomService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * POST /api/rooms/create
     * Create a new game room.
     *
     * Headers: Authorization: Bearer {token}
     * Body: { "color": "RED", "maxPlayers": 4, "coinAmount": 100 }
     * Response: { roomCode: "ABC123", players: [...] }
     */
    @PostMapping("/create")
    public ResponseEntity<?> createRoom(@AuthenticationPrincipal User user,
                                        @Valid @RequestBody CreateRoomRequest request) {
        try {
            RoomDTO room = roomService.createRoom(user.getId(), request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Room created!", room));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * POST /api/rooms/join/{code}
     * Join an existing room using room code.
     *
     * Headers: Authorization: Bearer {token}
     * Body: { "color": "BLUE" }
     */
    @PostMapping("/join/{code}")
    public ResponseEntity<?> joinRoom(@AuthenticationPrincipal User user,
                                      @PathVariable String code,
                                      @Valid @RequestBody JoinRoomRequest request) {
        try {
            RoomDTO room = roomService.joinRoom(user.getId(), code, request);
            return ResponseEntity.ok(ApiResponse.success("Joined room!", room));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * POST /api/rooms/{code}/add-bot
     * Add a computer player (admin only).
     */
    @PostMapping("/{code}/add-bot")
    public ResponseEntity<?> addBot(@AuthenticationPrincipal User user,
                                    @PathVariable String code) {
        try {
            RoomDTO room = roomService.addBot(user.getId(), code);
            // Broadcast room update to all players in the lobby
            messagingTemplate.convertAndSend("/topic/room/" + code, room);
            return ResponseEntity.ok(ApiResponse.success("Bot added!", room));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * GET /api/rooms/{code}
     * Get room details and player list.
     */
    @GetMapping("/{code}")
    public ResponseEntity<?> getRoom(@PathVariable String code) {
        try {
            RoomDTO room = roomService.getRoom(code);
            return ResponseEntity.ok(ApiResponse.success("Room details", room));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * DELETE /api/rooms/{code}/leave
     * Leave a room.
     */
    @DeleteMapping("/{code}/leave")
    public ResponseEntity<?> leaveRoom(@AuthenticationPrincipal User user,
                                       @PathVariable String code) {
        try {
            RoomDTO room = roomService.leaveRoom(user.getId(), code);
            return ResponseEntity.ok(ApiResponse.success("Left room", room));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * GET /api/rooms/available
     * List all rooms with WAITING status.
     */
    @GetMapping("/available")
    public ResponseEntity<?> getAvailableRooms() {
        List<RoomDTO> rooms = roomService.getAvailableRooms();
        return ResponseEntity.ok(ApiResponse.success("Available rooms", rooms));
    }
}
