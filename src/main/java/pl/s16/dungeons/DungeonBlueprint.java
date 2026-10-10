package pl.s16.dungeons;

import java.util.*;

/**
 * Deterministyczny plan blokow, BEZ API Bukkit i BEZ modyfikacji swiata.
 * Kazda pozycja to wspolrzedne lokalne; sufit nie przekracza wysokosci 6.
 * WALL to skorupa, AIR to pusta przestrzen, FLOOR podloga.
 * Kolejnosc: posadzki i skorupy sa nadpisywane przez AIR tylko wewnatrz przejsc.
 */
public final class DungeonBlueprint {
    public enum Cell { WALL, FLOOR, AIR }
    public record Pos(int x,int y,int z) {}
    public record Bounds(int minX,int maxX,int minY,int maxY,int minZ,int maxZ) {}
    private final Map<Pos,Cell> cells;
    private final Bounds bounds;
    private DungeonBlueprint(Map<Pos,Cell> cells, Bounds bounds) {
        this.cells=Collections.unmodifiableMap(new HashMap<>(cells));this.bounds=bounds;
    }
    public Map<Pos,Cell> cells() { return cells; }
    public Bounds bounds() { return bounds; }
    public long airCount() { return cells.values().stream().filter(c->c==Cell.AIR).count(); }

    public static DungeonBlueprint fromLayout(DungeonLayoutPlanner.Layout layout) {
        Map<Pos,Cell> out=new HashMap<>();
        // Wysokosc pokoi: 5 blokow powietrza (y=1..5), pelny dach y=6.
        for(var r:layout.rooms()) {
            int rad=r.radius();
            for(int x=r.x()-rad-1;x<=r.x()+rad+1;x++)
                for(int z=r.z()-rad-1;z<=r.z()+rad+1;z++)
                    for(int y=0;y<=6;y++) {
                        boolean inside=Math.abs(x-r.x())<=rad && Math.abs(z-r.z())<=rad;
                        Pos p=new Pos(x,y,z);
                        out.put(p,inside ? (y==0?Cell.FLOOR:y==6?Cell.WALL:Cell.AIR) : Cell.WALL);
                    }
        }
        // Korytarze sa na wspolnym poziomie; kazdy laczy dwa srodki pokoju.
        // Jednoznaczna kolejność: od srodka pokoju A w osi X, potem w osi Z.
        for(var e:layout.corridors()) {
            var a=layout.rooms().get(e.from());var b=layout.rooms().get(e.to());
            walk(out,a.x(),a.z(),b.x(),a.z());
            walk(out,b.x(),a.z(),b.x(),b.z());
        }
        // Podloga jest zawsze odtwarzana przy ryciu korytarza.
        // Bounds MUST come from actual changed cells (including corridor walls), not
        // approximate layout dimensions. Collision and world-border checks rely on this.
        int minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,minY=Integer.MAX_VALUE,maxY=Integer.MIN_VALUE;
        int minZ=Integer.MAX_VALUE,maxZ=Integer.MIN_VALUE;
        for(Pos p:out.keySet()) {
            minX=Math.min(minX,p.x());maxX=Math.max(maxX,p.x());
            minY=Math.min(minY,p.y());maxY=Math.max(maxY,p.y());
            minZ=Math.min(minZ,p.z());maxZ=Math.max(maxZ,p.z());
        }
        if(out.isEmpty())throw new IllegalStateException("Plan nie zawiera zadnych blokow");
        return new DungeonBlueprint(out,new Bounds(minX,maxX,minY,maxY,minZ,maxZ));
    }
    private static void walk(Map<Pos,Cell> out,int x1,int z1,int x2,int z2) {
        int dx=Integer.compare(x2,x1),dz=Integer.compare(z2,z1);
        for(int x=x1,z=z1;;x+=dx,z+=dz) {
            for(int ox=-2;ox<=2;ox++)for(int oz=-2;oz<=2;oz++)for(int y=0;y<=6;y++){
                Pos p=new Pos(x+ox,y,z+oz);
                boolean interior=Math.abs(ox)<=1&&Math.abs(oz)<=1;
                Cell value=interior?(y==0?Cell.FLOOR:y==6?Cell.WALL:Cell.AIR):Cell.WALL;
                // Nie zaslaniaj wczesniej otwartych przejsc lub pomieszczen.
                out.merge(p,value,(old,now)-> old==Cell.AIR || now==Cell.AIR ? Cell.AIR
                    : old==Cell.FLOOR || now==Cell.FLOOR ? Cell.FLOOR : Cell.WALL);
            }
            if(x==x2&&z==z2)break;
        }
    }
}
