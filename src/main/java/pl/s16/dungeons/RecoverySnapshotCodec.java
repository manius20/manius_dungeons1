package pl.s16.dungeons;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Snapshot on disk. V1/V2 remain readable; V3 binds per-block tile payloads to SHA-256.
 * Pure Java, so corruption and backwards-compatibility tests run without Paper.
 * Note: V3 is deliberately NOT a general NBT/world backup format.
 */
final class RecoverySnapshotCodec {
    static final int MAGIC=0x53313644;
    static final int VERSION=3;
    static final int MAX_ENTRIES=500_000;
    static final int MAX_TILE_BYTES=262_144;
    static final int MAX_TILE_TOTAL_BYTES=32*1024*1024;
    record Cell(int x,int y,int z,String blockData,String tileYaml) {
        Cell(int x,int y,int z,String blockData){this(x,y,z,blockData,"");}
    }
    record Snapshot(List<Cell> cells,int version) {
        boolean legacy(){return version==1;}
        boolean hasTileSupport(){return version>=3;}
        long tileCount(){return cells.stream().filter(c->!c.tileYaml().isEmpty()).count();}
    }
    private record Pos(int x,int y,int z) {}
    private RecoverySnapshotCodec() {}

    static void write(Path file, UUID worldId, List<Cell> cells) throws IOException {
        Objects.requireNonNull(worldId,"worldId");
        validate(cells);
        Path parent=file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        if(Files.exists(file))throw new FileAlreadyExistsException(file.toString());
        Path temp=Files.createTempFile(parent,"s16-snapshot-",".tmp");
        try {
            try(FileOutputStream fos=new FileOutputStream(temp.toFile());
                GZIPOutputStream gz=new GZIPOutputStream(new BufferedOutputStream(fos));
                DataOutputStream out=new DataOutputStream(gz)) {
                out.writeInt(MAGIC);out.writeInt(VERSION);out.writeUTF(worldId.toString());out.writeInt(cells.size());
                MessageDigest sha=digest();
                addBytes(sha,worldId.toString().getBytes(StandardCharsets.UTF_8));
                for(Cell c:cells) {
                    out.writeInt(c.x());out.writeInt(c.y());out.writeInt(c.z());out.writeUTF(c.blockData());
                    byte[] tile=c.tileYaml().getBytes(StandardCharsets.UTF_8);
                    out.writeInt(tile.length);out.write(tile);
                    addCell(sha,c,true);
                }
                out.write(sha.digest());
                out.flush();gz.finish();fos.getFD().sync();
            }
            try {Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException ex){Files.move(temp,file);}
        } finally {Files.deleteIfExists(temp);}
    }

    static Snapshot read(Path file, UUID expectedWorldId) throws IOException {
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
            if(in.readInt()!=MAGIC)throw new IOException("Zly naglowek snapshotu");
            int version=in.readInt();
            if(version<1||version>VERSION)throw new IOException("Nieobslugiwana wersja snapshotu: "+version);
            UUID worldId;
            try {worldId=UUID.fromString(in.readUTF());}
            catch(IllegalArgumentException ex){throw new IOException("Bledny identyfikator swiata",ex);}
            if(!worldId.equals(expectedWorldId))throw new IOException("Snapshot nalezy do innego swiata");
            int count=in.readInt();
            if(count<0||count>MAX_ENTRIES)throw new IOException("Bledna liczba blokow: "+count);
            List<Cell> cells=new ArrayList<>(count);
            Set<Pos> seen=new HashSet<>();
            MessageDigest sha=digest();addBytes(sha,worldId.toString().getBytes(StandardCharsets.UTF_8));
            long totalTileBytes=0;
            for(int i=0;i<count;i++) {
                int x=in.readInt(),y=in.readInt(),z=in.readInt();String blockData=in.readUTF();String tileYaml="";
                if(version>=3) {
                    int length=in.readInt();
                    if(length<0||length>MAX_TILE_BYTES)throw new IOException("Nieprawidlowa dlugosc danych kontenera: "+length);
                    totalTileBytes+=length;
                    if(totalTileBytes>MAX_TILE_TOTAL_BYTES)throw new IOException("Przekroczony limit danych kontenerow");
                    byte[] tile=new byte[length];in.readFully(tile);
                    tileYaml=decodeUtf8(tile);
                }
                Cell c=new Cell(x,y,z,blockData,tileYaml);
                validateCell(c);
                if(!seen.add(new Pos(c.x(),c.y(),c.z())))throw new IOException("Powtorzony blok w snapshotcie: "+c.x()+","+c.y()+","+c.z());
                cells.add(c);addCell(sha,c,version>=3);
            }
            if(version>=2) {
                byte[] expected=new byte[32];in.readFully(expected);
                if(!MessageDigest.isEqual(expected,sha.digest()))throw new IOException("Nieprawidlowa suma kontrolna SHA-256");
            }
            if(in.read()!=-1)throw new IOException("Nadmiarowe bajty w snapshotcie");
            return new Snapshot(List.copyOf(cells),version);
        }
    }
    private static String decodeUtf8(byte[] bytes)throws IOException {
        var decoder=StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
        try{return decoder.decode(ByteBuffer.wrap(bytes)).toString();}
        catch(java.nio.charset.CharacterCodingException ex){throw new IOException("Niepoprawne UTF-8 w danych kontenera",ex);}
    }
    private static void validate(List<Cell> cells) throws IOException {
        if(cells==null||cells.size()>MAX_ENTRIES)throw new IOException("Przekroczony limit blokow");
        Set<Pos> seen=new HashSet<>();long total=0;
        for(Cell c:cells){
            validateCell(c);
            if(!seen.add(new Pos(c.x(),c.y(),c.z())))throw new IOException("Powtorzony blok");
            total+=c.tileYaml().getBytes(StandardCharsets.UTF_8).length;
            if(total>MAX_TILE_TOTAL_BYTES)throw new IOException("Przekroczony limit danych kontenerow");
        }
    }
    private static void validateCell(Cell c) throws IOException {
        if(c==null||c.y() < -2048 || c.y() > 2048 || Math.abs((long)c.x()) > 30_000_000L || Math.abs((long)c.z()) > 30_000_000L)
            throw new IOException("Nieprawidlowe wspolrzedne w snapshotcie");
        if(c.blockData()==null || c.blockData().isBlank() || c.blockData().length()>4096 || !c.blockData().startsWith("minecraft:"))
            throw new IOException("Nieprawidlowe dane bloku w snapshotcie");
        if(c.tileYaml()==null||c.tileYaml().getBytes(StandardCharsets.UTF_8).length>MAX_TILE_BYTES)
            throw new IOException("Nieprawidlowe dane kontenera");
    }
    private static MessageDigest digest() {try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
    private static void addCell(MessageDigest sha,Cell c,boolean withTile){
        sha.update(ByteBuffer.allocate(12).putInt(c.x()).putInt(c.y()).putInt(c.z()).array());
        addBytes(sha,c.blockData().getBytes(StandardCharsets.UTF_8));
        if(withTile)addBytes(sha,c.tileYaml().getBytes(StandardCharsets.UTF_8));
    }
    private static void addBytes(MessageDigest sha,byte[] bytes){sha.update(ByteBuffer.allocate(4).putInt(bytes.length).array());sha.update(bytes);}
}
