package pl.s16.dungeons;

import java.util.List;

/** Ensure cost reporting counts *all* real test shafts, not only the first. */
public final class MultiEntranceEstimateSelfTest {
    public static void main(String[] args) {
        int scenarios=0;
        for(int roomCount:new int[]{15,20,35,50,75,100}) {
            int previousCells=0;
            for(int entries=1;entries<=3;entries++) {
                var layout=DungeonLayoutPlanner.generate(roomCount,entries,roomCount*777L,0.05,0.25);
                var blueprint=DungeonBlueprint.fromLayout(layout);
                var candidates=DungeonMultiEntrancePlan.locations(layout,100,200,3500);
                var shafts=candidates.stream()
                    .map(c->new DungeonMultiEntrancePlan.Site(c.roomIndex(),c.x(),c.z(),90)).toList();
                var estimate=DungeonPlanEstimate.analyzeMulti(layout,blueprint,100,200,66,shafts);
                if(estimate.cells()<blueprint.cells().size()) throw new AssertionError("Plan nie obejmuje konstrukcji");
                if(entries>1 && estimate.cells()<=previousCells)
                    throw new AssertionError("Dodatkowy szyb nie powiekszyl kosztu: "+roomCount+"/"+entries);
                if(estimate.chunks()<=0||estimate.maxY()<91||estimate.minY()>66)
                    throw new AssertionError("Nieprawidlowe granice");
                previousCells=estimate.cells();scenarios++;
            }
        }
        System.out.println("PASS: MultiEntranceEstimateSelfTest "+scenarios+" scenarios incl. 100 rooms x 3 shafts");
    }
}
