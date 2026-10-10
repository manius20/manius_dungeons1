package pl.s16.dungeons;

/** Pure geometry used by the TEST rollback lock, including its perimeter. */
public final class TestProtectionPolicy {
    private TestProtectionPolicy() {}
    public static boolean within(int x,int y,int z,int minX,int maxX,int minY,int maxY,
                                 int minZ,int maxZ,int margin) {
        long m=Math.max(0L,Math.min(50L,(long) margin));
        return (long)x >= (long)minX-m && (long)x <= (long)maxX+m
            && (long)y >= (long)minY-m && (long)y <= (long)maxY+m
            && (long)z >= (long)minZ-m && (long)z <= (long)maxZ+m;
    }
    /** Active test geometry remains read-only until user edits are fully recoverable. */
    public static int margin(boolean active,int configuredClosedMargin) {
        return active ? 3 : Math.max(0,Math.min(50,configuredClosedMargin));
    }
}
