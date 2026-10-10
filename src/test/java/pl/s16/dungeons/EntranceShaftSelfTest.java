package pl.s16.dungeons;

import java.util.HashSet;

public final class EntranceShaftSelfTest {
    public static void main(String[] args) {
        for (int roomCount=15;roomCount<=100;roomCount++) {
            var layout=DungeonLayoutPlanner.generate(roomCount,1,121121L+roomCount,0.05,0.25);
            var first=layout.rooms().get(0);
            int entranceX=first.x(), entranceZ=first.z();
            var shaft=EntranceShaftPlan.design(entranceX,entranceZ,-12,64);
            if(shaft.cells().size()!=25*(64-(-12)+1))throw new AssertionError("Niepelny szyb: "+roomCount);
            var seen=new HashSet<String>();
            for (var c:shaft.cells()) {
                if(!seen.add(c.x()+":"+c.y()+":"+c.z()))throw new AssertionError("Powtorzony blok");
                if(Math.abs(c.x()-entranceX)>2 || Math.abs(c.z()-entranceZ)>2)
                    throw new AssertionError("Szyb wykracza poza 5x5");
            }
            if(!PlacementRules.validate(-first.x(),-first.z(),0,0,
                new DungeonBlueprint.Bounds(0,layout.width(),0,6,0,layout.length()),3500,4000,50).allowed())
                throw new AssertionError("Srodek swiata bezpieczny: "+roomCount);
            if(PlacementRules.validate(4000-first.x(),-first.z(),4000,0,
                new DungeonBlueprint.Bounds(0,layout.width(),0,6,0,layout.length()),3500,4000,50).allowed())
                throw new AssertionError("Wejscie poza promieniem powinno zostac odrzucone");
        }
        try { EntranceShaftPlan.design(0,0,30,35);throw new AssertionError("Zbyt plytki szyb przyjety"); }
        catch(IllegalArgumentException expected) { }
        System.out.println("OK: 86 geometrii szybu 5x5 i ograniczenia promienia 3500");
    }
}
