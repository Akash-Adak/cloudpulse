package com.cloudpulse.web;

import com.cloudpulse.service.DashboardService;
import com.cloudpulse.web.Dto.IncidentResponse;
import com.cloudpulse.web.Dto.SummaryResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/summary")
    public SummaryResponse summary() {
        return dashboard.summary();
    }

    /** @param status "active" (default) for open incidents only, or "all" for history */
    @GetMapping("/incidents")
    public List<IncidentResponse> incidents(@RequestParam(defaultValue = "all") String status,
                                            @RequestParam(defaultValue = "50") int limit) {
        return dashboard.incidents("active".equalsIgnoreCase(status), limit);
    }
}
