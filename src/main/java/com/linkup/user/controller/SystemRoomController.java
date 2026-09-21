package com.linkup.user.controller;

import com.linkup.user.service.SystemRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/rooms/system")
@RequiredArgsConstructor
public class SystemRoomController {
    private final SystemRoomService rooms;

    @GetMapping
    public List<SystemRoomService.SystemRoom> list() { return rooms.list(); }

    @PostMapping("/{roomId}/join")
    public SystemRoomService.SystemRoom join(@PathVariable String roomId) { return rooms.join(roomId); }
}
