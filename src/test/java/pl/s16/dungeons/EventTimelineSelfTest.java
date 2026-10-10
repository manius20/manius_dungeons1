package pl.s16.dungeons;

import java.time.*;
import java.util.*;

public final class EventTimelineSelfTest {
    public static void main(String[] args) {
        ZoneId zone=ZoneId.of("Europe/Warsaw");
        DungeonSchedule once=new DungeonSchedule("alpha","mesa",LocalDate.of(2026,10,10),null,
            LocalTime.of(23,30),120,15);
        var w=EventTimeline.window(once,once.date,zone);
        check(w.end().toLocalDate().equals(LocalDate.of(2026,10,11)),"across midnight");
        check(w.end().getHour()==1&&w.end().getMinute()==30,"duration");
        check(EventTimeline.notice(w.start().minusSeconds(1799),w.start(),List.of(30)).orElse(-1)==30,"rounded 30min");
        check(EventTimeline.notice(w.start().minusSeconds(1740),w.start(),List.of(30)).isEmpty(),"no false 30min");
        check(EventTimeline.notice(w.end().plusSeconds(1),w.end(),List.of(1)).isEmpty(),"no late warning");
        check(EventTimeline.finished(w.end(),w),"close inclusive");
        check(!EventTimeline.finished(w.end().minusSeconds(1),w),"not before close");
        check(EventTimeline.normalizeWarnings(List.of(15,30,1,15,-1,2000,5)).equals(List.of(30,15,5,1)),"normalize");
        System.out.println("PASS EventTimelineSelfTest");
    }
    private static void check(boolean yes,String reason){if(!yes)throw new AssertionError(reason);}
}
