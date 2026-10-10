package pl.s16.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.time.LocalDate;
import java.util.*;

/** Bounded read-only exploration; tries a few candidates per tick without loading chunks. */
public final class SiteSearchService {
    private final ManiusDungeons plugin;
    private final Map<UUID,BukkitTask> searches=new HashMap<>();
    SiteSearchService(ManiusDungeons plugin){this.plugin=plugin;}

    public void search(Player player,DungeonSchedule event,LocalDate day,int roomCount) {
        if(searches.containsKey(player.getUniqueId()))throw new IllegalStateException("Masz juz aktywne wyszukiwanie; poczekaj na wynik");
        if(!event.occursOn(day))throw new IllegalArgumentException("Wydarzenie nie przypada na ta date");
        long seed=Objects.hash(event.id,day);
        Random shape=new Random(seed);
        int rooms=roomCount>0?roomCount:plugin.getConfig().getInt("safety.default-rooms-min",15)
            +shape.nextInt(plugin.getConfig().getInt("safety.default-rooms-max",50)-plugin.getConfig().getInt("safety.default-rooms-min",15)+1);
        var layout=DungeonLayoutPlanner.generate(rooms,1,seed,0,0);
        int tries=Math.max(1,Math.min(128,plugin.getConfig().getInt("safety.site-search-attempts",48)));
        int searchRadius=Math.max(32,Math.min(512,plugin.getConfig().getInt("safety.site-search-radius-blocks",192)));
        int perTick=Math.max(1,Math.min(4,plugin.getConfig().getInt("safety.site-search-attempts-per-tick",2)));
        World world=player.getWorld();int centerX=player.getLocation().getBlockX(),centerZ=player.getLocation().getBlockZ();
        Random random=new Random(seed ^ player.getUniqueId().getMostSignificantBits() ^ world.getFullTime());
        String[] lastReason={"brak danych"};int[] attempted={0},unloaded={0};
        UUID who=player.getUniqueId();
        BukkitTask task=Bukkit.getScheduler().runTaskTimer(plugin,()->{
            if(!player.isOnline() || player.getWorld()!=world) {stop(who);return;}
            for(int i=0;i<perTick && attempted[0]<tries;i++) {
                attempted[0]++;
                int x=centerX+random.nextInt(searchRadius*2+1)-searchRadius;
                int z=centerZ+random.nextInt(searchRadius*2+1)-searchRadius;
                var result=DungeonSiteSurvey.inspect(world,layout,x,z,
                    plugin.getConfig().getInt("safety.entrance-radius-from-zero",3500),
                    plugin.getConfig().getInt("safety.world-coordinate-limit",4000),
                    plugin.getConfig().getInt("safety.closed-protection-margin",50));
                if(result.preliminaryMatch()) {
                    var first=layout.rooms().get(0);
                    int ox=x-first.x(), oz=z-first.z();
                    int margin=plugin.getConfig().getInt("safety.closed-protection-margin",50);
                    CountryOverlapSurvey.Verdict country=CountryOverlapSurvey.inspect(world,
                        ox-margin-3,oz-margin-3,ox+layout.width()+margin+3,oz+layout.length()+margin+3);
                    if(country.overlaps()) {lastReason[0]=country.details();continue;}
                    WorldGuardOverlapSurvey.Verdict wg=WorldGuardOverlapSurvey.inspect(world,
                        ox-margin-3,oz-margin-3,ox+layout.width()+margin+3,oz+layout.length()+margin+3);
                    if(wg.overlaps()) {lastReason[0]=wg.details();continue;}
                    // An absent/unreadable protection plugin is UNKNOWN, never certified safe.
                    if(plugin.getConfig().getBoolean("safety.require-country-check-for-site-results",true) && !country.verified()) {
                        lastReason[0]=country.details();continue;
                    }
                    if(plugin.getConfig().getBoolean("safety.require-worldguard-check-for-site-results",true) && !wg.verified()) {
                        lastReason[0]=wg.details();continue;
                    }
                    player.sendMessage(plugin.prefix()+plugin.color("&aWSTEPNA lokalizacja: &fX="+x+" Y="+result.surfaceY()+" Z="+z+
                        "&7; pietro Y="+result.floorY()+"; "+rooms+" pokoi; proby: "+attempted[0]+"."));
                    player.sendMessage(plugin.prefix()+plugin.color("&7S16Countries: "+country.details()));
                    player.sendMessage(plugin.prefix()+plugin.color("&7WorldGuard: "+wg.details()));
                    player.sendMessage(plugin.prefix()+plugin.color("&cUWAGA: pelne badanie budowli pod ziemia i snapshot NIE sa jeszcze zweryfikowane. NIE WOLNO tutaj generowac!"));
                    stop(who);return;
                }
                if(result.reasons().stream().anyMatch(s->s.contains("niewczytane chunki")))unloaded[0]++;
                if(!result.reasons().isEmpty())lastReason[0]=result.reasons().get(0);
            }
            if(attempted[0]>=tries) {
                player.sendMessage(plugin.prefix()+plugin.color("&eNie znaleziono wstepnej lokalizacji w "+attempted[0]+" probach (niewczytane chunki: "+unloaded[0]+")."));
                player.sendMessage(plugin.prefix()+plugin.color("&7Przykladowa przyczyna: "+lastReason[0]+". Sprobuj w innym terenie z zaladowanymi chunkami."));
                stop(who);
            }
        },1L,1L);
        searches.put(who,task);
    }
    public void stop(UUID who){BukkitTask task=searches.remove(who);if(task!=null)task.cancel();}
    public void stopAll(){for(BukkitTask task:searches.values())task.cancel();searches.clear();}
}
