package com.dongVu1105.personal_chatbot.service.RagModules.retriever;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class Bm25IndexService {

    private final JdbcTemplate jdbcTemplate;
    private Directory indexDirectory;
    private Analyzer analyzer;
    private IndexSearcher indexSearcher;
    private IndexReader indexReader;
    private final Object indexLock = new Object();

    public Bm25IndexService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initializeIndex() {
        try {
            // Use MMapDirectory for better performance (memory-mapped files)
            // For in-memory, we can use a temporary directory
            java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("lucene-index");
            this.indexDirectory = new MMapDirectory(tempDir);
            this.analyzer = new StandardAnalyzer();
            rebuildIndex();
            log.info("BM25 index initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize BM25 index", e);
            throw new RuntimeException("Failed to initialize BM25 index", e);
        }
    }

    /**
     * Rebuild the entire index from kb_embeddings table
     */
    @Scheduled(fixedDelay = 300000) // Rebuild every 5 minutes
    public void rebuildIndex() {
        synchronized (indexLock) {
            try {
                log.info("Rebuilding BM25 index...");

                // Close existing reader if any
                if (indexReader != null) {
                    indexReader.close();
                }

                // Create new index
                IndexWriterConfig config = new IndexWriterConfig(analyzer);
                config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

                try (IndexWriter writer = new IndexWriter(indexDirectory, config)) {
                    // Query all documents from kb_embeddings
                    String sql = "SELECT\n" +
                            "\n" +
                            "    e.id,\n" +
                            "\n" +
                            "    e.document as content,\n" +
                            "\n" +
                            "    e.cmetadata as metadata,\n" +
                            "\n" +
                            "    c.name as doc_type  FROM langchain_pg_embedding e JOIN langchain_pg_collection c ON e.collection_id = c.uuid";
                    List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

                    for (Map<String, Object> row : rows) {
                        org.apache.lucene.document.Document doc = new org.apache.lucene.document.Document();
                        doc.add(new StringField("id", row.get("id").toString(), Field.Store.YES));
                        doc.add(new TextField("content", row.get("content").toString(), Field.Store.YES));
                        doc.add(new StringField("doc_type", row.get("doc_type").toString(), Field.Store.NO));
                        // Store metadata as JSON string for later retrieval
                        if (row.get("metadata") != null) {
                            doc.add(new StringField("metadata", row.get("metadata").toString(), Field.Store.YES));
                        }

                        writer.addDocument(doc);
                    }

                    writer.commit();
                    log.info("Indexed {} documents", rows.size());
                }

                // Create new searcher
                indexReader = DirectoryReader.open(indexDirectory);
                indexSearcher = new IndexSearcher(indexReader);

                log.info("BM25 index rebuilt successfully with {} documents", indexReader.numDocs());
            } catch (Exception e) {
                log.error("Failed to rebuild BM25 index", e);
                throw new RuntimeException("Failed to rebuild BM25 index", e);
            }
        }
    }

    /**
     * Add a single document to the index
     */
    public void addDocument(String id, String content, String metadata) {
        synchronized (indexLock) {
            try {
                IndexWriterConfig config = new IndexWriterConfig(analyzer);
                config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

                try (IndexWriter writer = new IndexWriter(indexDirectory, config)) {
                    org.apache.lucene.document.Document doc = new org.apache.lucene.document.Document();
                    doc.add(new StringField("id", id, Field.Store.YES));
                    doc.add(new TextField("content", content, Field.Store.YES));
                    if (metadata != null) {
                        doc.add(new StringField("metadata", metadata, Field.Store.YES));
                    }
                    writer.addDocument(doc);
                    writer.commit();
                }

                // Refresh searcher
                if (indexReader != null) {
                    indexReader.close();
                }
                indexReader = DirectoryReader.open(indexDirectory);
                indexSearcher = new IndexSearcher(indexReader);

                log.debug("Added document {} to BM25 index", id);
            } catch (Exception e) {
                log.error("Failed to add document to BM25 index", e);
            }
        }
    }

    /**
     * Search using BM25 algorithm
     * 
     * @param queryText The search query
     * @param topK      Number of results to return
     * @return List of Spring AI Documents with scores
     */
    public List<org.springframework.ai.document.Document> search(String queryText, RetrievalType retrievalType,
            int topK) {
        synchronized (indexLock) {
            if (indexSearcher == null) {
                log.warn("BM25 index not initialized, returning empty results");
                return Collections.emptyList();
            }

            try {
                String docTypeFilter = "";
                switch (retrievalType) {
                    case KNOWLEDGE_RETRIEVE:
                        docTypeFilter = "gemini_knowledge_base";
                        break;
                    case CASE_STUDIES_RETRIEVE:
                        docTypeFilter = "advisory_case_studies";
                        break;
                    default:
                        break;
                }
                QueryParser parser = new QueryParser("content", analyzer);
                Query contentQuery = parser.parse(QueryParser.escape(queryText));
                Query typeQuery = new TermQuery(new org.apache.lucene.index.Term("doc_type", docTypeFilter));
                BooleanQuery.Builder booleanQueryBuilder = new BooleanQuery.Builder();
                booleanQueryBuilder.add(contentQuery, org.apache.lucene.search.BooleanClause.Occur.MUST);   // Phải khớp nội dung
                booleanQueryBuilder.add(typeQuery, org.apache.lucene.search.BooleanClause.Occur.FILTER);    // VÀ phải đúng loại này

                Query finalQuery = booleanQueryBuilder.build();

                TopDocs topDocs = indexSearcher.search(finalQuery, topK);
                ScoreDoc[] hits = topDocs.scoreDocs;

                List<org.springframework.ai.document.Document> results = new ArrayList<>();
                for (ScoreDoc hit : hits) {
                    org.apache.lucene.document.Document luceneDoc = indexSearcher.storedFields().document(hit.doc);
                    String content = luceneDoc.get("content");
                    String id = luceneDoc.get("id");
                    String metadataStr = luceneDoc.get("metadata");

                    // Convert to Spring AI Document
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("id", id);
                    if (metadataStr != null && !metadataStr.isEmpty()) {
                        // Parse JSONB metadata if needed
                        metadata.put("bm25_score", hit.score);
                    }

                    org.springframework.ai.document.Document springDoc = new org.springframework.ai.document.Document(
                            content, metadata);
                    results.add(springDoc);
                }

                log.debug("BM25 search returned {} results for query: {}", results.size(), queryText);
                return results;
            } catch (ParseException | IOException e) {
                log.error("Error during BM25 search", e);
                return Collections.emptyList();
            }
        }
    }

    /**
     * Get document by ID from index
     */
    public Optional<org.springframework.ai.document.Document> getDocumentById(String id) {
        synchronized (indexLock) {
            if (indexSearcher == null) {
                return Optional.empty();
            }

            try {
                QueryParser parser = new QueryParser("id", analyzer);
                Query query = parser.parse(QueryParser.escape(id));

                TopDocs topDocs = indexSearcher.search(query, 1);
                if (topDocs.totalHits.value > 0) {
                    org.apache.lucene.document.Document luceneDoc = indexSearcher.storedFields()
                            .document(topDocs.scoreDocs[0].doc);
                    String content = luceneDoc.get("content");
                    String metadataStr = luceneDoc.get("metadata");

                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("id", id);
                    if (metadataStr != null) {
                        metadata.put("metadata", metadataStr);
                    }

                    return Optional.of(new org.springframework.ai.document.Document(content, metadata));
                }
            } catch (Exception e) {
                log.error("Error retrieving document by ID from BM25 index", e);
            }
            return Optional.empty();
        }
    }
}
