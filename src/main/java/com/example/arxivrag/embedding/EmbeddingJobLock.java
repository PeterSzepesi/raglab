package com.example.arxivrag.embedding;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/** Prevents duplicate remote embedding work in a local or single-node deployment. */
@Component
public class EmbeddingJobLock {

    private static final String LOCK_NAME = "arxiv-rag.embedding-ingestion";
    private static final ReentrantLock NON_POSTGRES_LOCK = new ReentrantLock();

    private final DataSource dataSource;

    public EmbeddingJobLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Lease acquire() {
        try {
            var connection = dataSource.getConnection();
            if (isPostgres(connection)) {
                try (var statement = connection.prepareStatement("select pg_try_advisory_lock(hashtext(?))")) {
                    statement.setString(1, LOCK_NAME);
                    try (var result = statement.executeQuery()) {
                        result.next();
                        if (!result.getBoolean(1)) {
                            connection.close();
                            throw new IllegalStateException("Another embedding job is already running");
                        }
                    }
                }
                return new Lease(connection, false);
            }
            connection.close();
            if (!NON_POSTGRES_LOCK.tryLock()) {
                throw new IllegalStateException("Another embedding job is already running");
            }
            return new Lease(null, true);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not acquire embedding job lock", exception);
        }
    }

    private static boolean isPostgres(Connection connection) throws SQLException {
        return connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("postgresql");
    }

    public static final class Lease implements AutoCloseable {

        private final Connection connection;
        private final boolean jvmLock;

        private Lease(Connection connection, boolean jvmLock) {
            this.connection = connection;
            this.jvmLock = jvmLock;
        }

        @Override
        public void close() {
            if (connection != null) {
                try (var statement = connection.prepareStatement("select pg_advisory_unlock(hashtext(?))")) {
                    statement.setString(1, LOCK_NAME);
                    statement.execute();
                } catch (SQLException ignored) {
                    // Closing the session also releases a PostgreSQL advisory lock.
                } finally {
                    try {
                        connection.close();
                    } catch (SQLException ignored) {
                        // Nothing useful can be done during cleanup.
                    }
                }
            }
            if (jvmLock) {
                NON_POSTGRES_LOCK.unlock();
            }
        }
    }
}
