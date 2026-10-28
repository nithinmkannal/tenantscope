package com.triagelens.service;

import com.triagelens.domain.*;
import com.triagelens.repository.IncidentRepository;
import com.triagelens.repository.IncidentRepository.KeyCount;
import com.triagelens.web.dto.DashboardSummary;
import com.triagelens.web.dto.DashboardSummary.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    static final List<IncidentState> OPEN = List.of(IncidentState.NEW, IncidentState.ACKNOWLEDGED, IncidentState.IN_PROGRESS);
    static final Duration AGING_AFTER = Duration.ofDays(3);

    private final IncidentRepository incidents;
    private final Clock clock;

    public DashboardService(IncidentRepository incidents, Clock clock) {
        this.incidents = incidents;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardSummary summary(int days) {
        Instant now = clock.instant();
        Instant since = now.minus(Duration.ofDays(days));

        long open = incidents.countByStateIn(OPEN);
        long urgent = incidents.countOpenByPriorities(OPEN, List.of(Priority.P1, Priority.P2));
        long breached = incidents.findByStateInAndResolveDueAtBefore(OPEN, now).size();
        long aging = incidents.countByStateInAndCreatedAtBefore(OPEN, now.minus(AGING_AFTER));

        List<Incident> recent = incidents.findByCreatedAtGreaterThanEqual(since);
        List<Incident> resolved = incidents.findByResolvedAtGreaterThanEqual(since);

        List<CriticalIncident> critical = incidents
                .findByStateInAndPriorityInOrderByCreatedAtDesc(OPEN, List.of(Priority.P1), PageRequest.of(0, 5))
                .stream()
                .map(i -> new CriticalIncident(i.getNumber(), i.getPriority().name(), i.getService().name(),
                        i.getTitle(), i.getState().name()))
                .toList();

        return new DashboardSummary(open, urgent, breached, aging,
                meanTimeToAcknowledgeMinutes(recent), meanTimeToResolveHours(resolved),
                incidents.countOpenByCategory(OPEN, IssueCategory.CONFIGURATION),
                incidents.countOpenByCategory(OPEN, IssueCategory.API_FAILURE),
                incidents.countOpenByCategory(OPEN, IssueCategory.PROCESSING_FAILURE),
                toMap(incidents.openByPriority(OPEN)),
                toMap(incidents.openByService(OPEN)),
                toMap(incidents.openByGroup(OPEN)),
                toMap(incidents.createdByCategory(since)),
                trend(recent, resolved, days, now),
                critical);
    }

    static Double meanTimeToAcknowledgeMinutes(List<Incident> list) {
        return average(list.stream().filter(i -> i.getAcknowledgedAt() != null)
                .mapToDouble(i -> Duration.between(i.getCreatedAt(), i.getAcknowledgedAt()).toSeconds() / 60.0));
    }

    static Double meanTimeToResolveHours(List<Incident> list) {
        return average(list.stream().filter(i -> i.getResolvedAt() != null)
                .mapToDouble(i -> Duration.between(i.getCreatedAt(), i.getResolvedAt()).toSeconds() / 3600.0));
    }

    private static Double average(java.util.stream.DoubleStream s) {
        OptionalDouble avg = s.average();
        return avg.isPresent() ? Math.round(avg.getAsDouble() * 100) / 100.0 : null;
    }

    static List<TrendPoint> trend(List<Incident> created, List<Incident> resolved, int days, Instant now) {
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        Map<LocalDate, Long> c = created.stream().collect(Collectors.groupingBy(
                i -> i.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate(), Collectors.counting()));
        Map<LocalDate, Long> r = resolved.stream().collect(Collectors.groupingBy(
                i -> i.getResolvedAt().atZone(ZoneOffset.UTC).toLocalDate(), Collectors.counting()));
        List<TrendPoint> out = new ArrayList<>();
        for (int d = days - 1; d >= 0; d--) {
            LocalDate day = today.minusDays(d);
            out.add(new TrendPoint(day, c.getOrDefault(day, 0L), r.getOrDefault(day, 0L)));
        }
        return out;
    }

    private static Map<String, Long> toMap(List<KeyCount> counts) {
        return counts.stream().sorted(Comparator.comparingLong(KeyCount::getTotal).reversed())
                .collect(Collectors.toMap(k -> String.valueOf(k.getKey()), KeyCount::getTotal,
                        (a, b) -> a, LinkedHashMap::new));
    }
}
