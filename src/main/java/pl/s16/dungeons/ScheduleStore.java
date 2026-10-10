package pl.s16.dungeons;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.logging.Level;

/** Osobny plik schedules.yml, niezalezny od manius_gildie. */
public final class ScheduleStore {
    private final ManiusDungeons plugin;
    private final File file;
    private final Map<String, DungeonSchedule> entries = new LinkedHashMap<>();
    private final Map<String, Map<LocalDate, Integer>> overrides = new LinkedHashMap<>();
    private final Map<String, Set<LocalDate>> cancelledDates = new LinkedHashMap<>();
    ScheduleStore(ManiusDungeons p) { plugin=p; file=new File(p.getDataFolder(), "schedules.yml"); reload(); }

    public synchronized void reload() {
        if (!file.exists()) { save(); return; }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        Map<String,DungeonSchedule> loaded = new LinkedHashMap<>();
        Map<String,Map<LocalDate,Integer>> over = new LinkedHashMap<>();
        Map<String,Set<LocalDate>> cancellations = new LinkedHashMap<>();
        ConfigurationSection root=y.getConfigurationSection("events");
        if(root != null) for(String id:root.getKeys(false)) {
            try {
                String base="events."+id+".";
                String theme=y.getString(base+"theme", "mechowy");
                String type=y.getString(base+"type", "once");
                LocalDate date = type.equals("once")?LocalDate.parse(Objects.requireNonNull(y.getString(base+"date"))):null;
                EnumSet<DayOfWeek> days=EnumSet.noneOf(DayOfWeek.class);
                if(!type.equals("once")) for(String v:y.getStringList(base+"days")) days.add(DayOfWeek.valueOf(v));
                DungeonSchedule s=new DungeonSchedule(id,theme,date,days,LocalTime.parse(y.getString(base+"start","17:00")),y.getInt(base+"duration",60),y.getInt(base+"rooms",0));
                loaded.put(id,s);
                Set<LocalDate> exclusions=new HashSet<>();
                for(String value:y.getStringList(base+"cancelled-dates")) exclusions.add(LocalDate.parse(value));
                cancellations.put(id,exclusions);
                ConfigurationSection os=y.getConfigurationSection(base+"room-overrides");
                if(os!=null) {
                    Map<LocalDate,Integer> m=new HashMap<>();
                    for(String day:os.getKeys(false)) {
                        int count=os.getInt(day);
                        if(count<15||count>100) throw new IllegalArgumentException("Pokoi poza zakresem 15-100");
                        m.put(LocalDate.parse(day), count);
                    }
                    over.put(id,m);
                }
            } catch(Exception e) { plugin.getLogger().log(Level.WARNING, "Nie mozna odczytac wydarzenia "+id+" z schedules.yml", e); }
        }
        entries.clear(); entries.putAll(loaded); overrides.clear(); overrides.putAll(over);
        cancelledDates.clear(); cancelledDates.putAll(cancellations);
    }
    public synchronized boolean add(DungeonSchedule s) {
        if(entries.containsKey(s.id)) return false;
        entries.put(s.id,s);
        if(!save()) { entries.remove(s.id); return false; }
        return true;
    }
    public synchronized boolean remove(String id) {
        DungeonSchedule old=entries.remove(id); if(old==null)return false;
        Map<LocalDate,Integer> previous=overrides.remove(id);
        Set<LocalDate> previousCancelled=cancelledDates.remove(id);
        if(!save()) {entries.put(id,old);if(previous!=null)overrides.put(id,previous);if(previousCancelled!=null)cancelledDates.put(id,previousCancelled);return false;}
        return true;
    }
    public synchronized void setRooms(String id, LocalDate day, int count) {
        DungeonSchedule s=entries.get(id);
        if(s==null) throw new IllegalArgumentException("Brak wydarzenia: "+id);
        if(!occurs(s,day)) throw new IllegalArgumentException("Wydarzenie nie przypada na "+day+" (lub anulowano wyjatek)");
        if(count<15||count>100)throw new IllegalArgumentException("Pokoje: 15-100");
        Integer old=overrides.computeIfAbsent(id,k->new HashMap<>()).put(day,count);
        if(!save()) {if(old==null)overrides.get(id).remove(day);else overrides.get(id).put(day,old);throw new IllegalStateException("Nie zapisano schedules.yml");}
    }
    public synchronized boolean occurs(DungeonSchedule s, LocalDate date) {
        return s.occursOn(date) && !cancelledDates.getOrDefault(s.id, Set.of()).contains(date);
    }
    public synchronized boolean isCancelled(String id, LocalDate date) {
        return cancelledDates.getOrDefault(id,Set.of()).contains(date);
    }
    public synchronized void setCancelled(String id, LocalDate day, boolean cancelled) {
        DungeonSchedule s=entries.get(id);
        if(s==null)throw new IllegalArgumentException("Nieznane wydarzenie: "+id);
        if(!s.occursOn(day))throw new IllegalArgumentException("Wydarzenie nie przypada na "+day);
        Set<LocalDate> dates=cancelledDates.computeIfAbsent(id,k->new HashSet<>());
        boolean old=dates.contains(day);
        if(cancelled)dates.add(day);else dates.remove(day);
        if(!save()) {if(old)dates.add(day);else dates.remove(day);throw new IllegalStateException("Nie zapisano schedules.yml");}
    }
    public synchronized int rooms(DungeonSchedule s, LocalDate date) {
        Integer n=overrides.getOrDefault(s.id,Map.of()).get(date);
        return n!=null?n:s.rooms;
    }
    public synchronized List<DungeonSchedule> all() {return List.copyOf(entries.values());}
    public synchronized DungeonSchedule get(String id) {return entries.get(id);}
    public synchronized List<DungeonSchedule> on(LocalDate day) {return entries.values().stream().filter(s->occurs(s,day)).toList();}
    public synchronized boolean save() {
        YamlConfiguration y=new YamlConfiguration();
        for(DungeonSchedule s:entries.values()) {
            String k="events."+s.id+".";
            y.set(k+"theme",s.theme);y.set(k+"type",s.date==null?"weekly":"once");
            if(s.date!=null)y.set(k+"date",s.date.toString());
            else y.set(k+"days",s.days.stream().map(Enum::name).toList());
            y.set(k+"start",s.start.toString());y.set(k+"duration",s.durationMinutes);y.set(k+"rooms",s.rooms);
            overrides.getOrDefault(s.id,Map.of()).forEach((d,n)->y.set(k+"room-overrides."+d,n));
            y.set(k+"cancelled-dates",cancelledDates.getOrDefault(s.id,Set.of()).stream().sorted().map(LocalDate::toString).toList());
        }
        try { AtomicYaml.write(y,file); return true; }
        catch(IOException e) {plugin.getLogger().log(Level.SEVERE,"Nie udalo sie zapisac schedules.yml",e);return false;}
    }
}
