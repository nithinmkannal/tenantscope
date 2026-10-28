package com.tenantscope.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tenant_config")
public class TenantConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServiceArea service;
    @Column(name = "config_key", nullable = false)
    private String configKey;
    @Column(name = "config_value")
    private String configValue;
    @Column(nullable = false)
    private boolean sensitive;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected TenantConfig() {
    }

    public TenantConfig(Long tenantId, ServiceArea service, String configKey, String configValue, boolean sensitive) {
        this.tenantId = tenantId;
        this.service = service;
        this.configKey = configKey;
        this.configValue = configValue;
        this.sensitive = sensitive;
    }

    public Long getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public ServiceArea getService() { return service; }
    public String getConfigKey() { return configKey; }
    public String getConfigValue() { return configValue; }
    public boolean isSensitive() { return sensitive; }
    public Instant getUpdatedAt() { return updatedAt; }
}
