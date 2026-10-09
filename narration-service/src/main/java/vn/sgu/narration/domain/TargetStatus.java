package vn.sgu.narration.domain;

public enum TargetStatus {
    PENDING, TRANSLATING, SYNTHESIZING, PUBLISHED, FAILED, CANCELLED;

    public boolean isTerminal() {
        return this == PUBLISHED || this == FAILED || this == CANCELLED;
    }

    public int rank() {
        return switch (this) {
            case PENDING -> 0;
            case TRANSLATING -> 1;
            case SYNTHESIZING -> 2;
            case PUBLISHED, FAILED, CANCELLED -> 3;
        };
    }
}
