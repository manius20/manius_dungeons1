package pl.s16.dungeons;
public final class TestProtectionSelfTest {
    public static void main(String[] args){
        int m=TestProtectionPolicy.margin(false,50);
        ok(m==50,"closed margin");
        ok(TestProtectionPolicy.margin(true,50)==3,"active margin");
        ok(TestProtectionPolicy.within(150,110,150,100,100,60,60,100,100,m),"50 edge included");
        ok(!TestProtectionPolicy.within(151,110,150,100,100,60,60,100,100,m),"51 out x");
        ok(!TestProtectionPolicy.within(150,111,150,100,100,60,60,100,100,m),"51 out y");
        ok(!TestProtectionPolicy.within(150,110,151,100,100,60,60,100,100,m),"51 out z");
        ok(TestProtectionPolicy.within(97,60,100,100,100,60,60,100,100,3),"active 3");
        ok(!TestProtectionPolicy.within(96,60,100,100,100,60,60,100,100,3),"active 4 out");
        ok(TestProtectionPolicy.margin(false,900)==50,"margin capped");
        ok(TestProtectionPolicy.margin(false,-2)==0,"negative capped");
        System.out.println("PASS TestProtectionSelfTest (10 checks)");
    }
    private static void ok(boolean v,String message){if(!v)throw new AssertionError(message);}
}
