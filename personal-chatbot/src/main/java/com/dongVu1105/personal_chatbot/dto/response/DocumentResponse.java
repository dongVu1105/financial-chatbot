package com.dongVu1105.personal_chatbot.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentResponse {
    String id;
    String filename;
    String contentType;
    Long fileSize;
    Integer chunkCount;
    LocalDateTime uploadDate;
    String status;
    String userId;
}
