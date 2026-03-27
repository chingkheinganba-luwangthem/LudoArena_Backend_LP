package com.ludoarena.service;

import com.ludoarena.dto.*;
import com.ludoarena.model.*;
import com.ludoarena.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * Room Service - Business logic for room management (create, join, leave, get).
 *
 * THREAD SAFETY:
 * - Stateless singleton service - no mutable instance fields.
 * - Room join operations use @Transactional with pessimistic considerations.
 * - The synchronized block in joinRoom() prevents race conditions when
 *   multiple players try to join the same room simultaneously.
 * - SecureRandom is thread-safe (used for room code generation).
 *
 * CONCURRENT SCENARIOS HANDLED:
 * 1. Two players joining same room simultaneously → synchronized on room code
 * 2. Player joining a full room → checked inside synchronized block
 * 3. Duplicate color selection → validated inside synchronized block
 */
@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final PlayerGameRepository playerGameRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // THREAD: SecureRandom is thread-safe, used for generating unique room codes
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    public RoomService(RoomRepository roomRepository,
                       PlayerGameRepository playerGameRepository,
                       UserRepository userRepository,
                       SimpMessagingTemplate messagingTemplate) {
        this.roomRepository = roomRepository;
        this.playerGameRepository = playerGameRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Create a new game room.
     *
     * @param userId the creating user's ID (becomes admin)
     * @param request room configuration (color, maxPlayers, coinAmount)
     * @return RoomDTO with room details and code
     */
    @Transactional
    public RoomDTO createRoom(Long userId, CreateRoomRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if user has enough coins
        long entryFee = request.getCoinAmount() != null ? request.getCoinAmount().longValue() : 0L;
        if (entryFee > 0 && user.getCoins() < entryFee) {
            throw new RuntimeException("Not enough coins. You have " + user.getCoins());
        }

        // Deduct entry fee
        if (entryFee > 0) {
            user.setCoins(user.getCoins() - entryFee);
            userRepository.save(user);
        }

        // Generate unique 6-character room code
        // THREAD: SecureRandom.nextInt() is thread-safe
        String roomCode = generateRoomCode();

        // Create room
        Room room = Room.builder()
                .roomCode(roomCode)
                .maxPlayers(request.getMaxPlayers())
                .coinAmount(request.getCoinAmount().longValue())
                .status(RoomStatus.WAITING)
                .admin(user)
                .build();

        room = roomRepository.save(room);

        // Add creator as first player with chosen color
        PlayerColor color = PlayerColor.valueOf(request.getColor().toUpperCase());
        PlayerGame playerGame = PlayerGame.builder()
                .user(user)
                .room(room)
                .color(color)
                .turnOrder(0)
                .isBot(false)
                .build();

        playerGameRepository.save(playerGame);
        room.getPlayers().add(playerGame);

        return toRoomDTO(room);
    }

    /**
     * Join an existing room by room code.
     *
     * THREAD: synchronized on the interned room code string to prevent
     * race conditions when multiple players join simultaneously.
     * String.intern() ensures the same monitor is used for the same code.
     */
    @Transactional
    public RoomDTO joinRoom(Long userId, String roomCode, JoinRoomRequest request) {
        // THREAD: Synchronize on the room code to prevent concurrent join issues
        synchronized (roomCode.intern()) {
            Room room = roomRepository.findByRoomCode(roomCode)
                    .orElseThrow(() -> new RuntimeException("Room not found with code: " + roomCode));

            // Validate room state
            if (room.getStatus() != RoomStatus.WAITING) {
                throw new RuntimeException("Room is no longer accepting players");
            }

            // Check if room is full
            if (room.getPlayers().size() >= room.getMaxPlayers()) {
                throw new RuntimeException("Room is full");
            }

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Check if user is already in this room
            if (playerGameRepository.existsByRoomIdAndUserId(room.getId(), userId)) {
                throw new RuntimeException("You are already in this room");
            }

            // Check if chosen color is taken
            PlayerColor color = PlayerColor.valueOf(request.getColor().toUpperCase());
            boolean colorTaken = room.getPlayers().stream()
                    .anyMatch(p -> p.getColor() == color);
            if (colorTaken) {
                throw new RuntimeException("Color " + color + " is already taken");
            }

            // Check coins
            long entryFee = room.getCoinAmount() != null ? room.getCoinAmount().longValue() : 0L;
            if (entryFee > 0 && user.getCoins() < entryFee) {
                throw new RuntimeException("Not enough coins");
            }

            // Deduct entry fee
            if (entryFee > 0) {
                user.setCoins(user.getCoins() - entryFee);
                userRepository.save(user);
            }

            // Add player to room
            PlayerGame playerGame = PlayerGame.builder()
                    .user(user)
                    .room(room)
                    .color(color)
                    .turnOrder(room.getPlayers().size())
                    .isBot(false)
                    .build();

            playerGameRepository.save(playerGame);
            room.getPlayers().add(playerGame);

            RoomDTO roomDTO = toRoomDTO(room);
            messagingTemplate.convertAndSend("/topic/room/" + roomCode, roomDTO);
            return roomDTO;
        }
    }

    /**
     * Get room details by code.
     */
    @Transactional(readOnly = true)
    public RoomDTO getRoom(String roomCode) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RuntimeException("Room not found"));
        return toRoomDTO(room);
    }

    /**
     * Leave a room.
     */
    @Transactional
    public RoomDTO leaveRoom(Long userId, String roomCode) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        PlayerGame playerGame = room.getPlayers().stream()
                .filter(p -> p.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("You are not in this room"));

        room.getPlayers().remove(playerGame);
        playerGameRepository.delete(playerGame);

        // Refund coins if the game hasn't started yet
        if (room.getStatus() == RoomStatus.WAITING && room.getCoinAmount() > 0) {
            User user = playerGame.getUser();
            user.setCoins(user.getCoins() + room.getCoinAmount());
            userRepository.save(user);
        }

        // If admin leaves, cancel the room or transfer admin
        if (room.getAdmin().getId().equals(userId)) {
            if (room.getPlayers().isEmpty()) {
                room.setStatus(RoomStatus.CANCELLED);
            } else {
                room.setAdmin(room.getPlayers().get(0).getUser());
            }
        }

        roomRepository.save(room);
        RoomDTO roomDTO = toRoomDTO(room);
        messagingTemplate.convertAndSend("/topic/room/" + roomCode, roomDTO);
        return roomDTO;
    }

    /**
     * Get all available rooms (WAITING status).
     */
    @Transactional(readOnly = true)
    public List<RoomDTO> getAvailableRooms() {
        return roomRepository.findByStatus(RoomStatus.WAITING).stream()
                .map(this::toRoomDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public RoomDTO addBot(Long adminId, String roomCode) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (!room.getAdmin().getId().equals(adminId)) {
            throw new RuntimeException("Only admin can add bots");
        }

        if (room.getPlayers().size() >= room.getMaxPlayers()) {
            throw new RuntimeException("Room is full");
        }

        // Find an unused color
        PlayerColor botColor = null;
        for (PlayerColor color : PlayerColor.values()) {
            final PlayerColor c = color;
            boolean taken = room.getPlayers().stream().anyMatch(p -> p.getColor() == c);
            if (!taken) {
                botColor = color;
                break;
            }
        }

        if (botColor == null) throw new RuntimeException("No colors available");

        final PlayerColor finalColor = botColor;
        User botUser = userRepository.findByName("Bot_" + finalColor)
                .orElseGet(() -> {
                    User b = User.builder()
                            .name("Bot_" + finalColor)
                            .email("bot_" + finalColor.name().toLowerCase() + "@ludoarena.com")
                            .coins(1000L)
                            .authProvider(AuthProvider.LOCAL)
                            .isGuest(true)
                            .build();
                    return userRepository.save(b);
                });

        PlayerGame botPlayer = PlayerGame.builder()
                .user(botUser)
                .room(room)
                .color(finalColor)
                .turnOrder(room.getPlayers().size())
                .isBot(true)
                .build();

        playerGameRepository.save(botPlayer);
        room.getPlayers().add(botPlayer);

        RoomDTO roomDTO = toRoomDTO(room);
        messagingTemplate.convertAndSend("/topic/room/" + roomCode, roomDTO);
        return roomDTO;
    }

    // ========== HELPER METHODS ==========

    /**
     * Generate a unique 6-character room code.
     * THREAD: SecureRandom is thread-safe.
     */
    private String generateRoomCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            code = sb.toString();
        } while (roomRepository.existsByRoomCode(code));
        return code;
    }

    /**
     * Convert Room entity to RoomDTO.
     */
    private RoomDTO toRoomDTO(Room room) {
        List<RoomDTO.PlayerDTO> players = room.getPlayers().stream()
                .map(pg -> RoomDTO.PlayerDTO.builder()
                        .userId(pg.getUser().getId())
                        .name(pg.getUser().getName())
                        .avatarUrl(pg.getUser().getAvatarUrl())
                        .color(pg.getColor().name())
                        .isBot(pg.getIsBot())
                        .build())
                .collect(Collectors.toList());

        return RoomDTO.builder()
                .id(room.getId())
                .roomCode(room.getRoomCode())
                .maxPlayers(room.getMaxPlayers())
                .coinAmount(room.getCoinAmount())
                .status(room.getStatus().name())
                .adminId(room.getAdmin().getId())
                .adminName(room.getAdmin().getName())
                .players(players)
                .build();
    }
}
