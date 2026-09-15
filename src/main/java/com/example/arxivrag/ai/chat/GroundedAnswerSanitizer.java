package com.example.arxivrag.ai.chat;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class GroundedAnswerSanitizer {

    private static final Pattern CITATION = Pattern.compile("\\[arXiv:([^]\\s]+)]", Pattern.CASE_INSENSITIVE);

    public String sanitize(String answer, java.util.List<ChatCitation> citations) {
        if (answer == null) {
            return null;
        }
        var allowed = citations.stream().map(ChatCitation::arxivId).collect(java.util.stream.Collectors.toUnmodifiableSet());
        var matcher = CITATION.matcher(answer);
        var result = new StringBuffer();
        while (matcher.find()) {
            if (allowed.contains(matcher.group(1))) {
                matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(matcher.group()));
            } else {
                matcher.appendReplacement(result, "[unsupported citation removed]");
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
