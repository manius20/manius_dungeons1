package pl.s16.dungeons;

/** Bound synchronous terrain work by time as well as by blocks per tick. */
final class RecoveryWorkBudget {
    private RecoveryWorkBudget() {}
    static long deadline(long startNanos, int budgetMillis) {
        long millis=Math.max(1,Math.min(25,budgetMillis));
        return startNanos+millis*1_000_000L;
    }
    static boolean hasTime(long deadlineNanos,long nowNanos) {
        return nowNanos<deadlineNanos;
    }
}
