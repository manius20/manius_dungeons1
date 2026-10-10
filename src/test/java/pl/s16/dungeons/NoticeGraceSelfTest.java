package pl.s16.dungeons;

import java.time.Instant;

public final class NoticeGraceSelfTest {
    public static void main(String[] args) {
        Instant start=Instant.parse("2026-10-17T18:00:00Z");
        var at30=start.minusSeconds(1800);
        var at10=start.minusSeconds(600);
        check(DungeonStartNotices.due(at30.minusSeconds(1),at30,start,true,90).orElse(-1)==30,"on time 30");
        check(DungeonStartNotices.due(at10.minusSeconds(1),at10,start,true,90).orElse(-1)==10,"on time 10");
        check(DungeonStartNotices.due(at30.minusSeconds(1),at30.plusSeconds(89),start,true,90).orElse(-1)==30,"lag +89s");
        check(DungeonStartNotices.due(at30.minusSeconds(1),at30.plusSeconds(91),start,true,90).isEmpty(),"stale 30");
        check(DungeonStartNotices.due(at30.minusSeconds(1),at10.plusSeconds(30),start,true,90).orElse(-1)==10,"skip stale 30, deliver latest 10");
        check(DungeonStartNotices.due(at30.minusSeconds(1),at10.plusSeconds(150),start,true,90).isEmpty(),"skip both stale");
        check(DungeonStartNotices.due(at30.minusSeconds(1),at30.plusSeconds(30),start,false,90).isEmpty(),"unbuilt silent");
        check(DungeonStartNotices.due(at30.minusSeconds(1),start,start,true,90).isEmpty(),"after opening silent");
        check(DungeonStartNotices.due(null,at30,start,true,90).isEmpty(),"after restart no backlog");
        check(DungeonStartNotices.due(at30.minusSeconds(1),at30,start,true,301).isEmpty(),"bad grace fail closed");
        System.out.println("PASS NoticeGraceSelfTest (10 late-tick and readiness checks)");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
