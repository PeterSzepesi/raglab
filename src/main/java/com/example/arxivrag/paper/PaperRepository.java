package com.example.arxivrag.paper;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaperRepository extends JpaRepository<Paper, Long> {

    Optional<Paper> findByArxivId(String arxivId);

    List<Paper> findAllByArxivIdIn(Collection<String> arxivIds);

    List<Paper> findBySubmittedDateGreaterThanEqual(LocalDate submittedDate);

    @Query(value = "select * from papers where :category = any(categories)", nativeQuery = true)
    List<Paper> findByCategory(@Param("category") String category);

    @Query(value = """
            select * from papers
            where (:query is null
                or lower(title) like lower(concat('%', :query, '%'))
                or lower(abstract_text) like lower(concat('%', :query, '%')))
              and (:category is null or :category = any(categories))
              and (:fromDate is null or submitted_date >= :fromDate)
              and (:toDate is null or submitted_date <= :toDate)
            order by submitted_date desc nulls last, id
            limit :limit
            """, nativeQuery = true)
    List<Paper> searchRelational(
            @Param("query") String query,
            @Param("category") String category,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("limit") int limit
    );

    @Query(value = """
            select * from papers
            where search_vector @@ websearch_to_tsquery('english', :query)
              and (:category is null or :category = any(categories))
              and (:fromDate is null or submitted_date >= :fromDate)
              and (:toDate is null or submitted_date <= :toDate)
            order by ts_rank_cd(search_vector, websearch_to_tsquery('english', :query)) desc,
                     submitted_date desc nulls last, id
            limit :limit
            """, nativeQuery = true)
    List<Paper> searchLexical(
            @Param("query") String query,
            @Param("category") String category,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("limit") int limit
    );

    @Query(value = """
            select * from papers
            where (:afterId is null or id > :afterId)
              and (:fromId is null or id >= :fromId)
              and (:toId is null or id <= :toId)
              and (:category is null or :category = any(categories))
              and (:fromDate is null or submitted_date >= :fromDate)
              and (:toDate is null or submitted_date <= :toDate)
            order by id
            """, nativeQuery = true)
    List<Paper> findEmbeddingPage(
            @Param("afterId") Long afterId,
            @Param("fromId") Long fromId,
            @Param("toId") Long toId,
            @Param("category") String category,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );

    @Query(value = """
            select count(*) from papers
            where (:fromId is null or id >= :fromId)
              and (:toId is null or id <= :toId)
              and (:category is null or :category = any(categories))
              and (:fromDate is null or submitted_date >= :fromDate)
              and (:toDate is null or submitted_date <= :toDate)
            """, nativeQuery = true)
    long countEmbeddingCandidates(
            @Param("fromId") Long fromId,
            @Param("toId") Long toId,
            @Param("category") String category,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );
}
