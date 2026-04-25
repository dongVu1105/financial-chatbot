package com.dongVu1105.personal_chatbot.dto.request;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;


@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatRequest {
    String question;
}
