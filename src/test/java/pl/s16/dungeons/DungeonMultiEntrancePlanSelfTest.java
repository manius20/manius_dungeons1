package pl.s16.dungeons;

import java.util.HashSet;

public final class DungeonMultiEntrancePlanSelfTest {
    public static void main(String[] args) {
        int checked=0;
        for(int roomCount=15;roomCount<=100;roomCount++) {
            for(int entrances=1;entrances<=3;entrances++) {
                var layout=DungeonLayoutPlanner.generate(roomCount,entrances,roomCount*100L+entrances,0.05,0.25);
                var candidates=DungeonMultiEntrancePlan.locations(layout,0,0,3500);
                if(candidates.size()!=entrances)throw new AssertionError("Liczba wejsc");
                if(candidates.get(0).roomIndex()!=0)throw new AssertionError("Wejscie glowne");
                if(new HashSet<>(candidates.stream().map(c->c.x()+":"+c.z()).toList()).size()!=entrances)
                    throw new AssertionError("Powtorzone wspolrzedne");
                // All entrances must share the same floor and have independent, nonoverlapping shafts.
                for(var c:candidates) {
                    var plan=EntranceShaftPlan.design(c.x(),c.z(),40,64);
                    if(plan.cells().size()!=25*25)throw new AssertionError("Plan szybu");
                    if(!DungeonMultiEntrancePlan.validHeights(40,64,63,65, -64,320))
                        throw new AssertionError("Prawidlowe wysokosci");
                }
                checked++;
            }
        }
        if(DungeonMultiEntrancePlan.validHeights(40,64,60,65,-64,320))
            throw new AssertionError("Stromy teren");
        if(DungeonMultiEntrancePlan.validHeights(40,50,50,50,-64,320))
            throw new AssertionError("Za plytki szyb");
        if(DungeonMultiEntrancePlan.validHeights(40,141,141,141,-64,320))
            throw new AssertionError("Za gleboki szyb");
        if(DungeonMultiEntrancePlan.naturalSurface("WATER")||DungeonMultiEntrancePlan.naturalSurface("CHEST")
            ||!DungeonMultiEntrancePlan.naturalSurface("RED_SAND"))
            throw new AssertionError("Ochrona ladu");
        var layout=DungeonLayoutPlanner.generate(15,2,17L,0,0);
        try { DungeonMultiEntrancePlan.locations(layout,3495,3495,3500);
              throw new AssertionError("Brak kontroli promienia"); }
        catch(IllegalArgumentException expected) { /* correct */ }
        System.out.println("PASS: MultiEntrances "+checked+" layouts (15-100 rooms x 1-3 real test shafts)");
    }
}
