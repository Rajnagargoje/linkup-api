package com.linkup.user.dto.chat;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Chat entities use LocalDateTime.now(), which follows the server's timezone.
 * Include that offset on the wire so clients do not interpret server time as
 * device-local time. The database values and DTO constructor types stay intact.
 */
public final class ChatTimestampSerializer extends JsonSerializer<LocalDateTime> {
    @Override
    public void serialize(LocalDateTime value, JsonGenerator generator,
                          SerializerProvider provider) throws IOException {
        generator.writeString(value.atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }
}
