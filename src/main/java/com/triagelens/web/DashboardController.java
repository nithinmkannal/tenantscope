package com.triagelens.web;

import com.triagelens.service.DashboardService;
import com.triagelens.web.dto.DashboardSummary;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public DashboardSummary summary(@RequestParam(defaultValue = "14") int days) {
        return service.summary(Math.min(Math.max(days, 1), 90));
    }
}
