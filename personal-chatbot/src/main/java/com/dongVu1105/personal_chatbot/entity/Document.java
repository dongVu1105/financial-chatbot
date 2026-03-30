package com.dongVu1105.personal_chatbot.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "documents")
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(name = "filename", nullable = false)
    String filename;

    @Column(name = "file_path")
    String filePath;

    @Column(name = "content_type")
    String contentType;

    @Column(name = "file_size")
    Long fileSize;

    @Column(name = "chunk_count")
    Integer chunkCount;

    @Column(name = "upload_date", nullable = false)
    LocalDateTime uploadDate;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    DocumentStatus status;

    @Column(name = "user_id")
    String userId;

    @PrePersist
    protected void onCreate() {
        uploadDate = LocalDateTime.now();
        if (status == null) {
            status = DocumentStatus.PENDING;
        }
    }

    public enum DocumentStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}
