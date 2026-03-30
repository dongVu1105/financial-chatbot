package com.dongVu1105.personal_chatbot.controller;

import com.dongVu1105.personal_chatbot.dto.response.ApiResponse;
import com.dongVu1105.personal_chatbot.dto.response.DocumentResponse;
import com.dongVu1105.personal_chatbot.entity.Document;
import com.dongVu1105.personal_chatbot.service.DocumentService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class DocumentController {

    DocumentService documentService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentResponse> uploadDocument(@RequestParam("file") MultipartFile file) {
        try {
            log.info("Received PDF upload request: {}", file.getOriginalFilename());

            String userId = SecurityContextHolder.getContext().getAuthentication().getName();

            Document document = documentService.ingestPdfDocument(file, userId);

            DocumentResponse response = mapToResponse(document);

            return ApiResponse.<DocumentResponse>builder()
                    .message("Document uploaded and processed successfully")
                    .data(response)
                    .build();

        } catch (IllegalArgumentException e) {
            log.error("Invalid file: {}", e.getMessage());
            return ApiResponse.<DocumentResponse>builder()
                    .code(400)
                    .message("Invalid file: " + e.getMessage())
                    .build();

        } catch (IOException e) {
            log.error("Error processing file", e);
            return ApiResponse.<DocumentResponse>builder()
                    .code(500)
                    .message("Error processing file: " + e.getMessage())
                    .build();

        } catch (Exception e) {
            log.error("Unexpected error", e);
            return ApiResponse.<DocumentResponse>builder()
                    .code(500)
                    .message("Unexpected error: " + e.getMessage())
                    .build();
        }
    }

    @GetMapping("/my-documents")
    public ApiResponse<List<DocumentResponse>> getMyDocuments() {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        List<Document> documents = documentService.getUserDocuments(userId);

        List<DocumentResponse> responses = documents.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return ApiResponse.<List<DocumentResponse>>builder()
                .data(responses)
                .build();
    }

    @GetMapping("/{documentId}")
    public ApiResponse<DocumentResponse> getDocument(@PathVariable String documentId) {
        Document document = documentService.getDocument(documentId);
        DocumentResponse response = mapToResponse(document);

        return ApiResponse.<DocumentResponse>builder()
                .data(response)
                .build();
    }

    @DeleteMapping("/{documentId}")
    public ApiResponse<Void> deleteDocument(@PathVariable String documentId) {
        documentService.deleteDocument(documentId);

        return ApiResponse.<Void>builder()
                .message("Document deleted successfully")
                .build();
    }

    private DocumentResponse mapToResponse(Document document) {
        return DocumentResponse.builder()
                .id(document.getId())
                .filename(document.getFilename())
                .contentType(document.getContentType())
                .fileSize(document.getFileSize())
                .chunkCount(document.getChunkCount())
                .uploadDate(document.getUploadDate())
                .status(document.getStatus().name())
                .userId(document.getUserId())
                .build();
    }
}
