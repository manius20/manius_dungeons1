package pl.s16.dungeons;

import java.nio.file.*;
import java.util.Comparator;

public final class RecoveryDirectoryAuditSelfTest {
    private static void check(boolean ok){if(!ok)throw new AssertionError("Recovery artifact audit failed");}
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("s16-recovery-audit-");
        try {
            check(RecoveryDirectoryAudit.inspect(dir).safeForNewTests());
            Files.writeString(dir.resolve("test1.yml"),"id: test1\n");
            check(RecoveryDirectoryAudit.inspect(dir).safeForNewTests()); // CAPTURING before snapshot
            Files.write(dir.resolve("test1.snapshot.gz"),new byte[]{1,2});
            check(RecoveryDirectoryAudit.inspect(dir).safeForNewTests()); // Integrity checked separately by codec.
            Files.write(dir.resolve("orphan.snapshot.gz"),new byte[]{1});
            check(!RecoveryDirectoryAudit.inspect(dir).safeForNewTests());
            Files.delete(dir.resolve("orphan.snapshot.gz"));
            Files.writeString(dir.resolve("recovery.yml.tmp"),"unfinished");
            check(!RecoveryDirectoryAudit.inspect(dir).safeForNewTests());
            Files.delete(dir.resolve("recovery.yml.tmp"));
            Files.writeString(dir.resolve("s16-snapshot-123.tmp"),"unfinished");
            check(!RecoveryDirectoryAudit.inspect(dir).safeForNewTests());
            Files.delete(dir.resolve("s16-snapshot-123.tmp"));
            Files.writeString(dir.resolve("extra.snapshot.gz"),"orphan");
            check(RecoveryDirectoryAudit.inspect(dir).findings().stream().anyMatch(s->s.contains("extra")));
            Files.delete(dir.resolve("extra.snapshot.gz"));
            check(RecoveryDirectoryAudit.inspect(dir).safeForNewTests());
            check(RecoveryDirectoryAudit.validId("t123_20261010"));
            check(!RecoveryDirectoryAudit.validId("../tampered"));
            System.out.println("RecoveryDirectoryAuditSelfTest OK");
        }finally {
            try(var stream=Files.walk(dir)){for(Path p:stream.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
        }
    }
}
