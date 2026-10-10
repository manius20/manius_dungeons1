package pl.s16.dungeons;

public final class DungeonTestReadinessSelfTest {
    public static void main(String[] args) {
        int cases=0, overLimit=0;
        for(int rooms=15;rooms<=100;rooms++) {
            var layout=DungeonLayoutPlanner.generate(rooms,1,77L+rooms*13,0.05,0.25);
            var bp=DungeonBlueprint.fromLayout(layout);
            var e=DungeonPlanEstimate.analyze(layout,bp,300,500,90);
            var report=DungeonTestReadiness.check(e,20,100_000,350);
            if(report.minMutationTicks()!=(e.cells()+349)/350)throw new AssertionError("Bad ticks");
            if(report.chunks()!=e.chunks()||report.warnings().isEmpty())throw new AssertionError("Missing warnings");
            if(rooms>20 && report.fitsTestLimits())throw new AssertionError("Large world mutation authorized!");
            if(!report.fitsTestLimits())overLimit++;
            cases++;
        }
        if(cases!=86||overLimit<80)throw new AssertionError("Bad capacity coverage");
        System.out.println("PASS DungeonTestReadinessSelfTest: "+cases+" layouts, "+overLimit+" blocked physical tests");
    }
}
