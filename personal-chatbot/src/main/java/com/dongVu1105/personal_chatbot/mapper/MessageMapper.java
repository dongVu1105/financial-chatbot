package com.dongVu1105.personal_chatbot.mapper;

import com.team14.chatbot.dto.request.MessageRequest;
import com.team14.chatbot.dto.response.MessageResponse;
import com.team14.chatbot.entity.Message;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MessageMapper {
    Message toMessage (MessageRequest request);
    MessageResponse toMessageResponse (Message message);
}
