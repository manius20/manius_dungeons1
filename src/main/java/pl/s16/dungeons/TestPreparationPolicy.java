package pl.s16.dungeons;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.HexFormat;

/** Pure, testable policy for TEST-WORLD event preparation. Never mutates Minecraft. */
public final class TestPreparationPolicy {
    private TestPreparationPolicy() {}

    public static boolean due(Instant now,Instant start,Instant end,int leadMinutes,boolean alreadyAttempted) {
        if (now==null || start==null || end==null || leadMinutes<0 || leadMinutes>1440
            || !start.isBefore(end) || alreadyAttempted) return false;
        return !now.isBefore(start.minus(Duration.ofMinutes(leadMinutes))) && now.isBefore(end);
    }

    /** Unique recovery ID per approved occurrence, even when the schedule is weekly. */
    public static String recoveryId(String eventId,LocalDate date) {
        if(eventId==null || !eventId.matches("[A-Za-z0-9_-]{1,32}") || date==null)
            throw new IllegalArgumentException("Niepoprawny termin dungeona");
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            byte[] hash=digest.digest((eventId+"/"+date).getBytes(StandardCharsets.UTF_8));
            return "t"+HexFormat.of().formatHex(hash,0,8)+"_"+date.toString().replace("-","");
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException("Brak SHA-256",e);}
    }
}
