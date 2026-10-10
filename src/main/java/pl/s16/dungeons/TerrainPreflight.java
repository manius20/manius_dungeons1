package pl.s16.dungeons;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import java.util.*;

/**
 * Ostrozny podglad lokalizacji, uruchamiany komenda tylko przez admina.
 * Nie wczytuje nowych chunkow, nie modyfikuje blokow. NIE sprawdza jeszcze
 * claimow ani WorldGuard; wynik NIE jest zgoda na generowanie w SMP.
 */
public final class TerrainPreflight {
    public record Result(boolean allowed, List<String> reasons, int groundMin, int groundMax,
                         int suggestedFloorY, long blueprintCells) {}
    private TerrainPreflight() {}

    public static Result inspect(Player player,DungeonLayoutPlanner.Layout layout,
                                 DungeonBlueprint blueprint,int radius,int limit,int margin) {
        World world=player.getWorld();
        int centerX=player.getLocation().getBlockX(),centerZ=player.getLocation().getBlockZ();
        // Srodek prostokata planu przypada na gracza. Wejscie nie jest jeszcze zbudowane.
        int originX=centerX-layout.width()/2,originZ=centerZ-layout.length()/2;
        List<String> errors=new ArrayList<>();
        var pos=PlacementRules.validate(originX,originZ,centerX,centerZ,blueprint.bounds(),radius,limit,margin);
        errors.addAll(pos.reasons());
        // Pelny test po siatce max ok. 25x25; nie wolno generowac w niezaladowanym terenie.
        int minY=Integer.MAX_VALUE,maxY=Integer.MIN_VALUE;
        int step=16;
        boolean unknown=false,caves=false;
        int sampled=0;
        for(int xx=0;xx<=layout.width();xx+=step)for(int zz=0;zz<=layout.length();zz+=step) {
            int x=originX+xx,z=originZ+zz;
            if(!world.isChunkLoaded(x>>4,z>>4)) { unknown=true;continue; }
            int y=world.getHighestBlockYAt(x,z);
            minY=Math.min(minY,y);maxY=Math.max(maxY,y);sampled++;
        }
        if(unknown)errors.add("Czesc chunkow jest niewczytana — sprawdzanie przerwano bez ich ladowania");
        if(sampled==0)errors.add("Brak probek terenu");
        // Pod jeziorami dungeony sa dozwolone, ale SAMO WEJSCIE musi byc na ladzie.
        if(world.isChunkLoaded(centerX>>4,centerZ>>4)) {
            int entryY=world.getHighestBlockYAt(centerX,centerZ);
            Material entryTop=world.getBlockAt(centerX,entryY,centerZ).getType();
            Material entryAbove=world.getBlockAt(centerX,entryY+1,centerZ).getType();
            if(entryTop==Material.WATER || entryTop==Material.LAVA || entryTop==Material.ICE
               || entryTop==Material.PACKED_ICE || entryTop==Material.BLUE_ICE
               || entryAbove==Material.WATER || entryAbove==Material.LAVA)
                errors.add("Wejscie znajduje sie w wodzie, lawie lub na lodzie");
        } else errors.add("Chunk planowanego wejscia jest niewczytany");
        if(sampled>0 && maxY-minY>12)errors.add("Zbyt nierowny teren: roznica wysokosci "+(maxY-minY)+" blokow");
        int floor=sampled==0?0:minY-24;
        if(sampled>0 && floor<world.getMinHeight()+10)errors.add("Za malo miejsca ponad dolna granica swiata");
        if(sampled>0) {
            // Sprawdzamy probki powietrza/wody w planowanej objetosci.
            for(int xx=0;xx<=layout.width();xx+=step)for(int zz=0;zz<=layout.length();zz+=step) {
                int x=originX+xx,z=originZ+zz;
                if(!world.isChunkLoaded(x>>4,z>>4))continue;
                for(int yy=floor;yy<=floor+6;yy+=3) {
                    Material m=world.getBlockAt(x,yy,z).getType();
                    if(m.isAir()||m==Material.WATER||m==Material.LAVA) { caves=true;break; }
                }
                if(caves)break;
            }
            if(caves)errors.add("Wykryto prawdopodobna jaskinie lub plyn wewnatrz obrysu");
        }
        // Nadal brak bezpiecznego wykrycia cudzych budowli/claimow.
        errors.add("Nie sprawdzono budowli, claimow gildii ani WorldGuard — to tylko wstepna analiza");
        return new Result(false,List.copyOf(errors),sampled==0?0:minY,sampled==0?0:maxY,floor,blueprint.cells().size());
    }
}
