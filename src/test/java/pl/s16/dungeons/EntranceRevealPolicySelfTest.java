package pl.s16.dungeons;

import java.time.*;

public final class EntranceRevealPolicySelfTest {
    public static void main(String[] args) {
        Instant open=Instant.parse("2026-10-17T15:00:00Z");
        Instant close=open.plusSeconds(7200);
        check(!EntranceRevealPolicy.publicVisible(open.minusSeconds(31*60),open,close,30,true),"too early");
        check(EntranceRevealPolicy.publicVisible(open.minusSeconds(30*60),open,close,30,true),"reveal boundary");
        check(EntranceRevealPolicy.publicVisible(open.minusSeconds(15*60),open,close,30,true),"before opening");
        check(EntranceRevealPolicy.publicVisible(open,open,close,30,true),"open instant");
        check(EntranceRevealPolicy.publicVisible(close.minusMillis(1),open,close,30,true),"end minus epsilon");
        check(!EntranceRevealPolicy.publicVisible(close,open,close,30,true),"no marker after close");
        check(!EntranceRevealPolicy.publicVisible(open,open,close,30,false),"no marker without built test");
        check(!EntranceRevealPolicy.entranceOpen(open.minusNanos(1),open,close,true),"barrier before");
        check(EntranceRevealPolicy.entranceOpen(open,open,close,true),"barrier opens at start");
        check(!EntranceRevealPolicy.entranceOpen(close,open,close,true),"barrier closes at end");
        check(!EntranceRevealPolicy.entranceOpen(open,open,close,false),"no event is not open");
        check(!EntranceRevealPolicy.publicVisible(open,open,close,-1,true),"bad lead");
        check(!EntranceRevealPolicy.publicVisible(open,close,open,30,true),"bad interval");
        System.out.println("EntranceRevealPolicySelfTest PASS (13 checks)");
    }
    private static void check(boolean value,String name){if(!value)throw new AssertionError(name);}
}
