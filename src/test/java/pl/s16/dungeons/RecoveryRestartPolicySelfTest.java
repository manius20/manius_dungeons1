package pl.s16.dungeons;

public final class RecoveryRestartPolicySelfTest {
    private static void verify(String phase,boolean file,boolean proof,RecoveryRestartPolicy.Action expected){
        var actual=RecoveryRestartPolicy.decide(phase,file,proof);
        if(actual!=expected)throw new AssertionError(phase+" snapshot="+file+" proof="+proof+": "+actual+" expected "+expected);
    }
    public static void main(String[] args) {
        verify("CAPTURING",false,false,RecoveryRestartPolicy.Action.NO_WORLD_CHANGES);
        verify("CAPTURING",true,false,RecoveryRestartPolicy.Action.NO_WORLD_CHANGES);
        verify("RESTORED",true,true,RecoveryRestartPolicy.Action.VERIFIED_COMPLETE);
        verify("RESTORED",false,true,RecoveryRestartPolicy.Action.INVALID);
        verify("RESTORED",true,false,RecoveryRestartPolicy.Action.RESUME_RESTORE);
        verify("RESTORED",false,false,RecoveryRestartPolicy.Action.INVALID);
        verify("FAILED",true,false,RecoveryRestartPolicy.Action.WAIT_FOR_ADMIN);
        verify("FAILED",false,false,RecoveryRestartPolicy.Action.INVALID);
        for(String phase:new String[]{"BUILDING","PREPARED","ACTIVE","RESTORING","RESTORING_TILES","VERIFYING"}){
            verify(phase,true,false,RecoveryRestartPolicy.Action.RESUME_RESTORE);
            verify(phase,false,false,RecoveryRestartPolicy.Action.INVALID);
        }
        verify("UNKNOWN",true,true,RecoveryRestartPolicy.Action.INVALID);
        System.out.println("RecoveryRestartPolicySelfTest OK");
    }
}
