package com.dongVu1105.personal_chatbot.controller;

import com.team14.chatbot.dto.request.GenerateTitleRequest;
import com.team14.chatbot.dto.response.ApiResponse;
import com.team14.chatbot.service.TitleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class TitleController {

    private final TitleService titleService;

    @PostMapping("/generate-title")
    public ApiResponse<String> generateTitle(@RequestBody GenerateTitleRequest request) {
        String newTitle = titleService.generateTitle(request.getConversationId(), request.getUserMessage());
        return ApiResponse.<String>builder()
                .data(newTitle)
                .build();
    }
}
