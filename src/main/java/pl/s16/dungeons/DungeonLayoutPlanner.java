package pl.s16.dungeons;

import java.util.*;

/**
 * Geometria podziemnego dungeona - BEZ uzywania Bukkit World#setType.
 * Umozliwia podglad losowego ukladu i testy polaczen przed kodowaniem
 * zapisu oraz przywracania blokow. Przyjmuje od 15 do 100 pokoi.
 */
public final class DungeonLayoutPlanner {
    public record Room(int index,int x,int z,int radius,int depth) {}
    public record Corridor(int from,int to) {}
    public record Layout(List<Room> rooms,List<Corridor> corridors,List<Integer> entrances,
                         boolean boss,boolean miniboss,int width,int length) {}
    private DungeonLayoutPlanner() {}
    public static Layout generate(int count,int entryCount,long seed,double bossChance,double minibossChance){
        if(count<15||count>100)throw new IllegalArgumentException("Pokoje: 15-100");
        if(entryCount<1||entryCount>10)throw new IllegalArgumentException("Wejscia: 1-10");
        Random r=new Random(seed);
        int cols=(int)Math.ceil(Math.sqrt(count));
        int rows=(count+cols-1)/cols;
        List<Room> rooms=new ArrayList<>();
        for(int i=0;i<count;i++){
            int cx=i%cols;int cz=i/cols;
            // Siatka co 28 blokow; pokoje nie nakladaja sie na siebie.
            rooms.add(new Room(i,cx*28+14,cz*28+14,5+r.nextInt(3),20+r.nextInt(12)));
        }
        // Drzewo spinajace: kazdy pokoj ma fizyczna droge do wejscia.
        // Randomized frontier algorithm: tworzymy wszystkie sasiednie krawedzie.
        List<Corridor> connected=new ArrayList<>();
        Set<Integer> visited=new HashSet<>();visited.add(0);
        List<Corridor> frontier=new ArrayList<>();addNeighbours(0,count,cols,frontier);
        while(visited.size()<count){
            if(frontier.isEmpty())throw new IllegalStateException("Nie mozna polaczyc pokoi");
            int selected=r.nextInt(frontier.size());Corridor e=frontier.remove(selected);
            if(visited.contains(e.to()))continue;
            visited.add(e.to());connected.add(e);addNeighbours(e.to(),count,cols,frontier);
        }
        // Do 15% dodatkowych korytarzy - mniej liniowy dungeon.
        for(int i=0;i<count;i++) {
            List<Corridor> edges=new ArrayList<>();addNeighbours(i,count,cols,edges);
            for(Corridor e:edges)if(e.to()>e.from() && r.nextDouble()<0.15
                && connected.stream().noneMatch(old->sameUndirected(old,e)))connected.add(e);
        }
        List<Integer> boundary=new ArrayList<>();
        for(int i=0;i<count;i++)if(i%cols==0||i%cols==cols-1||i<cols||i/cols==rows-1)boundary.add(i);
        // Wejscie glowne w pokoju 0 (zgodnie z rzeczywistym testowym szybem).
        // Dodatkowe 1-9 wejsci sa dopiero PLANEM — bez kopii terenu nie budujemy szybikow.
        boundary.remove(Integer.valueOf(0));
        Collections.shuffle(boundary,r);
        List<Integer> chosenEntrances=new ArrayList<>();
        chosenEntrances.add(0);
        chosenEntrances.addAll(boundary.subList(0,Math.min(entryCount-1,boundary.size())));
        List<Integer> entrances=List.copyOf(chosenEntrances);
        boolean boss=r.nextDouble()<bossChance;
        boolean miniboss=r.nextDouble()<minibossChance;
        int width=cols*28,length=rows*28;
        return new Layout(List.copyOf(rooms),List.copyOf(connected),entrances,boss,miniboss,width,length);
    }
    private static boolean sameUndirected(Corridor a,Corridor b){return (a.from()==b.from()&&a.to()==b.to())||(a.from()==b.to()&&a.to()==b.from());}
    private static void addNeighbours(int index,int count,int columns,List<Corridor> target){
        int col=index%columns;
        if(col>0)target.add(new Corridor(index,index-1));
        if(col+1<columns&&index+1<count)target.add(new Corridor(index,index+1));
        if(index>=columns)target.add(new Corridor(index,index-columns));
        if(index+columns<count)target.add(new Corridor(index,index+columns));
    }
}
