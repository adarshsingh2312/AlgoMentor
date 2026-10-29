package com.algomentor.dto;

import com.algomentor.domain.Problem;
import com.algomentor.domain.Recommendation;
import com.algomentor.domain.Tag;

import java.util.List;
import java.util.UUID;

public record RecommendationResponseDto(
        UUID id,
        String problemName,
        Integer rating,
        List<String> tags,
        String url,
        String reason,
        String status
) {
    public static RecommendationResponseDto from(Recommendation r) {
        Problem p = r.getProblem();
        List<String> tagNames = (p != null && p.getTags() != null)
                ? p.getTags().stream().map(Tag::getName).sorted().toList()
                : List.of();

        return new RecommendationResponseDto(
                r.getId(),
                p != null ? p.getName() : "Unknown",
                p != null ? p.getRating() : null,
                tagNames,
                p != null ? p.getUrl() : "",
                r.getReason(),
                r.getStatus() != null ? r.getStatus().name() : "SERVED"
        );
    }
}
