package com.tenantscope.web;

import com.tenantscope.service.DiagnosticsService;
import com.tenantscope.web.dto.DiagnosticsReport;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/diagnostics")
public class DiagnosticsController {

    private final DiagnosticsService service;

    public DiagnosticsController(DiagnosticsService service) {
        this.service = service;
    }

    /** Configuration export + processing health for a tenant. */
    @GetMapping("/tenants/{tenantCode}")
    public DiagnosticsReport tenant(@PathVariable String tenantCode,
                                    @RequestParam(defaultValue = "7") int windowDays) {
        return service.forTenant(tenantCode, clamp(windowDays));
    }

    /** Same report, resolved from an incident; a summary is appended to the incident timeline. */
    @PostMapping("/incidents/{number}")
    public DiagnosticsReport incident(@PathVariable String number,
                                      @RequestParam(defaultValue = "7") int windowDays) {
        return service.forIncident(number, clamp(windowDays));
    }

    private static int clamp(int days) {
        return Math.min(Math.max(days, 1), 90);
    }
}
