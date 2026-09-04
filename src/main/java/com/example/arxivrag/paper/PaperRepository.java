package com.example.arxivrag.paper;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
