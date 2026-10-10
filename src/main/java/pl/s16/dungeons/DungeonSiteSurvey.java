package pl.s16.dungeons;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import java.util.ArrayList;
import java.util.List;

/**
 * WSTEPNA, tylko-odczytowa ocena lokalizacji. Fail-closed: nie laduje chunkow,
 * nie daje zgody na zmiany swiata ani nie twierdzi, ze sprawdzila gildie/WG.
 * Wywolywac na glownym watku serwera.
 */
public final class DungeonSiteSurvey {
    public record Result(boolean preliminaryMatch, List<String> reasons, int surfaceY,
                         int floorY, int minGround, int maxGround, int shaftCells) {}
    private DungeonSiteSurvey() {}

    public static Result inspect(World world, DungeonLayoutPlanner.Layout layout,
                                 int entranceX, int entranceZ, int radius, int limit, int margin) {
        List<String> reasons=new ArrayList<>();
        var first=layout.rooms().get(0);
        int ox=entranceX-first.x(), oz=entranceZ-first.z();
        var bounds=new DungeonBlueprint.Bounds(0,layout.width(),0,6,0,layout.length());
        reasons.addAll(PlacementRules.validate(ox,oz,entranceX,entranceZ,bounds,radius,limit,margin).reasons());
        int minX=ox-3, maxX=ox+layout.width()+3, minZ=oz-3, maxZ=oz+layout.length()+3;
        // Wymagamy WSZYSTKICH chunkow w obrebie planu, nie tylko w rogach czy siatce.
        for(int cx=Math.floorDiv(minX,16);cx<=Math.floorDiv(maxX,16);cx++)
            for(int cz=Math.floorDiv(minZ,16);cz<=Math.floorDiv(maxZ,16);cz++)
                if(!world.isChunkLoaded(cx,cz)) {
                    reasons.add("Czesc terenu ma niewczytane chunki (bez ich automatycznego ladowania)");
                    return new Result(false,List.copyOf(reasons),0,0,0,0,0);
                }
        int minY=Integer.MAX_VALUE,maxY=Integer.MIN_VALUE;
        // Rozmieszczenie probek w obrebie calej konstrukcji, lacznie z ostatnia krawedzia.
        for(int x=minX;x<=maxX;x+=16)for(int z=minZ;z<=maxZ;z+=16) {
            int y=world.getHighestBlockYAt(x,z);
            minY=Math.min(minY,y);maxY=Math.max(maxY,y);
        }
        int sy=world.getHighestBlockYAt(entranceX,entranceZ);
        minY=Math.min(minY,sy);maxY=Math.max(maxY,sy);
        if(maxY-minY>24)reasons.add("Bardzo nierowny teren pod planem (roznica "+(maxY-minY)+" blokow)");
        // Wejscie: naturalny, staly lad, nie woda, rosliny, dach, budowla ani stromizna.
        int low=Integer.MAX_VALUE, high=Integer.MIN_VALUE;
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++) {
            int x=entranceX+dx,z=entranceZ+dz;
            int surface=world.getHighestBlockYAt(x,z);
            low=Math.min(low,surface);high=Math.max(high,surface);
            Block top=world.getBlockAt(x,surface,z);
            Material mat=top.getType();
            if(!naturalSurface(mat)) {
                reasons.add("Wejscie nie lezy na pewnym naturalnym ladzie: "+mat);
                dx=4;break;
            }
            if(surface+2>=world.getMaxHeight() || !world.getBlockAt(x,surface+1,z).isPassable()) {
                reasons.add("Wejscie moze byc zasloniete od gory");
                dx=4;break;
            }
        }
        if(high-low>2)reasons.add("Wejscie wymaga w miare plaskiego terenu (roznica "+(high-low)+" blokow)");
        int floor=minY-24;
        if(floor<world.getMinHeight()+10 || floor+6>=world.getMaxHeight()-8)
            reasons.add("Brak zapasu miejsca na podziemne pokoje");
        int shaftCells=0;
        if(sy-floor<12 || sy-floor>96)reasons.add("Za dlugi lub zbyt krotki szyb wejscia");
        else shaftCells=EntranceShaftPlan.design(entranceX,entranceZ,floor,sy).cells().size();
        // Nie dopuszczamy nieznanej jaskini/przeplywu w wybranych probkach kondygnacji.
        if(floor>=world.getMinHeight() && floor+6<world.getMaxHeight()) {
            boolean found=false;
            for(int x=minX;x<=maxX && !found;x+=24)for(int z=minZ;z<=maxZ && !found;z+=24)
                for(int y=floor;y<=floor+6;y+=3) {
                    Material m=world.getBlockAt(x,y,z).getType();
                    if(m.isAir() || m==Material.WATER || m==Material.LAVA) {
                        reasons.add("Wykryto jaskinie/powietrze/wode w probce podziemia");
                        found=true;break;
                    }
                }
        }
        return new Result(reasons.isEmpty(),List.copyOf(reasons),sy,floor,minY,maxY,shaftCells);
    }

    private static boolean naturalSurface(Material m) {return switch(m) {
        case GRASS_BLOCK,DIRT,COARSE_DIRT,PODZOL,MYCELIUM,STONE,ANDESITE,DIORITE,
             GRANITE,DEEPSLATE,TUFF,SAND,RED_SAND,GRAVEL,CLAY,MOSS_BLOCK,
             TERRACOTTA,WHITE_TERRACOTTA,ORANGE_TERRACOTTA,YELLOW_TERRACOTTA,
             RED_TERRACOTTA,BROWN_TERRACOTTA,LIGHT_GRAY_TERRACOTTA -> true;
        default -> false;
    };}
}
