package pl.s16.dungeons;

public final class DungeonPerformanceEstimateSelfTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args) {
        var report=DungeonPerformanceEstimate.of(212001,350);
        check(report.minimumSinglePhaseTicks()==606,"ceil(cells/budget)");
        check(report.minimumThreePhaseTicks()==1818,"3 phases estimate");
        check(report.minimumSinglePhaseSeconds()==30.3,"ticks at 20 TPS");
        check(report.warnings().size()>=3,"large layout warning");
        check(DungeonPerformanceEstimate.of(1,350).minimumSinglePhaseTicks()==1,"minimum one tick");
        check(DungeonPerformanceEstimate.of(Long.MAX_VALUE,1).minimumThreePhaseTicks()==Long.MAX_VALUE,"saturating multiplication");
        boolean thrown=false;
        try{DungeonPerformanceEstimate.of(10,0);}catch(IllegalArgumentException correct){thrown=true;}
        check(thrown,"invalid throughput refused");
        System.out.println("PASS DungeonPerformanceEstimateSelfTest (7 controls)");
    }
}
