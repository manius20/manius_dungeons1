package pl.s16.dungeons;

import java.util.*;

/** Read-only capacity diagnostics. No Bukkit calls, no permission to build. */
final class DungeonTestReadiness {
    record Report(boolean fitsTestLimits, int minMutationTicks, int chunks, List<String> warnings) {}
    private DungeonTestReadiness() {}

    static Report check(DungeonPlanEstimate.Estimate estimate, int maxTestRooms,
                        int maxTestBlocks, int blocksPerTick) {
        if(estimate.cells() <= 0 || estimate.chunks() <= 0 || blocksPerTick <= 0
            || maxTestRooms < 1 || maxTestBlocks < 1)throw new IllegalArgumentException("Bad limits");
        List<String> warnings=new ArrayList<>();
        if(estimate.roomCount() > maxTestRooms)
            warnings.add("Za duzo pokoi do fizycznego TESTU (maks. " + maxTestRooms + ")");
        if(estimate.cells() > maxTestBlocks)
            warnings.add("Za duzo blokow do fizycznego TESTU (maks. " + maxTestBlocks + ")");
        // Lower bound for one phase, not total wall-clock time (snapshot/restore/verification cost more).
        int ticks=(int)Math.min(Integer.MAX_VALUE,((long)estimate.cells()+blocksPerTick-1)/blocksPerTick);
        warnings.add("Przed testem nadal wymagane: wczytane chunki, kontrola claimow/WG, kopia swiata i zielony build Paper");
        return new Report(estimate.roomCount() <= maxTestRooms && estimate.cells() <= maxTestBlocks,
            ticks,estimate.chunks(),List.copyOf(warnings));
    }
}
