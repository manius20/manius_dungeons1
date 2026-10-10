package pl.s16.dungeons;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Fingerprint of the EXACT coordinates that will be mutated. Independent of
 * the original block data stored by RecoverySnapshotCodec, and persisted in
 * the recovery manifest BEFORE capture begins. This is not a replacement for
 * a full world backup or for the snapshot SHA-256.
 */
final class SnapshotCoverage {
    record Pos(int x, int y, int z) {}
    record Seal(int count, String sha256) {}
    private SnapshotCoverage() {}

    static Seal seal(Collection<Pos> positions) throws IOException {
        if (positions == null || positions.isEmpty() || positions.size() > RecoverySnapshotCodec.MAX_ENTRIES)
            throw new IOException("Pusty lub zbyt duzy zestaw zmian");
        List<Pos> sorted = new ArrayList<>(positions);
        sorted.sort(Comparator.comparingInt(Pos::x).thenComparingInt(Pos::y).thenComparingInt(Pos::z));
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        digest.update(ByteBuffer.allocate(4).putInt(sorted.size()).array());
        Pos previous = null;
        for (Pos position : sorted) {
            if (position == null || position.equals(previous)) throw new IOException("Powtorzona / pusta pozycja w zestawie zmian");
            digest.update(ByteBuffer.allocate(12)
                .putInt(position.x()).putInt(position.y()).putInt(position.z()).array());
            previous = position;
        }
        return new Seal(sorted.size(), HexFormat.of().formatHex(digest.digest()));
    }

    static void verify(Seal expected, Collection<Pos> actual) throws IOException {
        if (expected == null || expected.count() <= 0 || expected.sha256() == null || !expected.sha256().matches("[0-9a-f]{64}"))
            throw new IOException("Brak poprawnej pieczeci planu zmian");
        Seal found = seal(actual);
        if (!expected.equals(found))
            throw new IOException("Kopia NIE pokrywa dokladnie planu zmian: wymagane "
                + expected.count() + " pozycji, znaleziono " + found.count() + " (SHA-256 rozny)");
    }
}
