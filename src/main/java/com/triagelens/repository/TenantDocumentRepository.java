package com.triagelens.repository;

import com.triagelens.domain.DocumentStatus;
import com.triagelens.domain.TenantDocument;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TenantDocumentRepository extends JpaRepository<TenantDocument, Long> {

    interface StatusCount {
        String getDocType();
        DocumentStatus getStatus();
        long getTotal();
    }

    @Query("""
            select d.docType as docType, d.status as status, count(d) as total
            from TenantDocument d
            where d.tenantId = :tenantId and d.receivedAt >= :since
            group by d.docType, d.status
            order by d.docType, d.status
            """)
    List<StatusCount> countByTypeAndStatus(Long tenantId, Instant since);

    List<TenantDocument> findByTenantIdAndStatusInOrderByReceivedAtDesc(
            Long tenantId, List<DocumentStatus> statuses, Pageable pageable);
}
