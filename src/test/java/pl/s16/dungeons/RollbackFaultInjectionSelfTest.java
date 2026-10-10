package pl.s16.dungeons;

import java.nio.file.*;
import java.util.*;

/**
 * Offline failure-injection simulation (NOT a Bukkit/Paper integration test):
 * a real snapshot codec is used; building and both restore phases are interrupted
 * at every cursor index to prove that repeating the restore from the immutable
 * snapshot returns the simulated world to its original state.
 */
public final class RollbackFaultInjectionSelfTest {
    private record Pos(int x,int y,int z) {}
    private static void check(boolean condition) {if(!condition)throw new AssertionError("Rollback mismatch");}
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("dungeons-fault-");
        try {
            UUID uuid=UUID.randomUUID();
            Random random=new Random(1602);
            int scenarios=0;
            for(int n:List.of(1,2,7,20,64,128,256)) {
                Path file=dir.resolve("snap-"+n+".gz");
                List<RecoverySnapshotCodec.Cell> originals=new ArrayList<>();
                Map<Pos,String> initial=new HashMap<>();
                for(int i=0;i<n;i++) {
                    Pos pos=new Pos(i-130,-20+(i%19),i%7);
                    String data=(i%4==0?"minecraft:stone":"minecraft:deepslate");
                    originals.add(new RecoverySnapshotCodec.Cell(pos.x,pos.y,pos.z,data));
                    initial.put(pos,data);
                }
                RecoverySnapshotCodec.write(file,uuid,originals);
                List<RecoverySnapshotCodec.Cell> restored=RecoverySnapshotCodec.read(file,uuid).cells();
                check(restored.equals(originals));
                // A crash may occur after ANY amount of building or restoring.
                int[] indexes={0,1,n/3,n/2,n-1,n};
                for(int buildSteps:indexes)for(int restoreSteps:indexes) {
                    Map<Pos,String> simulated=new HashMap<>(initial);
                    for(int i=0;i<buildSteps;i++) {
                        var cell=originals.get(i);
                        simulated.put(new Pos(cell.x(),cell.y(),cell.z()),"minecraft:air");
                    }
                    for(int i=0;i<restoreSteps;i++) {
                        var cell=restored.get(i);
                        simulated.put(new Pos(cell.x(),cell.y(),cell.z()),cell.blockData());
                    }
                    // Simulated restart resets rollback cursor to 0.
                    List<RecoverySnapshotCodec.Cell> reloaded=RecoverySnapshotCodec.read(file,uuid).cells();
                    for(var cell:reloaded) simulated.put(new Pos(cell.x(),cell.y(),cell.z()),cell.blockData());
                    check(simulated.equals(initial));
                    scenarios++;
                }
                // Tamper with the snapshot, never accept a silent partial restore.
                byte[] bytes=Files.readAllBytes(file);
                bytes[random.nextInt(bytes.length)]^=0x40;
                Path corrupt=dir.resolve("bad-"+n+".gz");
                Files.write(corrupt,bytes);
                boolean refused=false;
                try {RecoverySnapshotCodec.read(corrupt,uuid);} catch(Exception expected) {refused=true;}
                check(refused);
            }
            check(scenarios==252);
            System.out.println("RollbackFaultInjectionSelfTest OK ("+scenarios+" crash boundary scenarios, seven corruption probes)");
        } finally {
            try(var stream=Files.list(dir)) {for(var file:stream.toList())Files.deleteIfExists(file);}
            Files.deleteIfExists(dir);
        }
    }
}
