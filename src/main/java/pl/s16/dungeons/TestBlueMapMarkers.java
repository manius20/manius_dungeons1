package pl.s16.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Soft BlueMap integration via public API and reflection (no hard dependency in the JAR).
 * Markers are session-only and recalculated from ACTUAL active test jobs.
 * A scheduled-only dungeon NEVER causes a marker to be published.
 */
public final class TestBlueMapMarkers {
    private static final String GROUP="manius_dungeons_test";
    private final ManiusDungeons plugin;
    private String lastError="";
    TestBlueMapMarkers(ManiusDungeons plugin){this.plugin=plugin;}

    @SuppressWarnings("unchecked")
    void sync(List<RecoveryManager.TestEntrance> visible) {
        // Even if switched OFF via /reload, remove any markers created previously.
        if(!plugin.getConfig().getBoolean("entrances.enable-test-bluemap-markers",false)) visible=List.of();
        try {
            Object api=api();
            if(api==null)return;
            // Remove only OUR marker set from all maps before publishing current set.
            for(Object map:(Collection<?>)call(api,"getMaps"))
                ((Map<Object,Object>)call(map,"getMarkerSets")).remove(GROUP);
            if(visible.isEmpty())return;
            Map<UUID,List<RecoveryManager.TestEntrance>> grouped=new LinkedHashMap<>();
            for(var entrance:visible)grouped.computeIfAbsent(entrance.worldUid(),x->new ArrayList<>()).add(entrance);
            for(var group:grouped.entrySet()) {
                World world=Bukkit.getWorld(group.getKey());
                if(world==null)continue;
                Object result=api.getClass().getMethod("getWorld",Object.class).invoke(api,world);
                if(!(result instanceof Optional<?> optional)||optional.isEmpty())continue;
                Object bluemapWorld=optional.get();
                Object builder=builder("de.bluecolored.bluemap.api.markers.MarkerSet");
                call(builder,"label",String.class,"S16 SMP 2 - Dungeony TEST");
                Object markerSet=call(builder,"build");
                Map<Object,Object> all=(Map<Object,Object>)call(markerSet,"getMarkers");
                for(var entrance:group.getValue()) {
                    Object markerBuilder=builder("de.bluecolored.bluemap.api.markers.POIMarker");
                    call(markerBuilder,"label",String.class,"TEST: " + entrance.id() + " (" + entrance.x() + ", " + entrance.z() + ")");
                    markerBuilder.getClass().getMethod("position",double.class,double.class,double.class)
                        .invoke(markerBuilder,entrance.x()+0.5,(double)entrance.y(),entrance.z()+0.5);
                    all.put("test-"+entrance.id()+"-"+entrance.x()+"-"+entrance.z(),call(markerBuilder,"build"));
                }
                for(Object map:(Collection<?>)call(bluemapWorld,"getMaps"))
                    ((Map<Object,Object>)call(map,"getMarkerSets")).put(GROUP,markerSet);
            }
            lastError="";
        } catch(Exception | LinkageError problem) {
            String key=problem.getClass().getSimpleName()+": "+problem.getMessage();
            if(!key.equals(lastError)) {
                plugin.getLogger().warning("BlueMap TEST: nie mozna zsynchronizowac znacznikow: "+key);
                lastError=key;
            }
        }
    }
    void close(){sync(List.of());}
    private Object api() throws Exception {
        Plugin bm=Bukkit.getPluginManager().getPlugin("BlueMap");
        if(bm==null||!bm.isEnabled())return null;
        Class<?> clazz=bm.getClass().getClassLoader().loadClass("de.bluecolored.bluemap.api.BlueMapAPI");
        Object value=clazz.getMethod("getInstance").invoke(null);
        return value instanceof Optional<?> opt?opt.orElse(null):null;
    }
    private Object builder(String name)throws Exception {
        // Use BlueMap plugin classloader, not current plugin loader.
        Plugin bm=Bukkit.getPluginManager().getPlugin("BlueMap");
        return bm.getClass().getClassLoader().loadClass(name).getMethod("builder").invoke(null);
    }
    private static Object call(Object object,String method)throws Exception {
        return object.getClass().getMethod(method).invoke(object);
    }
    private static Object call(Object object,String method,Class<?> type,Object param)throws Exception {
        return object.getClass().getMethod(method,type).invoke(object,param);
    }
}
