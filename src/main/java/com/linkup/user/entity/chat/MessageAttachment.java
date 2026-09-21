package com.linkup.user.entity.chat;


import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "message_attachments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "message_id",
            nullable = false
    )
    private ChatMessage message;

    @Column(nullable = false)
    private String url;

    @Column
    private String fileName;

    @Column
    private String contentType;

    @Column
    private Long fileSize;

    @Column
    private Integer durationSeconds;

    @Column
    private Integer width;

    @Column
    private Integer height;
}