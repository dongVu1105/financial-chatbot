package com.dongVu1105.personal_chatbot.controller;

import com.team14.chatbot.dto.request.MessageRequest;
import com.team14.chatbot.dto.response.ApiResponse;
import com.team14.chatbot.dto.response.MessageResponse;
import com.team14.chatbot.dto.response.PageResponse;
import com.team14.chatbot.exception.AppException;
import com.team14.chatbot.service.MessageService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/message")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class MessageController {
    MessageService messageService;
    // ChatClient chatClient;

    @PostMapping(value = "/stream-create", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamCreate(@RequestBody MessageRequest request) throws AppException {
        return messageService.streamingCreate(request);
    }

    @PostMapping(value = "/stream-create-new", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamCreateNew(@RequestBody MessageRequest request) throws AppException {
        return messageService.streamingCreateNew(request);
    }

    @PostMapping("/create")
    public ApiResponse<MessageResponse> create(@RequestBody MessageRequest request) throws AppException {
        return ApiResponse.<MessageResponse>builder().data(messageService.create(request)).build();
    }

    @PostMapping("/create-new")
    public ApiResponse<MessageResponse> createNew(@RequestBody MessageRequest request) throws AppException {
        return ApiResponse.<MessageResponse>builder().data(messageService.createNew(request)).build();
    }

    @GetMapping("/list")
    public ApiResponse<PageResponse<MessageResponse>> findAll(
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size,
            @RequestParam(value = "conversationId") String conversationId) {
        return ApiResponse.<PageResponse<MessageResponse>>builder()
                .data(messageService.findAll(conversationId, page, size)).build();
    }
}
