package pl.s16.dungeons;

import java.util.ArrayList;
import java.util.List;

/** Validates all 49 footprint cells, slopes, liquids and head clearance. */
public final class EntranceSurfaceRulesSelfTest {
    private static List<EntranceSurfaceRules.Sample> terrain(int base) {
        List<EntranceSurfaceRules.Sample> items=new ArrayList<>();
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)
            items.add(new EntranceSurfaceRules.Sample(dx,dz,base,"GRASS_BLOCK",true,true,false));
        return items;
    }
    private static void check(boolean ok, String why) {if(!ok)throw new AssertionError(why);}
    public static void main(String[] args) {
        List<EntranceSurfaceRules.Sample> flat=terrain(90);
        check(EntranceSurfaceRules.check(flat,66,-64,320).allowed(),"flat ground must pass");
        var missing=new ArrayList<>(flat);missing.remove(0);
        check(!EntranceSurfaceRules.check(missing,66,-64,320).allowed(),"missing footprint");
        var duplicate=new ArrayList<>(flat);duplicate.set(0,flat.get(1));
        check(!EntranceSurfaceRules.check(duplicate,66,-64,320).allowed(),"duplicate footprint");
        var water=new ArrayList<>(flat);
        water.set(25,new EntranceSurfaceRules.Sample(-3+25/7,-3+25%7,90,"GRASS_BLOCK",true,true,true));
        check(!EntranceSurfaceRules.check(water,66,-64,320).allowed(),"fluid at entry");
        var roof=new ArrayList<>(flat);roof.set(10,new EntranceSurfaceRules.Sample(-2,0,90,"GRASS_BLOCK",true,false,false));
        check(!EntranceSurfaceRules.check(roof,66,-64,320).allowed(),"roof above head");
        var slope=new ArrayList<>(flat);slope.set(0,new EntranceSurfaceRules.Sample(-3,-3,94,"STONE",true,true,false));
        check(!EntranceSurfaceRules.check(slope,66,-64,320).allowed(),"slope 4 blocks");
        check(!EntranceSurfaceRules.check(flat,88,-64,320).allowed(),"shaft too shallow");
        check(!EntranceSurfaceRules.check(flat,-30,-64,320).allowed(),"shaft too deep");
        System.out.println("EntranceSurfaceRulesSelfTest OK");
    }
}
