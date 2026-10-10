package pl.s16.dungeons;

import java.util.*;

/** Deterministyczny, niezależny od Bukkit plan spotkań. Żadnych zmian świata. */
public final class MobEncounterPlan {
    public enum Kind { ZOMBIE, SKELETON, SPIDER, CAVE_SPIDER, HUSK, BOSS, MINIBOSS }
    public record Encounter(int roomIndex, Kind kind, boolean boosted) {}
    private MobEncounterPlan() {}

    public static List<Encounter> generate(DungeonLayoutPlanner.Layout layout,long seed,double occupiedFraction,double boostedFraction) {
        if (!Double.isFinite(occupiedFraction)||occupiedFraction<0||occupiedFraction>1
            ||!Double.isFinite(boostedFraction)||boostedFraction<0||boostedFraction>1)
            throw new IllegalArgumentException("Frakcje muszą należeć do [0,1]");
        Random random=new Random(seed ^ 0x5B16_2026_0ABCL);
        List<Integer> available=new ArrayList<>();
        for (int i=0;i<layout.rooms().size();i++)
            if(!layout.entrances().contains(i))available.add(i); // Wszystkie pokoje wejsciowe bez mobow
        Collections.shuffle(available,random);
        int count=Math.min(available.size(),(int)Math.round(layout.rooms().size()*occupiedFraction));
        if(available.isEmpty())throw new IllegalArgumentException("Brak pokoi bez wejsc dla mobow");
        Kind[] base={Kind.ZOMBIE,Kind.SKELETON,Kind.SPIDER,Kind.CAVE_SPIDER,Kind.HUSK};
        List<Encounter> encounters=new ArrayList<>();
        int strong=Math.min(count,(int)Math.round(count*boostedFraction));
        for(int i=0;i<count;i++)encounters.add(new Encounter(available.get(i),base[random.nextInt(base.length)],i<strong));
        if(layout.boss()) {
            int room=available.get(available.size()-1);
            encounters.removeIf(e->e.roomIndex()==room);
            encounters.add(new Encounter(room,Kind.BOSS,true));
        }
        if(layout.miniboss()) {
            // Oddzielny pokój, jeśli boss również występuje.
            int room=available.get(available.size()-(layout.boss()?2:1));
            encounters.removeIf(e->e.roomIndex()==room);
            encounters.add(new Encounter(room,Kind.MINIBOSS,true));
        }
        return List.copyOf(encounters);
    }
}
