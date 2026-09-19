package com.linkup.user.service.impl;


import com.linkup.user.dto.chat.ChatMessageResponse;
import com.linkup.user.dto.chat.SendMessageRequest;
import com.linkup.user.entity.User;
import com.linkup.user.entity.chat.ChatMessage;
import com.linkup.user.entity.chat.Conversation;
import com.linkup.user.entity.chat.ConversationParticipant;
import com.linkup.user.repository.ChatMessageRepository;
import com.linkup.user.repository.ConversationParticipantRepository;
import com.linkup.user.repository.ConversationRepository;
import com.linkup.user.repository.UserRepository;
import com.linkup.user.service.ChatMessageService;
import com.linkup.user.utils.MessageStatus;
import com.linkup.user.utils.MessageType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatMessageServiceImpl
        implements ChatMessageService {

    private final ChatMessageRepository messageRepository;

    private final ConversationRepository conversationRepository;

    private final ConversationParticipantRepository
            participantRepository;

    private final UserRepository userRepository;

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public ChatMessageResponse sendMessage(
            String username,
            SendMessageRequest request
    ) {

        User sender =
                getUserByUsername(username);

        Conversation conversation =
                conversationRepository
                        .findById(
                                request.conversationId()
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Conversation not found"
                                )
                        );

        /*
         * Security:
         * sender must be a participant.
         */
        ConversationParticipant senderParticipant =
                participantRepository
                        .findByConversationIdAndUserPublicId(
                                conversation.getId(),
                                sender.getPublicId()
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "You are not part of this conversation"
                                )
                        );

        if (request.content() == null
                || request.content().isBlank()) {

            throw new IllegalArgumentException(
                    "Message cannot be empty"
            );
        }

        ChatMessage replyTo = null;

        if (request.replyToMessageId() != null) {

            replyTo =
                    messageRepository
                            .findById(
                                    request.replyToMessageId()
                            )
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Reply message not found"
                                    )
                            );

            if (!replyTo
                    .getConversation()
                    .getId()
                    .equals(conversation.getId())) {

                throw new IllegalArgumentException(
                        "Reply message does not belong to this conversation"
                );
            }
        }

        ChatMessage message =
                ChatMessage.builder()
                        .conversation(conversation)
                        .sender(sender)
                        .type(MessageType.TEXT)
                        .content(
                                request.content().trim()
                        )
                        .status(MessageStatus.SENT)
                        .replyToMessage(replyTo)
                        .createdAt(LocalDateTime.now())
                        .build();

        ChatMessage saved =
                messageRepository.save(message);

        conversation.setLastMessageId(
                saved.getId()
        );

        conversation.setLastMessagePreview(
                saved.getContent()
        );

        conversation.setUpdatedAt(
                LocalDateTime.now()
        );

        conversationRepository.save(
                conversation
        );

        ChatMessageResponse response =
                toResponse(saved);

        /*
         * Send to every participant's private
         * WebSocket destination.
         */
        conversation
                .getParticipants()
                .forEach(participant -> {

                    String recipient =
                            participant
                                    .getUser()
                                    .getUsername();

                    messagingTemplate
                            .convertAndSendToUser(
                                    recipient,
                                    "/queue/conversations/"
                                            + conversation.getId(),
                                    response
                            );
                });

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(
            String username,
            Long conversationId,
            int page,
            int size
    ) {

        ensureParticipant(
                username,
                conversationId
        );

        int safeSize =
                Math.min(
                        Math.max(size, 1),
                        100
                );

        return messageRepository
                .findByConversationIdOrderByCreatedAtDesc(
                        conversationId,
                        PageRequest.of(
                                page,
                                safeSize,
                                Sort.by(
                                        Sort.Direction.DESC,
                                        "createdAt"
                                )
                        )
                )
                .map(this::toResponse)
                .getContent();
    }

    @Override
    public ChatMessageResponse editMessage(
            String username,
            Long messageId,
            String content
    ) {

        ChatMessage message =
                getMessage(messageId);

        if (!message
                .getSender()
                .getUsername()
                .equals(username)) {

            throw new IllegalArgumentException(
                    "You can edit only your own messages"
            );
        }

        if (message.isDeletedForEveryone()) {

            throw new IllegalArgumentException(
                    "Deleted message cannot be edited"
            );
        }

        if (content == null
                || content.isBlank()) {

            throw new IllegalArgumentException(
                    "Message cannot be empty"
            );
        }

        message.setContent(
                content.trim()
        );

        message.setEditedAt(
                LocalDateTime.now()
        );

        ChatMessage saved =
                messageRepository.save(message);

        ChatMessageResponse response =
                toResponse(saved);

        broadcast(
                saved,
                response
        );

        return response;
    }

    @Override
    public void deleteForEveryone(
            String username,
            Long messageId
    ) {

        ChatMessage message =
                getMessage(messageId);

        if (!message
                .getSender()
                .getUsername()
                .equals(username)) {

            throw new IllegalArgumentException(
                    "You can delete only your own message"
            );
        }

        message.setContent(null);

        message.setDeletedForEveryone(true);

        message.setDeletedAt(
                LocalDateTime.now()
        );

        ChatMessage saved =
                messageRepository.save(message);

        broadcast(
                saved,
                toResponse(saved)
        );
    }

    @Override
    public void markDelivered(
            String username,
            Long messageId
    ) {

        ChatMessage message =
                getMessage(messageId);

        ensureParticipant(
                username,
                message
                        .getConversation()
                        .getId()
        );

        if (!message
                .getSender()
                .getUsername()
                .equals(username)) {

            message.setStatus(
                    MessageStatus.DELIVERED
            );

            message.setDeliveredAt(
                    LocalDateTime.now()
            );

            ChatMessage saved =
                    messageRepository.save(message);

            broadcast(
                    saved,
                    toResponse(saved)
            );
        }
    }

    @Override
    public void markRead(
            String username,
            Long messageId
    ) {

        ChatMessage message =
                getMessage(messageId);

        ensureParticipant(
                username,
                message
                        .getConversation()
                        .getId()
        );

        if (!message
                .getSender()
                .getUsername()
                .equals(username)) {

            message.setStatus(
                    MessageStatus.READ
            );

            message.setReadAt(
                    LocalDateTime.now()
            );

            ChatMessage saved =
                    messageRepository.save(message);

            broadcast(
                    saved,
                    toResponse(saved)
            );
        }
    }

    private void ensureParticipant(
            String username,
            Long conversationId
    ) {

        User user =
                getUserByUsername(username);

        boolean participant =
                participantRepository
                        .existsByConversationIdAndUserPublicId(
                                conversationId,
                                user.getPublicId()
                        );

        if (!participant) {

            throw new IllegalArgumentException(
                    "You are not part of this conversation"
            );
        }
    }

    private ChatMessage getMessage(
            Long messageId
    ) {

        return messageRepository
                .findById(messageId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Message not found"
                        )
                );
    }

    private User getUserByUsername(
            String username
    ) {

        return userRepository
                .findByUsername(username)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "User not found"
                        )
                );
    }

    private void broadcast(
            ChatMessage message,
            ChatMessageResponse response
    ) {

        message
                .getConversation()
                .getParticipants()
                .forEach(participant ->
                        messagingTemplate
                                .convertAndSendToUser(
                                        participant
                                                .getUser()
                                                .getUsername(),
                                        "/queue/conversations/"
                                                + message
                                                .getConversation()
                                                .getId(),
                                        response
                                )
                );
    }

    private ChatMessageResponse toResponse(
            ChatMessage message
    ) {

        ChatMessage reply =
                message.getReplyToMessage();

        return new ChatMessageResponse(
                message.getId(),
                message
                        .getConversation()
                        .getId(),
                message
                        .getSender()
                        .getPublicId(),
                message
                        .getSender()
                        .getUsername(),
                message
                        .getSender()
                        .getProfilePhoto(),
                message.getType().name(),
                message.getContent(),
                message.getStatus().name(),
                reply != null
                        ? reply.getId()
                        : null,
                reply != null
                        ? reply.getContent()
                        : null,
                message.getCreatedAt(),
                message.getEditedAt(),
                message.getDeliveredAt(),
                message.getReadAt(),
                message.isDeletedForEveryone()
        );
    }
}