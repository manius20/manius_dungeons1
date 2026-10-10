package pl.s16.dungeons;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Parsowanie harmonogramu bez zaleznosci od Bukkit; uzywane przez komende i TAB. */
public final class ScheduleInput {
    private ScheduleInput() {}
    public record Selection(LocalDate date, EnumSet<DayOfWeek> days) {
        public Selection {
            days = days == null || days.isEmpty() ? EnumSet.noneOf(DayOfWeek.class) : EnumSet.copyOf(days);
        }
    }
    private static final Map<String,DayOfWeek> WEEKDAYS = Map.ofEntries(
        Map.entry("PON",DayOfWeek.MONDAY), Map.entry("PONIEDZIALEK",DayOfWeek.MONDAY),
        Map.entry("WT",DayOfWeek.TUESDAY), Map.entry("WTOREK",DayOfWeek.TUESDAY),
        Map.entry("SR",DayOfWeek.WEDNESDAY), Map.entry("SRODA",DayOfWeek.WEDNESDAY),
        Map.entry("CZW",DayOfWeek.THURSDAY), Map.entry("CZWARTEK",DayOfWeek.THURSDAY),
        Map.entry("PT",DayOfWeek.FRIDAY), Map.entry("PIATEK",DayOfWeek.FRIDAY),
        Map.entry("SOB",DayOfWeek.SATURDAY), Map.entry("SOBOTA",DayOfWeek.SATURDAY),
        Map.entry("ND",DayOfWeek.SUNDAY), Map.entry("NIEDZIELA",DayOfWeek.SUNDAY));

    public static Selection parse(String input, LocalDate today) {
        Objects.requireNonNull(input); Objects.requireNonNull(today);
        String v = input.trim().toLowerCase(Locale.ROOT);
        if(v.equals("dzis") || v.equals("dzisiaj")) return new Selection(today, null);
        if(v.equals("jutro")) return new Selection(today.plusDays(1), null);
        if(v.equals("codziennie") || v.equals("kazdy_dzien")) return new Selection(null, EnumSet.allOf(DayOfWeek.class));
        if(v.startsWith("dni:")) {
            EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
            String[] parts = v.substring(4).split(",",-1);
            for(String raw:parts) {
                String token=raw.trim().toUpperCase(Locale.ROOT);
                if(token.isEmpty()) throw new IllegalArgumentException("Pusta nazwa dnia. Przyklad: dni:MONDAY,FRIDAY");
                DayOfWeek day=WEEKDAYS.get(token);
                if(day == null) {
                    try {day=DayOfWeek.valueOf(token);} catch(IllegalArgumentException e) {
                        throw new IllegalArgumentException("Nieznany dzien tygodnia: "+raw);
                    }
                }
                days.add(day);
            }
            return new Selection(null,days);
        }
        return new Selection(LocalDate.parse(v,DateTimeFormatter.ISO_LOCAL_DATE),null);
    }
    public static List<String> times() {
        return java.util.stream.IntStream.range(0,24)
            .mapToObj(h -> String.format(Locale.ROOT,"%02d:00",h)).toList();
    }
    public static List<String> dates(LocalDate today) {
        List<String> out = new ArrayList<>(List.of("dzis","jutro","codziennie",
            "dni:MONDAY","dni:TUESDAY","dni:WEDNESDAY","dni:THURSDAY",
            "dni:FRIDAY","dni:SATURDAY","dni:SUNDAY","dni:MONDAY,WEDNESDAY,FRIDAY"));
        for(int i=0;i<14;i++) out.add(today.plusDays(i).toString());
        return List.copyOf(out);
    }
}
