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
    private final org.springframework.data.mongodb.core.MongoTemplate mongo;

    public ChatController(RoomRepository roomRepository, org.springframework.data.mongodb.core.MongoTemplate mongo) {
        this.roomRepository = roomRepository;
        this.mongo = mongo;
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

        // Concurrent messages must append atomically rather than overwrite another sender's history.
        mongo.updateFirst(org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("roomId").is(roomId)),
                new org.springframework.data.mongodb.core.query.Update().push("messages", message), Room.class);

        return message;
    }
}
