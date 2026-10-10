package pl.s16.dungeons;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.time.*;
import java.util.*;

/** Komendy sa przeznaczone dla administratorow; normalni gracze widza tylko publiczny status etapu. */
public final class AdminCommand implements CommandExecutor, TabCompleter {
    private final ManiusDungeons plugin;
    private final ScheduleStore schedules;
    private final LootStore loot;
    private final AdminMenu menu;
    private final RecoveryManager recovery;
    private final SiteSearchService siteSearch;
    private static final List<String> SUBS=List.of("pomoc","zaplanuj","usun","anuluj","wznow","testbuduj","lista","kalendarz","pokoje","loot","szansa","losujloot","podglad","podgladmoby","ocenplan","ocenwejsc","ocenpodziemie","sprawdzteren","szukajmiejsca","status","audyt","wejscia","sprawdzkopie","podgladpulapki","wyjatek","przygotowanie","podgladwejsc","testkomunikat","reload");
    AdminCommand(ManiusDungeons p,ScheduleStore schedules,LootStore loot,AdminMenu menu, RecoveryManager recovery, SiteSearchService siteSearch) {
        this.plugin=p;this.schedules=schedules;this.loot=loot;this.menu=menu;this.recovery=recovery;this.siteSearch=siteSearch;
    }
    private void msg(CommandSender s,String text){s.sendMessage(plugin.prefix()+plugin.color(text));}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(args.length==0){msg(sender,"&7System dungeonow jest w przygotowaniu. Wydarzenia nie sa jeszcze aktywne.");return true;}
        if(args[0].equalsIgnoreCase("wejscia")) {
            if(args.length!=1){msg(sender,"&eUzyj: /dungeon wejscia");return true;}
            var locations=plugin.entrances().visibleNow();
            if(locations.isEmpty())msg(sender,"&7Brak ujawnionych wejsci AKTYWNYCH dungeonow testowych.");
            else for(var e:locations)msg(sender,"&bTEST &f"+e.id()+"&7: swiat &f"+e.worldName()
                    +"&7, X=&f"+e.x()+"&7, Y=&f"+e.y()+"&7, Z=&f"+e.z());
            return true;
        }
        if(!args[0].equalsIgnoreCase("admin")){msg(sender,"&7Uzyj &f/dungeon wejscia &7lub &f/dungeon admin &7(dla administratorow).");return true;}
        if(!sender.hasPermission("maniusdungeons.admin")){msg(sender,"&cBrak uprawnienia maniusdungeons.admin.");return true;}
        if(args.length==1){ if(sender instanceof Player p)menu.openMain(p);else help(sender);return true; }
        String cmd=args[1].toLowerCase(Locale.ROOT);
        try {switch(cmd){
            case "pomoc" -> help(sender);
            case "lista" -> {
                if(schedules.all().isEmpty())msg(sender,"&eBrak zaplanowanych wydarzen.");
                else for(DungeonSchedule s:schedules.all())msg(sender,"&b"+s.summary());
            }
            case "kalendarz" -> {
                if(sender instanceof Player p)menu.openCalendar(p);else msg(sender,"&cGUI dziala tylko dla graczy.");
            }
            case "loot" -> {
                if(args.length<3){msg(sender,"&eUzyj: /dungeon admin loot <slaby|dobry|boss|miniboss>");return true;}
                if(!(sender instanceof Player p)){msg(sender,"&cGUI wymaga gracza.");return true;}
                menu.openLoot(p,args[2].toLowerCase(Locale.ROOT));
            }
            case "losujloot" -> {
                if(args.length<3||args.length>4){msg(sender,"&eUzyj: /dungeon admin losujloot <slaby|dobry|boss|miniboss> [max-przedmiotow]");return true;}
                String pool=args[2].toLowerCase(Locale.ROOT);
                int max=args.length==4?Integer.parseInt(args[3]):4;
                if(max<1||max>27)throw new IllegalArgumentException("Dopuszczalne 1-27 przedmiotow");
                List<org.bukkit.inventory.ItemStack> items=loot.roll(pool,new Random(),max);
                if(items.isEmpty())msg(sender,"&eW tym losowaniu nic nie wypadlo (mozliwe przy niskich szansach/nieuzupelnionym poolu).");
                else for(var item:items)msg(sender,"&aWylosowano: &f"+item.getType()+" x"+item.getAmount());
                msg(sender,"&7Symulacja, zadnych przedmiotow nie dodano do swiata ani ekwipunku.");
            }
            case "szansa" -> {
                if(args.length!=5){msg(sender,"&eUzyj: /dungeon admin szansa <pool> <slot 1-45> <procent 0-100>");return true;}
                String pool=args[2].toLowerCase(Locale.ROOT);int slot=Integer.parseInt(args[3]);double chance=Double.parseDouble(args[4]);
                loot.setChance(pool,slot,chance);
                msg(sender,"&aZapisano szanse w "+pool+" na slocie "+slot+": "+chance+"%. &7(Dziala w symulacji i w skrzyniach swiata testowego.)");
            }
            case "zaplanuj" -> {
                if(args.length<7||args.length>8){
                    msg(sender,"&eUzyj: /dungeon admin zaplanuj <id> <motyw> <RRRR-MM-DD|dzis|jutro|codziennie|dni:MONDAY,FRIDAY> <HH:mm> <minuty> [losowo|15-100]");return true;
                }
                String id=args[2], theme=args[3].toLowerCase(Locale.ROOT);
                ScheduleInput.Selection selection=ScheduleInput.parse(args[4],LocalDate.now(plugin.timezone()));
                LocalDate date=selection.date();EnumSet<DayOfWeek> days=selection.days();
                LocalTime at=LocalTime.parse(args[5]);int duration=Integer.parseInt(args[6]);
                int rooms=args.length==8&&!args[7].equalsIgnoreCase("losowo")?Integer.parseInt(args[7]):0;
                DungeonSchedule created=new DungeonSchedule(id,theme,date,days,at,duration,rooms);
                if(!schedules.add(created))msg(sender,"&cID juz istnieje lub nie udalo sie zapisac schedules.yml.");
                else msg(sender,"&aZapisano harmonogram: &f"+created.summary()+"&e (generacja automatyczna TYLKO testowo po dodatkowej zgodzie w configu)");
            }
            case "wyjatek" -> {
                if(args.length!=5) {msg(sender,"&eUzyj: /dungeon admin wyjatek <id> <RRRR-MM-DD> <odwolaj|przywroc>");return true;}
                LocalDate date=LocalDate.parse(args[3]);
                if(!args[4].equalsIgnoreCase("odwolaj")&&!args[4].equalsIgnoreCase("przywroc"))throw new IllegalArgumentException("Uzyj odwolaj albo przywroc");
                // Gdy test juz dziala, nie wolno ukryc go przed zegarem zamykania.
                // Najpierw uruchamiamy rollback, dopiero potem usuwamy wystapienie z kalendarza.
                if(args[4].equalsIgnoreCase("odwolaj") && recovery.hasOngoingTestForDate(args[2],date))
                    recovery.requestRestoreForOccurrence(args[2],date);
                schedules.setCancelled(args[2],date,args[4].equalsIgnoreCase("odwolaj"));
                msg(sender,"&aZmieniono wyjatek: &f"+args[2]+" / "+date+" / "+args[4]);
            }
            case "podgladpulapki" -> {
                if(args.length!=4){msg(sender,"&eUzyj: /dungeon admin podgladpulapki <id> <RRRR-MM-DD>");return true;}
                DungeonSchedule event=schedules.get(args[2]);
                if(event==null)throw new IllegalArgumentException("Brak wydarzenia");
                LocalDate day=LocalDate.parse(args[3]);
                if(!schedules.occurs(event,day))throw new IllegalArgumentException("Brak wydarzenia tego dnia lub wyjatek");
                long seed=Objects.hash(event.id,day);Random rng=new Random(seed);
                int rooms=schedules.rooms(event,day);
                if(rooms==0) {
                    int min=plugin.getConfig().getInt("safety.default-rooms-min",15);
                    int max=plugin.getConfig().getInt("safety.default-rooms-max",50);
                    if(min<15||max>100||max<min)throw new IllegalArgumentException("Bledne limity pokoi w config.yml");
                    rooms=min+rng.nextInt(max-min+1);
                }
                var layout=DungeonLayoutPlanner.generate(rooms,1,seed,0.05,0.25);
                var plan=TestTrapLayout.plan(layout,DungeonBlueprint.fromLayout(layout),seed,
                    plugin.getConfig().getDouble("traps.room-fraction",0.2));
                msg(sender,"&bPulapki w planie: &f"+plan.size()+"&7 (pajeczyna: &f"+plan.stream().filter(p->p.kind()==TestTrapLayout.Kind.COBWEB).count()+"&7, magma: &f"+plan.stream().filter(p->p.kind()==TestTrapLayout.Kind.MAGMA).count()+"&7)");
                msg(sender,"&eTylko symulacja. Fizyczne pulapki dzialaja wyłącznie w osobnym swiecie testowym po wlaczeniu opcji.");
            }
            case "usun" -> {
                if(args.length==3 && (recovery.hasJob(args[2]) || recovery.hasJobForSchedule(args[2])))throw new IllegalArgumentException("Istnieje aktywny zapis terenu. Uzyj /dungeon admin anuluj "+args[2]+" potwierdz");
                if(args.length!=3){msg(sender,"&eUzyj: /dungeon admin usun <id>");return true;}
                msg(sender,schedules.remove(args[2])?"&aUsunieto wydarzenie.":"&cNie znaleziono wydarzenia lub blad zapisu.");
            }
            case "anuluj" -> {
                if(args.length!=4 || !args[3].equalsIgnoreCase("potwierdz")) {
                    msg(sender,"&cAwaryjne usuwanie: /dungeon admin anuluj <id> potwierdz");
                    msg(sender,"&eTa operacja usuwa rowniez cykliczny harmonogram i uruchamia odtwarzanie terenu, jesli istnieje kopia.");
                    return true;
                }
                String id=args[2];
                boolean active=recovery.hasJob(id)||recovery.hasJobForSchedule(id);
                if(active) {
                    if(recovery.hasJob(id))recovery.requestRestore(id);
                    recovery.requestRestoreForSchedule(id);
                } // Musi sie udac PRZED skasowaniem harmonogramu.
                boolean removed=schedules.get(id)!=null && schedules.remove(id);
                if(!active&&!removed)msg(sender,"&cNie znaleziono aktywnego lub zaplanowanego dungeona: "+id);
                else if(active)msg(sender,"&eZlecono awaryjne zamkniecie "+id+". Jesli istnieje kopia, przywracanie przebiega partiami. Sprawdz /dungeon admin status.");
                else msg(sender,"&aAnulowano zaplanowany dungeon "+id+" (bez zmian terenu).");
            }
            case "wznow" -> {
                if(args.length!=4 || !args[3].equalsIgnoreCase("potwierdz")) {
                    msg(sender,"&eUzyj: /dungeon admin wznow <id> potwierdz");
                    return true;
                }
                boolean ok=recovery.hasJob(args[2]) ? recovery.requestRestore(args[2]) : recovery.requestRestoreForSchedule(args[2]);
                if(!ok)msg(sender,"&cNie ma zapisanego recovery o ID lub harmonogramie "+args[2]);
                else msg(sender,"&aSprawdzono kopie i zlecono ponowne odtwarzanie "+args[2]+". Monitoruj /dungeon admin status.");
            }
            case "testbuduj" -> {
                if(!(sender instanceof Player p))throw new IllegalArgumentException("Wymaga wejscia do osobnego swiata testowego");
                if(args.length!=5 || !args[4].equalsIgnoreCase("potwierdz")) {
                    msg(sender,"&cUzycie: /dungeon admin testbuduj <id> <RRRR-MM-DD> potwierdz");
                    return true;
                }
                DungeonSchedule event=schedules.get(args[2]);
                if(event==null)throw new IllegalArgumentException("Brak planu o tym ID");
                LocalDate date=LocalDate.parse(args[3]);
                if(!schedules.occurs(event,date))throw new IllegalArgumentException("Wydarzenie nie przypada na ten dzien");
                int n=schedules.rooms(event,date);
                if(n==0) {
                    long seed=Objects.hash(event.id,date);
                    Random r=new Random(seed);
                    n=plugin.getConfig().getInt("safety.default-rooms-min",15)
                        +r.nextInt(plugin.getConfig().getInt("safety.default-rooms-max",50)-plugin.getConfig().getInt("safety.default-rooms-min",15)+1);
                }
                recovery.beginTest(p,event.id,date,event,n);
                msg(sender,"&eRozpoczeto tworzenie kopii terenu w swiecie testowym. Nie wylaczaj serwera podczas testu.");
            }
            case "przygotowanie" -> {
                if(args.length!=4){msg(sender,"&eUzyj: /dungeon admin przygotowanie <id> <RRRR-MM-DD>");return true;}
                DungeonSchedule event=schedules.get(args[2]);
                if(event==null)throw new IllegalArgumentException("Nie ma takiego wydarzenia");
                LocalDate day=LocalDate.parse(args[3]);
                if(!schedules.occurs(event,day))throw new IllegalArgumentException("Ten termin nie jest zatwierdzony lub zostal odwolany");
                var w=EventTimeline.window(event,day,plugin.timezone());
                boolean auto=plugin.getConfig().getBoolean("safety.test-auto-prepare.enabled",false)
                    && plugin.getConfig().getBoolean("safety.enable-test-world-mutations",false);
                String jobId=TestPreparationPolicy.recoveryId(event.id,day);
                msg(sender,"&bTermin &f"+event.id+" / "+day+"&7; start: &f"+w.start()+"&7, koniec: &f"+w.end());
                msg(sender,"&7Auto-test: &f"+(auto?"wlaczony (tylko osobny swiat testowy)":"WYLACZONY")
                    +"&7, recovery: &f"+jobId);
                msg(sender,"&7Stan: &f"+recovery.describeOccurrence(event.id,day));
                if(auto)msg(sender,"&eWymaga testowego swiata, wczytanych chunkow i limitu 20 pokoi; brak gwarancji przygotowania.");
            }
            case "szukajmiejsca" -> {
                if(!(sender instanceof Player p))throw new IllegalArgumentException("Komenda wymaga gracza");
                if(args.length!=4){msg(sender,"&eUzyj: /dungeon admin szukajmiejsca <id> <RRRR-MM-DD>");return true;}
                DungeonSchedule event=schedules.get(args[2]);
                if(event==null)throw new IllegalArgumentException("Nieznany harmonogram: "+args[2]);
                LocalDate date=LocalDate.parse(args[3]);
                siteSearch.search(p,event,date,schedules.rooms(event,date));
                msg(sender,"&eTrwa WSTEPNE wyszukiwanie po zaladowanych chunkach, bez zmian blokow i ladowania nowych chunkow.");
            }
            case "pokoje" -> {
                if(args.length!=5){msg(sender,"&eUzyj: /dungeon admin pokoje <id> <RRRR-MM-DD> <15-100>");return true;}
                LocalDate day=LocalDate.parse(args[3]);int n=Integer.parseInt(args[4]);
                schedules.setRooms(args[2],day,n);
                msg(sender,"&aDzien "+day+", wydarzenie "+args[2]+": &f"+n+" pokoi.");
            }
            case "podglad" -> {
                if(args.length != 4) {msg(sender,"&eUzyj: /dungeon admin podglad <id> <RRRR-MM-DD>");return true;}
                DungeonSchedule event=schedules.get(args[2]);
                if(event==null)throw new IllegalArgumentException("Nieznane wydarzenie: "+args[2]);
                LocalDate day=LocalDate.parse(args[3]);
                if(!schedules.occurs(event,day))throw new IllegalArgumentException("Wydarzenie nie przypada na wskazana date");
                int requested=schedules.rooms(event,day);
                long seed=Objects.hash(event.id,day);
                Random rng=new Random(seed);
                int roomCount=requested>0?requested:plugin.getConfig().getInt("safety.default-rooms-min",15)
                    +rng.nextInt(plugin.getConfig().getInt("safety.default-rooms-max",50)-plugin.getConfig().getInt("safety.default-rooms-min",15)+1);
                DungeonLayoutPlanner.Layout layout=DungeonLayoutPlanner.generate(roomCount,1+rng.nextInt(10),seed,
                    plugin.getConfig().getDouble("mobs.boss-chance-per-dungeon",0.05),
                    plugin.getConfig().getDouble("mobs.miniboss-chance-per-dungeon",0.25));
                msg(sender,"&bPodglad ukladu (bez zmiany swiata): &f"+event.id+" / "+day);
                msg(sender,"&7Pokoje: &f"+layout.rooms().size()+"&7, korytarze: &f"+layout.corridors().size()+"&7, wejscia: &f"+layout.entrances().size());
                msg(sender,"&7Wymiary planu: &f"+layout.width()+" x "+layout.length()+"&7; boss: &f"+layout.boss()+"&7; miniboss: &f"+layout.miniboss());
                msg(sender,"&eTo tylko plan geometryczny, nie gotowy ani wygenerowany dungeon.");
            }
            case "ocenplan" -> {
                if(args.length!=4){msg(sender,"&eUzyj: /dungeon admin ocenplan <id> <RRRR-MM-DD>");return true;}
                DungeonSchedule event=schedules.get(args[2]);
                if(event==null)throw new IllegalArgumentException("Brak wydarzenia: "+args[2]);
                LocalDate day=LocalDate.parse(args[3]);
                if(!schedules.occurs(event,day))throw new IllegalArgumentException("Nie ma wydarzenia tego dnia");
                long seed=Objects.hash(event.id,day);
                Random rng=new Random(seed);
                int rooms=schedules.rooms(event,day);
                if(rooms==0){
                    int minimum=plugin.getConfig().getInt("safety.default-rooms-min",15);
                    int maximum=plugin.getConfig().getInt("safety.default-rooms-max",50);
                    if(minimum<15||maximum>100||minimum>maximum)throw new IllegalArgumentException("Bledne limity pokoi w config.yml");
                    rooms=minimum+rng.nextInt(maximum-minimum+1);
                }
                int entranceCount=Math.max(1,Math.min(3,
                    plugin.getConfig().getInt("safety.test-entrance-count",1)));
                var layout=DungeonLayoutPlanner.generate(rooms,entranceCount,seed,
                    plugin.getConfig().getDouble("mobs.boss-chance-per-dungeon",0.05),
                    plugin.getConfig().getDouble("mobs.miniboss-chance-per-dungeon",0.25));
                var blueprint=DungeonBlueprint.fromLayout(layout);
                int anchorX=sender instanceof Player pl?pl.getLocation().getBlockX():0;
                int anchorZ=sender instanceof Player pl?pl.getLocation().getBlockZ():0;
                int groundY=sender instanceof Player pl?pl.getLocation().getBlockY()-1:90;
                int originX=anchorX-layout.rooms().getFirst().x();
                int originZ=anchorZ-layout.rooms().getFirst().z();
                var proposed=DungeonMultiEntrancePlan.locations(layout,originX,originZ,
                    plugin.getConfig().getInt("safety.entrance-radius-from-zero",3500));
                var simulatedSites=proposed.stream()
                    .map(e->new DungeonMultiEntrancePlan.Site(e.roomIndex(),e.x(),e.z(),groundY)).toList();
                var estimate=DungeonPlanEstimate.analyzeMulti(layout,blueprint,originX,originZ,
                    groundY-24,simulatedSites);
                int margin=plugin.getConfig().getInt("safety.closed-protection-margin",50);
                var border=PlacementRules.validate(0,0,anchorX,anchorZ,
                    new DungeonBlueprint.Bounds(estimate.minX(),estimate.maxX(),estimate.minY(),estimate.maxY(),estimate.minZ(),estimate.maxZ()),
                    plugin.getConfig().getInt("safety.entrance-radius-from-zero",3500),
                    plugin.getConfig().getInt("safety.world-coordinate-limit",4000),margin);
                msg(sender,"&bSymulacja planu: &f"+event.id+" / "+day+" / "+rooms+" pokoi");
                msg(sender,"&7W symulacji uwzgledniono &f"+entranceCount+"&7 szyby (rownomierna wysokosc gruntu).");
                msg(sender,"&7Bloki do objecia kopia: &f"+estimate.cells()+"&7, unikalne chunki: &f"+estimate.chunks());
                msg(sender,"&7Zakres X: &f"+estimate.minX()+".."+estimate.maxX()+"&7, Z: &f"+estimate.minZ()+".."+estimate.maxZ());
                msg(sender,"&7Zalecany wolny dysk (test): &f"+(estimate.diskReserveBytes()/(1024*1024))+" MiB");
                var readiness=DungeonTestReadiness.check(estimate,20,
                    plugin.getConfig().getInt("safety.max-test-blocks",100000),
                    Math.max(1,plugin.getConfig().getInt("safety.blocks-per-tick",350)));
                msg(sender,"&7Minimum tickow na pojedyncza faze zmian: &f"+readiness.minMutationTicks()
                    +"&7 (NIE oznacza czasu calego wydarzenia ani gwarancji braku lagow)");
                msg(sender,readiness.fitsTestLimits()?"&eRozmiar miesci sie w LIMITACH TESTU, ale konieczne sa wszystkie kontrole terenu."
                    :"&cRozmiar tylko do SYMULACJI — generowanie fizyczne zabronione.");
                for(String warning:readiness.warnings())msg(sender,"&7- "+warning);
                msg(sender,border.allowed()?"&aWstepna geometria miesci sie w borderze (dla wskazanego punktu)."
                    :"&cGeometria poza borderem: "+String.join("; ",border.reasons()));
                msg(sender,"&eWYLACZNIE analiza offline: wysokosci kazdego wejscia sa symulowane. Nie sprawdza roszczen, wody, struktur ani terenu. Limit fizycznego TESTU: 20 pokoi.");
            }
            case "ocenpodziemie" -> {
                if(args.length!=4) {msg(sender,"&eUzyj: /dungeon admin ocenpodziemie <id> <RRRR-MM-DD>");return true;}
                if(!(sender instanceof Player player))throw new IllegalArgumentException("Podziemny skan wymaga gracza");
                DungeonSchedule event=schedules.get(args[2]);
                if(event==null)throw new IllegalArgumentException("Brak wydarzenia: "+args[2]);
                LocalDate day=LocalDate.parse(args[3]);
                if(!schedules.occurs(event,day))throw new IllegalArgumentException("Brak wydarzenia na te date");
                long seed=Objects.hash(event.id,day);
                int rooms=schedules.rooms(event,day);
                if(rooms==0) {
                    int min=plugin.getConfig().getInt("safety.default-rooms-min",15);
                    int max=plugin.getConfig().getInt("safety.default-rooms-max",50);
                    if(min<15||max>100||min>max)throw new IllegalArgumentException("Bledny zakres pokoi");
                    rooms=min+new Random(seed).nextInt(max-min+1);
                }
                var layout=DungeonLayoutPlanner.generate(rooms,1,seed,
                    plugin.getConfig().getDouble("mobs.boss-chance-per-dungeon",0.05),
                    plugin.getConfig().getDouble("mobs.miniboss-chance-per-dungeon",0.25));
                var blueprint=DungeonBlueprint.fromLayout(layout);
                int x=player.getLocation().getBlockX(),z=player.getLocation().getBlockZ();
                int ox=x-layout.rooms().get(0).x(),oz=z-layout.rooms().get(0).z();
                var world=player.getWorld();
                if(!world.isChunkLoaded(Math.floorDiv(x,16),Math.floorDiv(z,16)))
                    throw new IllegalArgumentException("Chunk gracza nie jest wczytany");
                int floor=world.getHighestBlockYAt(x,z)-24;
                int sampleLimit=Math.max(32,Math.min(8192,plugin.getConfig().getInt("safety.subsurface-max-samples",1024)));
                var report=LiveSubsurfaceSurvey.inspect(world,blueprint,ox,floor,oz,sampleLimit,
                    plugin.getConfig().getDouble("safety.subsurface-max-cave-fraction",0.40));
                msg(sender,"&bWSTEPNA analiza podziemia: &f"+event.id+" / "+day+"&7, pokoi: "+rooms);
                if(report.samples()!=null)msg(sender,"&7Probek: &f"+report.samples().samples()+"&7, jaskin: &f"+
                    report.samples().caves()+"&7, plynow: &f"+report.samples().fluids()+"&7, obcych blokow: &f"+
                    report.samples().suspicious());
                for(String error:report.errors())msg(sender,"&c"+error);
                if(report.accepted())msg(sender,"&aPróbki zaakceptowane. &eTo NIE jest zgoda na budowanie — skan jest niepelny.");
                else msg(sender,"&cPodziemie nie przeszlo analizy probkowej. Niczego nie zmieniono.");
                var perf=DungeonPerformanceEstimate.of(blueprint.cells().size(),
                    Math.max(1,Math.min(2000,plugin.getConfig().getInt("safety.blocks-per-tick",350))));
                msg(sender,"&7Dolne oszacowanie pojedynczej fazy: &f"+perf.minimumSinglePhaseTicks()
                    +" tickow / "+String.format(Locale.ROOT,"%.1f",perf.minimumSinglePhaseSeconds())+" s");
            }
            case "ocenwejsc" -> {
                if(args.length!=4){msg(sender,"&eUzyj: /dungeon admin ocenwejsc <id> <RRRR-MM-DD>");return true;}
                if(!(sender instanceof Player player))throw new IllegalArgumentException("Ocena terenu wymaga gracza stojacego w miejscu wejscia");
                DungeonSchedule event=schedules.get(args[2]);
                if(event==null)throw new IllegalArgumentException("Brak wydarzenia: "+args[2]);
                LocalDate day=LocalDate.parse(args[3]);
                if(!schedules.occurs(event,day))throw new IllegalArgumentException("Brak wydarzenia na te date");
                long seed=Objects.hash(event.id,day);
                int rooms=schedules.rooms(event,day);
                if(rooms==0){
                    int min=plugin.getConfig().getInt("safety.default-rooms-min",15);
                    int max=plugin.getConfig().getInt("safety.default-rooms-max",50);
                    if(min<15||max>100||min>max)throw new IllegalArgumentException("Niepoprawny zakres liczby pokoi");
                    rooms=min+new Random(seed).nextInt(max-min+1);
                }
                int count=Math.max(1,Math.min(3,plugin.getConfig().getInt("safety.test-entrance-count",1)));
                var layout=DungeonLayoutPlanner.generate(rooms,count,seed,
                    plugin.getConfig().getDouble("mobs.boss-chance-per-dungeon",0.05),
                    plugin.getConfig().getDouble("mobs.miniboss-chance-per-dungeon",0.25));
                int anchorX=player.getLocation().getBlockX(),anchorZ=player.getLocation().getBlockZ();
                var world=player.getWorld();
                if(!world.isChunkLoaded(Math.floorDiv(anchorX,16),Math.floorDiv(anchorZ,16)))
                    throw new IllegalArgumentException("Chunk administratora niewczytany");
                int originX=anchorX-layout.rooms().getFirst().x();
                int originZ=anchorZ-layout.rooms().getFirst().z();
                int floorY=world.getHighestBlockYAt(anchorX,anchorZ)-24;
                int radius=plugin.getConfig().getInt("safety.entrance-radius-from-zero",3500);
                var candidates=DungeonMultiEntrancePlan.locations(layout,originX,originZ,radius);
                var report=LiveEntranceSurvey.inspect(world,candidates,floorY,radius);
                msg(sender,"&bAudyt terenu wejsci: &f"+event.id+" / "+day+" &7(swiat: "+world.getName()+", pokoje: "+rooms+")");
                for(var entry:report.entries())msg(sender,"&aPokoj "+entry.roomIndex()+": &fX="+entry.x()+" Y="+(entry.y()+1)+" Z="+entry.z()
                    +" &7(grunt "+entry.lowGround()+".."+entry.highGround()+")");
                for(String error:report.errors())msg(sender,"&c"+error);
                if(report.accepted()) {
                    var sites=report.entries().stream().map(e->new DungeonMultiEntrancePlan.Site(
                        e.roomIndex(),e.x(),e.z(),e.y())).toList();
                    var estimate=DungeonPlanEstimate.analyzeMulti(layout,DungeonBlueprint.fromLayout(layout),
                        originX,originZ,floorY,sites);
                    var border=PlacementRules.validate(0,0,anchorX,anchorZ,
                        new DungeonBlueprint.Bounds(estimate.minX(),estimate.maxX(),estimate.minY(),
                            estimate.maxY(),estimate.minZ(),estimate.maxZ()),radius,
                        plugin.getConfig().getInt("safety.world-coordinate-limit",4000),
                        plugin.getConfig().getInt("safety.closed-protection-margin",50));
                    msg(sender,"&7Faktyczne wysokosci wejsc: &f"+estimate.cells()+"&7 unikalnych blokow, &f"+estimate.chunks()+"&7 chunkow w planie.");
                    if(!border.allowed())msg(sender,"&cPlan poza borderem: "+String.join("; ",border.reasons()));
                    else msg(sender,"&aSprawdzono powierzchnie wszystkich "+count+" wejsc i border.");
                    msg(sender,"&eTO NIE JEST ZGODA NA GENEROWANIE: kontrola WG, gildii, podziemia i snapshotu odbywa sie osobno.");
                }
                else msg(sender,"&cTeren nie przeszedl kontroli. Nie buduj tu dungeona.");
                msg(sender,"&7Tylko odczyt — nie laduje chunkow ani nie zmienia blokow.");
            }
            case "podgladmoby" -> {
                if(args.length!=4){msg(sender,"&eUzyj: /dungeon admin podgladmoby <id> <RRRR-MM-DD>");return true;}
                DungeonSchedule event=schedules.get(args[2]);
                if(event==null)throw new IllegalArgumentException("Nieznane wydarzenie: "+args[2]);
                LocalDate day=LocalDate.parse(args[3]);
                if(!schedules.occurs(event,day))throw new IllegalArgumentException("Nie ma wydarzenia tego dnia");
                long seed=Objects.hash(event.id,day);
                Random random=new Random(seed);
                int n=schedules.rooms(event,day);
                if(n==0)n=plugin.getConfig().getInt("safety.default-rooms-min",15)
                    +random.nextInt(plugin.getConfig().getInt("safety.default-rooms-max",50)
                    -plugin.getConfig().getInt("safety.default-rooms-min",15)+1);
                var layout=DungeonLayoutPlanner.generate(n,1,seed,
                    plugin.getConfig().getDouble("mobs.boss-chance-per-dungeon",0.05),
                    plugin.getConfig().getDouble("mobs.miniboss-chance-per-dungeon",0.25));
                var plan=MobEncounterPlan.generate(layout,seed,
                    plugin.getConfig().getDouble("mobs.occupied-room-fraction",0.5),
                    plugin.getConfig().getDouble("mobs.boosted-fraction",0.5));
                msg(sender,"&bSymulacja potworow: &f"+event.id+" / "+day+" / "+n+" pokoi");
                msg(sender,"&7Moby: &f"+plan.size()+" &7(wzmocnione: &f"+plan.stream().filter(MobEncounterPlan.Encounter::boosted).count()+"&7)");
                msg(sender,"&7Boss: &f"+layout.boss()+"&7, miniboss: &f"+layout.miniboss());
                msg(sender,"&eTylko podglad — nie tworzy bytow ani blokow.");
            }
            case "podgladwejsc" -> {
                if(args.length<4 || args.length>5) {
                    msg(sender,"&eUzyj: /dungeon admin podgladwejsc <id> <RRRR-MM-DD> [1-10]");return true;
                }
                DungeonSchedule event=schedules.get(args[2]);
                if(event==null)throw new IllegalArgumentException("Nieznane wydarzenie: "+args[2]);
                LocalDate day=LocalDate.parse(args[3]);
                if(!schedules.occurs(event,day))throw new IllegalArgumentException("Brak wydarzenia na ten dzien");
                int count=args.length==5?Integer.parseInt(args[4]):1;
                if(count<1||count>10)throw new IllegalArgumentException("Wejscia: od 1 do 10");
                int rooms=schedules.rooms(event,day);
                long seed=Objects.hash(event.id,day);
                if(rooms==0) {
                    int min=plugin.getConfig().getInt("safety.default-rooms-min",15);
                    int max=plugin.getConfig().getInt("safety.default-rooms-max",50);
                    if(min<15||max>100||max<min)throw new IllegalArgumentException("Bledne limity pokoi w config.yml");
                    rooms=min+new Random(seed).nextInt(max-min+1);
                }
                var layout=DungeonLayoutPlanner.generate(rooms,count,seed,
                    plugin.getConfig().getDouble("mobs.boss-chance-per-dungeon",0.05),
                    plugin.getConfig().getDouble("mobs.miniboss-chance-per-dungeon",0.25));
                var preview=DungeonEntrancePreview.forLayout(layout);
                msg(sender,"&bPLAN &f"+event.id+" &7/ "+day+": &f"+preview.size()+" propozycji wejsc, "+rooms+" pokoi.");
                for(var entrance:preview)msg(sender,"&7Pokoj #"+entrance.roomIndex()
                    +": przesuniecie X=&f"+entrance.relativeX()+" &7Z=&f"+entrance.relativeZ());
                msg(sender,"&cTYLKO PLAN LOKALNY: NIE sa to koordynaty swiata! Dodatkowe szyby nie sa jeszcze fizycznie generowane.");
            }
            case "testkomunikat" -> {
                if(args.length!=5){
                    msg(sender,"&eUzyj: /dungeon admin testkomunikat <id> <RRRR-MM-DD> <30|10>");return true;
                }
                DungeonSchedule event=schedules.get(args[2]);
                if(event==null)throw new IllegalArgumentException("Nieznane wydarzenie: "+args[2]);
                LocalDate day=LocalDate.parse(args[3]);
                if(!schedules.occurs(event,day))throw new IllegalArgumentException("Brak wydarzenia tego dnia lub wyjatek");
                int minutes=Integer.parseInt(args[4]);
                if(minutes!=30&&minutes!=10)throw new IllegalArgumentException("Podaj 30 lub 10 minut");
                var built=recovery.activeTestEntrances().stream().filter(e -> e.scheduleId().equals(event.id)
                    && e.day().equals(day)).toList();
                if(built.isEmpty()) {
                    msg(sender,"&cBrak faktycznie przygotowanego testu dla "+event.id+" / "+day
                        +". Nie mozna pokazywac fikcyjnej liczby wejsci ani komunikatu gotowosci.");
                    return true;
                }
                if(minutes==30) {
                    msg(sender,"&bDungeon &f"+event.id+" &botworzy sie za &e30 minut&b! &8(PRYWATNY TEST)");
                    if(sender instanceof Player player)player.sendTitle(plugin.color("&b&lDUNGEON ZA 30 MINUT!"),
                        plugin.color("&f"+event.id+" &7• podglad dla administratora"),10,70,20);
                }else {
                    msg(sender,"&eDungeon &f"+event.id+" &eotworzy sie za 10 minut! &b"
                        +DungeonStartNotices.tenMinuteDetails(built.size(),event.durationMinutes)
                        +" &8(PRYWATNY TEST)");
                }
                msg(sender,"&7Wiadomosc wyslana tylko Tobie. Nic nie ogloszono innym graczom i nie zmieniono swiata.");
            }
            case "sprawdzteren" -> {
                if (!(sender instanceof Player p)) { msg(sender,"&cUzyj tej komendy w grze."); return true; }
                if (args.length != 4) {msg(sender,"&eUzyj: /dungeon admin sprawdzteren <id> <RRRR-MM-DD>"); return true;}
                DungeonSchedule event=schedules.get(args[2]);
                if (event==null) throw new IllegalArgumentException("Nieznane wydarzenie: "+args[2]);
                LocalDate date=LocalDate.parse(args[3]);
                if (!schedules.occurs(event,date))throw new IllegalArgumentException("Brak wydarzenia w podanym dniu");
                int n=schedules.rooms(event,date);
                long seed=Objects.hash(event.id,date);
                Random rng=new Random(seed);
                if(n==0) n=plugin.getConfig().getInt("safety.default-rooms-min",15)
                    +rng.nextInt(plugin.getConfig().getInt("safety.default-rooms-max",50)
                     -plugin.getConfig().getInt("safety.default-rooms-min",15)+1);
                var layout=DungeonLayoutPlanner.generate(n,1+rng.nextInt(10),seed,
                    plugin.getConfig().getDouble("mobs.boss-chance-per-dungeon",0.05),
                    plugin.getConfig().getDouble("mobs.miniboss-chance-per-dungeon",0.25));
                var blueprint=DungeonBlueprint.fromLayout(layout);
                var result=TerrainPreflight.inspect(p,layout,blueprint,
                    plugin.getConfig().getInt("safety.entrance-radius-from-zero",3500),
                    plugin.getConfig().getInt("safety.world-coordinate-limit",4000),
                    plugin.getConfig().getInt("safety.closed-protection-margin",50));
                msg(sender,"&bAnaliza 3D (bez budowania): &f"+layout.rooms().size()+" pokoi, "+blueprint.cells().size()+" blokow planu.");
                msg(sender,"&7Poziom terenu (probki): &f"+result.groundMin()+".."+result.groundMax()+"&7; proponowana podloga Y=&f"+result.suggestedFloorY());
                for(String reason:result.reasons())msg(sender,"&e- "+reason);
                msg(sender,"&cUWAGA: to NIE jest potwierdzenie bezpiecznego miejsca; plugin nie zmienia terenu.");
            }
            case "audyt" -> {
                if(args.length!=3){msg(sender,"&eUzyj: /dungeon admin audyt <id|wszystkie>");return true;}
                if(args[2].equalsIgnoreCase("wszystkie")) {
                    msg(sender,"&bAktywne odzyskiwania: &f"+recovery.describe());
                    for(String line:recovery.auditDirectory())msg(sender,line.startsWith("ALARM")?"&c"+line:"&7"+line);
                    msg(sender,"&7Pelna diagnostyka: /dungeon admin audyt <id> (odczyt sumy kontrolnej kopii)");
                } else {
                    for(String line:recovery.audit(args[2]))
                        msg(sender,(line.startsWith("ALARM")||line.startsWith("UWAGA")?"&c":"&7")+line);
                }
            }
            case "sprawdzkopie" -> {
                if(args.length!=3){msg(sender,"&eUzyj: /dungeon admin sprawdzkopie <id>");return true;}
                msg(sender,"&aOdczyt i kontrola pliku OK: &f"+recovery.verifyBackup(args[2]));
                msg(sender,"&eTo nie jest gwarancja pelnego odtworzenia NBT, ekwipunkow ani zmian innych pluginow.");
            }
            case "wejscia" -> {
                if(args.length!=2){msg(sender,"&eUzyj: /dungeon admin wejscia");return true;}
                var active=recovery.activeTestEntrances();
                if(active.isEmpty())msg(sender,"&7Brak aktywnych, rzeczywiscie wygenerowanych testow.");
                else for(var e:active)msg(sender,"&bTEST &f"+e.id()+"&7 ("+e.day()+") &7"+e.worldName()
                    +"&7: X=&f"+e.x()+"&7 Y=&f"+e.y()+"&7 Z=&f"+e.z());
            }
            case "status" -> {
                msg(sender,"&eETAP 20: audyt recoveries, test komunikatu dla admina, plan 1-10 wejsc; bariery i wspolrzedne wejsc AKTYWNEGO testu; opcjonalne znaczniki BlueMap tylko test. Brak automatycznej generacji i ujawniania dungeonow SMP.");
                msg(sender,"&7Odzyskiwanie: &f"+recovery.describe());
                msg(sender,"&7Strefa: &f"+plugin.timezone()+"&7; terminow: &f"+schedules.all().size());
            }
            case "reload" -> msg(sender,plugin.reloadAll()?"&aPonownie wczytano config.yml, schedules.yml i loot.yml.":"&cBlad reload; sprawdz konsole.");
            default -> help(sender);
        }} catch(Exception e) {msg(sender,"&cBlad polecenia: &f"+e.getMessage());}
        return true;
    }
    private void help(CommandSender s){
        msg(s,"&bKomendy manius_dungeons (TAB = podpowiedzi):");
        msg(s,"&f/dungeon admin &7- panel GUI");
        msg(s,"&b/dungeon wejscia &7- publiczne, ujawnione wejscia testowe");
        msg(s,"&b/dungeon admin wejscia &7- wspolrzedne wszystkich aktywnych TESTOW");
        msg(s,"&f/dungeon admin zaplanuj <id> <motyw> <RRRR-MM-DD|dzis|jutro|codziennie|dni:...> <HH:mm> <minuty> [pokoje]");
        msg(s,"&7TAB podpowiada wszystkie godziny 00:00-23:00; minuty wpisz recznie np. 14:30.");
        msg(s,"&aNajlatwiej: &f/dungeon admin kalendarz &7-> przycisk Zaplanuj dungeon.");
        msg(s,"&f/dungeon admin lista &7- wszystkie wydarzenia");
        msg(s,"&f/dungeon admin wyjatek <id> <data> <odwolaj|przywroc> &7- wyjątek w harmonogramie");
        msg(s,"&b/dungeon admin podgladpulapki <id> <data> &7- symulacja pulapek");
        msg(s,"&f/dungeon admin kalendarz &7- GUI terminarza");
        msg(s,"&f/dungeon admin pokoje <id> <data> <15-100> &7- pokojow na konkretny dzien");
        msg(s,"&f/dungeon admin usun <id> &7- usuwa termin (bez aktywnego terenu)");
        msg(s,"&c/dungeon admin anuluj <id> potwierdz &7- awaryjne usuwanie i odtwarzanie");
        msg(s,"&e/dungeon admin wznow <id> potwierdz &7- ponowny odczyt snapshotu i rollback");
        msg(s,"&b/dungeon admin sprawdzkopie <id> &7- sprawdzenie kopii (bez zmian swiata)");
        msg(s,"&b/dungeon admin audyt <id|wszystkie> &7- audyt stanu, chunkow i snapshotu");
        msg(s,"&e/dungeon admin testbuduj <id> <data> potwierdz &7- wylacznie osobny swiat testowy");
        msg(s,"&b/dungeon admin przygotowanie <id> <data> &7- stan przygotowania auto-testu (read-only)");
        msg(s,"&f/dungeon admin podglad <id> <data> &7- podglad ukladu bez budowania");
        msg(s,"&b/dungeon admin ocenplan <id> <data> &7- liczba blokow/chunkow i rezerwa dysku dla 15-100 pokoi");
        msg(s,"&b/dungeon admin podgladmoby <id> <data> &7- podglad mobow i bossow, bez budowania");
        msg(s,"&b/dungeon admin podgladwejsc <id> <data> [1-10] &7- lokalne plany dodatkowych wejsc");
        msg(s,"&b/dungeon admin testkomunikat <id> <data> <30|10> &7- PRYWATNY podglad chat/title");
        msg(s,"&f/dungeon admin ocenpodziemie <id> <data> &7- probkowy, odczytowy audyt podziemia");
        msg(s,"&f/dungeon admin ocenwejsc <id> <data> &7- odczyt 1-3 faktycznych wysokosci wejsc");
        msg(s,"&f/dungeon admin sprawdzteren <id> <data> &7- analiza blokow wokol gracza bez budowania");
        msg(s,"&b/dungeon admin szukajmiejsca <id> <data> &7- wstepny losowy poszukiwacz miejsc (read-only)");
        msg(s,"&f/dungeon admin loot <slaby|dobry|boss|miniboss> &7- GUI przedmiotow");
        msg(s,"&f/dungeon admin szansa <pool> <slot> <0-100> &7- zapis szansy");
        msg(s,"&b/dungeon admin losujloot <pool> [max] &7- symulacja losowania bez zmian swiata");
        msg(s,"&f/dungeon admin reload / status / pomoc");
        msg(s,"&eUWAGA: testowe skrzynie i moby wymagaja osobnych zgod w configu, brak generacji na SMP.");
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(args.length==1)return starts(List.of("admin","wejscia"),args[0]);
        if(!sender.hasPermission("maniusdungeons.admin")||!args[0].equalsIgnoreCase("admin"))return List.of();
        if(args.length==2)return starts(SUBS,args[1]);
        String sub=args[1].toLowerCase(Locale.ROOT);
        List<String> choices=switch(sub){
            case "loot", "szansa", "losujloot" -> args.length==3?LootStore.POOLS:args.length==4&&sub.equals("szansa")?List.of("1","2","3","4","5","10","20","45"):args.length==4&&sub.equals("losujloot")?List.of("1","2","4","8","16","27"):
                args.length==5?List.of("0.01","1","5","10","25","50","100"):List.of();
            case "zaplanuj" -> switch(args.length){
                case 3 -> List.of("dungeon1");
                case 4 -> List.of("mechowy","siarkowy","pustynny","mesa");
                case 5 -> ScheduleInput.dates(LocalDate.now(plugin.timezone()));
                case 6 -> ScheduleInput.times();
                case 7 -> List.of("60","120");
                case 8 -> List.of("losowo","15","20","30","50","100");
                default -> List.of();
            };
            case "anuluj", "wznow", "sprawdzkopie", "audyt" -> args.length==3
                ? java.util.stream.Stream.concat(java.util.stream.Stream.concat(schedules.all().stream().map(x->x.id),recovery.recoveryIds().stream()),
                    sub.equals("audyt") ? java.util.stream.Stream.of("wszystkie") : java.util.stream.Stream.<String>empty()).distinct().sorted().toList()
                : args.length==4 && !sub.equals("sprawdzkopie")?List.of("potwierdz"):List.of();
            case "usun", "testbuduj", "pokoje", "podglad", "podgladmoby", "ocenplan", "ocenwejsc", "ocenpodziemie", "podgladpulapki", "podgladwejsc", "testkomunikat", "wyjatek", "przygotowanie", "sprawdzteren", "szukajmiejsca" -> args.length==3?schedules.all().stream().map(x->x.id).toList()
                :args.length==4&&(sub.equals("pokoje")||sub.equals("podglad")||sub.equals("podgladmoby")||sub.equals("ocenplan")||sub.equals("ocenwejsc")||sub.equals("ocenpodziemie")||sub.equals("podgladpulapki")||sub.equals("podgladwejsc")||sub.equals("testkomunikat")||sub.equals("wyjatek")||sub.equals("przygotowanie")||sub.equals("sprawdzteren")||sub.equals("szukajmiejsca")||sub.equals("testbuduj"))?List.of(LocalDate.now(plugin.timezone()).toString())
                :args.length==5&&sub.equals("wyjatek")?List.of("odwolaj","przywroc")
                :args.length==5&&sub.equals("podgladwejsc")?List.of("1","2","3","5","10")
                :args.length==5&&sub.equals("testkomunikat")?List.of("30","10")
                :args.length==5&&sub.equals("testbuduj")?List.of("potwierdz")
                :args.length==5?List.of("15","20","50","100"):List.of();
            default -> List.of();
        };
        return starts(choices,args[args.length-1]);
    }
    private List<String> starts(List<String> options,String typed){
        String lower=typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(o->o.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
