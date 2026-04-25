package com.dongVu1105.personal_chatbot.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GenerateTitleRequest {
    String conversationId;
    String userMessage;
}
