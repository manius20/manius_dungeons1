package pl.s16.dungeons;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import java.util.ArrayList;
import java.util.List;

/** Read-only ground check for ALL shafts. Must be called synchronously on the Paper main thread.
 * Does not load chunks, change blocks, or approve a protected region. */
public final class LiveEntranceSurvey {
    public record Entry(int roomIndex,int x,int y,int z,int lowGround,int highGround) {}
    public record Report(boolean accepted, List<Entry> entries, List<String> errors) {}
    private LiveEntranceSurvey() {}

    public static Report inspect(World world, List<DungeonMultiEntrancePlan.Candidate> candidates,
                                 int floorY,int radius) {
        List<Entry> entries=new ArrayList<>();
        List<String> errors=new ArrayList<>();
        if(candidates==null||candidates.isEmpty()||candidates.size()>3)
            return new Report(false,List.of(),List.of("Liczba wejsc musi wynosic 1-3"));
        for(int i=0;i<candidates.size();i++) {
            var c=candidates.get(i);
            List<EntranceSurfaceRules.Sample> samples=new ArrayList<>();
            boolean missing=false;
            for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++) {
                int x=c.x()+dx,z=c.z()+dz;
                if(!world.isChunkLoaded(Math.floorDiv(x,16),Math.floorDiv(z,16))) {
                    missing=true;continue;
                }
                int y=world.getHighestBlockYAt(x,z);
                // Never read outside the build height or silently consider water passable.
                if(y<world.getMinHeight()||y+2>=world.getMaxHeight()) {missing=true;continue;}
                Block top=world.getBlockAt(x,y,z);
                Block feet=world.getBlockAt(x,y+1,z);
                Block head=world.getBlockAt(x,y+2,z);
                Material ground=top.getType();
                boolean fluid=ground==Material.WATER||ground==Material.LAVA
                    ||feet.isLiquid()||head.isLiquid();
                samples.add(new EntranceSurfaceRules.Sample(dx,dz,y,ground.name(),
                    feet.isPassable()&&!feet.isLiquid(),head.isPassable()&&!head.isLiquid(),fluid));
            }
            if(missing) {
                errors.add("Wejscie "+(i+1)+" ("+c.x()+","+c.z()+"): niewczytany chunk lub wysokosc poza swiatem");
                continue;
            }
            var result=EntranceSurfaceRules.check(samples,floorY,world.getMinHeight(),world.getMaxHeight());
            if(!result.allowed()) {
                errors.add("Wejscie "+(i+1)+" ("+c.x()+","+c.z()+"): "+String.join("; ",result.reasons()));
                continue;
            }
            int y=world.getHighestBlockYAt(c.x(),c.z());
            entries.add(new Entry(c.roomIndex(),c.x(),y,c.z(),result.minHeight(),result.maxHeight()));
        }
        if(errors.isEmpty()) {
            try {
                DungeonMultiEntrancePlan.validateSites(entries.stream().map(e->
                    new DungeonMultiEntrancePlan.Site(e.roomIndex(),e.x(),e.z(),e.y())).toList(),
                    candidates.size(),floorY,world.getMinHeight(),world.getMaxHeight(),radius);
            } catch(IllegalArgumentException ex) {errors.add(ex.getMessage());}
        }
        return new Report(errors.isEmpty(),List.copyOf(entries),List.copyOf(errors));
    }
}
