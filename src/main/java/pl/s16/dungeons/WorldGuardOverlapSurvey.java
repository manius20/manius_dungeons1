package pl.s16.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Read-only optional WorldGuard 7 bridge. Does not link against WorldGuard at compile time.
 * Unknown API/disabled plugin -> unverified, NEVER permission to modify the world.
 * WorldGuard's __global__ region is skipped: it has no meaningful physical footprint.
 */
public final class WorldGuardOverlapSurvey {
    public record Verdict(boolean verified, boolean overlaps, String details) {}
    private WorldGuardOverlapSurvey() {}

    public static Verdict inspect(World world, int minX, int minZ, int maxX, int maxZ) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (plugin == null || !plugin.isEnabled())
            return new Verdict(false, false, "WorldGuard nie jest aktywny: nie zweryfikowano regionow");
        try {
            // com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(org.bukkit.World)
            Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter", false, plugin.getClass().getClassLoader());
            Method adapt = adapter.getMethod("adapt", World.class);
            Object weWorld = adapt.invoke(null, world);

            Class<?> wgType = Class.forName("com.sk89q.worldguard.WorldGuard", false, plugin.getClass().getClassLoader());
            Object wg = wgType.getMethod("getInstance").invoke(null);
            Object platform = wgType.getMethod("getPlatform").invoke(wg);
            Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Method get = null;
            for (Method m : container.getClass().getMethods())
                if (m.getName().equals("get") && m.getParameterCount() == 1
                    && m.getParameterTypes()[0].isInstance(weWorld)) {get = m; break;}
            if (get == null) throw new ReflectiveOperationException("Brak RegionContainer.get(World)");
            Object manager = get.invoke(container, weWorld);
            if (manager == null) return new Verdict(false, false, "WorldGuard nie udostepnil RegionManager dla tego swiata");
            Object regions = manager.getClass().getMethod("getRegions").invoke(manager);
            if (!(regions instanceof Map<?,?> map)) throw new ReflectiveOperationException("Nieznana lista regionow WorldGuard");
            for (Object object : map.values()) {
                String name = String.valueOf(object.getClass().getMethod("getId").invoke(object));
                if (name.equalsIgnoreCase("__global__")) continue;
                Object low = object.getClass().getMethod("getMinimumPoint").invoke(object);
                Object high = object.getClass().getMethod("getMaximumPoint").invoke(object);
                int rx1 = (int) low.getClass().getMethod("getBlockX").invoke(low);
                int rz1 = (int) low.getClass().getMethod("getBlockZ").invoke(low);
                int rx2 = (int) high.getClass().getMethod("getBlockX").invoke(high);
                int rz2 = (int) high.getClass().getMethod("getBlockZ").invoke(high);
                if (RectangleCollision.intersects(minX,minZ,maxX,maxZ,rx1,rz1,rx2,rz2))
                    return new Verdict(true,true,"Kolizja z regionem WorldGuard: "+name);
            }
            return new Verdict(true,false,"Sprawdzono regiony WorldGuard (stan biezacy)");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            return new Verdict(false, true, "WorldGuard: nie mozna zweryfikowac regionow: "+ex.getClass().getSimpleName());
        }
    }
}
