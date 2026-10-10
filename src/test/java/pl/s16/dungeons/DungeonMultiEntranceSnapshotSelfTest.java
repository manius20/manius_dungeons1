package pl.s16.dungeons;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Regressions for all 3 shafts in a single immutable coverage-sealed snapshot. */
public final class DungeonMultiEntranceSnapshotSelfTest {
    public static void main(String[] args) throws Exception {
        var layout=DungeonLayoutPlanner.generate(15,3,8217L,1,1);
        var blueprint=DungeonBlueprint.fromLayout(layout);
        var candidates=DungeonMultiEntrancePlan.locations(layout,100,100,3500);
        int floorY=44;
        Set<SnapshotCoverage.Pos> positions=new HashSet<>();
        for(var p:blueprint.cells().keySet())
            positions.add(new SnapshotCoverage.Pos(100+p.x(),floorY+p.y(),100+p.z()));
        List<Set<SnapshotCoverage.Pos>> perEntrance=new ArrayList<>();
        for(var candidate:candidates) {
            var shaft=EntranceShaftPlan.design(candidate.x(),candidate.z(),floorY,68);
            Set<SnapshotCoverage.Pos> own=new HashSet<>();
            for(var c:shaft.cells()) {
                var pos=new SnapshotCoverage.Pos(c.x(),c.y(),c.z());
                own.add(pos);positions.add(pos);
            }
            perEntrance.add(own);
        }
        if(perEntrance.size()!=3)throw new AssertionError("3 shafts expected");
        Set<SnapshotCoverage.Pos> unique=new HashSet<>();
        for(Set<SnapshotCoverage.Pos> entrance:perEntrance)
            for(var pos:entrance)if(!unique.add(pos))throw new AssertionError("Overlapping shafts");
        for(var encounter:MobEncounterPlan.generate(layout,8217L,0.5,0.5))
            if(layout.entrances().contains(encounter.roomIndex()))throw new AssertionError("Mob inside entrance");
        for(var spot:TestTrapLayout.plan(layout,blueprint,8217L,1))
            if(layout.entrances().contains(spot.room()))throw new AssertionError("Trap inside entrance");
        for(var spot:TestChestLayout.plan(layout,blueprint,8217L))
            for(var roomIndex:layout.entrances()) {
                var r=layout.rooms().get(roomIndex);
                if(Math.abs(r.x()-spot.x())<=r.radius()&&Math.abs(r.z()-spot.z())<=r.radius())
                    throw new AssertionError("Chest inside entrance room");
            }
        var cells=positions.stream().map(p->new RecoverySnapshotCodec.Cell(p.x(),p.y(),p.z(),"minecraft:stone"))
            .toList();
        var seal=SnapshotCoverage.seal(new ArrayList<>(positions));
        Path file=Files.createTempFile("s16-3-shaft-", ".snapshot.gz");
        Files.delete(file);
        UUID world=UUID.randomUUID();
        try {
            RecoverySnapshotCodec.write(file,world,cells);
            var snapshot=RecoverySnapshotCodec.read(file,world);
            SnapshotCoverage.verify(seal,snapshot.cells().stream().map(c ->
                new SnapshotCoverage.Pos(c.x(),c.y(),c.z())).toList());
            // A single missing surface-opening block invalidates the entire snapshot.
            var omitted=perEntrance.get(2).stream().max(Comparator.comparingInt(SnapshotCoverage.Pos::y)).orElseThrow();
            var missing=new ArrayList<>(positions);missing.remove(omitted);
            try { SnapshotCoverage.verify(seal,missing);throw new AssertionError("Missing shaft cell accepted"); }
            catch(IOException expected) { /* fail closed */ }
            System.out.println("PASS: multi-shaft immutable snapshot "+positions.size()+" cells and 3 entrances");
        } finally {Files.deleteIfExists(file);}
    }
}
