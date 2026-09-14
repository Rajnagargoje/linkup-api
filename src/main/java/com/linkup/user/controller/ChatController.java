package com.linkup.user.controller;

import com.linkup.user.dto.request.MessageRequest;
import com.linkup.user.entity.Message;
import com.linkup.user.entity.Room;
import com.linkup.user.repository.RoomRepository;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDateTime;

@RestController
public class ChatController {

    private static final int MAX_MESSAGE_LENGTH = 2000;

    private final RoomRepository roomRepository;

    public ChatController(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    // for sending and receiving messages
    @MessageMapping("/sendMessage/{roomId}") // /app/sendMessage/roomId
    @SendTo("/topic/room/{roomId}") // subscribe
    public Message sendMessage(
            @DestinationVariable String roomId,
            @RequestBody MessageRequest request,
            Principal principal // set by StompAuthChannelInterceptor on CONNECT — this is the ONLY
                                 // trustworthy source of "who sent this", never request.getSender()
    ) {
        if (principal == null) {
            throw new RuntimeException("Unauthenticated");
        }

        String content = request.getContent();
        if (content == null || content.isBlank()) {
            throw new RuntimeException("Message content cannot be empty");
        }
        if (content.length() > MAX_MESSAGE_LENGTH) {
            content = content.substring(0, MAX_MESSAGE_LENGTH);
        }

        // Always trust the path variable for which room this belongs to,
        // not whatever roomId happens to be in the request body — they
        // could legitimately differ if a client sends stale/bad data.
        Room room = roomRepository.findByRoomId(roomId);
        if (room == null) {
            throw new RuntimeException("Room not found: " + roomId);
        }

        Message message = new Message();
        message.setContent(content);
        message.setSender(principal.getName());
        message.setTimeStamp(LocalDateTime.now());

        room.getMessages().add(message);
        roomRepository.save(room);

        return message;
    }
}
