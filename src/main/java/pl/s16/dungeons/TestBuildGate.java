package pl.s16.dungeons;

/** Pure fail-closed site and evacuation rules, independent from Bukkit. */
final class TestBuildGate {
    private TestBuildGate() {}
    static boolean allowRegions(boolean verified, boolean overlaps, boolean verificationRequired) {
        return !overlaps && (!verificationRequired || verified);
    }
    /** Only horizontal footprint matters when exiting to the surface. */
    static boolean exitOutsideProtectedFootprint(int x,int z,int minX,int maxX,int minZ,int maxZ,int margin) {
        long safeMargin=Math.max(0,Math.min(50,margin));
        return x<(long)minX-safeMargin || x>(long)maxX+safeMargin
            || z<(long)minZ-safeMargin || z>(long)maxZ+safeMargin;
    }
}
