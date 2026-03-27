package com.dongVu1105.personal_chatbot.service;

import com.dongVu1105.personal_chatbot.dto.request.UserCreationRequest;
import com.dongVu1105.personal_chatbot.dto.response.UserResponse;
import com.dongVu1105.personal_chatbot.entity.User;
import com.dongVu1105.personal_chatbot.exception.AppException;
import com.dongVu1105.personal_chatbot.exception.ErrorCode;
import com.dongVu1105.personal_chatbot.mapper.UserMapper;
import com.dongVu1105.personal_chatbot.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;


@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class UserService {
    UserMapper userMapper;
    UserRepository userRepository;
    PasswordEncoder passwordEncoder;


    public UserResponse createUser(UserCreationRequest request) throws AppException {
        User user = userMapper.toUser(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        try{
            userRepository.save(user);
        } catch (DataIntegrityViolationException exception){
            throw new AppException(ErrorCode.USER_EXISTED);
        }
        return userMapper.toUserResponse(user);
    }

    public UserResponse getMyInfo() throws AppException {
        var context = SecurityContextHolder.getContext();
        String username = context.getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow(
                () -> new AppException(ErrorCode.USER_NOT_EXISTED));
        return userMapper.toUserResponse(user);
    }

}
