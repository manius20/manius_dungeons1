package pl.s16.dungeons;

import java.util.ArrayList;
import java.util.List;

/** Pure validation for a 7x7 entrance footprint. No Bukkit access or world edits. */
public final class EntranceSurfaceRules {
    public record Sample(int dx, int dz, int height, String ground,
                         boolean clearFeet, boolean clearHead, boolean fluidNearby) {}
    public record Result(boolean allowed, int minHeight, int maxHeight, List<String> reasons) {}
    private EntranceSurfaceRules() {}

    public static Result check(List<Sample> samples, int floorY, int minWorldY, int maxWorldY) {
        List<String> errors = new ArrayList<>();
        if (samples == null || samples.size() != 49)
            return new Result(false,0,0,List.of("Niekompletne badanie 7x7: wymagane 49 probek"));
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
        boolean[][] seen=new boolean[7][7];
        for(Sample s:samples) {
            if(s==null || s.dx() < -3 || s.dx() > 3 || s.dz() < -3 || s.dz() > 3) {
                errors.add("Bledna pozycja probki");continue;
            }
            int xx=s.dx()+3, zz=s.dz()+3;
            if(seen[xx][zz])errors.add("Powtorzona probka gruntu");
            seen[xx][zz]=true;
            min=Math.min(min,s.height()); max=Math.max(max,s.height());
            if(!DungeonMultiEntrancePlan.naturalSurface(s.ground()))
                errors.add("Nienaturalny grunt przy wejsciu: "+s.ground());
            if(!s.clearFeet()||!s.clearHead())errors.add("Zasloniete przejscie nad powierzchnia");
            if(s.fluidNearby())errors.add("Woda lub lawa przy otworze wejsciowym");
        }
        for(boolean[] line:seen)for(boolean present:line)if(!present) {
            errors.add("Brakuje probki z terenu 7x7");break;
        }
        if(min==Integer.MAX_VALUE)return new Result(false,0,0,List.copyOf(errors));
        if(max-min>2)errors.add("Zbyt stromy teren: roznica "+(max-min)+" blokow");
        if(!DungeonMultiEntrancePlan.validHeights(floorY,(min+max)/2,min,max,minWorldY,maxWorldY))
            errors.add("Niewlasciwa glebokosc lub wysokosc szybu");
        return new Result(errors.isEmpty(),min,max,List.copyOf(errors));
    }
}
