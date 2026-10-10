package pl.s16.dungeons;

/** 2D inclusive collision test; coordinates may be supplied in either corner order. */
public final class RectangleCollision {
    private RectangleCollision() {}
    public static boolean intersects(int ax1,int az1,int ax2,int az2,int bx1,int bz1,int bx2,int bz2) {
        return Math.min(ax1,ax2)<=Math.max(bx1,bx2)
            && Math.max(ax1,ax2)>=Math.min(bx1,bx2)
            && Math.min(az1,az2)<=Math.max(bz1,bz2)
            && Math.max(az1,az2)>=Math.min(bz1,bz2);
    }
}
