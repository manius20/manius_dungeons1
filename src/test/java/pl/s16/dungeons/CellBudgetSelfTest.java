package pl.s16.dungeons;
public final class CellBudgetSelfTest {
    public static void main(String[] args) {
        for(int i=15;i<=20;i++) {
            var layout=DungeonLayoutPlanner.generate(i,1,9000+i,0,0);
            var bp=DungeonBlueprint.fromLayout(layout);
            int shaft=EntranceShaftPlan.design(0,0,40,64).cells().size();
            if(bp.cells().size()+shaft>100000)throw new AssertionError("Test exceeds block budget: "+i);
            System.out.println("Rooms="+i+" cells="+bp.cells().size()+" shaft="+shaft);
        }
    }
}
