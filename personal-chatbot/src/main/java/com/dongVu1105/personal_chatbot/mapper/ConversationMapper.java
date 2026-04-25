package com.dongVu1105.personal_chatbot.mapper;

import com.team14.chatbot.dto.request.ConversationRequest;
import com.team14.chatbot.dto.response.ConversationResponse;
import com.team14.chatbot.entity.Conversation;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ConversationMapper {
    Conversation toConversation (ConversationRequest request);
    ConversationResponse toConversationResponse (Conversation conversation);
}
