package com.dongVu1105.personal_chatbot.service;

import com.team14.chatbot.dto.request.ConversationRequest;
import com.team14.chatbot.dto.response.ConversationResponse;
import com.team14.chatbot.dto.response.PageResponse;
import com.team14.chatbot.entity.Conversation;
import com.team14.chatbot.entity.User;
import com.team14.chatbot.exception.AppException;
import com.team14.chatbot.exception.ErrorCode;
import com.team14.chatbot.mapper.ConversationMapper;
import com.team14.chatbot.repository.ConversationRepository;
import com.team14.chatbot.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConversationService {
    ConversationRepository conversationRepository;
    ConversationMapper conversationMapper;
    UserRepository userRepository;

    public ConversationResponse create(ConversationRequest request) throws AppException {
        Conversation conversation = conversationMapper.toConversation(request);
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        conversation.setCreatedDate(Instant.now());
        conversation.setUserId(user.getId());
        return conversationMapper.toConversationResponse(conversationRepository.save(conversation));
    }

    public PageResponse<ConversationResponse> findAllByUserId(int page, int size) throws AppException {
        Sort sort = Sort.by("createdDate").descending();
        Pageable pageable = PageRequest.of(page - 1, size, sort);
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        Page<Conversation> conversationPage = conversationRepository.findAllByUserId(user.getId(), pageable);
        var conversationData = conversationPage.getContent().stream().map(conversationMapper::toConversationResponse)
                .toList();
        return PageResponse.<ConversationResponse>builder()
                .currentPage(page)
                .totalPages(conversationPage.getTotalPages())
                .totalElements(conversationPage.getTotalElements())
                .pageSize(conversationPage.getSize())
                .result(conversationData)
                .hasNextPage(conversationPage.hasNext())
                .hasPreviousPage(conversationPage.hasPrevious()).build();
    }

    public void delete(String id) {
        conversationRepository.deleteById(id);
    }

}
