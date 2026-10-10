package pl.s16.dungeons;

import java.util.*;

/** Dry-run only. Computes actual union of blueprint+shaft blocks at a chosen
 * simulated anchor. Never reads chunks and NEVER changes blocks. */
final class DungeonPlanEstimate {
    record Estimate(int roomCount, int cells, int chunks, int minX, int maxX,
                    int minY, int maxY, int minZ, int maxZ, long diskReserveBytes) {}
    private record Pos(int x, int y, int z) {}
    private record Chunk(int x, int z) {}
    private DungeonPlanEstimate() {}

    static Estimate analyze(DungeonLayoutPlanner.Layout layout, DungeonBlueprint blueprint,
                            int entranceX, int entranceZ, int groundY) {
        int originX = entranceX - layout.rooms().getFirst().x();
        int originZ = entranceZ - layout.rooms().getFirst().z();
        return analyzeMulti(layout,blueprint,originX,originZ,groundY-24,
            List.of(new DungeonMultiEntrancePlan.Site(0,entranceX,entranceZ,groundY)));
    }

    /** Exact union of planned dungeon and multiple shafts; does not read a world.
     * The caller must provide simulated ground heights; actual build still requires
     * a separate live terrain scan for *every* shaft. */
    static Estimate analyzeMulti(DungeonLayoutPlanner.Layout layout,DungeonBlueprint blueprint,
                                 int originX,int originZ,int floorY,
                                 List<DungeonMultiEntrancePlan.Site> sites) {
        DungeonMultiEntrancePlan.validateSites(sites,layout.entrances().size(),floorY,
            -2048,4096,Integer.MAX_VALUE);
        Set<Pos> changed = new HashSet<>();
        for (DungeonBlueprint.Pos p : blueprint.cells().keySet())
            changed.add(new Pos(originX + p.x(), floorY + p.y(), originZ + p.z()));
        for(var shaft:sites)
            for(EntranceShaftPlan.Cell p : EntranceShaftPlan.design(shaft.x(),shaft.z(),floorY,shaft.surfaceY()).cells())
                changed.add(new Pos(p.x(), p.y(), p.z()));
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        Set<Chunk> chunks = new HashSet<>();
        for(Pos p : changed) {
            minX=Math.min(minX,p.x()); maxX=Math.max(maxX,p.x());
            minY=Math.min(minY,p.y()); maxY=Math.max(maxY,p.y());
            minZ=Math.min(minZ,p.z()); maxZ=Math.max(maxZ,p.z());
            chunks.add(new Chunk(Math.floorDiv(p.x(),16),Math.floorDiv(p.z(),16)));
        }
        return new Estimate(layout.rooms().size(),changed.size(),chunks.size(),
            minX,maxX,minY,maxY,minZ,maxZ,
            RecoveryDiskBudget.requiredBytes(changed.size(),RecoveryDiskBudget.MIN_RESERVE));
    }
}
