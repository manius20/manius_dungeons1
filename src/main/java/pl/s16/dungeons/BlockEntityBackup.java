package pl.s16.dungeons;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.block.data.type.Chest;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.Lootable;

import java.io.IOException;
import java.util.*;

/**
 * Opt-in TEST-WORLD-only backup of simple chest/barrel/shulker inventories.
 * Unhandled tile entities, PDC, custom loot tables, and double chests FAIL CLOSED.
 * Bukkit's ItemStack YAML serialization preserves normal item metadata/enchantments.
 * It does NOT constitute a full NBT backup.
 */
final class BlockEntityBackup {
    private BlockEntityBackup(){}
    private static boolean supported(Material type) {
        return type==Material.CHEST || type==Material.TRAPPED_CHEST || type==Material.BARREL
            || type.name().endsWith("SHULKER_BOX");
    }

    static boolean canBackup(Block block) {
        return supported(block.getType());
    }

    static String capture(Block block) throws IOException {
        BlockState state=block.getState();
        if(!(state instanceof TileState))return "";
        if(!(state instanceof Container container) || !supported(block.getType()))
            throw new IOException("Nieobslugiwany BlockEntity: "+block.getType());
        if(!container.getPersistentDataContainer().getKeys().isEmpty())
            throw new IOException("Kontener ma PDC; wymagany pelny backup NBT: "+block.getType());
        if(container instanceof Lootable loot && loot.getLootTable()!=null)
            throw new IOException("Kontener ma loot table, nie mozna bezstratnie zapisac: "+block.getType());
        if(block.getBlockData() instanceof Chest chest && chest.getType()!=Chest.Type.SINGLE)
            throw new IOException("Podwojna skrzynia nie jest obslugiwana w testach");
        YamlConfiguration y=new YamlConfiguration();
        y.set("format",1);
        y.set("material",block.getType().name());
        y.set("size",container.getSnapshotInventory().getSize());
        y.set("lock",container.getLock());
        y.set("name",container.getCustomName());
        // list, not an ItemStack[]: stable Bukkit YAML ConfigurationSerializable encoding.
        y.set("items",Arrays.asList(container.getSnapshotInventory().getContents()));
        String content=y.saveToString();
        if(content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>RecoverySnapshotCodec.MAX_TILE_BYTES)
            throw new IOException("Dane kontenera przekraczaja limit 256 KiB");
        return content;
    }

    static void validate(String blockData,String yaml) throws IOException {
        if(yaml.isEmpty())return;
        read(blockData,yaml);
    }

    private record Payload(Material type,ItemStack[] items,String lock,String name){}
    private static Payload read(String blockData,String yaml) throws IOException {
        YamlConfiguration config=new YamlConfiguration();
        try {config.loadFromString(yaml);} catch(InvalidConfigurationException ex){throw new IOException("Bledne YAML kontenera",ex);}
        if(config.getInt("format",-1)!=1)throw new IOException("Nieobslugiwany format danych kontenera");
        String material=config.getString("material","");
        Material type=Material.matchMaterial(material);
        if(type==null||!supported(type) || !(blockData.equals("minecraft:"+type.name().toLowerCase(Locale.ROOT)) || blockData.startsWith("minecraft:"+type.name().toLowerCase(Locale.ROOT)+"[")))
            throw new IOException("Typ danych kontenera nie zgadza sie z oryginalnym blokiem");
        int size=config.getInt("size",-1);
        if(size<1||size>54)throw new IOException("Nieprawidlowy rozmiar kontenera");
        List<?> items=config.getList("items");
        if(items==null||items.size()!=size)throw new IOException("Niezgodna liczba slotow kontenera");
        ItemStack[] contents=new ItemStack[size];
        for(int i=0;i<size;i++){
            Object item=items.get(i);
            if(item!=null && !(item instanceof ItemStack))throw new IOException("Nieprawidlowy przedmiot na pozycji "+i);
            contents[i]=item==null?null:((ItemStack)item).clone();
        }
        return new Payload(type,contents,config.getString("lock",""),config.getString("name"));
    }

    /** Compare semantic container data (ItemStack metadata, slots, lock, name), not YAML key order. */
    static boolean matchesOriginal(Block block,String originalBlockData,String originalYaml) throws IOException {
        if(originalYaml.isEmpty())return true;
        if(!block.getBlockData().getAsString().equals(originalBlockData))return false;
        Payload expected=read(originalBlockData,originalYaml);
        String currentYaml=capture(block);
        if(currentYaml.isEmpty())return false;
        Payload current=read(originalBlockData,currentYaml);
        return current.type()==expected.type()
            && Arrays.equals(current.items(),expected.items())
            && Objects.equals(current.lock(),expected.lock())
            && Objects.equals(current.name(),expected.name());
    }

    static void restore(Block block,String originalBlockData,String yaml) throws IOException {
        if(yaml.isEmpty())return;
        Payload payload=read(originalBlockData,yaml);
        if(block.getType()!=payload.type())throw new IOException("Blok podczas restore nie jest kontenerem: "+block.getType());
        BlockState state=block.getState();
        if(!(state instanceof Container container))throw new IOException("Kontener nie ma BlockState Container");
        if(container.getSnapshotInventory().getSize()!=payload.items().length)
            throw new IOException("Inna wielkosc ekwipunku podczas restore");
        container.getSnapshotInventory().setContents(payload.items());
        container.setLock(payload.lock());
        container.setCustomName(payload.name());
        if(!container.update(true,false))throw new IOException("Nie udalo sie zapisac inwentarza kontenera");
    }
}
