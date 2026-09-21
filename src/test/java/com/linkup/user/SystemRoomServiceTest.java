package com.linkup.user;

import com.linkup.user.controller.RoomController;
import com.linkup.user.dto.request.CreateRoomRequest;
import com.linkup.user.entity.Room;
import com.linkup.user.repository.RoomRepository;
import com.linkup.user.service.SystemRoomService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SystemRoomServiceTest {
    @Test void catalogueHasFourRoomsWithFeedbackFirstAndRulesForAll() {
        var rooms = new SystemRoomService(mock(MongoTemplate.class)).list();
        assertEquals(4, rooms.size());
        assertEquals("system-feedback", rooms.get(0).roomId());
        assertEquals(4, rooms.stream().map(SystemRoomService.SystemRoom::roomId).distinct().count());
        assertTrue(rooms.stream().allMatch(room -> !room.topic().isBlank() && !room.rules().isEmpty()));
    }

    @Test void repeatedJoinsOnlyInitializeHistoryOnInsert() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        var service = new SystemRoomService(mongo);
        service.join("system-feedback"); service.join("system-feedback");
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo, times(2)).upsert(query.capture(), update.capture(), eq(Room.class));
        assertEquals("system-feedback", query.getValue().getQueryObject().get("_id"));
        assertEquals(java.util.Set.of("$setOnInsert"), update.getValue().getUpdateObject().keySet());
    }

    @Test void unknownSystemRoomCannotBeCreated() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        assertThrows(IllegalArgumentException.class, () -> new SystemRoomService(mongo).join("unknown"));
        verifyNoInteractions(mongo);
    }

    @Test void ordinaryCreationCannotClaimASystemRoomId() {
        RoomRepository rooms = mock(RoomRepository.class);
        var response = new RoomController(rooms).createRoom(new CreateRoomRequest("system-feedback"));
        assertEquals(400, response.getStatusCode().value());
        verifyNoInteractions(rooms);
    }
}
