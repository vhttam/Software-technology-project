package vn.sgu.narration.domain;

public enum JobStatus {
    PENDING, PROCESSING, COMPLETED, PARTIALLY_COMPLETED, FAILED, CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == PARTIALLY_COMPLETED || this == FAILED || this == CANCELLED;
    }
}
