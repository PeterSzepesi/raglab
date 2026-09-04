# Dataset And Ingestion Design

The source dataset is the arXiv metadata JSON Lines file from Kaggle/Cornell. It contains paper metadata such as arXiv ID, title, abstract, authors, categories, version history, update date, DOI, and journal reference.

This branch has not implemented ingestion yet. The design below is the contract for that work.

## Dataset Slice

Start with a deterministic subset so developers can compare results across separate implementations.

Initial category allow-list:

- `cs.CL`
- `cs.AI`
- `cs.LG`
- `cs.CV`

Selection rule:
Include a paper if any arXiv category matches the allow-list.

Initial date rule:
Use the first version submission date when available. Fall back to `update_date` only when version metadata is absent.

Initial lower bound:
`submitted_date >= 2022-01-01`

Profiles:

- `tiny`: 500 matching papers
- `dev`: 50,000 matching papers
- `scale`: no matching-paper cap

The `tiny` profile is important because every implementation must be demonstrable without downloading or processing the full corpus.

## Proposed Configuration

```yaml
arxiv:
  import:
    categories:
      - cs.CL
      - cs.AI
      - cs.LG
      - cs.CV
    from-date: 2022-01-01
    max-records: 50000
    batch-size: 1000
    malformed-record-policy: warn-and-skip
```

Keep these values configurable. Do not hardcode category lists, limits, or file paths in Java code.

## Canonical Paper Schema

Recommended first schema:

```text
papers
------
id              BIGSERIAL primary key
arxiv_id        VARCHAR(64) unique not null
title           TEXT not null
abstract_text   TEXT not null
authors         TEXT[] not null
categories      VARCHAR(50)[] not null
submitted_date  DATE
updated_date    DATE
doi             VARCHAR(255)
journal_ref     TEXT
created_at      TIMESTAMPTZ not null
updated_at      TIMESTAMPTZ not null
```

Recommended indexes:

- unique index on `arxiv_id`
- B-tree index on `submitted_date`
- GIN index on `categories`
- optional GIN/trigram or normalized author table later if author queries become central

Arrays are acceptable for v1 because the project goal is RAG/vector-store comparison, not relational normalization. If author-level analytics become important, split authors into `authors` and `paper_authors` in a later migration.

## Import Components

### `ArxivDatasetReader`

Responsibilities:
- stream JSONL records line by line
- deserialize into an `ArxivRecord`
- track line numbers
- skip malformed records when configured to do so
- expose counts for read and invalid records

Standalone tests:
- reads a three-line fixture
- skips one malformed line and continues
- does not require Spring context or database

### `ArxivRecordFilter`

Responsibilities:
- evaluate category membership
- evaluate submitted-date lower bound
- apply max matching-record cap outside the filter, not inside it

Standalone tests:
- accepts records with one matching category
- rejects records with no matching category
- accepts boundary date `2022-01-01`
- rejects dates before the lower bound

### `PaperMapper`

Responsibilities:
- normalize whitespace in title and abstract
- parse authors and categories into lists/arrays
- preserve missing DOI and journal reference as `null`
- map source ID to `arxiv_id`

Standalone tests:
- normalizes multiline abstracts
- preserves optional nulls
- parses category strings correctly
- maps version date consistently

### `ArxivImportService`

Responsibilities:
- batch accepted records
- upsert by `arxiv_id`
- keep import idempotent
- report imported, updated, skipped, invalid, and elapsed counts

Standalone tests:
- imports a tiny fixture into PostgreSQL
- re-imports the same fixture without duplicate rows
- updates an existing paper when source metadata changes
- commits successful batches independently when that policy is selected

## CLI Contract

Recommended command:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=import-arxiv C:\data\arxiv-metadata-oai-snapshot.json"
```

Expected output:

```text
arXiv import complete
Records read:        <n>
Accepted:            <n>
Imported:            <n>
Updated:             <n>
Skipped:             <n>
Invalid records:     <n>
Elapsed:             <duration>
```

The CLI command should exit with status `0` on success and non-zero on invalid arguments, unreadable files, or unrecoverable database failures.

## Acceptance Criteria

- `tiny` fixture import completes in under a few seconds on a normal developer machine.
- Running the same import twice creates no duplicate `papers`.
- All schema changes are Flyway migrations.
- No importer path requires loading the full dataset into memory.
- Unit tests cover reader/filter/mapper logic.
- Integration tests cover PostgreSQL schema and idempotent upsert behavior.
