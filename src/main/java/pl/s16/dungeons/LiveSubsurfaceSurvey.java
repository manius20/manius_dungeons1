package pl.s16.dungeons;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Synchronous, READ-ONLY sample of the exact underground blueprint footprint.
 * Never loads chunks. This is advisory, never a substitute for whole-plan
 * snapshot/validation; intended for the dedicated test world.
 */
public final class LiveSubsurfaceSurvey {
    public record Report(boolean accepted, SubsurfaceSamplePolicy.Report samples, List<String> errors) {}
    private LiveSubsurfaceSurvey() {}

    public static Report inspect(World world, DungeonBlueprint blueprint,int originX,int floorY,int originZ,
                                 int maxSamples, double maxCaveFraction) {
        if(world==null || blueprint==null || maxSamples<32 || maxSamples>8192)
            throw new IllegalArgumentException("Nieprawidlowy swiat, plan lub limit skanowania");
        var bounds=blueprint.bounds();
        List<String> errors=new ArrayList<>();
        if(floorY+bounds.minY()<world.getMinHeight() || floorY+bounds.maxY()>=world.getMaxHeight())
            errors.add("Plan poza wysokoscia swiata");
        long minX=(long)originX+bounds.minX(), maxX=(long)originX+bounds.maxX();
        long minZ=(long)originZ+bounds.minZ(), maxZ=(long)originZ+bounds.maxZ();
        if(minX<Integer.MIN_VALUE || maxX>Integer.MAX_VALUE || minZ<Integer.MIN_VALUE || maxZ>Integer.MAX_VALUE)
            errors.add("Plan poza dopuszczalnymi wspolrzednymi");
        if(!errors.isEmpty())return new Report(false,null,List.copyOf(errors));
        for(int cx=Math.floorDiv((int)minX,16);cx<=Math.floorDiv((int)maxX,16);cx++)
            for(int cz=Math.floorDiv((int)minZ,16);cz<=Math.floorDiv((int)maxZ,16);cz++)
                if(!world.isChunkLoaded(cx,cz)) {
                    errors.add("Niewczytane chunki podziemia; skan NIE wczytuje ich samoczynnie");
                    return new Report(false,null,List.copyOf(errors));
                }
        int total=blueprint.cells().size();
        // Bound API calls; no full 100-room scan synchronously in a command.
        List<Integer> selected=SubsurfaceSampleIndices.select(total,maxSamples);
        List<SubsurfaceSamplePolicy.Sample> samples=new ArrayList<>(selected.size());
        int i=0, next=0;
        for(Map.Entry<DungeonBlueprint.Pos,DungeonBlueprint.Cell> entry:blueprint.cells().entrySet()) {
            if(next>=selected.size())break;
            if(i++ != selected.get(next))continue;
            next++;
            var p=entry.getKey();
            Block block=world.getBlockAt(originX+p.x(),floorY+p.y(),originZ+p.z());
            Material material=block.getType();
            boolean fluid=block.isLiquid() || material==Material.WATER || material==Material.LAVA;
            boolean cave=material.isAir();
            // Conservative audit: only basic geology and air are accepted here.
            // Actual build check remains independent and must reject unknown blocks.
            boolean natural=material.isAir() || switch(material) {
                case STONE, DEEPSLATE, TUFF, ANDESITE, DIORITE, GRANITE,
                     DIRT, COARSE_DIRT, ROOTED_DIRT, GRAVEL, COBBLESTONE,
                     CALCITE, DRIPSTONE_BLOCK, BLACKSTONE, BASALT -> true;
                default -> false;
            };
            samples.add(new SubsurfaceSamplePolicy.Sample(material.name(),fluid,natural,cave));
        }
        var result=SubsurfaceSamplePolicy.evaluate(samples,Math.min(32,total),maxCaveFraction);
        errors.addAll(result.reasons());
        return new Report(errors.isEmpty(),result,List.copyOf(errors));
    }
}
