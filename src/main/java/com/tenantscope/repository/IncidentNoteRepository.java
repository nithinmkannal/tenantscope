package com.tenantscope.repository;

import com.tenantscope.domain.IncidentNote;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentNoteRepository extends JpaRepository<IncidentNote, Long> {
    List<IncidentNote> findByIncidentIdOrderByCreatedAtAsc(Long incidentId);
}
