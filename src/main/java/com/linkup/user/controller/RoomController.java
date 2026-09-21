package com.linkup.user.controller;

import com.linkup.user.dto.request.CreateRoomRequest;
import com.linkup.user.entity.Message;
import com.linkup.user.entity.Room;
import com.linkup.user.repository.RoomRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// NOTE: this whole controller now requires a valid JWT (see
// SecurityConfig — /api/v1/rooms/** used to be permitAll, which meant
// anyone, logged in or not, could create rooms and read any room's full
// message history just by knowing/guessing its roomId).
@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {

    private static final int MAX_PAGE_SIZE = 50;

    private final RoomRepository roomRepository;

    public RoomController(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    // create room
    @PostMapping
    public ResponseEntity<?> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        if (request.roomId().toLowerCase(java.util.Locale.ROOT).startsWith("system-")) {
            return ResponseEntity.badRequest().body("System room IDs are reserved.");
        }
        if (roomRepository.findByRoomId(request.roomId()) != null) {
            return ResponseEntity.badRequest().body("Room already exists!");
        }

        Room room = new Room();
        room.setRoomId(request.roomId());
        Room savedRoom = roomRepository.save(room);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedRoom);
    }

    // get room: join
    @GetMapping("/{roomId}")
    public ResponseEntity<?> joinRoom(@PathVariable String roomId) {
        Room room = roomRepository.findByRoomId(roomId);
        if (room == null) {
            return ResponseEntity.badRequest().body("Room not found!!");
        }
        return ResponseEntity.ok(room);
    }

    // get messages of room
    @GetMapping("/{roomId}/messages")
    public ResponseEntity<List<Message>> getMessages(
            @PathVariable String roomId,
            @RequestParam(value = "page", defaultValue = "0", required = false) int page,
            @RequestParam(value = "size", defaultValue = "20", required = false) int size
    ) {
        Room room = roomRepository.findByRoomId(roomId);
        if (room == null) {
            return ResponseEntity.badRequest().build();
        }

        page = Math.max(0, page);
        size = Math.min(Math.max(1, size), MAX_PAGE_SIZE); // clamp so nobody can request the whole history in one shot

        List<Message> messages = room.getMessages();
        int end = (int) Math.max(0L, messages.size() - (long) page * size);
        int start = Math.max(0, end - size);
        List<Message> paginatedMessages = messages.subList(start, end);
        return ResponseEntity.ok(paginatedMessages);
    }
}
