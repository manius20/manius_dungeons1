package pl.s16.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityBlockFormEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.block.DoubleChest;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Level;

/**
 * ETAP 3: fail-closed generator TESTOWY + awaryjne odtwarzanie po restarcie.
 * Wylacznie synchroniczne wywolania Bukkit. Nic nie wykonuje sie na swiecie SMP.
 * Pliki kopii sa zachowywane po odtworzeniu do czasu recznego archiwizowania.
 */
public final class RecoveryManager implements Listener {
    private enum Phase { CAPTURING, BUILDING, PREPARED, ACTIVE, RESTORING, RESTORING_TILES, VERIFYING, RESTORED, FAILED }
    private record Original(int x,int y,int z,String data,String tileYaml) {}
    private record Change(int x,int y,int z,String blockData) {}
    /** World coordinates; persisted with the recovery manifest for every real test shaft. */
    private record EntryPoint(int x,int y,int z) {}
    private final ManiusDungeons plugin;
    private final File dir;
    private final Map<String,Job> jobs=new LinkedHashMap<>();
    private BukkitTask runner;
    private boolean recoveryFilesCorrupt;
    private final List<String> directoryFindings=new ArrayList<>();
    private final Set<String> worldsMissingWarned=new HashSet<>();
    private final Set<String> chunksMissingWarned=new HashSet<>();

    private static final class Job {
        String id,eventId,worldName; UUID worldUid;
        int minX,maxX,minY,maxY,minZ,maxZ,originX,originZ,floorY,entranceX,entranceZ,entranceY;
        Phase phase;
        List<Original> originals=new ArrayList<>();
        List<Change> changes=new ArrayList<>();
        final Set<BlockKey> generatedChests=new HashSet<>();
        final List<EntryPoint> entrances=new ArrayList<>();
        final Set<UUID> mobUuids=new LinkedHashSet<>();
        int cursor=0;
        int plannedCount=-1; String plannedCoordinatesSha="";
        int verificationAttempts=0, verificationMismatches=0;
        boolean restorationVerified=false;
        LocalDate scheduledDay; long scheduledEndEpochMillis;
        DungeonLayoutPlanner.Layout mobLayout; long mobSeed;
        Job(String id){this.id=id;this.eventId=id;}
        boolean contains(Location p,int pad){return p.getWorld()!=null && p.getWorld().getUID().equals(worldUid)
            && p.getBlockX()>=minX-pad && p.getBlockX()<=maxX+pad
            && p.getBlockY()>=minY-pad && p.getBlockY()<=maxY+pad
            && p.getBlockZ()>=minZ-pad && p.getBlockZ()<=maxZ+pad;}
    }
    RecoveryManager(ManiusDungeons p){
        plugin=p;dir=new File(p.getDataFolder(),"recoveries");
        if(!dir.isDirectory() && !dir.mkdirs()) {
            recoveryFilesCorrupt=true;
            directoryFindings.add("Nie mozna utworzyc katalogu recoveries/");
        }
        try {
            var audit=RecoveryDirectoryAudit.inspect(dir.toPath());
            directoryFindings.addAll(audit.findings());
            if(!audit.safeForNewTests())recoveryFilesCorrupt=true;
            for(String warning:audit.findings())plugin.getLogger().severe("RECOVERY ALARM: "+warning);
        }catch(IOException e){
            recoveryFilesCorrupt=true;
            directoryFindings.add("Blad odczytu recoveries/: "+e.getMessage());
            plugin.getLogger().log(Level.SEVERE,"Nie mozna skontrolowac folderu recoveries/",e);
        }
        loadManifests();
    }
    void start(){runner=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20L,1L);}
    void stop(){if(runner!=null)runner.cancel();}
    public boolean isActiveTest(String id){Job job=jobs.get(id);return job!=null&&job.phase==Phase.ACTIVE;}
    public boolean isActiveTestForDate(String id,LocalDate date){
        return jobs.values().stream().anyMatch(j -> j.phase==Phase.ACTIVE && id.equals(j.eventId) && date.equals(j.scheduledDay));
    }
    public boolean hasOngoingTestForDate(String id,LocalDate date){
        return jobs.values().stream().anyMatch(j -> id.equals(j.eventId)
            && j.phase!=Phase.RESTORED && date.equals(j.scheduledDay));
    }
    /** Deadline saved in the recovery manifest, independent of a later YAML schedule edit. */
    public List<String> expiredActiveTestIds(java.time.Instant now) {
        return jobs.values().stream().filter(j -> (j.phase == Phase.ACTIVE || j.phase == Phase.PREPARED)
            && TestDeadlinePolicy.expired(now, j.scheduledEndEpochMillis)).map(j -> j.id).toList();
    }
    /** Read-only operational report; for a specific id can validate the physical backup. */
    public List<String> audit(String id) {
        Job j = jobs.get(id);
        if (j == null) return List.of("Brak kopii/manifestu: " + id + ". To moze byc tylko zaplanowane wydarzenie.");
        List<String> out = new ArrayList<>();
        out.add("Recovery " + j.id + " (termin: " + j.eventId + "): " + j.phase + ", " + j.worldName
            + ", obszar X=" + j.minX + ".." + j.maxX + ", Y=" + j.minY + ".." + j.maxY
            + ", Z=" + j.minZ + ".." + j.maxZ);
        World world = Bukkit.getWorld(j.worldUid);
        if (world == null) out.add("UWAGA: swiat nie jest zaladowany - rollback czeka.");
        else {
            int missing = 0;
            for (int x = Math.floorDiv(j.minX,16); x <= Math.floorDiv(j.maxX,16); x++)
                for (int z = Math.floorDiv(j.minZ,16); z <= Math.floorDiv(j.maxZ,16); z++)
                    if (!world.isChunkLoaded(x,z)) missing++;
            out.add(missing==0?"Chunki obszaru: wszystkie zaladowane."
                :"UWAGA: brak zaladowanych chunkow: " + missing + ". Przywracanie czeka, nie usuwaj recoveries/.");
            out.add("Gracze w obszarze: " + world.getPlayers().stream()
                .filter(pl -> j.contains(pl.getLocation(),5)).count());
        }
        out.add("Wejscia utworzone (wg manifestu): " + (j.entrances.isEmpty()?1:j.entrances.size()));
        for(var e:j.entrances)out.add("Wejscie: X="+e.x()+" Y="+e.y()+" Z="+e.z());
        if (j.scheduledEndEpochMillis > 0L)
            out.add("Deadline testu (UTC): " + java.time.Instant.ofEpochMilli(j.scheduledEndEpochMillis));
        try { out.add("Kopia: " + verifyBackup(id)); }
        catch(IOException problem) {
            if(j.phase==Phase.CAPTURING || (j.phase==Phase.FAILED && !snapshotFile(j).exists()))
                out.add("Kopia: jeszcze niegotowa; w fazie CAPTURING nie wolno zmieniac blokow.");
            else out.add("ALARM: " + problem.getMessage());
        }
        if(recoveryFilesCorrupt) out.add("ALARM: uszkodzone manifesty - nowe generowanie zablokowane.");
        return List.copyOf(out);
    }
    public List<String> auditDirectory() {
        List<String> lines=new ArrayList<>();
        lines.add(recoveryFilesCorrupt ? "ALARM: nowe testowe generowanie ZABLOKOWANE przez recovery!"
            : "Katalog recoveries/: wstepny audyt przy starcie OK (nie gwarantuje integralnosci snapshotow).");
        lines.addAll(directoryFindings.stream().map(s->"ALARM: "+s).toList());
        if(directoryFindings.isEmpty() && recoveryFilesCorrupt)
            lines.add("ALARM: jeden lub wiecej manifestow jest niepoprawnych. Szczegoly w konsoli serwera.");
        return List.copyOf(lines);
    }
    public boolean hasJob(String id){Job j=jobs.get(id);return j!=null&&j.phase!=Phase.RESTORED;}
    /** Player notices are limited to the active TEST area's occupants; never sent as fake SMP announcements. */
    public void notifyActiveTestPlayers(String id,LocalDate date,String text) {
        Job job=jobs.values().stream().filter(j -> id.equals(j.eventId) && j.phase==Phase.ACTIVE && date.equals(j.scheduledDay))
            .findFirst().orElse(null);
        if(job==null)return;
        World world=Bukkit.getWorld(job.worldUid);
        if(world==null)return;
        for(Player player:world.getPlayers()) if(job.contains(player.getLocation(),5))
            player.sendMessage(plugin.prefix()+plugin.color(text));
    }

    /** Publication uses ACTUAL built test geometry. Never fabricate coordinates for scheduled-only events. */
    public record TestEntrance(String id, String scheduleId, LocalDate day, UUID worldUid, String worldName,
                               int x, int y, int z, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        public boolean inside(Location at) {
            return at != null && at.getWorld() != null && worldUid.equals(at.getWorld().getUID())
                && at.getBlockX() >= minX && at.getBlockX() <= maxX
                && at.getBlockY() >= minY && at.getBlockY() <= maxY
                && at.getBlockZ() >= minZ && at.getBlockZ() <= maxZ;
        }
    }
    /** No copies of mutable job references escape this manager. */
    public List<TestEntrance> activeTestEntrances() {
        return jobs.values().stream().filter(j -> (j.phase == Phase.ACTIVE || j.phase == Phase.PREPARED) && j.scheduledDay != null)
            .flatMap(j -> (j.entrances.isEmpty() ? List.of(new EntryPoint(j.entranceX,j.entranceY,j.entranceZ))
                : j.entrances).stream().map(entry -> new TestEntrance(j.id,j.eventId,j.scheduledDay,j.worldUid,j.worldName,
                entry.x(),entry.y(),entry.z(),j.minX,j.maxX,j.minY,j.maxY,j.minZ,j.maxZ)))
            .toList();
    }
    public List<String> recoveryIds(){return List.copyOf(jobs.keySet());}
    public boolean hasJobForSchedule(String scheduleId) {
        return jobs.values().stream().anyMatch(j -> scheduleId.equals(j.eventId) && j.phase!=Phase.RESTORED);
    }
    /** Restore every occurrence of the event; preserves the copy for each date. */
    public boolean requestRestoreForSchedule(String scheduleId) {
        boolean found=false;
        for(Job j:new ArrayList<>(jobs.values()))if(scheduleId.equals(j.eventId) && j.phase!=Phase.RESTORED) {
            if(!requestRestore(j.id)) throw new IllegalStateException("Brak recovery: "+j.id);
            found=true;
        }
        return found;
    }
    public boolean requestRestoreForOccurrence(String scheduleId,LocalDate day) {
        boolean restored=false;
        for(Job j:new ArrayList<>(jobs.values()))if(scheduleId.equals(j.eventId) && day.equals(j.scheduledDay) && j.phase!=Phase.RESTORED) {
            if(!requestRestore(j.id)) throw new IllegalStateException("Brak recovery: "+j.id);
            restored=true;
        }
        return restored;
    }
    /** Open prepared tests only at their actual approved schedule time. */
    public void activateDueTests(java.time.Instant now,ScheduleStore schedules,java.time.ZoneId timezone) {
        for(Job j:new ArrayList<>(jobs.values())) {
            if(j.phase != Phase.PREPARED || j.scheduledDay==null)continue;
            DungeonSchedule event=schedules.get(j.eventId);
            if(event==null || !schedules.occurs(event,j.scheduledDay)) {
                requestRestore(j.id);continue;
            }
            EventTimeline.Window window=EventTimeline.window(event,j.scheduledDay,timezone);
            if(!now.isBefore(window.end().toInstant())) {requestRestore(j.id);continue;}
            if(now.isBefore(window.start().toInstant()))continue;
            World world=Bukkit.getWorld(j.worldUid);
            if(world==null) {plugin.getLogger().warning("Test "+j.id+": swiat niedostepny, brak otwarcia");continue;}
            try {
                // The manifest becomes ACTIVE only when all mobs have been spawned.
                if(j.mobLayout!=null) j.mobUuids.addAll(plugin.testMobs().spawn(world,j.id,j.mobLayout,
                    j.originX,j.floorY,j.originZ,j.mobSeed));
                j.phase=Phase.ACTIVE;saveManifest(j);
                plugin.getLogger().warning("OTWARTO testowy dungeon "+j.id+" w swiecie "+j.worldName);
                for(Player player:Bukkit.getOnlinePlayers())if(player.hasPermission("maniusdungeons.admin"))
                    player.sendMessage(plugin.prefix()+plugin.color("&aTestowy dungeon "+j.eventId+" zostal otwarty."));
            }catch(Exception ex) {
                plugin.getLogger().log(Level.SEVERE,"Nie mozna otworzyc testu "+j.id+"; rozpoczynam rollback",ex);
                requestRestore(j.id);
            }
        }
    }
    public String describeOccurrence(String scheduleId,LocalDate day) {
        return jobs.values().stream().filter(j->scheduleId.equals(j.eventId)&&day.equals(j.scheduledDay))
            .map(j->j.id+"="+j.phase).findFirst().orElse("brak wygenerowanego testu");
    }
    public boolean isOpenedJob(String jobId) {return isActiveTest(jobId);}
    public String describe(){return jobs.values().stream().map(j->j.id+"="+j.phase+"("+j.cursor+"/"+Math.max(j.changes.size(),j.originals.size())+")").reduce((a,b)->a+", "+b).orElse("brak");}

    /** Kontrola pliku recovery BEZ uruchamiania rollbacku i BEZ zmiany swiata. */
    public String verifyBackup(String id) throws IOException {
        Job j=jobs.get(id);
        if(j==null)throw new IOException("Brak zarejestrowanego testu: "+id);
        File file=snapshotFile(j);
        if(!file.isFile())throw new IOException("Brak pliku snapshotu (nie wolno obiecywac rollbacku): "+file.getName());
        RecoverySnapshotCodec.Snapshot data=RecoverySnapshotCodec.read(file.toPath(),j.worldUid);
        verifyCoverage(j,data);
        for(RecoverySnapshotCodec.Cell cell:data.cells())BlockEntityBackup.validate(cell.blockData(),cell.tileYaml());
        // Weryfikuj tez zakres obszaru z manifestu, a nie tylko sumy kontrolne.
        for(RecoverySnapshotCodec.Cell c:data.cells()) {
            if(c.x()<j.minX||c.x()>j.maxX||c.y()<j.minY||c.y()>j.maxY
                ||c.z()<j.minZ||c.z()>j.maxZ)throw new IOException("Snapshot zawiera bloki poza obszarem z manifestu");
        }
        return "ID="+j.id+", stan="+j.phase+", swiat="+j.worldName
            +", blokow="+data.cells().size()+", plik="+file.length()+" B, format="
            +"v"+data.version()+(data.version()==1?" (bez SHA-256)":" (SHA-256)")+", kontenerow="+data.tileCount();
    }

    /** Rzeczywiste bloki tylko w dedykowanym swiecie testowym, po jawnej zgodzie w configu i komendzie. */
    public void beginTest(Player p,String id,LocalDate day,DungeonSchedule schedule,int rooms){
        beginTest(p.getWorld(),p.getLocation(),id,schedule.id,day,schedule,rooms);
    }
    /** Automatic opt-in: the anchor is loaded from config in a SEPARATE test world. */
    public void beginTest(World world,Location anchor,String id,String scheduleId,LocalDate day,DungeonSchedule schedule,int rooms){
        if(!java.time.Instant.now().isBefore(EventTimeline.window(schedule,day,plugin.timezone()).end().toInstant()))
            throw new IllegalStateException("Termin testu juz minal; nie rozpoczynam generowania po zamknieciu wydarzenia");
        if(recoveryFilesCorrupt)throw new IllegalStateException("Uszkodzone pliki recoveries/; generowanie ZABLOKOWANE do recznej naprawy");
        if(new File(dir,id+".yml").exists() || new File(dir,id+".snapshot.gz").exists())
            throw new IllegalStateException("Na dysku istnieje juz kopia lub manifest o tym ID");
        if(!plugin.getConfig().getBoolean("safety.enable-test-world-mutations",false))
            throw new IllegalStateException("Wylaczone: safety.enable-test-world-mutations=false");
        String testWorld=plugin.getConfig().getString("safety.test-world-name","");
        String production=plugin.getConfig().getString("settings.world","S16_wojny_1");
        if(testWorld.isBlank()||testWorld.equalsIgnoreCase(production)||!world.getName().equals(testWorld))
            throw new IllegalStateException("Wymagany ODDZIELNY swiat testowy rozny od settings.world");
        if(jobs.values().stream().anyMatch(j->j.phase!=Phase.RESTORED))
            throw new IllegalStateException("Najpierw zakoncz odtwarzanie poprzedniego testu");
        if(jobs.containsKey(id))throw new IllegalStateException("ID ma juz zapis recovery; uzyj nowego ID testu");
        if(rooms>20)throw new IllegalStateException("Budowanie TESTOWE ograniczone do 20 pokoi");
        long seed=Objects.hash(schedule.id,day);
        Random random=new Random(seed);
        int requestedEntrances=Math.max(1,Math.min(3,plugin.getConfig().getInt("safety.test-entrance-count",1)));
        DungeonLayoutPlanner.Layout layout=DungeonLayoutPlanner.generate(rooms,requestedEntrances,seed,
            plugin.getConfig().getDouble("mobs.boss-chance-per-dungeon",0.05),
            plugin.getConfig().getDouble("mobs.miniboss-chance-per-dungeon",0.25));
        DungeonBlueprint bp=DungeonBlueprint.fromLayout(layout);
        // Wejscie musi znajdowac sie W PIERWSZYM POKOJU, inaczej szyb nie bylby polaczony.
        int cx=anchor.getBlockX(),cz=anchor.getBlockZ();
        int ox=cx-layout.rooms().get(0).x(),oz=cz-layout.rooms().get(0).z();
        var valid=PlacementRules.validate(ox,oz,cx,cz,bp.bounds(),
           plugin.getConfig().getInt("safety.entrance-radius-from-zero",3500),
           plugin.getConfig().getInt("safety.world-coordinate-limit",4000),
           plugin.getConfig().getInt("safety.closed-protection-margin",50));
        if(!valid.allowed())throw new IllegalStateException(String.join("; ",valid.reasons()));
        World w=world;
        if(!w.isChunkLoaded(cx>>4,cz>>4))throw new IllegalStateException("Nieczytelny chunk wejscia; brak ladowania chunkow w fazie testowej");
        int top=w.getHighestBlockYAt(cx,cz);
        int y=top-24;
        if(y<w.getMinHeight()+5||y+6>w.getMaxHeight()-8)throw new IllegalStateException("Brak miejsca pod ziemia");
        // ETAP 24: bounded read-only underground preview; NEVER sufficient alone
        // to authorize mutations. Every target block is validated again while
        // the complete pre-build snapshot is captured on the main thread.
        var underground=LiveSubsurfaceSurvey.inspect(w,bp,ox,y,oz,
            Math.max(32,Math.min(8192,plugin.getConfig().getInt("safety.subsurface-max-samples",1024))),
            plugin.getConfig().getDouble("safety.subsurface-max-cave-fraction",0.40));
        if(!underground.accepted())throw new IllegalStateException(
            "Odmowa podziemnego skanowania: "+String.join("; ",underground.errors()));
        // In a TEST world, also honor existing protections if their plugins are present.
        // These are current read-only checks, not permission to build on SMP.
        int margin=plugin.getConfig().getInt("safety.closed-protection-margin",50);
        int x1=ox+bp.bounds().minX()-margin-3, x2=ox+bp.bounds().maxX()+margin+3;
        int z1=oz+bp.bounds().minZ()-margin-3, z2=oz+bp.bounds().maxZ()+margin+3;
        var countries=CountryOverlapSurvey.inspect(w,x1,z1,x2,z2);
        if (!TestBuildGate.allowRegions(countries.verified(),countries.overlaps(),
            plugin.getConfig().getBoolean("safety.require-country-verification-for-test-build",true)))
            throw new IllegalStateException("Odmowa budowania: "+countries.details());
        var wg=WorldGuardOverlapSurvey.inspect(w,x1,z1,x2,z2);
        if (!TestBuildGate.allowRegions(wg.verified(),wg.overlaps(),
            plugin.getConfig().getBoolean("safety.require-worldguard-verification-for-test-build",true)))
            throw new IllegalStateException("Odmowa budowania: "+wg.details());
        Job job=new Job(id);job.eventId=scheduleId;job.scheduledDay=day;job.worldName=w.getName();job.worldUid=w.getUID();job.originX=ox;job.originZ=oz;job.floorY=y;job.entranceX=cx;job.entranceZ=cz;job.entranceY=top+1;
        job.minX=ox+bp.bounds().minX()-3;job.maxX=ox+bp.bounds().maxX()+3;
        job.mobLayout=layout;job.mobSeed=seed;
        job.minY=y;job.maxY=top+1;job.minZ=oz+bp.bounds().minZ()-3;job.maxZ=oz+bp.bounds().maxZ()+3;
        if (top-y<12 || top-y>96) throw new IllegalStateException("Nieprawidlowa glebokosc wejscia");
        // Pelne sprawdzenie chunkow, nie sampling co 16 od nieosiowej granicy.
        for(int chunkX=Math.floorDiv(job.minX,16);chunkX<=Math.floorDiv(job.maxX,16);chunkX++)
            for(int chunkZ=Math.floorDiv(job.minZ,16);chunkZ<=Math.floorDiv(job.maxZ,16);chunkZ++)
                if(!w.isChunkLoaded(chunkX,chunkZ))throw new IllegalStateException("Niewczytane chunki; test przerwany");
        // ETAP 23: shared, full 7x7 read-only survey of EVERY entrance.
        // It does not load missing chunks; builder and admin preview use identical rules.
        var candidates=DungeonMultiEntrancePlan.locations(layout,ox,oz,
            plugin.getConfig().getInt("safety.entrance-radius-from-zero",3500));
        var report=LiveEntranceSurvey.inspect(w,candidates,y,
            plugin.getConfig().getInt("safety.entrance-radius-from-zero",3500));
        if(!report.accepted())
            throw new IllegalStateException("Niezdany audyt wejsc: "+String.join("; ",report.errors()));
        List<DungeonMultiEntrancePlan.Site> shaftSites=new ArrayList<>();
        for(var entry:report.entries()) {
            shaftSites.add(new DungeonMultiEntrancePlan.Site(entry.roomIndex(),entry.x(),entry.z(),entry.y()));
            job.entrances.add(new EntryPoint(entry.x(),entry.y()+1,entry.z()));
            job.maxY=Math.max(job.maxY,entry.y()+1);
        }
        DungeonMultiEntrancePlan.validateSites(shaftSites,requestedEntrances,y,
            w.getMinHeight(),w.getMaxHeight(),
            plugin.getConfig().getInt("safety.entrance-radius-from-zero",3500));
        // Deduplikacja wszystkich wspolrzednych: kazdy blok ma jedna kopie przed zmiana.
        Map<BlockKey,Change> cells=new LinkedHashMap<>();
        for(var entry:bp.cells().entrySet()) {
            DungeonBlueprint.Pos pos=entry.getKey();int x=ox+pos.x(),by=y+pos.y(),z=oz+pos.z();
            String data=switch(entry.getValue()) {
                case AIR -> "minecraft:air";
                case FLOOR -> "minecraft:stone_bricks";
                case WALL -> "minecraft:deepslate_bricks";
            };
            cells.put(new BlockKey(x,by,z),new Change(x,by,z,data));
        }
        // ALL shafts go through the SAME deduplicated change map and immutable snapshot.
        // Shaft cells deliberately override the centre of their connected room.
        for(var site:shaftSites) {
            for(EntranceShaftPlan.Cell c:EntranceShaftPlan.design(site.x(),site.z(),y,site.surfaceY()).cells()) {
                String data=switch(c.element()) {
                    case WALL, LADDER_BACKING -> "minecraft:stone_bricks";
                    case OPEN, SURFACE_OPENING -> "minecraft:air";
                    case LADDER -> "minecraft:ladder[facing=east]";
                };
                cells.put(new BlockKey(c.x(),c.y(),c.z()),new Change(c.x(),c.y(),c.z(),data));
            }
        }
        // Test-only loot chests: they are part of the change set, so their original
        // blocks are snapshotted before any chest is placed. This is NOT SMP gameplay.
        if (plugin.getConfig().getBoolean("loot.enable-test-chests",false)) {
            for(TestChestLayout.Spot spot:TestChestLayout.plan(layout,bp,seed)) {
                BlockKey key=new BlockKey(ox+spot.x(),y+spot.y(),oz+spot.z());
                // A chest must replace an actual air cell; no shaft/room zero intersections.
                Change previous=cells.get(key);
                if(previous==null || !previous.blockData.equals("minecraft:air"))continue;
                cells.put(key,new Change(key.x,key.y,key.z,"minecraft:chest"));
                job.generatedChests.add(key);
            }
        }
        // Dodatkowy opt-in: testowe pulapki (pajeczyna/magma).
        // Kazda zmiana wchodzi do TEGO SAMEGO snapshotu co pokoje, wejscie i skrzynie.
        if (plugin.getConfig().getBoolean("traps.enable-test-traps",false)) {
            double fraction=plugin.getConfig().getDouble("traps.room-fraction",0.2);
            for(TestTrapLayout.Spot spot:TestTrapLayout.plan(layout,bp,seed,fraction)) {
                BlockKey key=new BlockKey(ox+spot.x(),y+spot.y(),oz+spot.z());
                Change existing=cells.get(key);
                String needed=spot.kind()==TestTrapLayout.Kind.COBWEB?"minecraft:air":"minecraft:stone_bricks";
                if(existing==null||!needed.equals(existing.blockData)||job.generatedChests.contains(key))continue;
                cells.put(key,new Change(key.x,key.y,key.z,spot.kind()==TestTrapLayout.Kind.COBWEB
                    ? "minecraft:cobweb":"minecraft:magma_block"));
            }
        }
        // Nie obejmujemy starych budowli ani blokow NBT. Nie ma odstępstw dla wejsciowych blokow.
        for(Change c:cells.values()) {
            Block block=w.getBlockAt(c.x,c.y,c.z);
            if(!safeNatural(block.getType()) && !safeEntranceSurface(block.getType(),nearEntranceSurface(job,c.x,c.y,c.z)) && !allowContainer(block))
                throw new IllegalStateException("Blok nienaturalny lub wymagajacy danych NBT: "+block.getType()+" @"+c.x+","+c.y+","+c.z);
        }
        job.changes.addAll(cells.values());
        try {
            SnapshotCoverage.Seal seal=SnapshotCoverage.seal(job.changes.stream()
                .map(c->new SnapshotCoverage.Pos(c.x,c.y,c.z)).toList());
            job.plannedCount=seal.count();job.plannedCoordinatesSha=seal.sha256();
        } catch(IOException ex) { throw new IllegalStateException("Nie mozna zapieczetowac planu zmian",ex); }
        int max=plugin.getConfig().getInt("safety.max-test-blocks",100000);
        if(job.changes.size()>max)throw new IllegalStateException("Plan z wejsciem ma "+job.changes.size()+" blokow (limit: "+max+")");
        long reserve=Math.max(0L,plugin.getConfig().getLong("safety.minimum-free-recovery-bytes",RecoveryDiskBudget.MIN_RESERVE));
        if(!RecoveryDiskBudget.hasSpace(dir.getUsableSpace(),job.changes.size(),reserve))
            throw new IllegalStateException("Zbyt malo wolnego miejsca na dysku dla bezpiecznej kopii: wymaga "
                +RecoveryDiskBudget.requiredBytes(job.changes.size(),reserve)+" bajtow; dostepne "+dir.getUsableSpace());
        job.scheduledEndEpochMillis=EventTimeline.window(schedule,day,plugin.timezone()).end().toInstant().toEpochMilli();
        job.phase=Phase.CAPTURING;
        try { saveManifest(job); }
        catch(IOException ex){ throw new IllegalStateException("Nie mozna zapisac manifestu PRZED generowaniem",ex); }
        jobs.put(id,job);
        plugin.getLogger().warning("TEST "+id+": rozpoczynam zapis kopii oryginalnych blokow; brak modyfikacji do konca zapisu.");
    }
    /** Opt-in only for *simple* containers in the dedicated TEST world. */
    private boolean allowContainer(Block block) {
        return plugin.getConfig().getBoolean("safety.allow-test-container-overwrite",false)
            && BlockEntityBackup.canBackup(block);
    }
    private record BlockKey(int x,int y,int z) {}
    private static boolean nearEntranceSurface(Job job,int x,int y,int z) {
        for(EntryPoint entry:job.entrances)
            if(Math.abs(x-entry.x())<=3 && Math.abs(z-entry.z())<=3 && y>=entry.y()-5)
                return true;
        return false;
    }
    private static boolean safeEntranceSurface(Material m,boolean atSurface) {
        if (!atSurface) return false;
        return switch(m) {
            case GRASS_BLOCK, PODZOL, MYCELIUM, MOSS_BLOCK, SAND, RED_SAND,
                 TERRACOTTA, WHITE_TERRACOTTA, ORANGE_TERRACOTTA, RED_TERRACOTTA,
                 YELLOW_TERRACOTTA, BROWN_TERRACOTTA, LIGHT_GRAY_TERRACOTTA,
                 SHORT_GRASS, TALL_GRASS, FERN, LARGE_FERN, DEAD_BUSH, SNOW -> true;
            default -> false;
        };
    }
    private static boolean safeNatural(Material m){return switch(m){
        case STONE,DEEPSLATE,TUFF,ANDESITE,DIORITE,GRANITE,DIRT,COARSE_DIRT,ROOTED_DIRT,
             GRAVEL,COBBLESTONE,CALCITE,DRIPSTONE_BLOCK,BLACKSTONE,BASALT,
             AIR,CAVE_AIR,VOID_AIR -> true;
        default -> false;
    };}
    private void tick(){
        for(Job job:new ArrayList<>(jobs.values())){
            if(job.phase==Phase.RESTORED||job.phase==Phase.FAILED||job.phase==Phase.ACTIVE)continue;
            World w=Bukkit.getWorld(job.worldUid);
            if(w==null){
                if(worldsMissingWarned.add(job.id))plugin.getLogger().severe("Recovery "+job.id+": swiat niedostepny: "+job.worldName+". Czekam na wczytanie swiata; NIE usuwaj recoveries/!");
                continue;
            }
            worldsMissingWarned.remove(job.id);
            try{
                int perTick=Math.max(1,Math.min(2000,plugin.getConfig().getInt("safety.blocks-per-tick",350)));
                // Fail closed: never make a Bukkit getBlockAt call in unloaded chunks while
                // building/restoring. Pause and wait for the admin to load affected chunks.
                if (job.phase==Phase.CAPTURING || job.phase==Phase.BUILDING || job.phase==Phase.RESTORING || job.phase==Phase.RESTORING_TILES || job.phase==Phase.VERIFYING) {
                    boolean restoring=job.phase==Phase.RESTORING || job.phase==Phase.RESTORING_TILES || job.phase==Phase.VERIFYING;
                    int size=restoring?job.originals.size():job.changes.size();
                    int stop=Math.min(size,job.cursor+perTick);
                    boolean missing=false;
                    for (int i=job.cursor;i<stop;i++) {
                        int x=restoring?job.originals.get(i).x:job.changes.get(i).x;
                        int z=restoring?job.originals.get(i).z:job.changes.get(i).z;
                        if (!w.isChunkLoaded(Math.floorDiv(x,16),Math.floorDiv(z,16))) {missing=true;break;}
                    }
                    if (missing) {
                        if(chunksMissingWarned.add(job.id)) plugin.getLogger().warning(
                            "Recovery "+job.id+": oczekiwanie na wczytanie chunkow przez administratora; nie laduje ich samoczynnie");
                        continue;
                    }
                    chunksMissingWarned.remove(job.id);
                }
                // Start the wall-clock budget AFTER chunk checks. If the checks themselves
                // are slow, they cannot starve all future capture/restore progress.
                long deadline=RecoveryWorkBudget.deadline(System.nanoTime(),
                    plugin.getConfig().getInt("safety.max-mutation-millis-per-tick",5));
                if(job.phase==Phase.CAPTURING){
                    int stop=Math.min(job.changes.size(),job.cursor+perTick);
                    for(;job.cursor<stop && RecoveryWorkBudget.hasTime(deadline,System.nanoTime());job.cursor++){
                        Change c=job.changes.get(job.cursor);
                        Block b=w.getBlockAt(c.x,c.y,c.z);
                        if(!safeNatural(b.getType()) && !safeEntranceSurface(b.getType(),nearEntranceSurface(job,c.x,c.y,c.z)) && !allowContainer(b))
                            throw new IllegalStateException("Teren zmienil sie podczas tworzenia kopii; brak zmian pluginu");
                        job.originals.add(new Original(c.x,c.y,c.z,b.getBlockData().getAsString(),BlockEntityBackup.capture(b)));
                    }
                    if(job.cursor==job.changes.size()){
                        writeSnapshot(job); // Atomowy zapis CALOSCI kopii PRZED pierwsza modyfikacja.
                        if(TestDeadlinePolicy.expired(java.time.Instant.now(),job.scheduledEndEpochMillis)) {
                            // Never create an expired dungeon. Capture did not alter any block.
                            job.phase=Phase.RESTORED;job.cursor=0;saveManifest(job);
                            plugin.getLogger().warning("Test "+job.id+": minela godzina konca podczas kopii; nie zbudowano zadnego bloku.");
                        } else {
                            job.phase=Phase.BUILDING;job.cursor=0;saveManifest(job);
                            evacuate(job);
                            plugin.getLogger().warning("Snapshot zapisany, zaczynam kontrolowane budowanie TESTU: "+job.id);
                        }
                    }
                } else if(job.phase==Phase.BUILDING){
                    evacuate(job);
                    int stop=Math.min(job.changes.size(),job.cursor+perTick);
                    for(;job.cursor<stop && RecoveryWorkBudget.hasTime(deadline,System.nanoTime());job.cursor++){
                        Change c=job.changes.get(job.cursor);
                        w.getBlockAt(c.x,c.y,c.z).setBlockData(Bukkit.createBlockData(c.blockData),false);
                    }
                    if(job.cursor==job.changes.size()){
                        if(TestDeadlinePolicy.expired(java.time.Instant.now(),job.scheduledEndEpochMillis)) {
                            // Deadline crossed during building: skip chests/mobs and roll back immediately.
                            job.originals=readSnapshot(job);
                            job.phase=Phase.RESTORING;job.cursor=0;saveManifest(job);
                            plugin.getLogger().warning("Test "+job.id+": minela godzina konca podczas budowania. Rozpoczynam rollback.");
                        } else {
                            seedTestChests(w,job); // May throw => BUILDING rolls back automatically.
                            job.phase=Phase.PREPARED;saveManifest(job);
                            plugin.getLogger().warning("Testowy dungeon "+job.id+" PRZYGOTOWANY; moby uruchomi sie dopiero podczas otwarcia. Awaryjnie: /dungeon admin anuluj "+job.id+" potwierdz");
                        }
                    }
                } else if(job.phase==Phase.RESTORING){
                    evacuate(job);
                    if(job.cursor==0)plugin.testMobs().clearGenerated(w,job.id,job.mobUuids,
                        job.minX,job.maxX,job.minY,job.maxY,job.minZ,job.maxZ);
                    int stop=Math.min(job.originals.size(),job.cursor+perTick);
                    for(;job.cursor<stop && RecoveryWorkBudget.hasTime(deadline,System.nanoTime());job.cursor++){
                        Original orig=job.originals.get(job.cursor);
                        BlockData data=Bukkit.createBlockData(orig.data);
                        w.getBlockAt(orig.x,orig.y,orig.z).setBlockData(data,false);
                    }
                    if(job.cursor==job.originals.size()){
                        // Recover block material and BlockData FIRST, then tile inventories.
                        // Both steps are repeatable after a crash; the original snapshot is never deleted.
                        job.phase=Phase.RESTORING_TILES;job.cursor=0;saveManifest(job);
                    }
                } else if(job.phase==Phase.RESTORING_TILES){
                    evacuate(job);
                    int stop=Math.min(job.originals.size(),job.cursor+perTick);
                    for(;job.cursor<stop && RecoveryWorkBudget.hasTime(deadline,System.nanoTime());job.cursor++){
                        Original orig=job.originals.get(job.cursor);
                        if(!orig.tileYaml().isEmpty()){
                            Block b=w.getBlockAt(orig.x,orig.y,orig.z);
                            BlockEntityBackup.restore(b,orig.data(),orig.tileYaml());
                        }
                    }
                    if(job.cursor==job.originals.size()){
                        plugin.testMobs().clearGenerated(w,job.id,job.mobUuids,
                            job.minX,job.maxX,job.minY,job.maxY,job.minZ,job.maxZ);
                        job.phase=Phase.VERIFYING;job.cursor=0;job.verificationMismatches=0;saveManifest(job);
                        plugin.getLogger().warning("Recovery "+job.id+": zapisano bloki i kontenery; zaczynam weryfikacje po odtworzeniu.");
                    }
                } else if(job.phase==Phase.VERIFYING) {
                    // Do not mark RESTORED until the *actual* block state is checked.
                    // Verification is restart-safe: after a crash we replay restore from
                    // the immutable snapshot rather than trusting partial progress.
                    evacuate(job);
                    int stop=Math.min(job.originals.size(),job.cursor+perTick);
                    for(;job.cursor<stop && RecoveryWorkBudget.hasTime(deadline,System.nanoTime());job.cursor++) {
                        Original orig=job.originals.get(job.cursor);
                        Block b=w.getBlockAt(orig.x,orig.y,orig.z);
                        boolean matches=orig.data.equals(b.getBlockData().getAsString());
                        if(matches && !orig.tileYaml().isEmpty())
                            matches=BlockEntityBackup.matchesOriginal(b,orig.data,orig.tileYaml());
                        if(!matches)job.verificationMismatches++;
                    }
                    if(job.cursor==job.originals.size()) {
                        int maxRetries=Math.max(0,Math.min(5,plugin.getConfig().getInt("safety.max-restore-verification-retries",2)));
                        var result=RestoreVerificationPolicy.decide(job.originals.size(),job.cursor,
                            job.verificationMismatches,job.verificationAttempts,maxRetries);
                        if(result==RestoreVerificationPolicy.Action.FINISH) {
                            job.restorationVerified=true;
                            job.phase=Phase.RESTORED;saveManifest(job);
                            plugin.getLogger().warning("Recovery "+job.id+": zweryfikowano "+job.cursor+" blokow; status RESTORED (dane z kopii zachowane).");
                        } else if(result==RestoreVerificationPolicy.Action.RETRY_FROM_SNAPSHOT) {
                            int bad=job.verificationMismatches;
                            job.verificationAttempts++;
                            // Re-read and validate the immutable backup on every retry.
                            job.originals=readSnapshot(job);job.cursor=0;
                            job.verificationMismatches=0;job.phase=Phase.RESTORING;saveManifest(job);
                            plugin.getLogger().severe("Recovery "+job.id+": "+bad+" niezgodnych blokow; pelne odtwarzanie od poczatku (proba "+job.verificationAttempts+").");
                        } else {
                            job.phase=Phase.FAILED;saveManifest(job);
                            plugin.getLogger().severe("ALARM: recovery "+job.id+" ma "+job.verificationMismatches
                                +" NIEODTWORZONYCH blokow po "+job.verificationAttempts
                                +" ponowieniach. Zachowaj recoveries/, zaladuj swiat i uzyj /dungeon admin audyt "+job.id);
                        }
                    }
                }
            }catch(Exception ex){
                plugin.getLogger().log(Level.SEVERE,"Przerwano operacje "+job.id+" / "+job.phase+"; dane pozostaja na dysku",ex);
                if(job.phase==Phase.CAPTURING){job.phase=Phase.FAILED;}
                else if(job.phase==Phase.BUILDING){ // Snapshot juz zapisany; odzyskiwanie automatyczne.
                    try {job.originals=readSnapshot(job);job.phase=Phase.RESTORING;job.cursor=0;}catch(Exception problem){job.phase=Phase.FAILED;}
                } else job.phase=Phase.FAILED;
                persistSafely(job);
            }
        }
    }
    /** Fill test chests only after all blocks have been built, before ACTIVE is persisted. */
    private void seedTestChests(World w,Job job) {
        if(job.generatedChests.isEmpty())return;
        Random random=new Random(Objects.hash(job.id,job.worldUid,job.originX,job.originZ));
        int max=Math.max(1,Math.min(27,plugin.getConfig().getInt("loot.test-chest-max-items",4)));
        for(BlockKey key:job.generatedChests) {
            BlockState state=w.getBlockAt(key.x,key.y,key.z).getState();
            if(!(state instanceof Container container))throw new IllegalStateException("Nie mozna wypelnic testowej skrzyni "+key);
            Inventory inventory=container.getSnapshotInventory();
            inventory.clear();
            for(org.bukkit.inventory.ItemStack item:plugin.lootStore().rollChest(random,max)) {
                Map<Integer,org.bukkit.inventory.ItemStack> remaining=inventory.addItem(item);
                if(!remaining.isEmpty())plugin.getLogger().warning("Nie zmiescil sie przedmiot w testowej skrzyni "+key);
            }
            if(!container.update(true,false))throw new IllegalStateException("Nie udalo sie zapisac testowej skrzyni "+key);
        }
        plugin.getLogger().info("Test "+job.id+": napelniono "+job.generatedChests.size()+" skrzyn lootem (prywatne bloki testowe).");
    }
    /** An active generated loot chest can be looted, but never broken or fed by hoppers. */
    private boolean generatedLootInventory(Inventory inventory) {
        if(inventory==null || !(inventory.getHolder() instanceof BlockState state))return false;
        Location at=state.getLocation();
        BlockKey pos=new BlockKey(at.getBlockX(),at.getBlockY(),at.getBlockZ());
        return jobs.values().stream().anyMatch(j->j.phase==Phase.ACTIVE && j.worldUid.equals(at.getWorld().getUID())
            && j.generatedChests.contains(pos));
    }
    /** Zwraca false jesli nie ma fizycznej kopii; zaplanowane wydarzenie usuwa osobno ScheduleStore. */
    public boolean requestRestore(String id){
        Job job=jobs.get(id);if(job==null)return false;
        if(job.phase==Phase.RESTORED)return true;
        if(job.phase==Phase.CAPTURING){job.phase=Phase.FAILED;persistSafely(job);return true;} // Nic nie zmieniono.
        if(job.phase==Phase.FAILED && !snapshotFile(job).exists())
            throw new IllegalStateException("Brak poprawnej kopii: NIE mozna obiecac przywrocenia; interwencja administratora");
        try{
            List<Original> restored=readSnapshot(job); // Weryfikacja integralnosci przed operacja.
            evacuate(job); // Jesli nie da sie przeniesc graczy, NIE zapisuj falszywego stanu.
            job.originals=restored;job.phase=Phase.RESTORING;job.cursor=0;job.verificationMismatches=0;job.restorationVerified=false;
            saveManifest(job);return true;
        }catch(Exception ex){throw new IllegalStateException("Nie mozna odczytac kopii, NIE zmieniono stanu: "+ex.getMessage(),ex);}
    }
    /** Never alter blocks while a player cannot be evacuated to a confirmed safe loaded location. */
    private void evacuate(Job job) {
        World w=Bukkit.getWorld(job.worldUid);
        if(w==null)throw new IllegalStateException("Swiat nie jest zaladowany - brak bezpiecznej ewakuacji");
        List<Player> people=new ArrayList<>();
        for(Player player:w.getPlayers())if(job.contains(player.getLocation(),5))people.add(player);
        if(people.isEmpty())return;
        Location destination=findExit(w,job);
        if(destination==null)throw new IllegalStateException("Nie znaleziono bezpiecznej, wczytanej lokalizacji ewakuacji; operacja wstrzymana");
        for(Player player:people) {
            if(!player.teleport(destination))throw new IllegalStateException("Teleportacja nieudana dla "+player.getName()+"; operacja wstrzymana");
            player.sendMessage(plugin.prefix()+"§eDungeon jest zamykany; przeniesiono Cie na powierzchnie.");
        }
    }
    private Location findExit(World w,Job job) {
        Location spawn=w.getSpawnLocation();
        Location fromSpawn=safeExitAt(w,job,spawn.getBlockX(),spawn.getBlockZ());
        if(fromSpawn!=null)return fromSpawn;
        int[][] points={
            {job.maxX+64,job.maxZ+64},{job.minX-64,job.minZ-64},
            {job.minX-64,job.maxZ+64},{job.maxX+64,job.minZ-64},
            {job.minX-64,job.originZ},{job.maxX+64,job.originZ},
            {job.originX,job.minZ-64},{job.originX,job.maxZ+64}
        };
        for(int[] p:points) {
            Location loc=safeExitAt(w,job,p[0],p[1]);
            if(loc!=null)return loc;
        }
        return null;
    }
    private Location safeExitAt(World w,Job job,int x,int z) {
        if(Math.abs((long)x)>3900||Math.abs((long)z)>3900)return null;
        if(!w.isChunkLoaded(Math.floorDiv(x,16),Math.floorDiv(z,16)))return null;
        int top=w.getHighestBlockYAt(x,z);
        if(top+2>=w.getMaxHeight()||top<=w.getMinHeight())return null;
        Block ground=w.getBlockAt(x,top,z);
        if(!ground.getType().isSolid() || ground.getType()==Material.MAGMA_BLOCK || ground.getType()==Material.CACTUS)return null;
        if(!w.getBlockAt(x,top+1,z).isPassable()||!w.getBlockAt(x,top+2,z).isPassable())return null;
        Location loc=new Location(w,x+0.5,top+1,z+0.5);
        int protectiveMargin=plugin.getConfig().getInt("safety.closed-protection-margin",50);
        if(!TestBuildGate.exitOutsideProtectedFootprint(x,z,job.minX,job.maxX,job.minZ,job.maxZ,protectiveMargin))return null;
        return loc;
    }
    private void writeSnapshot(Job job)throws IOException {
        List<RecoverySnapshotCodec.Cell> cells=new ArrayList<>(job.originals.size());
        for(Original o:job.originals)cells.add(new RecoverySnapshotCodec.Cell(o.x(),o.y(),o.z(),o.data(),o.tileYaml()));
        RecoverySnapshotCodec.write(snapshotFile(job).toPath(),job.worldUid,cells);
        // Verify the complete durable snapshot, including the checksum, before touching a single block.
        if(readSnapshot(job).size()!=job.changes.size())throw new IOException("Niezgodna liczba zapisanych blokow");
    }
    private List<Original> readSnapshot(Job job)throws IOException {
        RecoverySnapshotCodec.Snapshot snapshot=RecoverySnapshotCodec.read(snapshotFile(job).toPath(),job.worldUid);
        verifyCoverage(job,snapshot);
        List<Original> result=new ArrayList<>(snapshot.cells().size());
        for(RecoverySnapshotCodec.Cell c:snapshot.cells()) {
            if(c.x()<job.minX||c.x()>job.maxX||c.y()<job.minY||c.y()>job.maxY
                    ||c.z()<job.minZ||c.z()>job.maxZ)
                throw new IOException("Kopia zawiera blok poza obszarem testu");
            BlockEntityBackup.validate(c.blockData(),c.tileYaml());
            result.add(new Original(c.x(),c.y(),c.z(),c.blockData(),c.tileYaml()));
        }
        if(!snapshot.hasTileSupport())plugin.getLogger().warning("Stary snapshot v"+snapshot.version()+" (bez danych kontenerow): "+job.id+". Zachowaj backup swiata!");
        return result;
    }
    /** A sealed manifest rejects partial or different-coordinate snapshots even if their
     * embedded checksum is internally valid. Legacy manifests remain readable for rescue. */
    private void verifyCoverage(Job job,RecoverySnapshotCodec.Snapshot snapshot)throws IOException {
        if(job.plannedCount < 0 && job.plannedCoordinatesSha.isBlank()) {
            plugin.getLogger().warning("STARY manifest bez pieczeci planu: "+job.id+"; dodatkowa kontrola pokrycia jest niedostepna");
            return;
        }
        SnapshotCoverage.verify(new SnapshotCoverage.Seal(job.plannedCount,job.plannedCoordinatesSha),
            snapshot.cells().stream().map(c->new SnapshotCoverage.Pos(c.x(),c.y(),c.z())).toList());
    }
    private File snapshotFile(Job job){return new File(dir,job.id+".snapshot.gz");}
    private File manifest(Job job){return new File(dir,job.id+".yml");}
    private void saveManifest(Job job)throws IOException{
        YamlConfiguration y=new YamlConfiguration();y.set("id",job.id);y.set("world",job.worldName);y.set("world-uuid",job.worldUid.toString());
        y.set("event-id",job.eventId);y.set("phase",job.phase.name());if(job.scheduledDay!=null)y.set("scheduled-day",job.scheduledDay.toString());y.set("origin-x",job.originX);y.set("origin-z",job.originZ);y.set("floor-y",job.floorY);
        y.set("min-x",job.minX);y.set("max-x",job.maxX);y.set("min-y",job.minY);y.set("max-y",job.maxY);
        y.set("min-z",job.minZ);y.set("max-z",job.maxZ);y.set("entrance-x",job.entranceX);y.set("entrance-y",job.entranceY);y.set("entrance-z",job.entranceZ);y.set("progress",job.cursor);
        y.set("entrances",job.entrances.stream().map(e->e.x()+","+e.y()+","+e.z()).toList());
        y.set("scheduled-end-epoch-millis",job.scheduledEndEpochMillis);
        y.set("restoration-verified",job.restorationVerified);
        y.set("verification-attempts",job.verificationAttempts);
        y.set("verification-mismatches",job.verificationMismatches);
        if(job.plannedCount>=0){y.set("planned-cell-count",job.plannedCount);y.set("planned-coordinates-sha256",job.plannedCoordinatesSha);}
        y.set("mob-uuids",job.mobUuids.stream().map(UUID::toString).toList());
        AtomicYaml.write(y,manifest(job));
    }
    private void persistSafely(Job j){try{saveManifest(j);}catch(IOException ex){plugin.getLogger().log(Level.SEVERE,"Nie mozna zapisac stanu odzyskiwania "+j.id,ex);}}
    private void loadManifests(){
        File[] list=dir.listFiles((d,name)->name.endsWith(".yml"));if(list==null)return;
        for(File file:list)try{
            YamlConfiguration y=YamlConfiguration.loadConfiguration(file);String id=Objects.requireNonNull(y.getString("id"));
            if(!RecoveryDirectoryAudit.validId(id))throw new IOException("Nieprawidlowe ID");
            if(!file.getName().equals(id+".yml"))throw new IOException("ID manifestu nie zgadza sie z nazwa pliku: "+file.getName());
            if(jobs.containsKey(id))throw new IOException("Zdublowany identyfikator recovery: "+id);
            Job job=new Job(id);job.eventId=y.getString("event-id",id);job.worldName=y.getString("world","");job.worldUid=UUID.fromString(Objects.requireNonNull(y.getString("world-uuid")));
            job.originX=y.getInt("origin-x");job.originZ=y.getInt("origin-z");job.floorY=y.getInt("floor-y");
            job.minX=y.getInt("min-x");job.maxX=y.getInt("max-x");job.minY=y.getInt("min-y");job.maxY=y.getInt("max-y");
            job.minZ=y.getInt("min-z");job.maxZ=y.getInt("max-z");
            job.entranceX=y.getInt("entrance-x",job.originX);job.entranceY=y.getInt("entrance-y",job.maxY);job.entranceZ=y.getInt("entrance-z",job.originZ);
            List<String> savedEntrances=y.getStringList("entrances");
            if(savedEntrances.size()>3)throw new IOException("Zbyt wiele wejsc w manifeście: "+id);
            for(String raw:savedEntrances) {
                String[] c=raw.split(",",-1);
                if(c.length!=3)throw new IOException("Uszkodzony wpis wejscia: "+id);
                EntryPoint entry=new EntryPoint(Integer.parseInt(c[0]),Integer.parseInt(c[1]),Integer.parseInt(c[2]));
                if(entry.x()<job.minX||entry.x()>job.maxX||entry.y()<job.minY||entry.y()>job.maxY
                    ||entry.z()<job.minZ||entry.z()>job.maxZ)
                    throw new IOException("Wejscie poza kopia terenu: "+id);
                if(job.entrances.contains(entry))throw new IOException("Powtorzone wejscie w manifeście: "+id);
                job.entrances.add(entry);
            }
            if(job.entrances.isEmpty())job.entrances.add(new EntryPoint(job.entranceX,job.entranceY,job.entranceZ)); // Legacy v1-v20.
            for(String uuid:y.getStringList("mob-uuids"))job.mobUuids.add(UUID.fromString(uuid));
            job.phase=Phase.valueOf(Objects.requireNonNull(y.getString("phase")));
            if(y.isString("scheduled-day"))job.scheduledDay=LocalDate.parse(y.getString("scheduled-day"));
            job.scheduledEndEpochMillis=y.getLong("scheduled-end-epoch-millis",0L);
            job.restorationVerified=y.getBoolean("restoration-verified",false);
            job.verificationAttempts=y.getInt("verification-attempts",0);
            job.verificationMismatches=y.getInt("verification-mismatches",0);
            if(job.verificationAttempts<0||job.verificationAttempts>100||job.verificationMismatches<0)
                throw new IOException("Nieprawidlowe liczniki weryfikacji w manifeście: "+id);
            if(y.contains("planned-cell-count")||y.contains("planned-coordinates-sha256")) {
                job.plannedCount=y.getInt("planned-cell-count",-1);
                job.plannedCoordinatesSha=y.getString("planned-coordinates-sha256","");
                if(job.plannedCount<=0 || !job.plannedCoordinatesSha.matches("[0-9a-f]{64}"))
                    throw new IOException("Uszkodzona pieczec planu zmian w manifeście: "+id);
            }
            // Explicit safety policy: a FAILED restore may have exhausted its attempts;
            // do not silently restart it. A legacy RESTORED without proof is NOT trusted.
            var action=RecoveryRestartPolicy.decide(job.phase.name(),snapshotFile(job).isFile(),job.restorationVerified);
            switch(action) {
                case NO_WORLD_CHANGES -> {
                    job.phase=Phase.FAILED;persistSafely(job);
                    plugin.getLogger().warning("Przerwany snapshot bez modyfikacji blokow: "+job.id);
                }
                case VERIFIED_COMPLETE -> {
                    // Keep the immutable snapshot and verified manifest for admin review.
                }
                case WAIT_FOR_ADMIN -> plugin.getLogger().severe(
                    "Recovery "+job.id+" jest w stanie FAILED: wymagana inspekcja i /dungeon admin wznow "+job.id+" potwierdz");
                case RESUME_RESTORE -> {
                    job.originals=readSnapshot(job);job.phase=Phase.RESTORING;job.cursor=0;
                    job.restorationVerified=false;saveManifest(job);
                    plugin.getLogger().warning("Po restarcie wznawiam ODTWARZANIE terenu dla "+job.id);
                }
                case INVALID -> throw new IOException("Niespojny manifest/snapshot: "+job.id
                    +" / "+job.phase+". Wymagana reczna interwencja.");
            }
            jobs.put(id,job);
        }catch(Exception ex){recoveryFilesCorrupt=true;plugin.getLogger().log(Level.SEVERE,"Uszkodzony manifest "+file.getName()+". TESTOWE GENEROWANIE ZABLOKOWANE; NIE WOLNO recznie usuwac plikow recoveries",ex);}
    }
    // Temporary test-only restrictions: until the snapshot covers EVERY possible user edit,
    // all active test constructions remain read-only to players. Production gameplay NOT enabled.
    @EventHandler public void breakBlock(BlockBreakEvent e){if(isProtected(e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler public void placeBlock(BlockPlaceEvent e){if(isProtected(e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler public void flow(BlockFromToEvent e){
        if(isProtected(e.getBlock().getLocation())||isProtected(e.getToBlock().getLocation()))e.setCancelled(true);
    }
    @EventHandler public void burn(BlockBurnEvent e){if(isProtected(e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler public void fade(BlockFadeEvent e){if(isProtected(e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler public void pistonExtend(BlockPistonExtendEvent e){
        if(isProtected(e.getBlock().getLocation())||e.getBlocks().stream().anyMatch(b->isProtected(b.getLocation())||isProtected(b.getRelative(e.getDirection()).getLocation())))e.setCancelled(true);
    }
    @EventHandler public void pistonRetract(BlockPistonRetractEvent e){
        if(isProtected(e.getBlock().getLocation())||e.getBlocks().stream().anyMatch(b->isProtected(b.getLocation())||isProtected(b.getRelative(e.getDirection()).getLocation())))e.setCancelled(true);
    }
    @EventHandler public void entityExplode(EntityExplodeEvent e){
        if(e.blockList().stream().anyMatch(b->isProtected(b.getLocation())))e.setCancelled(true);
    }
    @EventHandler public void blockExplode(BlockExplodeEvent e){
        if(isProtected(e.getBlock().getLocation())||e.blockList().stream().anyMatch(b->isProtected(b.getLocation())))e.setCancelled(true);
    }
    // Etap 7: blokowanie innych zrodel zmian w dedykowanym swiecie TESTOWYM.
    // Nie zapewnia to stuprocentowej ochrony przed zewnetrznymi pluginami / edytorami.
    @EventHandler(ignoreCancelled=true) public void bucketEmpty(PlayerBucketEmptyEvent e){
        if(isProtected(e.getBlockClicked().getRelative(e.getBlockFace()).getLocation()))e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void bucketFill(PlayerBucketFillEvent e){
        if(isProtected(e.getBlockClicked().getLocation()))e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void interact(PlayerInteractEvent e){
        if(e.getClickedBlock()==null || !isProtected(e.getClickedBlock().getLocation()))return;
        // Permit right-click ONLY on a generated, active, opt-in test loot chest.
        // Cancelling PlayerInteractEvent otherwise prevents InventoryOpenEvent entirely.
        if(e.getAction()==org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK && isGeneratedTestChest(e.getClickedBlock()))return;
        e.setCancelled(true);
    }
    private boolean isGeneratedTestChest(Block block) {
        Location at=block.getLocation();
        BlockKey position=new BlockKey(at.getBlockX(),at.getBlockY(),at.getBlockZ());
        return jobs.values().stream().anyMatch(j->j.phase==Phase.ACTIVE && j.worldUid.equals(block.getWorld().getUID())
            && j.generatedChests.contains(position));
    }
    @EventHandler(ignoreCancelled=true) public void dispenser(BlockDispenseEvent e){
        if(isProtected(e.getBlock().getLocation()))e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void grow(BlockGrowEvent e){
        if(isProtected(e.getBlock().getLocation())||isProtected(e.getNewState().getLocation()))e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void spread(BlockSpreadEvent e){
        if(isProtected(e.getBlock().getLocation())||isProtected(e.getSource().getLocation()))e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void form(BlockFormEvent e){if(isProtected(e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void fertilize(BlockFertilizeEvent e){
        if(isProtected(e.getBlock().getLocation())||e.getBlocks().stream().anyMatch(b->isProtected(b.getLocation())))e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void growStructure(StructureGrowEvent e){
        if(isProtected(e.getLocation())||e.getBlocks().stream().anyMatch(b->isProtected(b.getLocation())))e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void ignite(BlockIgniteEvent e){if(isProtected(e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void leaves(LeavesDecayEvent e){if(isProtected(e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void sponge(SpongeAbsorbEvent e){
        if(isProtected(e.getBlock().getLocation())||e.getBlocks().stream().anyMatch(b->isProtected(b.getLocation())))e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void entityBlock(EntityChangeBlockEvent e){if(isProtected(e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void entityForm(EntityBlockFormEvent e){if(isProtected(e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void hangingPlace(HangingPlaceEvent e){if(isProtected(e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void hangingBreak(HangingBreakEvent e){if(isProtected(e.getEntity().getLocation()))e.setCancelled(true);}

    /** Dla skrzyn, beczek, lejkow i podwojnych skrzyn: zapobiega edycji ekwipunku w strefie testowej. */
    private boolean protectedInventory(Inventory inventory){
        return protectedAnyInventory(inventory) && !generatedLootInventory(inventory);
    }
    private boolean protectedAnyInventory(Inventory inventory){
        if(inventory==null)return false;
        InventoryHolder holder=inventory.getHolder();
        if(holder instanceof BlockState state)return isProtected(state.getLocation());
        if(holder instanceof DoubleChest chest){
            InventoryHolder left=chest.getLeftSide(), right=chest.getRightSide();
            return (left instanceof BlockState b && isProtected(b.getLocation()))
                || (right instanceof BlockState b && isProtected(b.getLocation()));
        }
        return false;
    }
    @EventHandler(ignoreCancelled=true) public void openInventory(InventoryOpenEvent e){
        if(protectedInventory(e.getInventory()))e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void clickInventory(InventoryClickEvent e){
        if(protectedInventory(e.getView().getTopInventory()))e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void dragInventory(InventoryDragEvent e){
        if(protectedInventory(e.getView().getTopInventory()))e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void moveInventory(InventoryMoveItemEvent e){
        if(protectedAnyInventory(e.getSource())||protectedAnyInventory(e.getDestination())
                ||protectedAnyInventory(e.getInitiator()))e.setCancelled(true);
    }
    private boolean isProtected(Location l){
        // If an orphan/corrupt recovery record cannot be mapped to an exact bounding box,
        // quarantine the ENTIRE dedicated test world. Never quarantine the SMP world.
        if (recoveryFilesCorrupt && l != null && l.getWorld() != null) {
            String testWorld=plugin.getConfig().getString("safety.test-world-name","");
            String production=plugin.getConfig().getString("settings.world","");
            if(testWorld!=null && !testWorld.isBlank() && !testWorld.equalsIgnoreCase(production)
                && l.getWorld().getName().equals(testWorld))return true;
        }
        int configuredMargin = Math.max(0,Math.min(50,plugin.getConfig().getInt("safety.closed-protection-margin",50)));
        return jobs.values().stream().anyMatch(j -> j.phase != Phase.RESTORED
            && (j.phase != Phase.FAILED || snapshotFile(j).exists())
            // Active test remains read-only at the dungeon itself; once closing, protect
            // the full 50-block surroundings until rollback finishes, in every axis.
            && l != null && l.getWorld() != null && l.getWorld().getUID().equals(j.worldUid)
            && TestProtectionPolicy.within(l.getBlockX(),l.getBlockY(),l.getBlockZ(),
                j.minX,j.maxX,j.minY,j.maxY,j.minZ,j.maxZ,
                TestProtectionPolicy.margin(j.phase == Phase.ACTIVE,configuredMargin)));
    }
}
