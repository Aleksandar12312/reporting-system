package com.example.monitor.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Overview data that does not belong to one unit. Read-only.
 *
 * <ul>
 *   <li>GET /api/summary: tier counts, window days and the time of the last statistics run</li>
 *   <li>GET /api/models: one row per model (units, faults, median and mean rate)</li>
 *   <li>GET /api/fault-kinds: the fault kinds the system counts (event_source.category = 'FAULT')</li>
 *   <li>GET /api/components: the components that get their own message (ranking.components), in order</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class MetaController {

    private final UnitQueryService service;

    public MetaController(UnitQueryService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public Summary summary() {
        return service.getSummary();
    }

    @GetMapping("/models")
    public List<ModelStatsRow> models() {
        return service.getModels();
    }

    @GetMapping("/components")
    public List<ComponentCatalog.ComponentDefinition> components() {
        return service.getComponents();
    }

    @GetMapping("/fault-kinds")
    public List<String> faultKinds() {
        return service.getFaultKinds();
    }
}
