package com.dongVu1105.personal_chatbot.controller;


import com.dongVu1105.personal_chatbot.dto.request.UserCreationRequest;
import com.dongVu1105.personal_chatbot.dto.response.ApiResponse;
import com.dongVu1105.personal_chatbot.dto.response.UserResponse;
import com.dongVu1105.personal_chatbot.exception.AppException;
import com.dongVu1105.personal_chatbot.service.UserService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class UserController {
    UserService userService;

    @PostMapping("/register")
    public ApiResponse<UserResponse> createUser (@RequestBody UserCreationRequest request) throws AppException {
        return ApiResponse.<UserResponse>builder()
                .data(userService.createUser(request)).build();
    }

    @GetMapping("/my-info")
    public ApiResponse<UserResponse> getMyInfo () throws AppException {
        return ApiResponse.<UserResponse>builder()
                .data(userService.getMyInfo()).build();
    }

}
