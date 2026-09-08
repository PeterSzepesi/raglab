package com.example.arxivrag.embedding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmbeddingCheckpointRepository extends JpaRepository<EmbeddingCheckpoint, EmbeddingCheckpointId> {

    long countByIdEmbeddingModel(String embeddingModel);

    @Query("select count(distinct checkpoint.id.paperId) from EmbeddingCheckpoint checkpoint where checkpoint.id.embeddingModel = :embeddingModel")
    long countDistinctPaperIdsByEmbeddingModel(@Param("embeddingModel") String embeddingModel);
}
