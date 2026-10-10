package pl.s16.dungeons;

/** Pure-Java end-of-rollback verification state machine.
 * A mismatch never results in a RESTORED state, even after retries are exhausted.
 * Retry count is persisted separately from the block cursor. */
final class RestoreVerificationPolicy {
    enum Action { FINISH, RETRY_FROM_SNAPSHOT, STOP_AND_ALERT }
    private RestoreVerificationPolicy() {}

    static Action decide(int expected, int verified, int mismatches, int attempts, int maxRetries) {
        if(expected <= 0 || verified < 0 || verified > expected || mismatches < 0
            || attempts < 0 || maxRetries < 0 || maxRetries > 5)
            throw new IllegalArgumentException("Invalid recovery verification inputs");
        if(verified != expected)throw new IllegalArgumentException("Verification did not scan all cells");
        if(mismatches == 0)return Action.FINISH;
        return attempts < maxRetries ? Action.RETRY_FROM_SNAPSHOT : Action.STOP_AND_ALERT;
    }
}
