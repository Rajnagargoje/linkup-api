package com.linkup.user.notification;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "notification_preferences") @Getter @Setter
public class NotificationPreferences {
    @Id private String userId;
    private boolean pushEnabled = true;
    private boolean messages = true;
    private boolean friendRequests = true;
    private boolean updates = true;
    private boolean messagePreview = false;
    public boolean allows(NotificationType type) {
        return switch (type) {
            case MESSAGE -> messages;
            case FRIEND_REQUEST, FRIEND_ACCEPTED -> friendRequests;
            case REPORT_UPDATE, SYSTEM -> updates;
        };
    }
}
