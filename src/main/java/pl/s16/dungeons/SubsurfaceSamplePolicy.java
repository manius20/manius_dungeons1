package pl.s16.dungeons;

import java.util.ArrayList;
import java.util.List;

/**
 * Conservative underground sampling policy. This is a PREVIEW only: it cannot
 * certify that ALL blocks are safe; full block checks and a snapshot still run
 * before a test-world mutation. No Bukkit dependency.
 */
public final class SubsurfaceSamplePolicy {
    public record Sample(String blockName, boolean fluid, boolean natural, boolean cave) {}
    public record Report(boolean acceptableSample, int samples, int caves, int fluids,
                         int suspicious, List<String> reasons) {}
    private SubsurfaceSamplePolicy() {}

    public static Report evaluate(List<Sample> sample, int minimumSamples, double maxCaveFraction) {
        if(minimumSamples < 1 || !Double.isFinite(maxCaveFraction) || maxCaveFraction < 0 || maxCaveFraction > 1)
            throw new IllegalArgumentException("Nieprawidlowe ograniczenia probkowania");
        if(sample == null) return new Report(false,0,0,0,0,List.of("Brak probek podziemia"));
        int caves=0,fluids=0,suspicious=0;
        for (Sample cell:sample) {
            if(cell==null) {suspicious++; continue;}
            if(cell.cave()) caves++;
            if(cell.fluid()) fluids++;
            if(!cell.natural()) suspicious++;
        }
        List<String> errors=new ArrayList<>();
        if(sample.size()<minimumSamples)errors.add("Za malo probek podziemia: "+sample.size()+" / "+minimumSamples);
        if(fluids>0)errors.add("Wykryto plyn w bryle dungeona: "+fluids+" probek");
        if(suspicious>0)errors.add("Wykryto bloki nienaturalne lub z danymi: "+suspicious+" probek");
        if(!sample.isEmpty() && caves>(int)Math.floor(sample.size()*maxCaveFraction))
            errors.add("Za duzo jaskin/pustek w podziemiu: "+caves+" / "+sample.size());
        return new Report(errors.isEmpty(),sample.size(),caves,fluids,suspicious,List.copyOf(errors));
    }
}
