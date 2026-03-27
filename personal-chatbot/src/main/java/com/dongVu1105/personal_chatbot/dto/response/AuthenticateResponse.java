package com.dongVu1105.personal_chatbot.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthenticateResponse {
    boolean valid;
    String accessToken;
    String refreshToken;
}
