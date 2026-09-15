package com.example.arxivrag.search;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Tunable ranking policy. Values are versioned through chat.retrieval-policy-version. */
@ConfigurationProperties("search.retrieval")
@Validated
public class SearchRetrievalProperties {

    private double hybridVectorWeight = 0.7d;
    private double hybridLexicalWeight = 0.3d;
    private double semanticMinimumSimilarity;
    private double hybridMinimumScore;
    private double duplicateTitleJaccardThreshold = 0.8d;

    public double getHybridVectorWeight() {
        return hybridVectorWeight;
    }

    public void setHybridVectorWeight(double value) {
        this.hybridVectorWeight = value;
    }

    public double getHybridLexicalWeight() {
        return hybridLexicalWeight;
    }

    public void setHybridLexicalWeight(double value) {
        this.hybridLexicalWeight = value;
    }

    public double getSemanticMinimumSimilarity() {
        return semanticMinimumSimilarity;
    }

    public void setSemanticMinimumSimilarity(double value) {
        this.semanticMinimumSimilarity = value;
    }

    public double getHybridMinimumScore() {
        return hybridMinimumScore;
    }

    public void setHybridMinimumScore(double value) {
        this.hybridMinimumScore = value;
    }

    public double getDuplicateTitleJaccardThreshold() {
        return duplicateTitleJaccardThreshold;
    }

    public void setDuplicateTitleJaccardThreshold(double value) {
        this.duplicateTitleJaccardThreshold = value;
    }

    public void validate() {
        if (!Double.isFinite(hybridVectorWeight) || !Double.isFinite(hybridLexicalWeight)
                || hybridVectorWeight < 0d || hybridLexicalWeight < 0d
                || hybridVectorWeight + hybridLexicalWeight <= 0d) {
            throw new IllegalStateException("search.retrieval hybrid weights must be finite, non-negative, and not both zero");
        }
        if (!Double.isFinite(semanticMinimumSimilarity) || semanticMinimumSimilarity < -1d
                || semanticMinimumSimilarity > 1d) {
            throw new IllegalStateException("search.retrieval.semantic-minimum-similarity must be between -1 and 1");
        }
        if (!Double.isFinite(hybridMinimumScore) || hybridMinimumScore < 0d) {
            throw new IllegalStateException("search.retrieval.hybrid-minimum-score must be non-negative");
        }
        if (!Double.isFinite(duplicateTitleJaccardThreshold) || duplicateTitleJaccardThreshold <= 0d
                || duplicateTitleJaccardThreshold > 1d) {
            throw new IllegalStateException(
                    "search.retrieval.duplicate-title-jaccard-threshold must be greater than zero and at most one");
        }
    }
}
