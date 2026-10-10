package pl.s16.dungeons;

import java.time.*;
import java.util.*;

/** Pure-Java assertions: no Paper runtime required. */
public final class TestLifecycleSelfTest {
    public static void main(String[] args) {
        Instant start=Instant.parse("2026-10-10T15:00:00Z");
        Instant end=start.plusSeconds(7200);
        require(EventTimeline.crossedMilestones(start.minusSeconds(1810),start.minusSeconds(1790),start,List.of(30)).equals(List.of(30)),"notice after lag");
        require(EventTimeline.crossedMilestones(start.minusSeconds(1920),start.minusSeconds(870),start,List.of(30,15)).equals(List.of(30,15)),"several notices during downtime");
        require(EventTimeline.crossedMilestones(start.minusSeconds(1800),start.minusSeconds(1750),start,List.of(30)).isEmpty(),"no replay");
        require(EventTimeline.crossedMilestones(start.minusSeconds(31),start.plusSeconds(1),start,List.of(1)).isEmpty(),"no stale notices after opening");
        require(EventTimeline.crossedMilestones(start.minusSeconds(60),start.minusSeconds(20),start,List.of(1)).isEmpty(),"not yet due");
        require(EventTimeline.crossedMilestones(start.minusSeconds(62),start.minusSeconds(59),start,List.of(1)).equals(List.of(1)),"notice exact milestone");
        require(EventTimeline.crossedMilestones(start.minusSeconds(301),start.minusSeconds(200),start,List.of(5,5)).equals(List.of(5)),"dedup");
        require(EventTimeline.crossedMilestones(start.minusSeconds(60),start.minusSeconds(120),start,List.of(1)).isEmpty(),"clock reversal");
        require(!TestDeadlinePolicy.expired(start,0),"legacy no deadline");
        require(!TestDeadlinePolicy.expired(start,start.plusSeconds(1).toEpochMilli()),"before deadline");
        require(TestDeadlinePolicy.expired(end,end.toEpochMilli()),"at deadline");
        require(TestDeadlinePolicy.expired(end.plusSeconds(1),end.toEpochMilli()),"after deadline");
        System.out.println("PASS: test lifecycle milestone crossing and durable deadline (11 checks)");
    }
    private static void require(boolean good,String text){if(!good)throw new AssertionError(text);}
}
