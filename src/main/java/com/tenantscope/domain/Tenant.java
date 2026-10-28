package com.tenantscope.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tenant")
public class Tenant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String code;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String tier;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Tenant() {
    }

    public Tenant(String code, String name, String tier) {
        this.code = code;
        this.name = name;
        this.tier = tier;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getTier() { return tier; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
}
