package pl.s16.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/** Bootstrap. Etap 3: mutacje tylko w dedykowanym swiecie testowym. */
public final class ManiusDungeons extends JavaPlugin {
    private ScheduleStore schedules;
    private LootStore loot;
    private AdminMenu menu;
    private ScheduleTicker ticker;
    private RecoveryManager recovery;
    private SiteSearchService siteSearch;
    private TestMobController testMobs;
    private TestEntranceService entrances;

    @Override public void onEnable() {
        saveDefaultConfig();
        try { ZoneId.of(getConfig().getString("settings.timezone", "Europe/Warsaw")); }
        catch (DateTimeParseException e) {
            getLogger().severe("Nieprawidlowa strefa czasowa w config.yml; plugin nie zostanie uruchomiony: " + e.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        schedules = new ScheduleStore(this);
        loot = new LootStore(this);
        menu = new AdminMenu(this, schedules, loot);
        testMobs = new TestMobController(this);
        recovery = new RecoveryManager(this);
        siteSearch = new SiteSearchService(this);
        entrances = new TestEntranceService(this,schedules,recovery);
        AdminCommand admin = new AdminCommand(this, schedules, loot, menu, recovery, siteSearch);
        PluginCommand command = getCommand("dungeon");
        if (command == null) throw new IllegalStateException("Brak komendy dungeon w plugin.yml");
        command.setExecutor(admin);
        command.setTabCompleter(admin);
        Bukkit.getPluginManager().registerEvents(menu, this);
        Bukkit.getPluginManager().registerEvents(recovery, this);
        Bukkit.getPluginManager().registerEvents(testMobs, this);
        Bukkit.getPluginManager().registerEvents(entrances, this);
        recovery.start();
        ticker = new ScheduleTicker(this, schedules, recovery, entrances);
        // 1 minuta; operacje lekkie, wylacznie odczyt danych i komunikaty.
        ticker.start();
        getLogger().warning("ETAP 15: limity czasu operacji, fail-closed sprawdzenie regionow, ostrozna ewakuacja; automatyczne przygotowanie TYLKO testow po dodatkowej zgodzie, faza PREPARED, otwarcie w terminie, unikalne kopie dat; testowe bariery, ujawnianie prawdziwych wejsc i opcjonalne znaczniki BlueMap. Testowe moby, boss, miniboss i drop po zabiciu; budowanie tylko w dedykowanym SWIECIE TESTOWYM, po jawnym wlaczeniu w configu. Brak automatycznej generacji na SMP.");
    }
    @Override public void onDisable() {
        if (ticker != null) ticker.stop();
        if (entrances != null) entrances.stop();
        if (siteSearch != null) siteSearch.stopAll();
        if (recovery != null) recovery.stop();
        if (schedules != null) schedules.save();
        if (loot != null) loot.save();
    }
    public String prefix() { return color(getConfig().getString("settings.prefix", "&9&lS16 SMP 2 &8» &r")); }
    public String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    public ZoneId timezone() { return ZoneId.of(getConfig().getString("settings.timezone", "Europe/Warsaw")); }
    public FileConfiguration settings() { return getConfig(); }
    LootStore lootStore() { return loot; }
    TestEntranceService entrances() { return entrances; }
    TestMobController testMobs() { return testMobs; }
    boolean isActiveTest(String id) { return recovery != null && recovery.isActiveTest(id); }
    public boolean reloadAll() {
        try { reloadConfig(); timezone(); schedules.reload(); loot.reload(); return true; }
        catch (Exception e) { getLogger().severe("Blad reload: " + e.getMessage()); return false; }
    }
}
