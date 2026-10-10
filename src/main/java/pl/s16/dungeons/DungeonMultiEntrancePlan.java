package pl.s16.dungeons;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure planning rules for real test-world shafts. Bukkit performs a second
 * block-by-block ground survey BEFORE the snapshot/build transaction begins.
 * This class changes no Minecraft block.
 */
public final class DungeonMultiEntrancePlan {
    public record Candidate(int roomIndex, int x, int z) {}
    public record Site(int roomIndex, int x, int z, int surfaceY) {}
    private DungeonMultiEntrancePlan() {}

    public static List<Candidate> locations(DungeonLayoutPlanner.Layout layout,
                                            int originX, int originZ, int radius) {
        List<DungeonEntrancePreview.ProposedEntrance> preview=DungeonEntrancePreview.forLayout(layout);
        if(preview.size()>3)throw new IllegalArgumentException("Testowe szyby: maksymalnie 3");
        List<Candidate> result=new ArrayList<>();
        Set<String> used=new HashSet<>();
        for(var entrance:preview) {
            int x=Math.addExact(originX,entrance.relativeX());
            int z=Math.addExact(originZ,entrance.relativeZ());
            if(Math.hypot(x,z)>radius)
                throw new IllegalArgumentException("Wejscie nr "+(result.size()+1)+" poza promieniem "+radius);
            if(!used.add(x+":"+z))throw new IllegalArgumentException("Dwa wejscia w jednym miejscu");
            result.add(new Candidate(entrance.roomIndex(),x,z));
        }
        return List.copyOf(result);
    }

    /** Check *all* surface shafts as one transaction before any world mutation.
     * Each opening reserves a 7x7 survey square, not merely its central 5x5 shaft.
     * A rejected plan cannot partially proceed to snapshot/building. */
    public static void validateSites(List<Site> sites,int expectedCount,int floorY,
                                     int worldMinY,int worldMaxY,int radius) {
        if(sites==null||sites.size()!=expectedCount||expectedCount<1||expectedCount>3)
            throw new IllegalArgumentException("Niekompletna lista szybów 1-3");
        Set<Integer> roomIds=new HashSet<>();
        for(int i=0;i<sites.size();i++) {
            Site a=sites.get(i);
            if(!roomIds.add(a.roomIndex()))throw new IllegalArgumentException("Powtórzony pokój wejściowy");
            if(Math.hypot(a.x(),a.z())>radius)
                throw new IllegalArgumentException("Wejście poza promieniem "+radius);
            if(!validHeights(floorY,a.surfaceY(),a.surfaceY()-1,a.surfaceY()+1,worldMinY,worldMaxY))
                throw new IllegalArgumentException("Nieprawidłowa wysokość szybu nr "+(i+1));
            for(int j=0;j<i;j++) {
                Site b=sites.get(j);
                // +-3 for survey square: touching squares are not independent.
                if(Math.abs((long)a.x()-b.x())<=6 && Math.abs((long)a.z()-b.z())<=6)
                    throw new IllegalArgumentException("Nakładające się strefy wejść nr "+(j+1)+" i "+(i+1));
            }
        }
    }

    public static boolean validHeights(int floorY,int surfaceY,int minGround,int maxGround,
                                       int worldMinY,int worldMaxY) {
        return floorY>=worldMinY+5 && floorY+6<worldMaxY-8
            && surfaceY-floorY>=12 && surfaceY-floorY<=96
            && maxGround-minGround<=2 && minGround>=worldMinY
            && maxGround+2<worldMaxY;
    }

    public static boolean naturalSurface(String material) {
        return switch(material) {
            case "GRASS_BLOCK", "DIRT", "COARSE_DIRT", "PODZOL", "MYCELIUM",
                 "STONE", "ANDESITE", "DIORITE", "GRANITE", "DEEPSLATE", "TUFF",
                 "SAND", "RED_SAND", "GRAVEL", "CLAY", "MOSS_BLOCK", "TERRACOTTA",
                 "WHITE_TERRACOTTA", "ORANGE_TERRACOTTA", "YELLOW_TERRACOTTA",
                 "RED_TERRACOTTA", "BROWN_TERRACOTTA", "LIGHT_GRAY_TERRACOTTA" -> true;
            default -> false;
        };
    }
}
