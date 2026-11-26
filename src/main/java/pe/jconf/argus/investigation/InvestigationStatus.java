package pe.jconf.argus.investigation;

public enum InvestigationStatus {
    PENDING, RUNNING, AGGREGATING, COMPLETED, PARTIAL, FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == PARTIAL || this == FAILED;
    }

    boolean canTransitionTo(InvestigationStatus next) {
        return switch (this) {
            case PENDING -> next == RUNNING;
            case RUNNING -> next == AGGREGATING;
            case AGGREGATING -> next.isTerminal();
            case COMPLETED, PARTIAL, FAILED -> false;
        };
    }
}
