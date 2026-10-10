package pl.s16.dungeons;

import java.util.*;

public final class TestTrapLayoutSelfTest {
    public static void main(String[] args) {
        int inspected=0;int traps=0;
        for(int size=15;size<=100;size++) {
            long seed=948365L+size;
            var layout=DungeonLayoutPlanner.generate(size,1,seed,0.05,0.25);
            var blueprint=DungeonBlueprint.fromLayout(layout);
            var chest=TestChestLayout.plan(layout,blueprint,seed);
            var plan=TestTrapLayout.plan(layout,blueprint,seed,0.8);
            check(plan.equals(TestTrapLayout.plan(layout,blueprint,seed,0.8)),"determinism");
            check(TestTrapLayout.plan(layout,blueprint,seed,0.0).isEmpty(),"disabled fraction");
            for(var spot:plan){
                check(spot.room()!=0,"entrance protection");
                var expected=spot.kind()==TestTrapLayout.Kind.MAGMA?DungeonBlueprint.Cell.FLOOR:DungeonBlueprint.Cell.AIR;
                check(blueprint.cells().get(new DungeonBlueprint.Pos(spot.x(),spot.y(),spot.z()))==expected,"valid placement");
                var room=layout.rooms().get(spot.room());
                check(Math.abs(spot.x()-room.x())>=2&&Math.abs(spot.z()-room.z())>=2,"not central path");
                check(chest.stream().noneMatch(c->Math.abs(c.x()-spot.x())<=1&&Math.abs(c.z()-spot.z())<=1),"chest clearance");
            }
            traps+=plan.size();inspected++;
        }
        boolean rejected=false;
        try {var l=DungeonLayoutPlanner.generate(15,1,1,0.05,0.25);
            TestTrapLayout.plan(l,DungeonBlueprint.fromLayout(l),1,1.5);} catch(IllegalArgumentException ex){rejected=true;}
        check(rejected,"invalid density rejected");
        System.out.println("PASS TestTrapLayoutSelfTest: "+inspected+" dungeon sizes, "+traps+" trap placements");
    }
    private static void check(boolean yes,String reason){if(!yes)throw new AssertionError(reason);}
}
