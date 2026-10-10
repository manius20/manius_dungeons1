package pl.s16.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.time.*;
import java.util.*;

/** GUI to zwykle Inventory Bukkit: administrator moze wkladac przedmioty do lootpooli. */
public final class AdminMenu implements Listener {
    private final ManiusDungeons plugin;private final ScheduleStore schedules;private final LootStore loot;
    private final Map<UUID,ScheduleWizard> planning=new HashMap<>();
    private static final String[] WEEKDAYS={"Poniedzialek","Wtorek","Sroda","Czwartek","Piatek","Sobota","Niedziela"};
    private static final int[] DURATIONS={30,60,90,120,180,240};
    private static final int[] ROOM_COUNTS={0,15,20,30,50,100};
    private static final String[] THEMES={"mechowy","siarkowy","pustynny","mesa"};
    private static final class ScheduleWizard {
        LocalDate date;
        int dayOffset=0;
        EnumSet<DayOfWeek> days=EnumSet.noneOf(DayOfWeek.class);
        String theme="mechowy";
        LocalTime time=LocalTime.of(17,0);
        int duration=60;
        int rooms=0;
        String when(){return date!=null?date.toString():"Co tydzien: "+days;}
        DungeonSchedule toSchedule(String id){return new DungeonSchedule(id,theme,date,days,time,duration,rooms);}
    }
    private ScheduleWizard wizard(Player p){return planning.computeIfAbsent(p.getUniqueId(),x->new ScheduleWizard());}

    AdminMenu(ManiusDungeons p,ScheduleStore s,LootStore l){plugin=p;schedules=s;loot=l;}
    private static final class MenuHolder implements InventoryHolder {
        final String type;
        private Inventory inventory;
        MenuHolder(String type){this.type=type;}
        void inventory(Inventory inv){this.inventory=inv;}
        @Override public Inventory getInventory(){return inventory;}
    }
    private Inventory create(String type,int size,String title){
        MenuHolder holder=new MenuHolder(type);Inventory inv=Bukkit.createInventory(holder,size,title);holder.inventory(inv);return inv;
    }
    private ItemStack icon(Material type,String title,String... lore){
        ItemStack stack=new ItemStack(type);ItemMeta meta=stack.getItemMeta();meta.setDisplayName("§b"+title);
        meta.setLore(Arrays.stream(lore).map(s->"§7"+s).toList());stack.setItemMeta(meta);return stack;
    }
    public void openMain(Player p){
        Inventory inv=create("main",27,"§9§lmanius_dungeons | Admin");
        inv.setItem(10,icon(Material.CLOCK,"Harmonogram", "Lista planowanych wydarzen", "Kliknij kalendarz, pozniej ZAPLANUJ"));
        inv.setItem(12,icon(Material.CHEST,"Slaby loot", "Wstaw przedmioty do 45 slotow"));
        inv.setItem(13,icon(Material.DIAMOND_CHESTPLATE,"Dobry loot", "Wstaw przedmioty do 45 slotow"));
        inv.setItem(14,icon(Material.NETHERITE_SWORD,"Drop bossa", "Edycja dropow globalnych"));
        inv.setItem(15,icon(Material.IRON_SWORD,"Drop minibossa", "Edycja dropow globalnych"));
        inv.setItem(16,icon(Material.WRITABLE_BOOK,"Komendy", "Wpisz /dungeon admin pomoc", "TAB podpowiada dostepne polecenia"));
        inv.setItem(20,icon(Material.ENDER_EYE,"Wejscia aktywnych TESTOW", "Kliknij: pokaze prawdziwe wspolrzedne", "Przed otwarciem gracze widza je dopiero po ujawnieniu"));
        inv.setItem(22,icon(Material.BARRIER,"Etap 12: generator TESTOWY", "Testuj tylko na osobnym swiecie", "Awaryjnie: /dungeon admin anuluj <id> potwierdz", "Prawdziwe dungeony jeszcze nieaktywne"));
        p.openInventory(inv);
    }
    public void openCalendar(Player p){
        Inventory inv=create("calendar",54,"§9§lmanius_dungeons | Kalendarz");
        List<DungeonSchedule> all=schedules.all();int slot=0;
        for(DungeonSchedule event:all){if(slot>=45)break;
            inv.setItem(slot++,icon(Material.PAPER,event.id,event.theme,event.recurrence(),"Start: "+event.start,"Czas: "+event.durationMinutes+" min","Pokoje: "+(event.rooms==0?"losowo":event.rooms)));
        }
        inv.setItem(49,icon(Material.EMERALD,"ZAPLANUJ DUNGEON", "Kliknij: wybierz dzien, motyw i godzine", "Wybierzesz wszystkie godziny 00:00 - 23:00", "Mozesz tez ustawic wydarzenie codziennie"));
        p.openInventory(inv);
    }
    /** Kreator zapisywany dopiero po zatwierdzeniu: zamkniecie GUI niczego nie planuje. */
    public void openScheduleDay(Player p){
        planning.put(p.getUniqueId(),new ScheduleWizard());
        showScheduleDay(p);
    }
    private void showScheduleDay(Player p){
        Inventory inv=create("schedule-day",54,"§9§lDungeon | Wybierz dzien");
        LocalDate today=LocalDate.now(plugin.timezone());
        ScheduleWizard wizard=wizard(p);
        for(int i=0;i<35;i++){
            LocalDate date=today.plusDays(wizard.dayOffset+i);
            inv.setItem(i,icon(Material.PAPER,"Dzien: "+date,"Jednorazowo: "+WEEKDAYS[date.getDayOfWeek().getValue()-1],"Kliknij, by wybrac"));
        }
        inv.setItem(45,icon(Material.EMERALD,"CODZIENNIE","Automatycznie w kazdy dzien tygodnia"));
        inv.setItem(46,icon(Material.BOOK,"Poniedzialek - Piatek","Powtarzaj w dni robocze"));
        inv.setItem(47,icon(Material.CLOCK,"Sobota i Niedziela","Powtarzaj w weekendy"));
        for(int i=0;i<7;i++)inv.setItem(36+i,icon(Material.MAP,"Co "+WEEKDAYS[i],"Wydarzenie cykliczne co tydzien"));
        inv.setItem(48,icon(Material.ARROW,"Poprzednie 35 dni", "Dostepne od dzisiaj"));
        inv.setItem(49,icon(Material.BARRIER,"Wroc do kalendarza"));
        inv.setItem(50,icon(Material.ARROW,"Nastepne 35 dni", "Mozesz przewijac do dowolnej daty"));
        p.openInventory(inv);
    }
    private void showScheduleTheme(Player p){
        Inventory inv=create("schedule-theme",27,"§9§lDungeon | Motyw");
        for(int i=0;i<4;i++) {
            Material material=switch(i){case 0->Material.MOSS_BLOCK;case 1->Material.MAGMA_BLOCK;case 2->Material.SAND;default->Material.RED_SAND;};
            inv.setItem(10+i,icon(material,THEMES[i],"Kliknij, aby wybrac motyw"));
        }
        inv.setItem(22,icon(Material.ARROW,"Wroc do daty"));
        p.openInventory(inv);
    }
    private void showScheduleHour(Player p){
        Inventory inv=create("schedule-hour",54,"§9§lDungeon | Godzina 00-23");
        for(int i=0;i<24;i++) inv.setItem(i,icon(Material.CLOCK,
            String.format(Locale.ROOT,"%02d:00",i),"Godzina rozpoczecia","Mozesz tez podac HH:mm w komendzie"));
        inv.setItem(49,icon(Material.ARROW,"Wroc do motywu"));
        p.openInventory(inv);
    }
    private void showScheduleDuration(Player p){
        Inventory inv=create("schedule-duration",27,"§9§lDungeon | Czas trwania");
        for(int i=0;i<DURATIONS.length;i++) inv.setItem(10+i,icon(Material.CLOCK,DURATIONS[i]+" minut","Kliknij, by ustawic dlugosc"));
        inv.setItem(22,icon(Material.ARROW,"Wroc do godziny"));
        p.openInventory(inv);
    }
    private void showScheduleRooms(Player p){
        Inventory inv=create("schedule-rooms",27,"§9§lDungeon | Pokoje");
        for(int i=0;i<ROOM_COUNTS.length;i++)inv.setItem(10+i,icon(Material.STONE_BRICKS,
            ROOM_COUNTS[i]==0?"Losowo 15-50 pokoi":ROOM_COUNTS[i]+" pokoi",
            "Kliknij, aby wybrac"));
        inv.setItem(22,icon(Material.ARROW,"Wroc do czasu trwania"));
        p.openInventory(inv);
    }
    private void showScheduleConfirm(Player p){
        ScheduleWizard w=wizard(p);
        Inventory inv=create("schedule-confirm",27,"§9§lDungeon | Potwierdzenie");
        inv.setItem(13,icon(Material.WRITABLE_BOOK,"Podsumowanie wydarzenia",
            "Termin: "+w.when(),"Motyw: "+w.theme,"Godzina: "+w.time,
            "Czas: "+w.duration+" minut","Pokoje: "+(w.rooms==0?"15-50 losowo":w.rooms)));
        inv.setItem(11,icon(Material.LIME_WOOL,"ZATWIERDZ I ZAPISZ","Dopiero to zapisuje harmonogram"));
        inv.setItem(15,icon(Material.RED_WOOL,"Wroc do wyboru pokoi"));
        p.openInventory(inv);
    }
    private void saveScheduled(Player p) {
        ScheduleWizard w=wizard(p);
        String day=w.date!=null?w.date.toString().replace("-",""):
            w.days.size()==7?"daily":w.days.size()==5?"workdays":w.days.size()==2?"weekend":w.days.iterator().next().name().toLowerCase(Locale.ROOT);
        String base=w.theme+"_"+day+"_"+String.format(Locale.ROOT,"%02d%02d",w.time.getHour(),w.time.getMinute());
        if(base.length()>26)base=base.substring(0,26);
        String id=base;int i=2;
        while(schedules.get(id)!=null){id=base+"_"+i++;}
        DungeonSchedule event=w.toSchedule(id);
        if(!schedules.add(event)){
            p.sendMessage(plugin.prefix()+"§cNie udalo sie zapisac harmonogramu. Sprawdz logi.");
            return;
        }
        planning.remove(p.getUniqueId());
        p.closeInventory();
        p.sendMessage(plugin.prefix()+"§aZaplanowano: §f"+event.summary());
        p.sendMessage(plugin.prefix()+"§7Id wydarzenia: §b"+id+"§7. Generacja na SMP nadal zablokowana.");
    }
    @EventHandler public void quit(PlayerQuitEvent e){planning.remove(e.getPlayer().getUniqueId());}
    public void openLoot(Player p,String pool){
        if(!LootStore.POOLS.contains(pool))throw new IllegalArgumentException("Nieznany lootpool");
        Inventory inv=create("loot:"+pool,54,"§9§lLoot: "+pool);
        ItemStack[] contents=loot.contents(pool);
        for(int i=0;i<45;i++)inv.setItem(i,contents[i]);
        for(int i=45;i<54;i++)inv.setItem(i,icon(Material.GRAY_STAINED_GLASS_PANE,"§8——"));
        inv.setItem(49,icon(Material.WRITABLE_BOOK,"Zapisz: zamknij GUI", "Sloty 1-45 to przedmioty", "Szanse: /dungeon admin szansa <pool> <slot> <0-100>", "Przedmioty zostana zachowane z enchantami"));
        p.openInventory(inv);
    }
    @EventHandler public void click(InventoryClickEvent e){
        Inventory top=e.getView().getTopInventory();
        if(!(top.getHolder() instanceof MenuHolder h))return;
        if(!(e.getWhoClicked() instanceof Player player)||!player.hasPermission("maniusdungeons.admin")){e.setCancelled(true);return;}
        int slot=e.getRawSlot();
        if(h.type.startsWith("loot:")){
            if(slot>=45&&slot<54)e.setCancelled(true);
            // Pozostale sloty dzialaja jak zwykla skrzynia.
            return;
        }
        e.setCancelled(true);
        if(slot<0||slot>=top.getSize())return;
        if(h.type.equals("calendar")) {
            if(slot==49)openScheduleDay(player);
            return;
        }
        if(h.type.equals("schedule-day")) {
            ScheduleWizard w=wizard(player);
            LocalDate today=LocalDate.now(plugin.timezone());
            if(slot==48){w.dayOffset=Math.max(0,w.dayOffset-35);showScheduleDay(player);return;}
            if(slot==50){w.dayOffset+=35;showScheduleDay(player);return;}
            if(slot==49){planning.remove(player.getUniqueId());openCalendar(player);return;}
            if(slot>=0&&slot<35){w.date=today.plusDays(w.dayOffset+slot);w.days.clear();}
            else if(slot==45){w.date=null;w.days=EnumSet.allOf(DayOfWeek.class);}
            else if(slot==46){w.date=null;w.days=EnumSet.range(DayOfWeek.MONDAY,DayOfWeek.FRIDAY);}
            else if(slot==47){w.date=null;w.days=EnumSet.of(DayOfWeek.SATURDAY,DayOfWeek.SUNDAY);}
            else if(slot>=36&&slot<43){w.date=null;w.days=EnumSet.of(DayOfWeek.of(slot-35));}
            else return;
            showScheduleTheme(player);return;
        }
        if(h.type.equals("schedule-theme")) {
            if(slot==22){showScheduleDay(player);return;}
            if(slot>=10&&slot<14){wizard(player).theme=THEMES[slot-10];showScheduleHour(player);}
            return;
        }
        if(h.type.equals("schedule-hour")){
            if(slot==49){showScheduleTheme(player);return;}
            if(slot>=0&&slot<24){wizard(player).time=LocalTime.of(slot,0);showScheduleDuration(player);}
            return;
        }
        if(h.type.equals("schedule-duration")){
            if(slot==22){showScheduleHour(player);return;}
            if(slot>=10&&slot<16){wizard(player).duration=DURATIONS[slot-10];showScheduleRooms(player);}
            return;
        }
        if(h.type.equals("schedule-rooms")){
            if(slot==22){showScheduleDuration(player);return;}
            if(slot>=10&&slot<16){wizard(player).rooms=ROOM_COUNTS[slot-10];showScheduleConfirm(player);}
            return;
        }
        if(h.type.equals("schedule-confirm")){
            if(slot==15)showScheduleRooms(player);
            else if(slot==11)saveScheduled(player);
            return;
        }
        if(h.type.equals("main")) switch(slot){
            case 10 -> openCalendar(player);
            case 12 -> openLoot(player,"slaby");
            case 13 -> openLoot(player,"dobry");
            case 14 -> openLoot(player,"boss");
            case 15 -> openLoot(player,"miniboss");
            case 16 -> {player.closeInventory();player.performCommand("dungeon admin pomoc");}
            case 20 -> {player.closeInventory();player.performCommand("dungeon admin wejscia");}
            default -> {}
        };
    }
    @EventHandler public void close(InventoryCloseEvent e){
        Inventory inv=e.getView().getTopInventory();
        if(!(inv.getHolder() instanceof MenuHolder h)||!h.type.startsWith("loot:"))return;
        if(!e.getPlayer().hasPermission("maniusdungeons.admin"))return;
        String pool=h.type.substring(5);
        ItemStack[] slots=new ItemStack[45];for(int i=0;i<45;i++)slots[i]=inv.getItem(i);
        try{loot.update(pool,slots);e.getPlayer().sendMessage(plugin.prefix()+"§aZapisano zawartosc lootpoolu §f"+pool+"§a.");}
        catch(Exception ex){e.getPlayer().sendMessage(plugin.prefix()+"§cBlad zapisu lootpoolu: "+ex.getMessage());}
    }
}
