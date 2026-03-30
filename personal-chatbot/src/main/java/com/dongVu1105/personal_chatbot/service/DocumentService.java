package com.dongVu1105.personal_chatbot.service;


import com.dongVu1105.personal_chatbot.entity.Document;
import com.dongVu1105.personal_chatbot.repository.DocumentRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class DocumentService {

    DocumentRepository documentRepository;
    VectorStore vectorStore;

    private static final String UPLOAD_DIR = "uploads/documents";
    private static final int CHUNK_SIZE = 800;
    private static final int CHUNK_OVERLAP = 200;

    /**
     * Main function: Load data from PDF source, chunk, embed, and save to Vector DB
     *
     * This function performs the complete RAG ingestion pipeline:
     * 1. Upload and save PDF file
     * 2. Extract text from PDF
     * 3. Split text into chunks
     * 4. Generate embeddings for each chunk
     * 5. Store embeddings in vector database
     *
     * @param file The PDF file to process
     * @param userId The ID of the user uploading the document
     * @return Document entity with metadata
     */
    public Document ingestPdfDocument(MultipartFile file, String userId) throws IOException {
        log.info("Starting PDF ingestion for file: {} by user: {}", file.getOriginalFilename(), userId);

        validatePdfFile(file);

        String filename = file.getOriginalFilename();
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String uniqueFilename = UUID.randomUUID() + "_" + filename;
        Path filePath = uploadPath.resolve(uniqueFilename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        Document document = Document.builder()
                .filename(filename)
                .filePath(filePath.toString())
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .userId(userId)
                .status(Document.DocumentStatus.PROCESSING)
                .uploadDate(LocalDateTime.now())
                .build();

        document = documentRepository.save(document);

        try {

            processAndStoreDocument(filePath, document.getId());

            document.setStatus(Document.DocumentStatus.COMPLETED);
            document = documentRepository.save(document);

            log.info("Successfully ingested document: {} with {} chunks", filename, document.getChunkCount());

        } catch (Exception e) {
            log.error("Error processing document: {}", filename, e);
            document.setStatus(Document.DocumentStatus.FAILED);
            documentRepository.save(document);
            throw new RuntimeException("Failed to process document: " + e.getMessage(), e);
        }

        return document;
    }

    /**
     * Core RAG processing function: Chunk, Embed, and Save to Vector DB
     *
     * @param filePath Path to the PDF file
     * @param documentId ID of the document metadata record
     */
    private void processAndStoreDocument(Path filePath, String documentId) throws IOException {
        log.info("Processing document at path: {}", filePath);

        // Step 1: Load PDF and extract text
        org.springframework.core.io.Resource fileResource = new org.springframework.core.io.FileSystemResource(filePath);
        DocumentReader pdfReader = new PagePdfDocumentReader(fileResource);
        List<org.springframework.ai.document.Document> documents = pdfReader.get();

        log.info("Extracted {} pages from PDF", documents.size());

        // Step 2: Split documents into chunks
        // TokenTextSplitter intelligently splits text while preserving context
        TokenTextSplitter textSplitter = new TokenTextSplitter(CHUNK_SIZE, CHUNK_OVERLAP, 5, 10000, true);
        List<org.springframework.ai.document.Document> chunks = textSplitter.apply(documents);

        log.info("Split document into {} chunks", chunks.size());

        // Add document ID to metadata for each chunk
        chunks.forEach(chunk -> {
            chunk.getMetadata().put("document_id", documentId);
            chunk.getMetadata().put("source", filePath.getFileName().toString());
        });

        // Step 3: Generate embeddings and store in vector database
        // The VectorStore automatically:
        // - Generates embeddings using the configured embedding model (OpenAI)
        // - Stores the embeddings in pgvector
        vectorStore.add(chunks);

        log.info("Successfully stored {} chunks in vector database", chunks.size());

        // Update chunk count in document metadata
        Document document = documentRepository.findById(documentId).orElseThrow();
        document.setChunkCount(chunks.size());
        documentRepository.save(document);
    }

    /**
     * Validate that the uploaded file is a valid PDF
     */
    private void validatePdfFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            throw new IllegalArgumentException("File must be a PDF");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("File must have .pdf extension");
        }
    }

    /**
     * Get all documents for a user
     */
    public List<Document> getUserDocuments(String userId) {
        return documentRepository.findByUserId(userId);
    }

    /**
     * Get document by ID
     */
    public Document getDocument(String documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));
    }

    /**
     * Delete document and its vectors from the database
     */
    public void deleteDocument(String documentId) {
        Document document = getDocument(documentId);

        // Delete file from disk
        try {
            Path filePath = Paths.get(document.getFilePath());
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("Could not delete file: {}", document.getFilePath(), e);
        }

        // Note: Vector deletion requires manual implementation based on document_id metadata
        // This depends on the VectorStore implementation

        // Delete document record
        documentRepository.delete(document);
        log.info("Deleted document: {}", documentId);
    }
}
