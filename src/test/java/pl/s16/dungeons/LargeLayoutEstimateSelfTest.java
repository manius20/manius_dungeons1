package pl.s16.dungeons;

/** Full 15..100-room geometry WITHOUT changing a world. */
public final class LargeLayoutEstimateSelfTest {
    public static void main(String[] args) {
        int cases=0;
        int max=0;
        for(int rooms=15;rooms<=100;rooms++) {
            long seed=786L+rooms*71L;
            var layout=DungeonLayoutPlanner.generate(rooms,1,seed,0.05,0.25);
            var blueprint=DungeonBlueprint.fromLayout(layout);
            var estimate=DungeonPlanEstimate.analyze(layout,blueprint,100,-100,90);
            if(estimate.roomCount()!=rooms)throw new AssertionError("Wrong rooms");
            if(estimate.cells()<blueprint.cells().size())throw new AssertionError("Missing shaft or blueprint");
            if(estimate.chunks()<1||estimate.minY()>66||estimate.maxY()<90)throw new AssertionError("Incorrect bounds");
            if(estimate.diskReserveBytes()<RecoveryDiskBudget.MIN_RESERVE)throw new AssertionError("Missing disk reserve");
            max=Math.max(max,estimate.cells());cases++;
        }
        if(!RecoveryDiskBudget.hasSpace(RecoveryDiskBudget.requiredBytes(100_000,RecoveryDiskBudget.MIN_RESERVE),
            100_000,RecoveryDiskBudget.MIN_RESERVE))throw new AssertionError("Disk guard boundary");
        if(RecoveryDiskBudget.hasSpace(1000,20,RecoveryDiskBudget.MIN_RESERVE))throw new AssertionError("Disk guard low space");
        System.out.println("PASS LargeLayoutEstimateSelfTest: "+cases+" dry runs, max planned changes "+max);
    }
}
