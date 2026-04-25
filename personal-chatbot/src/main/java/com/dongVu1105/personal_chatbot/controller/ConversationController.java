package com.dongVu1105.personal_chatbot.controller;

import com.team14.chatbot.dto.request.ConversationRequest;
import com.team14.chatbot.dto.response.ApiResponse;
import com.team14.chatbot.dto.response.ConversationResponse;
import com.team14.chatbot.dto.response.PageResponse;
import com.team14.chatbot.exception.AppException;
import com.team14.chatbot.service.ConversationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/conversation")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ConversationController {
    ConversationService conversationService;

    @PostMapping("/create")
    public ApiResponse<ConversationResponse> create(@RequestBody ConversationRequest request) throws AppException {
        return ApiResponse.<ConversationResponse>builder().data(conversationService.create(request)).build();
    }

    @GetMapping("/list")
    public ApiResponse<PageResponse<ConversationResponse>> findAllByUserId(
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size) throws AppException {
        return ApiResponse.<PageResponse<ConversationResponse>>builder()
                .data(conversationService.findAllByUserId(page, size)).build();
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") String id) {
        conversationService.delete(id);
        return ApiResponse.<Void>builder().build();
    }
}
