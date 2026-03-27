package com.dongVu1105.personal_chatbot.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse <T>{
    @Builder.Default
    long code = 1000;
    @Builder.Default
    String message = "Success!";
    T data;
}
