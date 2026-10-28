package com.triagelens.repository;

import com.triagelens.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {

    interface KeyCount {
        Object getKey();
        long getTotal();
    }

    Optional<Incident> findByNumber(String number);

    @Query(value = "select nextval('incident_number_seq')", nativeQuery = true)
    long nextNumber();

    long countByStateIn(List<IncidentState> states);

    @Query("select count(i) from Incident i where i.state in :states and i.priority in :priorities")
    long countOpenByPriorities(List<IncidentState> states, List<Priority> priorities);

    @Query("select count(i) from Incident i where i.state in :states and i.category = :category")
    long countOpenByCategory(List<IncidentState> states, IssueCategory category);

    @Query("select i.priority as key, count(i) as total from Incident i where i.state in :states group by i.priority")
    List<KeyCount> openByPriority(List<IncidentState> states);

    @Query("select i.service as key, count(i) as total from Incident i where i.state in :states group by i.service")
    List<KeyCount> openByService(List<IncidentState> states);

    @Query("select i.category as key, count(i) as total from Incident i where i.createdAt >= :since group by i.category")
    List<KeyCount> createdByCategory(Instant since);

    @Query("select i.state as key, count(i) as total from Incident i group by i.state")
    List<KeyCount> byState();

    @Query("select i.assignmentGroup as key, count(i) as total from Incident i where i.state in :states group by i.assignmentGroup")
    List<KeyCount> openByGroup(List<IncidentState> states);

    List<Incident> findByStateInOrderByCreatedAtAsc(List<IncidentState> states, Pageable pageable);

    List<Incident> findByStateInAndPriorityInOrderByCreatedAtDesc(
            List<IncidentState> states, List<Priority> priorities, Pageable pageable);

    List<Incident> findByStateInAndResolveDueAtBefore(List<IncidentState> states, Instant cutoff);

    List<Incident> findByCreatedAtGreaterThanEqual(Instant since);

    List<Incident> findByResolvedAtGreaterThanEqual(Instant since);

    long countByCreatedAtGreaterThanEqual(Instant since);

    long countByStateInAndCreatedAtBefore(List<IncidentState> states, Instant cutoff);
}
