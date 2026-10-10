package pl.s16.dungeons;

/** Explicit crash-recovery decisions, independent of Paper API, for exhaustive offline testing. */
final class RecoveryRestartPolicy {
    enum Action { NO_WORLD_CHANGES, RESUME_RESTORE, VERIFIED_COMPLETE, WAIT_FOR_ADMIN, INVALID }
    private RecoveryRestartPolicy() {}

    static Action decide(String state, boolean snapshotExists, boolean restorationVerified) {
        if(state==null)return Action.INVALID;
        return switch(state) {
            // CAPTURING never edits world blocks. A half-written snapshot is not trusted.
            case "CAPTURING" -> Action.NO_WORLD_CHANGES;
            // A terminal label must never bypass the actual verification proof.
            case "RESTORED" -> !snapshotExists ? Action.INVALID
                : restorationVerified ? Action.VERIFIED_COMPLETE : Action.RESUME_RESTORE;
            // Manual retry only: an already FAILED attempt may need investigation.
            case "FAILED" -> snapshotExists ? Action.WAIT_FOR_ADMIN : Action.INVALID;
            case "BUILDING", "PREPARED", "ACTIVE", "RESTORING", "RESTORING_TILES", "VERIFYING" ->
                snapshotExists ? Action.RESUME_RESTORE : Action.INVALID;
            default -> Action.INVALID;
        };
    }
}
