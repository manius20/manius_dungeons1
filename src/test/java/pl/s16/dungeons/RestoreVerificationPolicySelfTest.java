package pl.s16.dungeons;

public final class RestoreVerificationPolicySelfTest {
    private static void eq(Object actual,Object expected) {if(!actual.equals(expected))throw new AssertionError(actual+" != "+expected);}
    private static void fail(Runnable action){try{action.run();throw new AssertionError("Expected refusal");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args) {
        var p=RestoreVerificationPolicy.Action.class;
        eq(RestoreVerificationPolicy.decide(100,100,0,0,2),RestoreVerificationPolicy.Action.FINISH);
        eq(RestoreVerificationPolicy.decide(100,100,3,0,2),RestoreVerificationPolicy.Action.RETRY_FROM_SNAPSHOT);
        eq(RestoreVerificationPolicy.decide(100,100,1,1,2),RestoreVerificationPolicy.Action.RETRY_FROM_SNAPSHOT);
        eq(RestoreVerificationPolicy.decide(100,100,1,2,2),RestoreVerificationPolicy.Action.STOP_AND_ALERT);
        eq(RestoreVerificationPolicy.decide(1,1,1,0,0),RestoreVerificationPolicy.Action.STOP_AND_ALERT);
        // Restart must not reset attempts: after a persisted retry the next mismatch remains bounded.
        int attempts=0;
        for(int i=0;i<2;i++) {
            eq(RestoreVerificationPolicy.decide(200,200,1,attempts,2),RestoreVerificationPolicy.Action.RETRY_FROM_SNAPSHOT);
            attempts++; // persisted in the real recovery manifest
        }
        eq(RestoreVerificationPolicy.decide(200,200,1,attempts,2),RestoreVerificationPolicy.Action.STOP_AND_ALERT);
        fail(()->RestoreVerificationPolicy.decide(100,99,0,0,2));
        fail(()->RestoreVerificationPolicy.decide(100,101,0,0,2));
        fail(()->RestoreVerificationPolicy.decide(100,100,-1,0,2));
        fail(()->RestoreVerificationPolicy.decide(0,0,0,0,2));
        System.out.println("PASS RestoreVerificationPolicySelfTest: bounded retries, restart persistence rules");
    }
}
