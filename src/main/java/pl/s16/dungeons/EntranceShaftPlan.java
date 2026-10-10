package pl.s16.dungeons;

import java.util.ArrayList;
import java.util.List;

/**
 * Niezalezna od Bukkit propozycja PODZIEMNEGO dojscia do pierwszego pokoju.
 * To geometria pogladowa, NIE polecenia zmiany blokow. Budowa i snapshot
 * wszystkich blokow szybiku beda osobnym, pozniejszym etapem.
 */
public final class EntranceShaftPlan {
    public enum Element { WALL, OPEN, LADDER_BACKING, LADDER, SURFACE_OPENING }
    public record Cell(int x, int y, int z, Element element) {}
    public record Plan(int x, int z, int floorY, int surfaceY, List<Cell> cells) {}
    private EntranceShaftPlan() {}

    public static Plan design(int x, int z, int floorY, int surfaceY) {
        if (surfaceY - floorY < 12 || surfaceY - floorY > 96)
            throw new IllegalArgumentException("Szybik wymaga glebokosci od 12 do 96 blokow");
        List<Cell> cells = new ArrayList<>();
        for (int y=floorY+1; y<=surfaceY+1; y++) {
            for (int dx=-2; dx<=2; dx++) for (int dz=-2; dz<=2; dz++) {
                Element element;
                if (y>=surfaceY && Math.abs(dx)<=1 && Math.abs(dz)<=1) element=Element.SURFACE_OPENING;
                else if (Math.abs(dx)==2 || Math.abs(dz)==2) element=Element.WALL;
                else element=Element.OPEN;
                // Drabina na zachodniej scianie: komorka (-1,0) i podpora (-2,0).
                if (y<surfaceY && dx==-2 && dz==0) element=Element.LADDER_BACKING;
                if (y<surfaceY && dx==-1 && dz==0) element=Element.LADDER;
                cells.add(new Cell(x+dx,y,z+dz,element));
            }
        }
        return new Plan(x,z,floorY,surfaceY,List.copyOf(cells));
    }
}
