package com.linkup.user.service.impl;



import com.linkup.user.dto.chat.ConversationResponse;
import com.linkup.user.entity.User;
import com.linkup.user.entity.chat.Conversation;
import com.linkup.user.entity.chat.ConversationParticipant;
import com.linkup.user.repository.ConnectionRepository;
import com.linkup.user.repository.ConversationParticipantRepository;
import com.linkup.user.repository.ConversationRepository;
import com.linkup.user.repository.UserRepository;
import com.linkup.user.service.ConversationService;
import com.linkup.user.utils.ConnectionStatus;
import com.linkup.user.utils.ConversationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ConversationServiceImpl
        implements ConversationService {

    private final ConversationRepository conversationRepository;

    private final ConversationParticipantRepository
            participantRepository;

    private final ConnectionRepository connectionRepository;

    private final UserRepository userRepository;

    @Override
    public ConversationResponse getOrCreateDirectConversation(
            String currentUsername,
            String friendPublicId
    ) {

        User currentUser =
                getUserByUsername(currentUsername);

        User friend =
                userRepository.findByPublicId(friendPublicId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Friend not found"
                                )
                        );

        if (currentUser.getId()
                .equals(friend.getId())) {

            throw new IllegalArgumentException(
                    "You cannot chat with yourself"
            );
        }

        /*
         * IMPORTANT:
         * Chat is allowed only between accepted friends.
         */
        boolean connected =
                connectionRepository.areConnected(
                        currentUser.getId(),
                        friend.getId(),
                        ConnectionStatus.ACCEPTED
                );

        if (!connected) {
            throw new IllegalArgumentException(
                    "You can chat only with an accepted friend"
            );
        }

        Conversation conversation =
                conversationRepository
                        .findDirectConversation(
                                currentUser.getPublicId(),
                                friend.getPublicId(),
                                ConversationType.DIRECT
                        )
                        .orElseGet(() ->
                                createConversation(
                                        currentUser,
                                        friend
                                )
                        );

        return toResponse(
                conversation,
                currentUser
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationResponse> getMyConversations(
            String currentUsername
    ) {

        User currentUser =
                getUserByUsername(currentUsername);

        return participantRepository
                .findByUserPublicIdOrderByConversationUpdatedAtDesc(
                        currentUser.getPublicId()
                )
                .stream()
                .filter(participant ->
                        !participant.isArchived()
                )
                .map(participant ->
                        toResponse(
                                participant.getConversation(),
                                currentUser
                        )
                )
                .toList();
    }

    @Override
    public void markAsRead(
            Long conversationId,
            String currentUsername,
            Long messageId
    ) {

        ConversationParticipant participant =
                getParticipant(
                        conversationId,
                        currentUsername
                );

        participant.setLastReadMessageId(
                messageId
        );

        participantRepository.save(
                participant
        );
    }

    @Override
    public void setMuted(
            Long conversationId,
            String currentUsername,
            boolean muted
    ) {

        ConversationParticipant participant =
                getParticipant(
                        conversationId,
                        currentUsername
                );

        participant.setMuted(muted);

        participantRepository.save(
                participant
        );
    }

    @Override
    public void setArchived(
            Long conversationId,
            String currentUsername,
            boolean archived
    ) {

        ConversationParticipant participant =
                getParticipant(
                        conversationId,
                        currentUsername
                );

        participant.setArchived(archived);

        participantRepository.save(
                participant
        );
    }

    @Override
    public void setPinned(
            Long conversationId,
            String currentUsername,
            boolean pinned
    ) {

        ConversationParticipant participant =
                getParticipant(
                        conversationId,
                        currentUsername
                );

        participant.setPinned(pinned);

        participantRepository.save(
                participant
        );
    }

    private Conversation createConversation(
            User user1,
            User user2
    ) {

        Conversation conversation =
                Conversation.builder()
                        .type(ConversationType.DIRECT)
                        .build();

        conversation =
                conversationRepository.save(
                        conversation
                );

        ConversationParticipant p1 =
                ConversationParticipant.builder()
                        .conversation(conversation)
                        .user(user1)
                        .build();

        ConversationParticipant p2 =
                ConversationParticipant.builder()
                        .conversation(conversation)
                        .user(user2)
                        .build();

        participantRepository.saveAll(
                List.of(p1, p2)
        );

        conversation.setParticipants(
                new ArrayList<>(
                        List.of(p1, p2)
                )
        );

        return conversation;
    }

    private ConversationParticipant getParticipant(
            Long conversationId,
            String username
    ) {

        User user =
                getUserByUsername(username);

        return participantRepository
                .findByConversationIdAndUserPublicId(
                        conversationId,
                        user.getPublicId()
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "You are not a participant of this conversation"
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

    private ConversationResponse toResponse(
            Conversation conversation,
            User currentUser
    ) {

        User friend =
                conversation.getParticipants()
                        .stream()
                        .map(
                                ConversationParticipant::getUser
                        )
                        .filter(
                                user ->
                                        !user.getId()
                                                .equals(
                                                        currentUser.getId()
                                                )
                        )
                        .findFirst()
                        .orElseThrow();

        ConversationParticipant currentParticipant =
                conversation.getParticipants()
                        .stream()
                        .filter(
                                participant ->
                                        participant
                                                .getUser()
                                                .getId()
                                                .equals(
                                                        currentUser.getId()
                                                )
                        )
                        .findFirst()
                        .orElseThrow();

        return new ConversationResponse(
                conversation.getId(),
                conversation.getType().name(),
                friend.getPublicId(),
                friend.getUsername(),
                friend.getProfilePhoto(),
                friend.getAge(),
                friend.getOnline(),
                friend.getLastSeenAt(),
                conversation.getLastMessagePreview(),
                conversation.getUpdatedAt(),
                0,
                currentParticipant.isMuted(),
                currentParticipant.isArchived(),
                currentParticipant.isPinned()
        );
    }
}