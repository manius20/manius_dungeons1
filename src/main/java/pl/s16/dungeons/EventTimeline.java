package pl.s16.dungeons;

import java.time.*;
import java.util.*;

/** Pure time calculations. No Bukkit calls and no false world-state assumptions. */
public final class EventTimeline {
    private EventTimeline() {}
    public record Window(LocalDate day, ZonedDateTime start, ZonedDateTime end) {}

    public static Window window(DungeonSchedule schedule, LocalDate day, ZoneId zone) {
        if (!schedule.occursOn(day)) throw new IllegalArgumentException("Wydarzenie nie przypada na date " + day);
        ZonedDateTime start = schedule.begins(day, zone);
        return new Window(day, start, start.plusMinutes(schedule.durationMinutes));
    }

    /** Returns one of the requested milestones (minutes remaining), or empty.
     * Rounded up to the next minute so a once-per-minute ticker does not miss a notice.
     */
    public static OptionalInt notice(ZonedDateTime now, ZonedDateTime boundary, Collection<Integer> milestones) {
        long seconds = Duration.between(now.toInstant(), boundary.toInstant()).getSeconds();
        if (seconds <= 0) return OptionalInt.empty();
        long minutes = (seconds + 59L) / 60L;
        return minutes > Integer.MAX_VALUE || !milestones.contains((int)minutes)
                ? OptionalInt.empty() : OptionalInt.of((int)minutes);
    }

    /** Notice delivered when the scheduler crosses its deadline, even after lag.
     * Does not replay old messages at startup: previous should be the ticker's start instant.
     */
    public static List<Integer> crossedMilestones(Instant previous, Instant now,
                                                    Instant boundary, Collection<Integer> milestones) {
        if (previous == null || now.isBefore(previous) || !now.isBefore(boundary) || milestones == null)
            return List.of();
        return normalizeWarnings(new ArrayList<>(milestones)).stream()
            .filter(minutes -> {
                Instant milestone = boundary.minusSeconds(minutes * 60L);
                return previous.isBefore(milestone) && !now.isBefore(milestone);
            }).toList();
    }

    public static boolean finished(ZonedDateTime now, Window window) {
        return !now.toInstant().isBefore(window.end().toInstant());
    }
    public static List<Integer> normalizeWarnings(List<Integer> values) {
        if (values == null) return List.of();
        return values.stream().filter(Objects::nonNull).filter(v -> v >= 1 && v <= 720)
                .distinct().sorted(Comparator.reverseOrder()).toList();
    }
}
