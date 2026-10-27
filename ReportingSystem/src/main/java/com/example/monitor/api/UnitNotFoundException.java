package com.example.monitor.api;

/** Thrown when a single unit is asked for and does not exist in unit_stats; becomes status 404. */
public class UnitNotFoundException extends RuntimeException {

    public UnitNotFoundException(long unitId) {
        super("Unit " + unitId + " not found");
    }
}
