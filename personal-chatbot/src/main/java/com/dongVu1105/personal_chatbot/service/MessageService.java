package com.dongVu1105.personal_chatbot.service;

import com.team14.chatbot.dto.request.MessageRequest;
import com.team14.chatbot.dto.response.MessageResponse;
import com.team14.chatbot.dto.response.PageResponse;
import com.team14.chatbot.entity.Conversation;
import com.team14.chatbot.entity.Message;
import com.team14.chatbot.entity.User;
import com.team14.chatbot.exception.AppException;
import com.team14.chatbot.exception.ErrorCode;
import com.team14.chatbot.mapper.MessageMapper;
import com.team14.chatbot.repository.ConversationRepository;
import com.team14.chatbot.repository.HybridChatMemoryRepository;
import com.team14.chatbot.repository.MessageRepository;
import com.team14.chatbot.repository.UserRepository;
import com.team14.chatbot.service.SummaryService;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
// @RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MessageService {
        final MessageMapper messageMapper;
        final MessageRepository messageRepository;
        final ConversationRepository conversationRepository;
        final UserRepository userRepository;
        final ChatService chatService;
        final HybridChatMemoryRepository hybridChatMemoryRepository;
        final SummaryService summaryService;
        final ChatClient chatClient;

        public MessageService(
                        MessageMapper messageMapper,
                        MessageRepository messageRepository,
                        ConversationRepository conversationRepository,
                        UserRepository userRepository,
                        ChatService chatService,
                        HybridChatMemoryRepository hybridChatMemoryRepository,
                        SummaryService summaryService,
                        @Qualifier("geminiFlashClient") ChatClient chatClient) {
                this.messageMapper = messageMapper;
                this.messageRepository = messageRepository;
                this.conversationRepository = conversationRepository;
                this.userRepository = userRepository;
                this.chatService = chatService;
                this.hybridChatMemoryRepository = hybridChatMemoryRepository;
                this.summaryService = summaryService;
                this.chatClient = chatClient;
        }

        public Flux<String> streamingCreate(MessageRequest request) throws AppException {
                Conversation conversation = conversationRepository.findById(request.getConversationId()).orElseThrow(
                                () -> new AppException(ErrorCode.CONVERSATION_NOT_EXISTED));
                String username = SecurityContextHolder.getContext().getAuthentication().getName();
                User user = userRepository.findByUsername(username).orElseThrow(
                                () -> new AppException(ErrorCode.USER_NOT_EXISTED));
                if (!conversation.getUserId().equals(user.getId())) {
                        throw new AppException(ErrorCode.UNAUTHENTICATED);
                }

                UserMessage userQuery = new UserMessage(request.getText());
                Prompt prompt = chatService.generatePrompt(userQuery, request.getConversationId());

                StringBuilder aiResponse = new StringBuilder();

                return chatClient.prompt(prompt)
                                .stream()
                                .content()
                                .doOnNext(chunk -> {
                                        aiResponse.append(chunk);

                                })
                                .doOnComplete(() -> {
                                        Message userMessage = Message.builder()
                                                        .text(request.getText())
                                                        .conversationId(request.getConversationId())
                                                        .role(MessageType.USER.name())
                                                        .build();

                                        Message aiMessage = Message.builder()
                                                        .text(aiResponse.toString())
                                                        .conversationId(request.getConversationId())
                                                        .role(MessageType.ASSISTANT.name())
                                                        .build();

                                        messageRepository.saveAll(List.of(userMessage, aiMessage));
                                        
                                        // Trigger async summarization
                                        summaryService.updateSummaryAsync(request.getConversationId());

                                        AssistantMessage aiMessage2 = new AssistantMessage(aiResponse.toString());
                                        hybridChatMemoryRepository.saveAll(request.getConversationId(),
                                                        List.of(userQuery, aiMessage2));

                                        System.out.println("Saved AI message: " + aiMessage.getId());
                                })
                                .doOnError(err -> {
                                        System.err.println("Stream error: " + err.getMessage());
                                });
        }

        public Flux<String> streamingCreateNew(MessageRequest request) throws AppException {
                Conversation conversation = conversationRepository.findById(request.getConversationId()).orElseThrow(
                                () -> new AppException(ErrorCode.CONVERSATION_NOT_EXISTED));
                String username = SecurityContextHolder.getContext().getAuthentication().getName();
                User user = userRepository.findByUsername(username).orElseThrow(
                                () -> new AppException(ErrorCode.USER_NOT_EXISTED));
                if (!conversation.getUserId().equals(user.getId())) {
                        throw new AppException(ErrorCode.UNAUTHENTICATED);
                }

                UserMessage userQuery = new UserMessage(request.getText());
                String aiText = chatService.generatePrompt_new(userQuery, request.getConversationId());

                Message userMessage = Message.builder()
                                .text(request.getText())
                                .conversationId(request.getConversationId())
                                .role(MessageType.USER.name())
                                .build();

                Message aiMessage = Message.builder()
                                .text(aiText)
                                .conversationId(request.getConversationId())
                                .role(MessageType.ASSISTANT.name())
                                .build();

                messageRepository.saveAll(List.of(userMessage, aiMessage));
                AssistantMessage aiMessageMem = new AssistantMessage(aiText);
                hybridChatMemoryRepository.saveAll(request.getConversationId(), List.of(userQuery, aiMessageMem));

                return Flux.just(aiText);
        }

        public MessageResponse create(MessageRequest request) throws AppException {
                Conversation conversation = conversationRepository.findById(request.getConversationId()).orElseThrow(
                                () -> new AppException(ErrorCode.CONVERSATION_NOT_EXISTED));
                String username = SecurityContextHolder.getContext().getAuthentication().getName();
                User user = userRepository.findByUsername(username).orElseThrow(
                                () -> new AppException(ErrorCode.USER_NOT_EXISTED));
                if (!conversation.getUserId().equals(user.getId())) {
                        throw new AppException(ErrorCode.UNAUTHENTICATED);
                }

                // Message message = messageMapper.toMessage(request);
                // message.setUserId(user.getId());

                UserMessage userQuery = new UserMessage(request.getText());
                org.springframework.ai.chat.messages.Message aiResponse = chatService
                                .oneTimeResponse(chatService.generatePrompt(userQuery, request.getConversationId()));

                hybridChatMemoryRepository.saveAll(request.getConversationId(), List.of(userQuery, aiResponse));

                Message userMessage = Message.builder()
                                .text(request.getText())
                                .conversationId(request.getConversationId())
                                .role(MessageType.USER.name())
                                .build();
                Message aiMessage = Message.builder()
                                .text(aiResponse.getText())
                                .conversationId(request.getConversationId())
                                .role(MessageType.ASSISTANT.name())
                                .build();
                messageRepository.saveAll(List.of(userMessage, aiMessage));

                return MessageResponse.builder()
                                .id(aiMessage.getId())
                                .role(MessageType.ASSISTANT.name())
                                .text(aiMessage.getText())
                                .conversationId(aiMessage.getConversationId())
                                .createdAt(aiMessage.getCreatedAt())
                                .build();
        }

        public MessageResponse createNew(MessageRequest request) throws AppException {
                Conversation conversation = conversationRepository.findById(request.getConversationId()).orElseThrow(
                                () -> new AppException(ErrorCode.CONVERSATION_NOT_EXISTED));
                String username = SecurityContextHolder.getContext().getAuthentication().getName();
                User user = userRepository.findByUsername(username).orElseThrow(
                                () -> new AppException(ErrorCode.USER_NOT_EXISTED));
                if (!conversation.getUserId().equals(user.getId())) {
                        throw new AppException(ErrorCode.UNAUTHENTICATED);
                }

                UserMessage userQuery = new UserMessage(request.getText());
                String aiText = chatService.generatePrompt_new(userQuery, request.getConversationId());

                hybridChatMemoryRepository.saveAll(request.getConversationId(),
                                List.of(userQuery, new AssistantMessage(aiText)));

                Message userMessage = Message.builder()
                                .text(request.getText())
                                .conversationId(request.getConversationId())
                                .role(MessageType.USER.name())
                                .build();
                Message aiMessage = Message.builder()
                                .text(aiText)
                                .conversationId(request.getConversationId())
                                .role(MessageType.ASSISTANT.name())
                                .build();
                messageRepository.saveAll(List.of(userMessage, aiMessage));
                // Trigger async summarization
                summaryService.updateSummaryAsync(request.getConversationId());
                return MessageResponse.builder()
                                .id(aiMessage.getId())
                                .role(MessageType.ASSISTANT.name())
                                .text(aiMessage.getText())
                                .conversationId(aiMessage.getConversationId())
                                .createdAt(aiMessage.getCreatedAt())
                                .build();
        }

        public PageResponse<MessageResponse> findAll(String conversationId, int page, int size) {
                Sort sort = Sort.by("createdAt").descending();
                Pageable pageable = PageRequest.of(page - 1, size, sort);
                Page<Message> messagePage = messageRepository.findAllByConversationId(conversationId, pageable);
                var messageData = messagePage.getContent().stream().map(
                                message -> MessageResponse.builder()
                                                .id(message.getId())
                                                .text(message.getText())
                                                .role(message.getRole())
                                                .createdAt(message.getCreatedAt())
                                                .conversationId(message.getConversationId()).build())
                                .toList();
                return PageResponse.<MessageResponse>builder()
                                .currentPage(page)
                                .pageSize(messagePage.getSize())
                                .totalPages(messagePage.getTotalPages())
                                .totalElements(messagePage.getTotalElements())
                                .hasPreviousPage(messagePage.hasPrevious())
                                .hasNextPage(messagePage.hasNext())
                                .result(messageData).build();
        }

        private MessageResponse toMessageResponse(Message message) throws AppException {
                MessageResponse messageResponse = messageMapper.toMessageResponse(message);
                return messageResponse;
        }
}
