package pl.s16.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * WYŁĄCZNIE świat testowy: moby mają trwałe tagi PDC, dzięki czemu rollback
 * usuwa tylko byty pluginu. W razie restartu job wraca do fazy przywracania.
 */
final class TestMobController implements Listener {
    private final ManiusDungeons plugin;
    private final NamespacedKey idKey,kindKey;
    private final Random rewardRandom=new Random();

    TestMobController(ManiusDungeons plugin) {
        this.plugin=plugin;
        idKey=new NamespacedKey(plugin,"test_dungeon_id");
        kindKey=new NamespacedKey(plugin,"test_mob_kind");
    }

    List<UUID> spawn(World world,String jobId,DungeonLayoutPlanner.Layout layout,int ox,int y,int oz,long seed) {
        if(!plugin.getConfig().getBoolean("mobs.enable-test-mobs",false))return List.of();
        if(!isTestWorld(world))throw new IllegalStateException("Moby można generować tylko w odrębnym świecie testowym");
        List<MobEncounterPlan.Encounter> plan=MobEncounterPlan.generate(layout,seed,
            plugin.getConfig().getDouble("mobs.occupied-room-fraction",0.5),
            plugin.getConfig().getDouble("mobs.boosted-fraction",0.5));
        Random random=new Random(seed^0xB0055L);
        List<UUID> spawned=new ArrayList<>();
        for(MobEncounterPlan.Encounter e:plan) {
            DungeonLayoutPlanner.Room room=layout.rooms().get(e.roomIndex());
            Location pos=new Location(world,ox+room.x()+0.5,y+1,oz+room.z()+0.5);
            if(!world.isChunkLoaded(Math.floorDiv(pos.getBlockX(),16),Math.floorDiv(pos.getBlockZ(),16)))
                throw new IllegalStateException("Chunk moba nie jest załadowany");
            if(!pos.getBlock().isPassable()||!pos.clone().add(0,1,0).getBlock().isPassable())
                throw new IllegalStateException("Pokój moba jest zablokowany: "+room.index());
            EntityType type=switch(e.kind()) {
                case SKELETON -> EntityType.SKELETON;
                case SPIDER -> EntityType.SPIDER;
                case CAVE_SPIDER -> EntityType.CAVE_SPIDER;
                case HUSK,MINIBOSS -> EntityType.HUSK;
                default -> EntityType.ZOMBIE;
            };
            Entity entity=world.spawnEntity(pos,type);
            // Tag jak najwcześniej: w razie awarii da się wykryć wygenerowany byt.
            entity.getPersistentDataContainer().set(idKey,PersistentDataType.STRING,jobId);
            entity.getPersistentDataContainer().set(kindKey,PersistentDataType.STRING,e.kind().name());
            spawned.add(entity.getUniqueId());
            if(!(entity instanceof Monster mob)) {
                entity.remove();
                throw new IllegalStateException("Nie można stworzyć potwora typu "+type);
            }
            try {
                mob.setRemoveWhenFarAway(false);
                double multiplier=e.kind()==MobEncounterPlan.Kind.BOSS?2.8:
                    e.kind()==MobEncounterPlan.Kind.MINIBOSS?1.8:
                    e.boosted()?plugin.getConfig().getDouble("mobs.boosted-health-multiplier",1.2):1.0;
                boost(mob,multiplier,e.kind()==MobEncounterPlan.Kind.BOSS?1.8:
                    e.kind()==MobEncounterPlan.Kind.MINIBOSS?1.5:
                    e.boosted()?plugin.getConfig().getDouble("mobs.boosted-damage-multiplier",1.2):1.0);
                if(e.kind()==MobEncounterPlan.Kind.BOSS)mob.setCustomName("§5§lBOSS DUNGEONU");
                else if(e.kind()==MobEncounterPlan.Kind.MINIBOSS)mob.setCustomName("§6§lMINIBOSS DUNGEONU");
                else if(e.boosted())mob.setCustomName("§cWzmocniony potwór");
                mob.setCustomNameVisible(e.kind()==MobEncounterPlan.Kind.BOSS||e.kind()==MobEncounterPlan.Kind.MINIBOSS);
                equip(mob,random);
            }catch(RuntimeException ex){mob.remove();throw ex;}
        }
        plugin.getLogger().info("Test "+jobId+": zespawnowano "+plan.size()+" mobów; boss="+layout.boss()+", miniboss="+layout.miniboss());
        return List.copyOf(spawned);
    }

    private void boost(LivingEntity entity,double hp,double damage) {
        if(!Double.isFinite(hp)||hp<1||hp>10||!Double.isFinite(damage)||damage<1||damage>10)
            throw new IllegalArgumentException("Nieprawidłowe mnożniki mobów");
        AttributeInstance maxHealth=entity.getAttribute(Attribute.MAX_HEALTH);
        if(maxHealth!=null){maxHealth.setBaseValue(maxHealth.getBaseValue()*hp);entity.setHealth(maxHealth.getValue());}
        AttributeInstance attack=entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if(attack!=null)attack.setBaseValue(attack.getBaseValue()*damage);
    }
    private void equip(Mob mob,Random random) {
        EntityEquipment equipment=mob.getEquipment();
        if(equipment==null)return;
        if(random.nextDouble()<0.38) {
            Material type=random.nextBoolean()?Material.IRON_HELMET:Material.CHAINMAIL_HELMET;
            ItemStack helmet=new ItemStack(type);
            if(random.nextDouble()<0.12)helmet.addUnsafeEnchantment(Enchantment.PROTECTION,1);
            equipment.setHelmet(helmet);equipment.setHelmetDropChance(0);
        }
        if(random.nextDouble()<0.22){equipment.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));equipment.setChestplateDropChance(0);}
        if(random.nextDouble()<0.4 && mob.getType()!=EntityType.SPIDER && mob.getType()!=EntityType.CAVE_SPIDER) {
            equipment.setItemInMainHand(new ItemStack(random.nextBoolean()?Material.IRON_SWORD:Material.BOW));
            equipment.setItemInMainHandDropChance(0);
        }
    }

    /** Nie usuwa graczy ani zwykłych mobów. Skanuje TYLKO już wczytane chunki testu. */
    void clearGenerated(World world,String jobId,Collection<UUID> tracked,int minX,int maxX,int minY,int maxY,int minZ,int maxZ) {
        if(!isTestWorld(world))return;
        // UUID z manifestu: usuwa też potwory, które odeszły dalej od pokoju.
        for(UUID uuid:tracked) {
            Entity entity=Bukkit.getEntity(uuid);
            if(entity!=null && world.getUID().equals(entity.getWorld().getUID())
                && jobId.equals(entity.getPersistentDataContainer().get(idKey,PersistentDataType.STRING))) entity.remove();
        }
        for(int cx=Math.floorDiv(minX,16);cx<=Math.floorDiv(maxX,16);cx++)
            for(int cz=Math.floorDiv(minZ,16);cz<=Math.floorDiv(maxZ,16);cz++) {
                if(!world.isChunkLoaded(cx,cz))continue;
                Chunk chunk=world.getChunkAt(cx,cz);
                for(Entity entity:chunk.getEntities()) {
                    Location loc=entity.getLocation();
                    if(loc.getBlockX()<minX||loc.getBlockX()>maxX||loc.getBlockZ()<minZ||loc.getBlockZ()>maxZ
                        ||loc.getBlockY()<minY-5||loc.getBlockY()>maxY+5)continue;
                    String marker=entity.getPersistentDataContainer().get(idKey,PersistentDataType.STRING);
                    // Wszystkie itemy leżące w obszarze są świadomie czyszczone po wydarzeniu TESTOWYM.
                    if(jobId.equals(marker)||entity instanceof Item)entity.remove();
                }
            }
    }

    @EventHandler(ignoreCancelled=true)
    public void death(EntityDeathEvent event) {
        LivingEntity mob=event.getEntity();
        String id=mob.getPersistentDataContainer().get(idKey,PersistentDataType.STRING);
        if(id==null||!isTestWorld(mob.getWorld()))return;
        String kind=mob.getPersistentDataContainer().get(kindKey,PersistentDataType.STRING);
        if(!plugin.isActiveTest(id)) {event.getDrops().clear();event.setDroppedExp(0);return;}
        if(!"BOSS".equals(kind)&&!"MINIBOSS".equals(kind))return;
        event.getDrops().clear();event.setDroppedExp(0);
        int rolls="BOSS".equals(kind)?Math.max(1,Math.min(10,plugin.getConfig().getInt("mobs.boss-reward-rolls",3))):2;
        String pool="BOSS".equals(kind)?"boss":"miniboss";
        double chance="BOSS".equals(kind)?plugin.getConfig().getDouble("mobs.mace-chance-per-boss-reward-roll",0.0001):0;
        if(!Double.isFinite(chance)||chance<0||chance>1)chance=0;
        List<ItemStack> candidates=plugin.lootStore().roll(pool,rewardRandom,27);
        if(candidates.isEmpty())candidates=plugin.lootStore().nonEmptyTemplates(pool);
        for(BossRewardRoller.Reward<ItemStack> reward:BossRewardRoller.roll(rewardRandom,rolls,chance,candidates)) {
            ItemStack item=reward.mace()?new ItemStack(Material.MACE):reward.normalItem().clone();
            mob.getWorld().dropItemNaturally(mob.getLocation(),item);
        }
        plugin.getLogger().info("Pokonano "+kind+" w teście "+id+"; nagrody upuszczone na ziemię.");
    }
    private boolean isTestWorld(World world) {
        String t=plugin.getConfig().getString("safety.test-world-name","");
        String production=plugin.getConfig().getString("settings.world","");
        return !t.isBlank()&&!t.equalsIgnoreCase(production)&&world.getName().equals(t);
    }
}
