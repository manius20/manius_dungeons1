package pl.s16.dungeons;

import java.nio.file.*;
import java.util.Comparator;

public final class DurableAtomicTextSelfTest {
    private static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("s16-atomic-");
        try {
            Path file=dir.resolve("schedules.yml");
            for(int i=0;i<120;i++){
                String expected="events:\n  abc: 'zapis "+i+" żółw'\n";
                DurableAtomicText.write(file,expected);
                check(Files.readString(file).equals(expected),"Incomplete UTF-8 or missing replacement");
                try(var stream=Files.list(dir)){
                    check(stream.count()==1,"Unfinished temporary write left behind");
                }
            }
            check(RecoveryDirectoryAudit.inspect(dir).safeForNewTests(),"Unknown harmless YAML should not confuse audit");
            System.out.println("DurableAtomicTextSelfTest OK (120 atomic replacements)");
        }finally{
            try(var stream=Files.walk(dir)){for(Path p:stream.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
        }
    }
}
