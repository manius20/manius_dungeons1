package pl.s16.dungeons;

import java.time.*;
import java.util.*;

public final class ScheduleInputSelfTest {
    private static void ok(boolean result, String what){if(!result)throw new AssertionError(what);}
    public static void main(String[] args) {
        LocalDate now=LocalDate.of(2026,10,10);
        List<String> hours=ScheduleInput.times();
        ok(hours.size()==24,"24 godzin");
        ok(hours.get(0).equals("00:00") && hours.get(23).equals("23:00"),"00 do 23");
        ok(new HashSet<>(hours).size()==24,"Bez duplikatow");
        for(String t:hours)ok(LocalTime.parse(t).getMinute()==0,"Poprawny czas "+t);
        ok(LocalTime.parse("07:30").equals(LocalTime.of(7,30)),"Wlasne minuty");
        ok(ScheduleInput.parse("dzis",now).date().equals(now),"dzis");
        ok(ScheduleInput.parse("jutro",now).date().equals(now.plusDays(1)),"jutro");
        ok(ScheduleInput.parse("2026-12-31",now).date().equals(LocalDate.of(2026,12,31)),"dowolna data");
        ok(ScheduleInput.parse("codziennie",now).days().size()==7,"Codziennie");
        ok(ScheduleInput.parse("dni:MONDAY,FRIDAY",now).days().equals(EnumSet.of(DayOfWeek.MONDAY,DayOfWeek.FRIDAY)),"Wiele dni");
        ok(ScheduleInput.parse("dni:pon,wt,sr",now).days().size()==3,"Polskie skroty");
        ok(ScheduleInput.dates(now).contains(now.plusDays(13).toString()),"14 dni w TAB");
        for(DayOfWeek d: DayOfWeek.values())ok(ScheduleInput.dates(now).contains("dni:"+d.name()),"TAB dzien "+d);
        DungeonSchedule daily=new DungeonSchedule("test_daily","mesa",null,ScheduleInput.parse("codziennie",now).days(),LocalTime.MIDNIGHT,60,0);
        for(int i=0;i<14;i++)ok(daily.occursOn(now.plusDays(i)),"Codzienny event: "+i);
        boolean rejected=false;
        try {ScheduleInput.parse("dni:niewiadomo",now);}catch(IllegalArgumentException e){rejected=true;}
        ok(rejected,"Bledny dzien tygodnia");
        System.out.println("PASS schedule input: 24 godziny, codziennie, daty, 14 dni TAB");
    }
}
