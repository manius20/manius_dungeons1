package pl.s16.dungeons;

import java.util.*;

/** Czyste reguly geometrii i borderu; niezalezne od serwera. */
public final class PlacementRules {
    public record Decision(boolean allowed, List<String> reasons) {}
    private PlacementRules() {}

    public static Decision validate(int x,int z,int entranceX,int entranceZ,
                                    DungeonBlueprint.Bounds bounds,int entranceRadius,int worldLimit,int margin) {
        List<String> errors=new ArrayList<>();
        // Math.hypot chroni przed przepelnieniem x*x.
        if(Math.hypot(entranceX,entranceZ)>entranceRadius)
            errors.add("Wejscie poza promieniem "+entranceRadius+" od 0,0");
        // Use 64-bit maths: the source coordinates come from an untrusted player/location
        // and may be near Integer.MIN/MAX_VALUE. Signed int overflow could
        // previously let out-of-border constructions pass the safety gate.
        long minX=(long)x+bounds.minX()-margin, maxX=(long)x+bounds.maxX()+margin;
        long minZ=(long)z+bounds.minZ()-margin, maxZ=(long)z+bounds.maxZ()+margin;
        if(minX< -worldLimit || maxX>worldLimit || minZ< -worldLimit || maxZ>worldLimit)
            errors.add("Konstrukcja lub margines ochrony wykracza poza granice ±"+worldLimit);
        return new Decision(errors.isEmpty(),List.copyOf(errors));
    }
}
