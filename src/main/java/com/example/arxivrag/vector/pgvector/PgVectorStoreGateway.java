package com.example.arxivrag.vector.pgvector;

import com.example.arxivrag.vector.EmbeddedDocument;
import com.example.arxivrag.vector.VectorSearchRequest;
import com.example.arxivrag.vector.VectorSearchResult;
import com.example.arxivrag.vector.VectorStoreGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.util.List;

@Component
@ConditionalOnProperty(name = "vector.store.type", havingValue = "pgvector")
public class PgVectorStoreGateway implements VectorStoreGateway {

    private final JdbcTemplate jdbcTemplate;

    public PgVectorStoreGateway(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void upsert(EmbeddedDocument document) {
        jdbcTemplate.update("""
                        insert into paper_embeddings (
                            paper_id, arxiv_id, chunk_id, content_hash, embedding_model,
                            embedding_dimension, text, categories, submitted_date, embedding
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::vector)
                        on conflict (paper_id, chunk_id, embedding_model) do update set
                            arxiv_id = excluded.arxiv_id,
                            content_hash = excluded.content_hash,
                            embedding_dimension = excluded.embedding_dimension,
                            text = excluded.text,
                            categories = excluded.categories,
                            submitted_date = excluded.submitted_date,
                            embedding = excluded.embedding,
                            embedded_at = current_timestamp
                        """,
                document.paperId(),
                document.arxivId(),
                document.chunkId(),
                document.contentHash(),
                document.embeddingModel(),
                document.embeddingDimension(),
                document.text(),
                document.categories().toArray(String[]::new),
                document.submittedDate() == null ? null : Date.valueOf(document.submittedDate()),
                vectorLiteral(document.embedding())
        );
    }

    @Override
    public List<VectorSearchResult> search(VectorSearchRequest request) {
        return jdbcTemplate.query("""
                        select paper_id, arxiv_id, chunk_id, content_hash, embedding_model,
                               1 - (embedding <=> ?::vector) as score, text
                        from paper_embeddings
                        where embedding_model = ?
                        order by embedding <=> ?::vector
                        limit ?
                        """,
                (resultSet, rowNumber) -> new VectorSearchResult(
                        resultSet.getLong("paper_id"),
                        resultSet.getString("arxiv_id"),
                        resultSet.getString("chunk_id"),
                        resultSet.getString("content_hash"),
                        resultSet.getString("embedding_model"),
                        resultSet.getDouble("score"),
                        resultSet.getString("text")
                ),
                vectorLiteral(request.embedding()),
                request.embeddingModel(),
                vectorLiteral(request.embedding()),
                request.topK()
        );
    }

    @Override
    public long countByEmbeddingModel(String embeddingModel) {
        var count = jdbcTemplate.queryForObject(
                "select count(*) from paper_embeddings where embedding_model = ?",
                Long.class,
                embeddingModel
        );
        return count == null ? 0L : count;
    }

    private static String vectorLiteral(List<Double> values) {
        return values.stream()
                .map(PgVectorStoreGateway::formatValue)
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static String formatValue(Double value) {
        if (value == null || !Double.isFinite(value)) {
            throw new IllegalArgumentException("Vector values must be finite numbers");
        }
        return Double.toString(value);
    }
}
