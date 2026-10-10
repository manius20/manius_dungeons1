package pl.s16.dungeons;

import java.util.*;

public final class BlueprintSelfTest {
    public static void main(String[] args) {
        for(int n=15;n<=100;n++) {
            var layout=DungeonLayoutPlanner.generate(n,10,7411L+n,0.05,0.25);
            var plan=DungeonBlueprint.fromLayout(layout);
            if(plan.cells().size()<n*100)throw new AssertionError("Pusty projekt: "+n);
            if(plan.airCount()<n*100)throw new AssertionError("Brak komnat: "+n);
            var bounds=plan.bounds();
            for(var cell:plan.cells().keySet())if(cell.x()<bounds.minX()||cell.x()>bounds.maxX()
                ||cell.y()<bounds.minY()||cell.y()>bounds.maxY()
                ||cell.z()<bounds.minZ()||cell.z()>bounds.maxZ())
                throw new AssertionError("Blok poza obrysem, brak kontroli kolizji: "+cell);
            for(var room:layout.rooms()) {
                var c=plan.cells().get(new DungeonBlueprint.Pos(room.x(),2,room.z()));
                if(c!=DungeonBlueprint.Cell.AIR)throw new AssertionError("Zasloniety srodek: "+room.index());
            }
            if(!PlacementRules.validate(0,0,0,0,plan.bounds(),3500,4000,50).allowed())
                throw new AssertionError("Centrum swiata powinno byc dozwolone");
            if(PlacementRules.validate(3900,3900,3900,3900,plan.bounds(),3500,4000,50).allowed())
                throw new AssertionError("Zla granica nie zostala odrzucona");
        }
        System.out.println("OK: 86 projektow 3D, sprawdzone pokoje i zasady borderu");
    }
}
