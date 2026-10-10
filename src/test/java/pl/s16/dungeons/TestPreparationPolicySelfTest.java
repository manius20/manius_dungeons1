package pl.s16.dungeons;

import java.time.*;
import java.util.*;

public final class TestPreparationPolicySelfTest {
    static void check(boolean passed,String why) {if(!passed)throw new AssertionError(why);}
    public static void main(String[] args) {
        Instant start=Instant.parse("2026-10-17T17:00:00Z");
        Instant end=start.plusSeconds(120*60);
        check(!TestPreparationPolicy.due(start.minusSeconds(181*60),start,end,180,false),"too early");
        check(TestPreparationPolicy.due(start.minusSeconds(180*60),start,end,180,false),"boundary prepare");
        check(TestPreparationPolicy.due(start.plusSeconds(45),start,end,180,false),"late approval");
        check(!TestPreparationPolicy.due(end,start,end,180,false),"past deadline");
        check(!TestPreparationPolicy.due(start,start,end,180,true),"dedup");
        check(!TestPreparationPolicy.due(start,start,end,1441,false),"invalid lead");
        check(!TestPreparationPolicy.due(start,end,start,180,false),"reversed range");
        String weekly1=TestPreparationPolicy.recoveryId("mesa",LocalDate.of(2026,10,17));
        String weekly2=TestPreparationPolicy.recoveryId("mesa",LocalDate.of(2026,10,24));
        String other=TestPreparationPolicy.recoveryId("mech",LocalDate.of(2026,10,17));
        check(weekly1.matches("[a-zA-Z0-9_-]{1,32}"),"valid id");
        check(!weekly1.equals(weekly2),"distinct dates");
        check(!weekly1.equals(other),"distinct events");
        check(weekly1.equals(TestPreparationPolicy.recoveryId("mesa",LocalDate.of(2026,10,17))),"stable IDs");
        Set<String> ids=new HashSet<>();
        for(int d=1;d<=365;d++)ids.add(TestPreparationPolicy.recoveryId("weekly",LocalDate.of(2026,1,1).plusDays(d-1)));
        check(ids.size()==365,"annual uniqueness");
        System.out.println("ETAP 14: policy przygotowania 3h, spoznione planowanie, wygasanie i 365 unikalnych recovery ID OK");
    }
}
