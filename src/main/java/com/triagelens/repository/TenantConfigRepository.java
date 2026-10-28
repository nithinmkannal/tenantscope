package com.triagelens.repository;

import com.triagelens.domain.TenantConfig;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantConfigRepository extends JpaRepository<TenantConfig, Long> {
    List<TenantConfig> findByTenantIdOrderByServiceAscConfigKeyAsc(Long tenantId);
}
