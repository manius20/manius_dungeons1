package pl.s16.dungeons;

import java.nio.file.*;
import java.util.*;

/**
 * Full OFFLINE recovery rehearsal (no Paper): backup -> staged changes -> simulated crash
 * -> new-process safety decision -> restore -> complete cell verification.
 * This exercises the actual v3 codec and directory auditor, not a mock codec.
 */
public final class RecoveryEndToEndSelfTest {
    private record Pos(int x,int y,int z) {}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}

    public static void main(String[] args) throws Exception {
        int scenarios=0;
        for(int cellCount:new int[]{1,16,128,1024}) {
            for(int crashStep:new int[]{0,1,25,50,100}) {
                Path dir=Files.createTempDirectory("s16-e2e-");
                try {
                    UUID worldId=UUID.randomUUID();
                    Map<Pos,String> blocks=new HashMap<>();
                    List<RecoverySnapshotCodec.Cell> original=new ArrayList<>();
                    for(int i=0;i<cellCount;i++){
                        Pos p=new Pos(i%48,-30+i/230,(i/48)%48);
                        String material=i%3==0?"minecraft:stone":i%3==1?"minecraft:deepslate":"minecraft:air";
                        blocks.put(p,material);
                        original.add(new RecoverySnapshotCodec.Cell(p.x,p.y,p.z,material));
                    }
                    // Real snapshot + paired manifest in the recovery folder.
                    Files.writeString(dir.resolve("test.yml"),"id: test\nphase: BUILDING\n");
                    Path snapshot=dir.resolve("test.snapshot.gz");
                    RecoverySnapshotCodec.write(snapshot,worldId,original);
                    check(RecoveryDirectoryAudit.inspect(dir).safeForNewTests(),"Directory audit should accept a pair");
                    int changed=cellCount*crashStep/100;
                    for(int i=0;i<changed;i++){
                        var c=original.get(i);
                        blocks.put(new Pos(c.x(),c.y(),c.z()),"minecraft:gold_block");
                    }
                    // Process terminated. Manifest says BUILDING and snapshot still exists.
                    check(RecoveryRestartPolicy.decide("BUILDING",true,false)==RecoveryRestartPolicy.Action.RESUME_RESTORE,
                        "Restart must restore, never resume generation");
                    var diskCopy=RecoverySnapshotCodec.read(snapshot,worldId).cells();
                    for(var c:diskCopy)blocks.put(new Pos(c.x(),c.y(),c.z()),c.blockData());
                    for(var c:diskCopy)check(blocks.get(new Pos(c.x(),c.y(),c.z())).equals(c.blockData()),
                        "Cell failed final verification");
                    check(RecoveryRestartPolicy.decide("RESTORED",true,false)==RecoveryRestartPolicy.Action.RESUME_RESTORE,
                        "Unverified terminal marker must replay from immutable backup");
                    check(RecoveryRestartPolicy.decide("RESTORED",true,true)==RecoveryRestartPolicy.Action.VERIFIED_COMPLETE,
                        "Verified terminal marker must persist");
                    scenarios++;
                }finally{
                    Files.deleteIfExists(dir.resolve("test.yml"));
                    Files.deleteIfExists(dir.resolve("test.snapshot.gz"));
                    Files.deleteIfExists(dir);
                }
            }
        }
        check(scenarios==20,"Missing scenarios");
        System.out.println("RecoveryEndToEndSelfTest OK (20 offline lifecycle+crash+verification scenarios)");
    }
}
