package pl.s16.dungeons;

import java.time.*;

/** Offline checks for title/chat milestones; never requires Paper or Minecraft. */
public final class DungeonStartNoticesSelfTest {
    private static int checks;
    public static void main(String[] args) {
        Instant start=Instant.parse("2026-10-17T15:00:00Z");
        Instant m31=start.minusSeconds(31*60);
        Instant m30=start.minusSeconds(30*60);
        Instant m29=start.minusSeconds(29*60);
        Instant m11=start.minusSeconds(11*60);
        Instant m10=start.minusSeconds(10*60);
        Instant m9=start.minusSeconds(9*60);
        check(DungeonStartNotices.due(m31,m30,start,true).orElse(-1)==30,"30-minute title and chat");
        check(DungeonStartNotices.due(m11,m10,start,true).orElse(-1)==10,"10-minute details");
        check(DungeonStartNotices.due(m31,m29,start,true).orElse(-1)==30,"lagged 30-minute notice");
        check(DungeonStartNotices.due(m11,m9,start,true).orElse(-1)==10,"lagged 10-minute notice");
        check(DungeonStartNotices.due(m31,m9,start,true).orElse(-1)==10,"huge lag skips obsolete 30-minute title");
        check(DungeonStartNotices.due(m30,m29,start,true).isEmpty(),"no repeated notification");
        check(DungeonStartNotices.due(m31,m30,start,false).isEmpty(),"schedule without built test is silent");
        check(DungeonStartNotices.due(m31,start,start,true).isEmpty(),"no notices after start");
        check(DungeonStartNotices.due(start,m9,start,true).isEmpty(),"clock backwards doesn't announce");
        check(DungeonStartNotices.due(null,m30,start,true).isEmpty(),"no restart backlog");
        check(DungeonStartNotices.entranceCount(1).equals("1 wejście"),"one entrance");
        check(DungeonStartNotices.entranceCount(2).equals("2 wejścia"),"two entrances");
        check(DungeonStartNotices.entranceCount(5).equals("5 wejść"),"five entrances");
        check(DungeonStartNotices.entranceCount(12).equals("12 wejść"),"twelve entrances");
        check(DungeonStartNotices.entranceCount(22).equals("22 wejścia"),"twenty-two entrances");
        check(DungeonStartNotices.duration(120).equals("2 godz."),"hours");
        check(DungeonStartNotices.duration(90).equals("1 godz. 30 min"),"mixed duration");
        check(DungeonStartNotices.duration(45).equals("45 min"),"minutes");
        check(DungeonStartNotices.tenMinuteDetails(3,120).contains("3 wejścia"),"real count");
        check(DungeonStartNotices.tenMinuteDetails(3,120).contains("2 godz."),"real event duration");
        System.out.println("PASS DungeonStartNoticesSelfTest ("+checks+" checks)");
    }
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
}
