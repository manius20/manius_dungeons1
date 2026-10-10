package pl.s16.dungeons;

import java.util.ArrayList;
import java.util.List;

/** Pure capacity estimate. Not a measured TPS value and not permission to generate. */
public final class DungeonPerformanceEstimate {
    public record Report(long plannedCells, int blocksPerTick, long minimumSinglePhaseTicks,
                         double minimumSinglePhaseSeconds, long minimumThreePhaseTicks,
                         List<String> warnings) {}
    private DungeonPerformanceEstimate() {}
    public static Report of(long cells, int blocksPerTick) {
        if(cells<=0 || blocksPerTick<=0) throw new IllegalArgumentException("Plan lub budzet tickow pusty");
        long ticks=1+(cells-1)/blocksPerTick;
        List<String> notes=new ArrayList<>();
        notes.add("To dolne oszacowanie, bez odczytu danych blokow, IO, lagow i oczekiwania na chunki");
        notes.add("Fazy: zapis kopii, budowanie i odtwarzanie; weryfikacja trwa dodatkowo");
        if(cells>100000)notes.add("Bardzo duzy plan; wymagane testy czasu tickow i restartu na Paper");
        return new Report(cells,blocksPerTick,ticks,ticks/20.0,
            ticks>Long.MAX_VALUE/3?Long.MAX_VALUE:ticks*3,List.copyOf(notes));
    }
}
