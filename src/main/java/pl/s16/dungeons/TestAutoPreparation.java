package pl.s16.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.time.*;
import java.util.*;

/**
 * Opt-in scheduler for an explicitly named, dedicated TEST world and FIXED anchor.
 * No new chunks loaded; all world edits still go through RecoveryManager snapshots.
 * Fail-closed: one attempt per occurrence per process (a persisted recovery ID prevents
 * reusing the same world blocks after a failed attempt).
 */
final class TestAutoPreparation {
    private final ManiusDungeons plugin;
    private final ScheduleStore schedules;
    private final RecoveryManager recovery;
    private final Set<String> attempted=new HashSet<>();

    TestAutoPreparation(ManiusDungeons plugin,ScheduleStore schedules,RecoveryManager recovery) {
        this.plugin=plugin;this.schedules=schedules;this.recovery=recovery;
    }

    void tick(ZonedDateTime now) {
        if (!plugin.getConfig().getBoolean("safety.enable-test-world-mutations",false)
            || !plugin.getConfig().getBoolean("safety.test-auto-prepare.enabled",false))return;
        String configured=plugin.getConfig().getString("safety.test-world-name","");
        String production=plugin.getConfig().getString("settings.world","");
        if(configured==null||configured.isBlank()||configured.equalsIgnoreCase(production))return;
        if(!plugin.getConfig().isInt("safety.test-auto-prepare.anchor-x")
            || !plugin.getConfig().isInt("safety.test-auto-prepare.anchor-z"))return;
        World world=Bukkit.getWorld(configured);
        if(world==null)return;
        int x=plugin.getConfig().getInt("safety.test-auto-prepare.anchor-x");
        int z=plugin.getConfig().getInt("safety.test-auto-prepare.anchor-z");
        if(!world.isChunkLoaded(Math.floorDiv(x,16),Math.floorDiv(z,16)))return;
        int lead=plugin.getConfig().getInt("settings.regenerate-before-start-minutes",180);
        if(lead<0||lead>1440)return;
        // Midnight-crossing windows are supported, with approved schedule exceptions.
        for(LocalDate day:List.of(now.toLocalDate().minusDays(1),now.toLocalDate(),now.toLocalDate().plusDays(1))) {
            for(DungeonSchedule event:schedules.on(day)) {
                EventTimeline.Window window=EventTimeline.window(event,day,plugin.timezone());
                String occurrenceId=TestPreparationPolicy.recoveryId(event.id,day);
                if(!TestPreparationPolicy.due(now.toInstant(),window.start().toInstant(),window.end().toInstant(),lead,
                    attempted.contains(occurrenceId)||recovery.recoveryIds().contains(occurrenceId)))continue;
                attempted.add(occurrenceId);
                try {
                    int count=schedules.rooms(event,day);
                    if(count==0) {
                        int min=plugin.getConfig().getInt("safety.default-rooms-min",15);
                        int max=plugin.getConfig().getInt("safety.default-rooms-max",50);
                        if(min<15||min>max||max>100)throw new IllegalStateException("Niepoprawny zakres pokoi");
                        // Stage 14 is intentionally capped to 20 rooms in a test world.
                        int testMaximum=Math.min(20,max);
                        if(min>testMaximum)throw new IllegalStateException("Domyslne minimum przekracza testowy limit 20 pokoi");
                        count=min+new Random(Objects.hash(event.id,day)).nextInt(testMaximum-min+1);
                    }
                    if(count>20)throw new IllegalStateException("Limit testowy wynosi 20 pokoi; wymagane "+count);
                    if(recovery.hasJobForSchedule(event.id))throw new IllegalStateException("Inne wystapienie tego dungeona nadal wymaga przywrocenia");
                    Location anchor=new Location(world,x+0.5,world.getHighestBlockYAt(x,z)+1,z+0.5);
                    recovery.beginTest(world,anchor,occurrenceId,event.id,day,event,count);
                    plugin.getLogger().warning("AUTO TEST: zaczeto snapshot dla "+event.id+" / "+day+"; recovery="+occurrenceId);
                }catch(Exception e) {
                    plugin.getLogger().warning("AUTO TEST: odmowa przygotowania "+event.id+" / "+day+": "+e.getMessage()
                        +"; bez przygotowanego testu nie bedzie wejscia ani ogloszenia.");
                }
            }
        }
        // Do not clear the attempt ledger in this process: clearing could generate
        // an already rejected occurrence repeatedly during a long server uptime.
    }
}
