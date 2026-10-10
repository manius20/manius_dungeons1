package pl.s16.dungeons;

import org.bukkit.World;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Live read-only bridge for the EXACT S16Countries implementation used by
 * manius_gildie 2.0. Reflective integration avoids bundling/modifying that plugin.
 * Fail-closed when the plugin exists but its API cannot be read.
 */
public final class CountryOverlapSurvey {
    public record Verdict(boolean verified, boolean overlaps, String details) {}
    private CountryOverlapSurvey() {}

    public static Verdict inspect(World world,int minX,int minZ,int maxX,int maxZ) {
        Plugin countries=Bukkit.getPluginManager().getPlugin("S16Countries");
        if(countries==null || !countries.isEnabled())
            return new Verdict(false,false,"S16Countries nie jest wlaczony; claimy niezweryfikowane");
        try {
            Object store=countries.getClass().getMethod("store").invoke(countries);
            Object regions=countries.getClass().getMethod("regions").invoke(countries);
            Collection<?> nations=(Collection<?>)store.getClass().getMethod("all").invoke(store);
            int left=Math.floorDiv(minX,16),right=Math.floorDiv(maxX,16);
            int top=Math.floorDiv(minZ,16),bottom=Math.floorDiv(maxZ,16);
            UUID worldId=world.getUID();
            for(Object nation:nations) {
                String name=String.valueOf(nation.getClass().getField("name").get(nation));
                Collection<?> claims=(Collection<?>)nation.getClass().getField("claims").get(nation);
                for(Object claim:claims) {
                    Class<?> type=claim.getClass();
                    UUID cw=(UUID)type.getMethod("world").invoke(claim);
                    int cx=(int)type.getMethod("x").invoke(claim);
                    int cz=(int)type.getMethod("z").invoke(claim);
                    if(cw.equals(worldId) && cx>=left && cx<=right && cz>=top && cz<=bottom)
                        return new Verdict(true,true,"Kolizja z claimem panstwa "+name);
                }
            }
            Collection<?> allRegions=(Collection<?>)regions.getClass().getMethod("all").invoke(regions);
            for(Object region:allRegions) {
                Class<?> type=region.getClass();
                UUID rw=(UUID)type.getMethod("worldId").invoke(region);
                if(!worldId.equals(rw))continue;
                int rx1=(int)type.getMethod("minX").invoke(region);
                int rz1=(int)type.getMethod("minZ").invoke(region);
                int rx2=(int)type.getMethod("maxX").invoke(region);
                int rz2=(int)type.getMethod("maxZ").invoke(region);
                if(minX<=rx2 && maxX>=rx1 && minZ<=rz2 && maxZ>=rz1) {
                    String name=String.valueOf(type.getMethod("name").invoke(region));
                    return new Verdict(true,true,"Kolizja z regionem admina "+name);
                }
            }
            return new Verdict(true,false,"Sprawdzono claimy i regiony S16Countries (stan biezacy)");
        } catch(ReflectiveOperationException | ClassCastException | SecurityException e) {
            return new Verdict(false,true,"Nie udalo sie zweryfikowac claimow S16Countries: "+e.getClass().getSimpleName());
        }
    }
}
