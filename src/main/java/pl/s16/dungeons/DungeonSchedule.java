package pl.s16.dungeons;

import java.time.*;
import java.util.*;

/** Zatwierdzony przez administratora termin dungeona. */
public final class DungeonSchedule {
    public static final Set<String> THEMES = Set.of("mechowy", "siarkowy", "pustynny", "mesa");
    public final String id;
    public final String theme;
    public final LocalDate date; // null = cykliczny
    public final EnumSet<DayOfWeek> days; // puste dla jednorazowego
    public final LocalTime start;
    public final int durationMinutes;
    public final int rooms; // zero = losowo 15-50

    public DungeonSchedule(String id, String theme, LocalDate date, Set<DayOfWeek> days,
                           LocalTime start, int durationMinutes, int rooms) {
        if (!id.matches("[a-zA-Z0-9_-]{1,32}")) throw new IllegalArgumentException("ID: 1-32 znakow a-z, 0-9, _ lub -");
        if (!THEMES.contains(theme)) throw new IllegalArgumentException("Nieznany motyw: " + theme);
        if ((date == null) == (days == null || days.isEmpty())) throw new IllegalArgumentException("Podaj date albo dni tygodnia");
        if (durationMinutes < 10 || durationMinutes > 720) throw new IllegalArgumentException("Czas trwania: 10-720 minut");
        if (rooms != 0 && (rooms < 15 || rooms > 100)) throw new IllegalArgumentException("Pokoje: losowo lub 15-100");
        this.id = id; this.theme = theme; this.date = date;
        this.days = days == null || days.isEmpty() ? EnumSet.noneOf(DayOfWeek.class) : EnumSet.copyOf(days);
        this.start = Objects.requireNonNull(start); this.durationMinutes = durationMinutes; this.rooms = rooms;
    }
    public boolean occursOn(LocalDate d) { return date != null ? date.equals(d) : days.contains(d.getDayOfWeek()); }
    public ZonedDateTime begins(LocalDate d, ZoneId zone) { return ZonedDateTime.of(d, start, zone); }
    public String recurrence() { return date == null ? "dni:" + String.join(",", days.stream().map(Enum::name).toList()) : date.toString(); }
    public String summary() { return id + " | " + theme + " | " + recurrence() + " " + start + " | " + durationMinutes + " min | " + (rooms == 0 ? "15-50 losowo" : rooms) + " pokoi"; }
}
