package com.tenantscope.domain;

public enum ServiceArea {
    API_GATEWAY("platform-api-team"),
    EDI_PROCESSING("edi-ops-team"),
    CONFIGURATION("config-support-team"),
    TRANSACTION_PROCESSING("transaction-team"),
    SCHEDULER("platform-api-team"),
    INVOICING("invoice-team");

    private final String defaultGroup;

    ServiceArea(String defaultGroup) {
        this.defaultGroup = defaultGroup;
    }

    public String defaultGroup() {
        return defaultGroup;
    }
}
