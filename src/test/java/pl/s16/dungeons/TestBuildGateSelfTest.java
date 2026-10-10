package pl.s16.dungeons;

public final class TestBuildGateSelfTest {
    private static void yes(boolean value) {if(!value)throw new AssertionError("Gate test failed");}
    public static void main(String[] args) {
        yes(TestBuildGate.allowRegions(true,false,true));
        yes(!TestBuildGate.allowRegions(false,false,true));
        yes(!TestBuildGate.allowRegions(false,true,true));
        yes(!TestBuildGate.allowRegions(true,true,false));
        yes(TestBuildGate.allowRegions(false,false,false)); // explicit test override
        // Inclusive protection edges and overflow-prone limits.
        yes(!TestBuildGate.exitOutsideProtectedFootprint(150,0,0,100,0,100,50));
        yes(TestBuildGate.exitOutsideProtectedFootprint(151,20,0,100,0,100,50));
        yes(!TestBuildGate.exitOutsideProtectedFootprint(20,-50,0,100,0,100,50));
        yes(TestBuildGate.exitOutsideProtectedFootprint(20,-51,0,100,0,100,50));
        yes(TestBuildGate.exitOutsideProtectedFootprint(Integer.MAX_VALUE,0,-10,10,-10,10,50));
        yes(RecoveryWorkBudget.deadline(0,100)>0);
        yes(RecoveryWorkBudget.deadline(0,0)==1_000_000L);
        yes(RecoveryWorkBudget.deadline(0,100)==25_000_000L);
        yes(RecoveryWorkBudget.hasTime(100,99));
        yes(!RecoveryWorkBudget.hasTime(100,100));
        System.out.println("TestBuildGateSelfTest OK");
    }
}
