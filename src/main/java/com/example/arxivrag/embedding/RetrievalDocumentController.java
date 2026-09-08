package com.example.arxivrag.embedding;

import com.example.arxivrag.paper.PaperRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/retrieval-documents")
public class RetrievalDocumentController {

    private final RetrievalDocumentFactory retrievalDocumentFactory;

    private final PaperRepository paperRepository;

    public RetrievalDocumentController(RetrievalDocumentFactory retrievalDocumentFactory, PaperRepository paperRepository) {
        this.retrievalDocumentFactory = retrievalDocumentFactory;
        this.paperRepository = paperRepository;
    }

    @PostMapping("/preview")
    public RetrievalDocument preview(@RequestBody RetrievalDocumentPreviewRequest request) {
        try {
            return retrievalDocumentFactory.preview(request.arxivId(), request.title(), request.abstractText());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/papers/{arxivId}")
    public RetrievalDocument fromPaper(@PathVariable String arxivId) {
        return paperRepository.findByArxivId(arxivId)
                .map(retrievalDocumentFactory::fromPaper)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paper not found: " + arxivId));
    }

    public record RetrievalDocumentPreviewRequest(
            String arxivId,
            String title,
            String abstractText
    ) {
    }
}
