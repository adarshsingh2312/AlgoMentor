package com.algomentor.dto;

public record SyncResult(
        int problemsInserted,
        int problemsUpdated,
        int submissionsInserted,
        int submissionsSkipped
) {
    public static final SyncResult EMPTY = new SyncResult(0, 0, 0, 0);

    public SyncResult add(SyncResult o) {
        return new SyncResult(
                problemsInserted + o.problemsInserted,
                problemsUpdated  + o.problemsUpdated,
                submissionsInserted + o.submissionsInserted,
                submissionsSkipped  + o.submissionsSkipped);
    }
}
