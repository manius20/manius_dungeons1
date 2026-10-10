package pl.s16.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.EventPriority;

import java.time.*;
import java.util.*;

/** Only acts on truly ACTIVE, manually generated TEST-world dungeons. */
public final class TestEntranceService implements Listener {
    private final ManiusDungeons plugin;
    private final ScheduleStore schedules;
    private final RecoveryManager recovery;
    private final TestBlueMapMarkers markers;
    private final Set<String> announcedMilestones = new HashSet<>();
    private final Map<UUID,Long> lastDenied = new HashMap<>();
    TestEntranceService(ManiusDungeons p, ScheduleStore s, RecoveryManager r) {
        plugin=p;schedules=s;recovery=r;markers=new TestBlueMapMarkers(p);
    }
    /** Called from the once-per-minute scheduler on Bukkit's main thread. Read-only. */
    void tick(Instant previous, ZonedDateTime now) {
        List<RecoveryManager.TestEntrance> visible = visibleNow(now);
        // Opt-in remains OFF by default: tests must not make public SMP announcements.
        if (plugin.getConfig().getBoolean("entrances.test-public-announcements",false)) {
            Map<String,List<RecoveryManager.TestEntrance>> grouped=new LinkedHashMap<>();
            for(var entry:recovery.activeTestEntrances()) {
                if(!isDedicatedTestWorld(entry))continue;
                grouped.computeIfAbsent(entry.scheduleId()+":"+entry.day(),ignored->new ArrayList<>()).add(entry);
            }
            for(var entries:grouped.values()) {
                var first=entries.get(0);
                DungeonSchedule event=schedules.get(first.scheduleId());
                if(event==null || !schedules.occurs(event,first.day()))continue;
                EventTimeline.Window window=EventTimeline.window(event,first.day(),plugin.timezone());
                Instant start=window.start().toInstant();
                if(!now.toInstant().isBefore(window.end().toInstant()))continue;
                int grace=Math.max(0,Math.min(300,plugin.getConfig().getInt("entrances.test-notice-grace-seconds",90)));
                OptionalInt milestone=DungeonStartNotices.due(previous,now.toInstant(),start,!entries.isEmpty(),grace);
                if(milestone.isEmpty())continue;
                int minutes=milestone.getAsInt();
                String key=first.scheduleId()+":"+first.day()+":"+minutes;
                if(!announcedMilestones.add(key))continue;
                if(minutes==30) {
                    Bukkit.broadcastMessage(plugin.prefix()+plugin.color("&bDungeon &f"+event.id+" &botworzy się za &e30 minut&b!"));
                    if(plugin.getConfig().getBoolean("entrances.test-title-30-minutes",true))
                        for(Player player:Bukkit.getOnlinePlayers())
                            player.sendTitle(plugin.color("&b&lDUNGEON ZA 30 MINUT!"),
                                plugin.color("&f"+event.id+" &7• przygotuj się do wyprawy"),10,70,20);
                    // Reveal coordinates only when their configured visibility window starts.
                    int lead=plugin.getConfig().getInt("settings.reveal-before-minutes",30);
                    if(lead>=30) {
                        int index=0;
                        for(var entry:entries) {
                            index++;
                            Bukkit.broadcastMessage(plugin.prefix()+plugin.color("&bWejście " + index + ": &f"
                                +entry.worldName()+" &7X=&f"+entry.x()+" &7Y=&f"+entry.y()+" &7Z=&f"+entry.z()
                                +" &8(świat testowy)"));
                        }
                    }
                } else if(minutes==10 && plugin.getConfig().getBoolean("entrances.test-details-10-minutes",true)) {
                    String details=DungeonStartNotices.tenMinuteDetails(entries.size(),event.durationMinutes);
                    Bukkit.broadcastMessage(plugin.prefix()+plugin.color("&eDungeon &f"+event.id+
                        " &eotworzy się za 10 minut! &b"+details+" &8(świat testowy)"));
                }
            }
        }
        markers.sync(visible);
        if(announcedMilestones.size()>10000)announcedMilestones.clear();
        if(lastDenied.size()>5000)lastDenied.clear();
    }
    void stop() { markers.close(); }
    List<RecoveryManager.TestEntrance> visibleNow(ZonedDateTime now) {
        int lead=Math.max(0,Math.min(720,plugin.getConfig().getInt("settings.reveal-before-minutes",30)));
        List<RecoveryManager.TestEntrance> answer=new ArrayList<>();
        for(var entrance:recovery.activeTestEntrances()) {
            if(!isDedicatedTestWorld(entrance))continue;
            var event=schedules.get(entrance.scheduleId());
            if(event==null || !schedules.occurs(event,entrance.day()))continue;
            var window=EventTimeline.window(event,entrance.day(),plugin.timezone());
            if(EntranceRevealPolicy.publicVisible(now.toInstant(),window.start().toInstant(),window.end().toInstant(),lead,true)) answer.add(entrance);
        }
        return List.copyOf(answer);
    }
    List<RecoveryManager.TestEntrance> visibleNow() {return visibleNow(ZonedDateTime.now(plugin.timezone()));}
    private boolean isDedicatedTestWorld(RecoveryManager.TestEntrance e) {
        String test=plugin.getConfig().getString("safety.test-world-name","");
        String main=plugin.getConfig().getString("settings.world","");
        return !test.isBlank() && !test.equalsIgnoreCase(main) && test.equals(e.worldName());
    }
    private boolean openNow(RecoveryManager.TestEntrance entrance) {
        var event=schedules.get(entrance.scheduleId());
        if(event==null || !schedules.occurs(event,entrance.day()))return false;
        var window=EventTimeline.window(event,entrance.day(),plugin.timezone());
        return recovery.isOpenedJob(entrance.id())
            && EntranceRevealPolicy.entranceOpen(Instant.now(),window.start().toInstant(),window.end().toInstant(),true);
    }
    /** Cancel crossing INTO the TEST dungeon when outside opening hours. No solid barrier blocks placed. */
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onMove(PlayerMoveEvent e) {
        Location to=e.getTo();Location from=e.getFrom();
        if(to==null || (to.getBlockX()==from.getBlockX() && to.getBlockY()==from.getBlockY()
            && to.getBlockZ()==from.getBlockZ() && to.getWorld()==from.getWorld())) return;
        if(!plugin.getConfig().getBoolean("entrances.enable-test-magic-barrier",true))return;
        if(e.getPlayer().hasPermission("maniusdungeons.barrier.bypass"))return;
        for(var entrance:recovery.activeTestEntrances()) {
            if(!isDedicatedTestWorld(entrance) || openNow(entrance))continue;
            if(entrance.inside(to) && !entrance.inside(from)) {
                e.setCancelled(true);warn(e.getPlayer());return;
            }
        }
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onTeleport(PlayerTeleportEvent e) {
        Location to=e.getTo();
        if(to==null || !plugin.getConfig().getBoolean("entrances.enable-test-magic-barrier",true))return;
        if(e.getPlayer().hasPermission("maniusdungeons.barrier.bypass"))return;
        for(var entrance:recovery.activeTestEntrances()) {
            if(isDedicatedTestWorld(entrance) && !openNow(entrance) && entrance.inside(to)
                && !entrance.inside(e.getFrom())) {
                e.setCancelled(true);warn(e.getPlayer());return;
            }
        }
    }
    private void warn(Player p) {
        long current=System.currentTimeMillis();
        if(current-lastDenied.getOrDefault(p.getUniqueId(),0L)<5000L)return;
        lastDenied.put(p.getUniqueId(),current);
        p.sendMessage(plugin.prefix()+plugin.color("&cMagiczna bariera: testowy dungeon jest jeszcze zamkniety!"));
    }
}
