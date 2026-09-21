package com.linkup.user.service;

import com.linkup.user.entity.Room;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemRoomService {
    private final MongoTemplate mongo;

    public record SystemRoom(String roomId, String title, String topic, String description, List<String> rules) {}

    private static final List<SystemRoom> ROOMS = List.of(
        new SystemRoom("system-feedback", "LinkUp Feedback", "Performance, issues, suggestions & bugs",
            "Help make LinkUp better. Share what works, what feels slow, and what you would like us to improve.",
            List.of("Describe one issue or idea at a time.", "For bugs, include your device, app version, and steps to reproduce.", "Never post passwords, private messages, or personal details.", "Keep feedback constructive. No spam or personal attacks.")),
        new SystemRoom("system-interests", "Interests Lounge", "Music, gaming, movies & hobbies",
            "Find people who enjoy the things you do. Share a recommendation or tell us about your latest hobby.",
            List.of("Introduce your interest so others can join in.", "Respect different tastes and avoid spoilers without a warning.", "No advertising, repeated messages, or harassment.")),
        new SystemRoom("system-languages", "Language Exchange", "Learn, practise & share languages",
            "Practise a language together. Tell the room what you speak and what you are learning.",
            List.of("Mention the language you want to practise.", "Correct others kindly and only when they welcome it.", "Welcome beginners. No mocking accents or mistakes.")),
        new SystemRoom("system-lounge", "Everyday Chat", "Introductions & everyday conversations",
            "A relaxed place to say hello, share a small win, or talk about your day.",
            List.of("Be welcoming and keep conversations respectful.", "Do not share anyone's private information.", "No explicit content, hate, spam, or unsolicited promotions."))
    );

    public List<SystemRoom> list() { return ROOMS; }

    public SystemRoom join(String roomId) {
        SystemRoom info = ROOMS.stream().filter(room -> room.roomId().equals(roomId)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown system room."));
        // Fixed Mongo identity makes concurrent/repeated joins idempotent without clearing history.
        mongo.upsert(Query.query(Criteria.where("_id").is(roomId)),
            new Update().setOnInsert("roomId", roomId).setOnInsert("messages", List.of()), Room.class);
        return info;
    }
}
