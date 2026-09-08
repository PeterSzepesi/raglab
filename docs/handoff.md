### Handoff Summary: RAGLab Project

#### Current Status
The project has completed the foundational infrastructure for the arXiv RAG application. We have implemented dataset ingestion, document chunking/formatting, and an idempotent embedding ingestion pipeline with checkpointing.

#### Key Achievements
- **Dataset Import**: Streaming arXiv JSONL reader with filtering and mapping to PostgreSQL (`Paper` entity).
- **Retrieval Preparation**: `RetrievalDocumentFactory` produces deterministic text chunks and SHA-256 content hashes.
- **Embedding Ingestion**:
    - `EmbeddingIngestionService` handles batch processing.
    - `EmbeddingCheckpoint` tracks which papers+models have been embedded to avoid redundant work.
    - `OfflineDeterministicEmbeddingModel` provides a stable 1536-dim vector for testing without API costs.
- **CLI Tools**:
    - `import-arxiv <file>`: Imports papers from JSONL.
    - `embed-papers`: Generates and checkpoints embeddings for all imported papers.
- **UI Architecture**:
    - Single Page Application (vanilla JS) with routing.
    - **System Status**: Backend health and Spring profile visibility.
    - **Search (Preview)**: Real-time preview of retrieval document formatting and hashing.
    - **Vector Stores (Status)**: Dashboard for embedding coverage and model configuration.

#### Next Steps (from `implementation_steps.txt`)
1. **Step 6 — Add Vector Gateway**:
    - Define `VectorStoreGateway` interface.
    - Implement `PgVectorStoreAdapter` using `pgvector`.
    - Enable semantic search in the `Search` page.
2. **Step 7 — Add RAG Prompting**:
    - Implement `ChatService` and `GenerationGateway`.
    - Ground generation using retrieved documents.
3. **Step 8 — Add Advanced Search**:
    - Hybrid search (BM25 + Vector).
    - Metadata filtering.

#### Technical Notes
- **Database**: PostgreSQL with `pgvector` extension (configured in `docker-compose.yml`).
- **Profiles**: 
    - `default`: Standard dev settings.
    - `test`: H2 (for unit tests) or test containers.
- **Endpoints**:
    - `GET /api/health`: Health status.
    - `GET /api/config/runtime`: Active profiles and feature flags.
    - `POST /api/retrieval-documents/preview`: Preview chunking/hashing logic.
    - `GET /api/embedding/status`: Checkpointing progress.

#### Working Commands
```powershell
# Build and test
.\mvnw.cmd clean test

# Import dataset
java -jar target/arxiv-rag-0.0.1-SNAPSHOT.jar import-arxiv docs/fixtures/arxiv-tiny.jsonl

# Run embedding ingestion
java -jar target/arxiv-rag-0.0.1-SNAPSHOT.jar embed-papers
```
