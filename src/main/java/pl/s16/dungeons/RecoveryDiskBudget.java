package pl.s16.dungeons;

/** Conservative free-disk guard for snapshots and manifests. Does not guarantee
 * future space availability; administrators still need external backups. */
final class RecoveryDiskBudget {
    static final long MIN_RESERVE = 512L * 1024 * 1024;
    private static final long ESTIMATED_BYTES_PER_CELL = 2048L;
    private RecoveryDiskBudget() {}

    static long requiredBytes(int numberOfBlocks, long configuredReserve) {
        if (numberOfBlocks <= 0 || numberOfBlocks > RecoverySnapshotCodec.MAX_ENTRIES)
            throw new IllegalArgumentException("Nieprawidlowa liczba blokow do zapisania");
        if (configuredReserve < 0) throw new IllegalArgumentException("Rezerwa dyskowa nie moze byc ujemna");
        return Math.addExact(configuredReserve, Math.multiplyExact(numberOfBlocks, ESTIMATED_BYTES_PER_CELL));
    }
    static boolean hasSpace(long usableBytes, int numberOfBlocks, long configuredReserve) {
        return usableBytes > 0 && usableBytes >= requiredBytes(numberOfBlocks, configuredReserve);
    }
}
