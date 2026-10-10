package pl.s16.dungeons;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** No Paper dependency. Coverage is tested against real compressed snapshots. */
public final class SnapshotCoverageSelfTest {
    public static void main(String[] args)throws Exception {
        List<SnapshotCoverage.Pos> expected=new ArrayList<>();
        for(int x=-17;x<19;x++)for(int y=20;y<26;y++)for(int z=-5;z<9;z++)
            expected.add(new SnapshotCoverage.Pos(x,y,z));
        SnapshotCoverage.Seal seal=SnapshotCoverage.seal(expected);
        if(seal.count()!=36*6*14)throw new AssertionError("count");
        List<SnapshotCoverage.Pos> reordered=new ArrayList<>(expected);
        Collections.shuffle(reordered,new Random(7));
        SnapshotCoverage.verify(seal,reordered);
        rejected(()->SnapshotCoverage.verify(seal,reordered.subList(1,reordered.size())),"missing cell");
        List<SnapshotCoverage.Pos> extra=new ArrayList<>(reordered);
        extra.add(new SnapshotCoverage.Pos(500,20,500));
        rejected(()->SnapshotCoverage.verify(seal,extra),"extra cell");
        List<SnapshotCoverage.Pos> replaced=new ArrayList<>(reordered);
        replaced.set(0,new SnapshotCoverage.Pos(501,20,501));
        rejected(()->SnapshotCoverage.verify(seal,replaced),"different same-count cell");
        List<SnapshotCoverage.Pos> duplicate=new ArrayList<>(reordered);
        duplicate.set(0,duplicate.get(1));
        rejected(()->SnapshotCoverage.verify(seal,duplicate),"duplicate cell");
        var world=UUID.randomUUID();
        Path temp=Files.createTempFile("s16-stage16-", ".gz"); Files.delete(temp);
        try {
            List<RecoverySnapshotCodec.Cell> cells=new ArrayList<>();
            for(var pos:expected)cells.add(new RecoverySnapshotCodec.Cell(pos.x(),pos.y(),pos.z(),"minecraft:stone"));
            RecoverySnapshotCodec.write(temp,world,cells);
            var read=RecoverySnapshotCodec.read(temp,world);
            SnapshotCoverage.verify(seal,read.cells().stream()
                .map(c->new SnapshotCoverage.Pos(c.x(),c.y(),c.z())).toList());
            // A checksum-valid, newly written snapshot may still be incomplete.
            Path incomplete=Files.createTempFile("s16-stage16-missing-", ".gz"); Files.delete(incomplete);
            try {
                RecoverySnapshotCodec.write(incomplete,world,cells.subList(0,cells.size()-1));
                var validButIncomplete=RecoverySnapshotCodec.read(incomplete,world);
                rejected(()->SnapshotCoverage.verify(seal,validButIncomplete.cells().stream()
                    .map(c->new SnapshotCoverage.Pos(c.x(),c.y(),c.z())).toList()),"valid but incomplete snapshot");
            } finally {Files.deleteIfExists(incomplete);}
        } finally {Files.deleteIfExists(temp);}
        System.out.println("PASS SnapshotCoverageSelfTest: sealed coords, permutations, omissions, additions, duplicates, valid-but-incomplete snapshot");
    }
    interface Work {void run() throws Exception;}
    private static void rejected(Work work,String label) throws Exception {
        try {work.run();throw new AssertionError("Accepted "+label);}catch(IOException expected) { /* fail closed */ }
    }
}
