package com.dongVu1105.personal_chatbot.mapper;


import com.dongVu1105.personal_chatbot.dto.request.UserCreationRequest;
import com.dongVu1105.personal_chatbot.dto.response.UserResponse;
import com.dongVu1105.personal_chatbot.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface UserMapper {
    public User toUser (UserCreationRequest userCreationRequest);
    public UserResponse toUserResponse (User user);
}
