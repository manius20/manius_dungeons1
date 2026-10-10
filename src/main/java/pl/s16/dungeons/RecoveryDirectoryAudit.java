package pl.s16.dungeons;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Offline-testable, read-only audit of recovery artifacts.
 * A detached snapshot is NOT proof that the corresponding event can be safely restored.
 * Any unresolved recovery artifact blocks further test-world modifications.
 */
final class RecoveryDirectoryAudit {
    record Result(boolean safeForNewTests, List<String> findings, int manifests, int snapshots) {
        Result { findings = List.copyOf(findings); }
    }
    private RecoveryDirectoryAudit() {}

    static Result inspect(Path directory) throws IOException {
        if(Files.isSymbolicLink(directory))
            return new Result(false,List.of("recoveries/ jest linkiem symbolicznym"),0,0);
        if(!Files.exists(directory,LinkOption.NOFOLLOW_LINKS))
            return new Result(true,List.of(),0,0);
        if(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))
            return new Result(false,List.of("recoveries/ nie jest katalogiem"),0,0);
        Set<String> manifests=new HashSet<>(),snapshots=new HashSet<>();
        List<String> problems=new ArrayList<>();
        try(DirectoryStream<Path> files=Files.newDirectoryStream(directory)) {
            for(Path file:files) {
                String name=file.getFileName().toString();
                if(Files.isSymbolicLink(file)) { problems.add("Link symboliczny: "+name); continue; }
                if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) {
                    problems.add("Nieoczekiwany element (nie jest plikiem): "+name); continue;
                }
                if(name.endsWith(".snapshot.gz")) {
                    String id=name.substring(0,name.length()-".snapshot.gz".length());
                    if(!validId(id))problems.add("Nieprawidlowa nazwa kopii: "+name);
                    else snapshots.add(id);
                } else if(name.endsWith(".yml")) {
                    String id=name.substring(0,name.length()-4);
                    if(!validId(id))problems.add("Nieprawidlowa nazwa manifestu: "+name);
                    else manifests.add(id);
                } else if(name.endsWith(".tmp") || name.startsWith("s16-snapshot-")) {
                    problems.add("Niedokonczony zapis: "+name);
                } else if(name.endsWith(".gz") || name.endsWith(".yaml")) {
                    problems.add("Nieznany plik odzyskiwania: "+name);
                }
            }
        }
        for(String id:snapshots)if(!manifests.contains(id))
            problems.add("Osierocona kopia bez manifestu: "+id+".snapshot.gz");
        problems.sort(String::compareTo);
        return new Result(problems.isEmpty(),problems,manifests.size(),snapshots.size());
    }
    static boolean validId(String id){return id!=null && id.matches("[A-Za-z0-9_-]{1,32}");}
}
