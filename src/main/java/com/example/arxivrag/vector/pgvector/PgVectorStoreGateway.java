package com.example.arxivrag.vector.pgvector;

import com.example.arxivrag.vector.EmbeddedDocument;
import com.example.arxivrag.vector.VectorSearchRequest;
import com.example.arxivrag.vector.VectorSearchResult;
import com.example.arxivrag.vector.VectorRecallDiagnostics;
import com.example.arxivrag.vector.VectorRecallOptions;
import com.example.arxivrag.vector.VectorStoreGateway;
import com.pgvector.PGvector;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        on conflict (paper_id, chunk_id, embedding_model) do update set
                            arxiv_id = excluded.arxiv_id,
                            content_hash = excluded.content_hash,
                            embedding_dimension = excluded.embedding_dimension,
                            text = excluded.text,
                            categories = excluded.categories,
                            submitted_date = excluded.submitted_date,
                            embedding = excluded.embedding,
                            embedded_at = current_timestamp
                        """, statement -> bindDocument(statement, document));
    }

    @Override
    public void upsertAll(List<EmbeddedDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate("""
                        insert into paper_embeddings (
                            paper_id, arxiv_id, chunk_id, content_hash, embedding_model,
                            embedding_dimension, text, categories, submitted_date, embedding
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                documents,
                documents.size(),
                (statement, document) -> {
                    bindDocument(statement, document);
                }
        );
    }

    @Override
    public List<VectorSearchResult> search(VectorSearchRequest request) {
        var sql = new StringBuilder("""
                with query_vector(value) as (values (?::vector)),
                nearest as (
                    select pe.paper_id, pe.arxiv_id, pe.chunk_id, pe.content_hash, pe.embedding_model,
                           1 - (pe.embedding <=> q.value) as score, pe.text
                    from paper_embeddings pe cross join query_vector q
                    where pe.embedding_model = ?
                """);
        var parameters = new java.util.ArrayList<Object>();
        parameters.add(toPgVector(request.embedding()));
        parameters.add(request.embeddingModel());
        if (request.category() != null) {
            sql.append(" and ? = any(pe.categories) ");
            parameters.add(request.category());
        }
        if (request.fromDate() != null) {
            sql.append(" and pe.submitted_date >= ? ");
            parameters.add(Date.valueOf(request.fromDate()));
        }
        if (request.toDate() != null) {
            sql.append(" and pe.submitted_date <= ? ");
            parameters.add(Date.valueOf(request.toDate()));
        }
        sql.append("""
                     order by pe.embedding <=> q.value limit ?
                )
                select n.*, p.title, p.abstract_text, p.authors as paper_authors,
                       p.categories as paper_categories, p.submitted_date as paper_submitted_date
                from nearest n join papers p on p.id = n.paper_id
                order by n.score desc
                """);
        parameters.add(request.topK());

        return jdbcTemplate.query(sql.toString(), preparedStatementSetter(parameters),
                (resultSet, rowNumber) -> new VectorSearchResult(
                        resultSet.getLong("paper_id"),
                        resultSet.getString("arxiv_id"),
                        resultSet.getString("chunk_id"),
                        resultSet.getString("content_hash"),
                        resultSet.getString("embedding_model"),
                        resultSet.getDouble("score"),
                        resultSet.getString("text"),
                        resultSet.getString("title"),
                        resultSet.getString("abstract_text"),
                        stringList(resultSet.getArray("paper_authors")),
                        stringList(resultSet.getArray("paper_categories")),
                        localDate(resultSet.getDate("paper_submitted_date"))
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public VectorRecallDiagnostics compareRecall(VectorSearchRequest request, VectorRecallOptions options) {
        applyApproximateSettings(options);
        var approximateStarted = System.nanoTime();
        var approximate = search(request);
        var approximateMilliseconds = elapsedMilliseconds(approximateStarted);

        // HNSW is an index scan. Disabling index scans for the remainder of this transaction
        // forces PostgreSQL to calculate the exact cosine ordering without changing global state.
        jdbcTemplate.execute("set local enable_indexscan = off");
        var exactStarted = System.nanoTime();
        var exact = search(request);
        var exactMilliseconds = elapsedMilliseconds(exactStarted);

        return VectorRecallDiagnostics.compare(request, options, approximate, exact,
                approximateMilliseconds, exactMilliseconds);
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

    @Override
    public List<String> explain(VectorSearchRequest request) {
        var sql = new StringBuilder("""
                explain (analyze, buffers, format text)
                select paper_id, arxiv_id, chunk_id, content_hash, embedding_model,
                       1 - (embedding <=> ?::vector) as score, text
                from paper_embeddings
                where embedding_model = ?
                """);
        var parameters = new java.util.ArrayList<Object>();
        parameters.add(toPgVector(request.embedding()));
        parameters.add(request.embeddingModel());
        if (request.category() != null) {
            sql.append(" and ? = any(categories) ");
            parameters.add(request.category());
        }
        if (request.fromDate() != null) {
            sql.append(" and submitted_date >= ? ");
            parameters.add(Date.valueOf(request.fromDate()));
        }
        if (request.toDate() != null) {
            sql.append(" and submitted_date <= ? ");
            parameters.add(Date.valueOf(request.toDate()));
        }
        sql.append(" order by embedding <=> ?::vector limit ?");
        parameters.add(toPgVector(request.embedding()));
        parameters.add(request.topK());
        return jdbcTemplate.query(sql.toString(), preparedStatementSetter(parameters),
                (resultSet, rowNumber) -> resultSet.getString(1));
    }

    @Override
    public void analyze() {
        jdbcTemplate.execute("analyze papers");
        jdbcTemplate.execute("analyze paper_embeddings");
    }

    private static void bindDocument(PreparedStatement statement, EmbeddedDocument document) throws SQLException {
        statement.setObject(1, document.paperId());
        statement.setString(2, document.arxivId());
        statement.setString(3, document.chunkId());
        statement.setString(4, document.contentHash());
        statement.setString(5, document.embeddingModel());
        statement.setInt(6, document.embeddingDimension());
        statement.setString(7, document.text());
        statement.setArray(8, statement.getConnection().createArrayOf("varchar", document.categories().toArray()));
        if (document.submittedDate() == null) {
            statement.setNull(9, Types.DATE);
        } else {
            statement.setDate(9, Date.valueOf(document.submittedDate()));
        }
        statement.setObject(10, toPgVector(document.embedding()));
    }

    private void applyApproximateSettings(VectorRecallOptions options) {
        if (options.hnswEfSearch() != null) {
            jdbcTemplate.execute("set local hnsw.ef_search = " + options.hnswEfSearch());
        }
        switch (options.iterativeScan()) {
            case DEFAULT -> { }
            case OFF -> jdbcTemplate.execute("set local hnsw.iterative_scan = off");
            case STRICT_ORDER -> jdbcTemplate.execute("set local hnsw.iterative_scan = strict_order");
            case RELAXED_ORDER -> jdbcTemplate.execute("set local hnsw.iterative_scan = relaxed_order");
        }
    }

    private static long elapsedMilliseconds(long startedNanos) {
        return java.time.Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    private static org.springframework.jdbc.core.PreparedStatementSetter preparedStatementSetter(List<Object> values) {
        return statement -> {
            for (var index = 0; index < values.size(); index++) {
                statement.setObject(index + 1, values.get(index));
            }
        };
    }

    private static PGvector toPgVector(List<Double> values) {
        var vector = new float[values.size()];
        for (var index = 0; index < values.size(); index++) {
            var value = values.get(index);
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("Vector values must be finite numbers");
            }
            vector[index] = value.floatValue();
        }
        return new PGvector(vector);
    }

    private static List<String> stringList(java.sql.Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        return List.copyOf(Arrays.asList((String[]) array.getArray()));
    }

    private static LocalDate localDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }
}
