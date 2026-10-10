package pl.s16.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import java.time.*;
import java.util.*;

/** Safe scheduler: planned events are NOT advertised as playable dungeons.
 * The sole automatic world-changing action is closing a *matching active test*
 * through the existing snapshot recovery manager.
 */
public final class ScheduleTicker {
    private final ManiusDungeons plugin;
    private final ScheduleStore store;
    private final RecoveryManager recovery;
    private final TestEntranceService entrances;
    private final TestAutoPreparation preparation;
    private BukkitTask task;
    private final Set<String> notified = new HashSet<>();
    private Instant previousTick;
    ScheduleTicker(ManiusDungeons p,ScheduleStore s,RecoveryManager r,TestEntranceService e){plugin=p;store=s;recovery=r;entrances=e;preparation=new TestAutoPreparation(p,s,r);}
    void start(){previousTick=Instant.now();task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,40L,1200L);}
    void stop(){if(task!=null)task.cancel();}
    private void tick(){
        ZonedDateTime now=ZonedDateTime.now(plugin.timezone());
        Instant previous=previousTick;
        previousTick=now.toInstant();
        // The only automatic physical generation is explicitly opt-in in the dedicated TEST world.
        preparation.tick(now);
        // Opening cannot happen before construction is complete and the schedule is active.
        recovery.activateDueTests(now.toInstant(),store,plugin.timezone());
        // Closing is driven by the durable recovery deadline, NOT only schedules.yml.
        // A reload/edit of a planned event cannot leave an ACTIVE test running indefinitely.
        if(plugin.getConfig().getBoolean("settings.auto-close-active-tests",true)) {
            for(String id:recovery.expiredActiveTestIds(now.toInstant())) {
                try {
                    if(recovery.requestRestore(id))
                        informAdmins("deadline:"+id, "&cUplynal trwale zapisany koniec testu " + id
                            + ". Rozpoczeto rollback; monitoruj /dungeon admin audyt " + id);
                } catch(Exception ex) {plugin.getLogger().severe("Automatyczne zamkniecie "+id+": "+ex.getMessage());}
            }
        }
        LocalDate today=now.toLocalDate();
        boolean daily=plugin.getConfig().getBoolean("settings.unplanned.daily-warning-enabled",true);
        boolean missing=plugin.getConfig().getBoolean("settings.unplanned.missing-slot-warning-enabled",true);
        String dailyTime=plugin.getConfig().getString("settings.unplanned.daily-warning-time","12:00");
        String slotTime=plugin.getConfig().getString("settings.unplanned.missing-slot-time","17:00");
        int advance=Math.max(1,plugin.getConfig().getInt("settings.unplanned.missing-slot-advance-minutes",60));
        try {
            if(daily&&crossedDaily(previous,now,LocalTime.parse(dailyTime))&&store.on(today).isEmpty())
                once("daily:"+today,"&eNa dzis nie zaplanowano dungeonow. Skontaktujcie sie z administracja!",true);
            if(missing&&crossedDaily(previous,now,LocalTime.parse(slotTime).minusMinutes(advance))&&store.on(today).isEmpty())
                once("slot:"+today,"&eBrak zaplanowanego dungeona na godzine "+slotTime+". Napiszcie do administracji!",true);
        }catch(Exception ex){plugin.getLogger().warning("Bledne godziny przypomnien: "+ex.getMessage());}

        // Also examine yesterday for events crossing midnight, including when server was offline.
        for(LocalDate day:List.of(today.minusDays(1),today)) {
            for(DungeonSchedule event:store.on(day)) {
                EventTimeline.Window window=EventTimeline.window(event,day,plugin.timezone());
                boolean active=recovery.isActiveTestForDate(event.id,day);
                String key=event.id+":"+day+":";
                int reveal=plugin.getConfig().getInt("settings.reveal-before-minutes",30);
                var before=EventTimeline.crossedMilestones(previous,now.toInstant(),window.start().toInstant(),List.of(reveal));
                if(!before.isEmpty()) {
                    // Never invent coordinates: a generated dungeon does not yet exist on SMP.
                    informAdmins(key+"planned", "&eWydarzenie "+event.id+" ma zaplanowany start za "+reveal
                        +" min. &7Stan generowania: " + recovery.describeOccurrence(event.id,day));
                }
                if(active) {
                    if(EventTimeline.finished(now,window)) {
                        if(plugin.getConfig().getBoolean("settings.auto-close-active-tests",true)) {
                            try {
                                if(recovery.requestRestoreForOccurrence(event.id,day))
                                    informAdmins(key+"closing", "&cTest "+event.id+" zakonczyl czas. Rozpoczeto ewakuacje i rollback. Kontroluj /dungeon admin status.");
                            } catch(Exception ex){plugin.getLogger().severe("Nie mozna zamknac testu "+event.id+": "+ex.getMessage());}
                        }
                    } else {
                        for(int warn:EventTimeline.normalizeWarnings(plugin.getConfig().getIntegerList("settings.close-warnings-minutes"))) {
                            if(EventTimeline.crossedMilestones(previous,now.toInstant(),window.end().toInstant(),List.of(warn)).contains(warn))
                            {
                                String message="&eTEST "+event.id+" zamknie sie za "+warn+" min. Opuść dungeon przed rollbackiem!";
                                if(notified.add(key+"close:"+warn)) {
                                    recovery.notifyActiveTestPlayers(event.id,day,message);
                                    Bukkit.getOnlinePlayers().stream().filter(p->p.hasPermission("maniusdungeons.admin"))
                                        .forEach(p->p.sendMessage(plugin.prefix()+plugin.color(message)));
                                }
                            }
                        }
                    }
                }
            }
        }
        entrances.tick(previous,now); // Built test entrances only; announcements require explicit opt-in.
        if(notified.size()>2000)notified.clear(); // Dedup bounds across long-running servers.
    }
    private static boolean crossedDaily(Instant previous,ZonedDateTime now,LocalTime target){
        Instant boundary=ZonedDateTime.of(now.toLocalDate(),target,now.getZone()).toInstant();
        return previous!=null && previous.isBefore(boundary) && !now.toInstant().isBefore(boundary);
    }
    private void informAdmins(String key,String msg){once(key,msg,false);}
    private void once(String key,String msg,boolean publicChat){
        if(!notified.add(key))return;
        if(publicChat) Bukkit.broadcastMessage(plugin.prefix()+plugin.color(msg));
        else Bukkit.getOnlinePlayers().stream().filter(p->p.hasPermission("maniusdungeons.admin"))
              .forEach(p->p.sendMessage(plugin.prefix()+plugin.color(msg)));
    }
}
