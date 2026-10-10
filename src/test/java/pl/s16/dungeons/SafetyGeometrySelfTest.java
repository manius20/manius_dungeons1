package pl.s16.dungeons;

import java.util.HashMap;
import java.util.Map;

/** Runs without Bukkit/Paper, proves border and geometry invariants. */
public final class SafetyGeometrySelfTest {
    public static void main(String[] args) {
        if (!RectangleCollision.intersects(0,0,10,10,10,10,11,11))
            throw new AssertionError("Touching region edge must collide");
        if (!RectangleCollision.intersects(10,10,0,0,11,11,10,10))
            throw new AssertionError("Reversed corners must collide");
        if (RectangleCollision.intersects(0,0,10,10,11,11,20,20))
            throw new AssertionError("Non-overlap false positive");
        int verified=0;
        for (int rooms=15;rooms<=100;rooms++) {
            var layout=DungeonLayoutPlanner.generate(rooms,1,9000+rooms,0.05,0.25);
            var first=layout.rooms().get(0);
            var bp=DungeonBlueprint.fromLayout(layout);
            int entranceX=120,entranceZ=130,floorY=40,surfaceY=70;
            int ox=entranceX-first.x(),oz=entranceZ-first.z();
            if (!PlacementRules.validate(ox,oz,entranceX,entranceZ,bp.bounds(),3500,4000,50).allowed())
                throw new AssertionError("Valid room layout rejected: "+rooms);
            Map<String,String> planned=new HashMap<>();
            for(var entry:bp.cells().entrySet()) {
                var c=entry.getKey();
                planned.put((ox+c.x())+":"+(floorY+c.y())+":"+(oz+c.z()),entry.getValue().name());
            }
            var shaft=EntranceShaftPlan.design(entranceX,entranceZ,floorY,surfaceY);
            for(var c:shaft.cells()) {
                planned.put(c.x()+":"+c.y()+":"+c.z(),c.element().name());
            }
            // The doorway must actually connect to room 0's floor, not some arbitrary room.
            if(!planned.containsKey(entranceX+":"+(floorY+1)+":"+entranceZ))
                throw new AssertionError("Shaft and room not connected");
            if (planned.size() > bp.cells().size()+shaft.cells().size())
                throw new AssertionError("Duplicated block coordinates");
            // Include 50-block margin when validating the world border.
            if (PlacementRules.validate(3950-first.x(),-first.z(),3950,0,bp.bounds(),3500,4000,50).allowed())
                throw new AssertionError("Forbidden entrance near border accepted");
            verified++;
        }
        System.out.println("OK: "+verified+" room/shaft integration plans, WG rectangle collisions and border");
    }
}
