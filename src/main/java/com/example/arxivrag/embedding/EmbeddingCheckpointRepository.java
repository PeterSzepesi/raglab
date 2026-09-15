package com.example.arxivrag.embedding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmbeddingCheckpointRepository extends JpaRepository<EmbeddingCheckpoint, EmbeddingCheckpointId> {

    long countByIdEmbeddingModel(String embeddingModel);

    @Query("select count(distinct checkpoint.id.paperId) from EmbeddingCheckpoint checkpoint where checkpoint.id.embeddingModel = :embeddingModel")
    long countDistinctPaperIdsByEmbeddingModel(@Param("embeddingModel") String embeddingModel);

    @Query("select distinct checkpoint.id.paperId from EmbeddingCheckpoint checkpoint "
            + "where checkpoint.id.embeddingModel = :embeddingModel and checkpoint.id.paperId in :paperIds")
    java.util.List<Long> findEmbeddedPaperIds(@Param("embeddingModel") String embeddingModel,
                                               @Param("paperIds") java.util.Collection<Long> paperIds);

    @Query("select checkpoint from EmbeddingCheckpoint checkpoint "
            + "where checkpoint.id.embeddingModel = :embeddingModel and checkpoint.id.paperId in :paperIds")
    java.util.List<EmbeddingCheckpoint> findByPaperIdsAndModel(
            @Param("embeddingModel") String embeddingModel,
            @Param("paperIds") java.util.Collection<Long> paperIds);
}
