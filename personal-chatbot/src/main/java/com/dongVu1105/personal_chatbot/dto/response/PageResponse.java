package com.dongVu1105.personal_chatbot.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Collections;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PageResponse<T> {
    int currentPage;
    int totalPages;
    int pageSize;
    long totalElements;
    boolean hasNextPage;
    boolean hasPreviousPage;

    @Builder.Default
    private List<T> result = Collections.emptyList();
}
