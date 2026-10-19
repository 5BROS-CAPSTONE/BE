package com.fivebros.backend.area.dto;

import java.util.List;

public record KmlPreviewResponse(List<Candidate> candidates, List<String> warnings) {
    public record Issue(String code, String message) { }
    public record Candidate(int index, String placemarkName, boolean valid, BoundaryPreview preview,
                            List<Issue> errors, List<String> warnings) { }
}
