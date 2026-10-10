package pl.s16.dungeons;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Pure Java regression tests for v1/v2 compatibility and v3 inventories. */
public final class RecoverySnapshotSelfTest {
    public static void main(String[] args)throws Exception {
        Path dir=Files.createTempDirectory("s16-recovery-test-");
        try {
            UUID uid=UUID.randomUUID();
            String testInventory="format: 1\nmaterial: CHEST\nsize: 27\nitems: []\n";
            List<RecoverySnapshotCodec.Cell> originals=List.of(
                new RecoverySnapshotCodec.Cell(0,64,0,"minecraft:stone"),
                new RecoverySnapshotCodec.Cell(-35,-55,99,"minecraft:oak_log[axis=y]"),
                new RecoverySnapshotCodec.Cell(15,100,27,"minecraft:chest[facing=north,type=single,waterlogged=false]",testInventory));
            Path file=dir.resolve("originals.gz");
            RecoverySnapshotCodec.write(file,uid,originals);
            var read=RecoverySnapshotCodec.read(file,uid);
            check(read.version()==3 && read.cells().equals(originals) && read.tileCount()==1,"round-trip v3 tile content");
            fails(()->RecoverySnapshotCodec.write(file,uid,originals),"no overwrite");
            fails(()->RecoverySnapshotCodec.read(file,UUID.randomUUID()),"wrong world");
            Path truncated=dir.resolve("truncated.gz");
            byte[] bytes=Files.readAllBytes(file);
            Files.write(truncated,Arrays.copyOf(bytes,bytes.length-8));
            fails(()->RecoverySnapshotCodec.read(truncated,uid),"truncated compressed payload");
            byte[] tampered=Files.readAllBytes(file);tampered[tampered.length/2]^=1;
            Path bad=dir.resolve("tampered.gz");Files.write(bad,tampered);
            fails(()->RecoverySnapshotCodec.read(bad,uid),"corrupted snapshot");
            fails(()->RecoverySnapshotCodec.write(dir.resolve("dups.gz"),uid,List.of(originals.get(0),originals.get(0))),"duplicate positions");
            fails(()->RecoverySnapshotCodec.write(dir.resolve("huge.gz"),uid,List.of(
                new RecoverySnapshotCodec.Cell(1,64,1,"minecraft:chest","x".repeat(RecoverySnapshotCodec.MAX_TILE_BYTES+1)))),
                "tile size cap");
            Path legacy=dir.resolve("legacy-v1.gz");
            try(DataOutputStream out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(legacy)))) {
                out.writeInt(RecoverySnapshotCodec.MAGIC);out.writeInt(1);out.writeUTF(uid.toString());out.writeInt(1);
                out.writeInt(3);out.writeInt(45);out.writeInt(7);out.writeUTF("minecraft:stone");
            }
            var v1=RecoverySnapshotCodec.read(legacy,uid);
            check(v1.version()==1 && v1.legacy() && v1.cells().size()==1 && v1.cells().get(0).x()==3,"v1 compatibility");
            Path previous=dir.resolve("previous-v2.gz");
            writeOldV2(previous,uid,List.of(originals.get(0),originals.get(1)));
            var v2=RecoverySnapshotCodec.read(previous,uid);
            check(v2.version()==2 && v2.cells().size()==2 && !v2.hasTileSupport(),"v2 compatibility + sha");
            // The gzip CRC can still be valid when a snapshot is maliciously re-packed: SHA detects it.
            Path modified=dir.resolve("repacked-corrupt-tile.gz");
            byte[] uncompressed;
            try(GZIPInputStream gzip=new GZIPInputStream(Files.newInputStream(file))){uncompressed=gzip.readAllBytes();}
            byte[] marker=testInventory.getBytes(StandardCharsets.UTF_8);
            int start=indexOf(uncompressed,marker);
            check(start>=0,"tile data occurs in v3 stream");
            uncompressed[start+8]^=1;
            try(GZIPOutputStream gzip=new GZIPOutputStream(Files.newOutputStream(modified))){gzip.write(uncompressed);}
            fails(()->RecoverySnapshotCodec.read(modified,uid),"tile payload protected by SHA-256");
            System.out.println("RecoverySnapshotSelfTest: OK (v3 tiles SHA-256, v1/v2, wrong-world, truncation, corruption, size, duplicates)");
        } finally {try(var paths=Files.walk(dir)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignore){}});}}
    }
    private static int indexOf(byte[] haystack,byte[] needle){
        outer:for(int i=0;i<=haystack.length-needle.length;i++) {
            for(int j=0;j<needle.length;j++)if(haystack[i+j]!=needle[j])continue outer;
            return i;
        }
        return -1;
    }
    private static void writeOldV2(Path file,UUID uid,List<RecoverySnapshotCodec.Cell> cells)throws Exception {
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        addBytes(hash,uid.toString().getBytes(StandardCharsets.UTF_8));
        try(DataOutputStream out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file)))) {
            out.writeInt(RecoverySnapshotCodec.MAGIC);out.writeInt(2);out.writeUTF(uid.toString());out.writeInt(cells.size());
            for(var c:cells){
                out.writeInt(c.x());out.writeInt(c.y());out.writeInt(c.z());out.writeUTF(c.blockData());
                hash.update(ByteBuffer.allocate(12).putInt(c.x()).putInt(c.y()).putInt(c.z()).array());
                addBytes(hash,c.blockData().getBytes(StandardCharsets.UTF_8));
            }
            out.write(hash.digest());
        }
    }
    private static void addBytes(MessageDigest sha,byte[] b){sha.update(ByteBuffer.allocate(4).putInt(b.length).array());sha.update(b);}
    private interface Action {void run()throws Exception;}
    private static void fails(Action action,String test)throws Exception {
        try{action.run();throw new AssertionError("Expected failure: "+test);}catch(IOException expected){}
    }
    private static void check(boolean yes,String label){if(!yes)throw new AssertionError(label);}
}
