package com.example.monitor.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST access to the unit statistics. Read-only. The numbers are as fresh as the last run of the
 * statistics job (every 5 minutes); the event lists are read live.
 *
 * <ul>
 *   <li>GET /api/units/scores: every unit, highest ratio first</li>
 *   <li>GET /api/units/problematic: WATCH and CRITICAL units; optional ?tier=CRITICAL or ?tier=WATCH</li>
 *   <li>GET /api/units/worst?limit=10: the worst units, CRITICAL first</li>
 *   <li>GET /api/units/top-by-fault?fault=JAMMED&amp;limit=10: units with the most faults of one kind</li>
 *   <li>GET /api/units/{unitId}: one unit (404 if it is unknown)</li>
 *   <li>GET /api/units/{unitId}/faults: faults of one unit by component and fault name</li>
 *   <li>GET /api/units/{unitId}/fault-events?limit=100: newest fault events of one unit</li>
 *   <li>GET /api/units/{unitId}/events?limit=100: newest events of one unit, of every kind</li>
 * </ul>
 *
 * A wrong parameter (unknown tier or fault, limit outside 1..1000, text instead of a number) gives status 400;
 * see {@link GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/units")
public class UnitsController {

    private final UnitQueryService service;

    public UnitsController(UnitQueryService service) {
        this.service = service;
    }

    @GetMapping("/scores")
    public List<UnitScore> scores() {
        return service.scoreAllUnits();
    }

    @GetMapping("/problematic")
    public List<UnitScore> problematic(@RequestParam(required = false) Tier tier) {
        List<UnitScore> flagged = service.findProblematicUnits();
        if (tier == null) {
            return flagged;
        }
        return flagged.stream().filter(score -> score.tier() == tier).toList();
    }

    @GetMapping("/worst")
    public List<UnitScore> worst(@RequestParam(defaultValue = "10") int limit) {
        return service.getWorstUnits(limit);
    }

    @GetMapping("/top-by-fault")
    public List<FaultRanking> topByFault(@RequestParam String fault, @RequestParam(defaultValue = "10") int limit) {
        return service.getTopUnitsByFault(fault, limit);
    }

    @GetMapping("/{unitId}")
    public UnitScore unit(@PathVariable long unitId) {
        return service.getUnitScore(unitId);
    }

    @GetMapping("/{unitId}/faults")
    public List<FaultBreakdown> faults(@PathVariable long unitId) {
        return service.getFaultBreakdown(unitId);
    }

    @GetMapping("/{unitId}/fault-events")
    public List<UnitEvent> faultEvents(@PathVariable long unitId, @RequestParam(defaultValue = "100") int limit) {
        return service.getUnitFaultEvents(unitId, limit);
    }

    @GetMapping("/{unitId}/events")
    public List<UnitEvent> events(@PathVariable long unitId, @RequestParam(defaultValue = "100") int limit) {
        return service.getUnitEvents(unitId, limit);
    }
}
