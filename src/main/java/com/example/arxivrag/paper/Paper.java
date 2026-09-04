package com.example.arxivrag.paper;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "papers")
public class Paper {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "arxiv_id", nullable = false, unique = true, length = 64)
    private String arxivId;

    @Column(nullable = false)
    private String title;

    @Column(name = "abstract_text", nullable = false)
    private String abstractText;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "varchar(1024) array")
    private List<String> authors = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "varchar(50) array")
    private List<String> categories = new ArrayList<>();

    @Column(name = "submitted_date")
    private LocalDate submittedDate;

    @Column(name = "updated_date")
    private LocalDate updatedDate;

    @Column(length = 255)
    private String doi;

    @Column(name = "journal_ref")
    private String journalRef;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Paper() {
    }

    public Paper(
            String arxivId,
            String title,
            String abstractText,
            List<String> authors,
            List<String> categories,
            LocalDate submittedDate,
            LocalDate updatedDate,
            String doi,
            String journalRef
    ) {
        this.arxivId = arxivId;
        this.title = title;
        this.abstractText = abstractText;
        setAuthors(authors);
        setCategories(categories);
        this.submittedDate = submittedDate;
        this.updatedDate = updatedDate;
        this.doi = doi;
        this.journalRef = journalRef;
    }

    public Long getId() {
        return id;
    }

    public String getArxivId() {
        return arxivId;
    }

    public void setArxivId(String arxivId) {
        this.arxivId = arxivId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAbstractText() {
        return abstractText;
    }

    public void setAbstractText(String abstractText) {
        this.abstractText = abstractText;
    }

    public List<String> getAuthors() {
        return List.copyOf(authors);
    }

    public void setAuthors(List<String> authors) {
        this.authors = new ArrayList<>(authors);
    }

    public List<String> getCategories() {
        return List.copyOf(categories);
    }

    public void setCategories(List<String> categories) {
        this.categories = new ArrayList<>(categories);
    }

    public LocalDate getSubmittedDate() {
        return submittedDate;
    }

    public void setSubmittedDate(LocalDate submittedDate) {
        this.submittedDate = submittedDate;
    }

    public LocalDate getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(LocalDate updatedDate) {
        this.updatedDate = updatedDate;
    }

    public String getDoi() {
        return doi;
    }

    public void setDoi(String doi) {
        this.doi = doi;
    }

    public String getJournalRef() {
        return journalRef;
    }

    public void setJournalRef(String journalRef) {
        this.journalRef = journalRef;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
