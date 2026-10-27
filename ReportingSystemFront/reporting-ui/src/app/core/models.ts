export type Tier = 'CRITICAL' | 'WATCH' | 'OK' | 'NO_BASELINE';
export type ProblemScope = 'PART' | 'GENERAL';

export const TIERS: Tier[] = ['CRITICAL', 'WATCH', 'OK', 'NO_BASELINE'];

/**
 * The 12 fault kinds known to the backend. Used only as a fallback while
 * GET /api/fault-kinds is not available; the backend list always wins.
 */
export const FAULT_KINDS: readonly string[] = [
  'ERROR_DURING_PAYOUT',
  'COIN_MECH_ERROR',
  'COIN_MECH_JAMMED',
  'PAYOUT_OUT_OF_SERVICE',
  'JAMMED',
  'TIME_OUT',
  'INCOMPLETE_PAYOUT',
  'INCOMPLETE_FLOAT',
  'UNSAFE_NOTE_JAM',
  'SAFE_NOTE_JAM',
  'SOFTWARE_ERROR',
  'FAIL',
];

/** Window length used in texts while GET /api/summary is not available. */
export const DEFAULT_WINDOW_DAYS = 90;

export interface UnitScore {
  unitId: number;
  serialNumber: string;
  modelName: string;
  faults: number;
  exposureDays: number;
  ratePerDay: number;
  modelMedianRate: number | null;
  ratioToModel: number | null;
  tier: Tier;
  topPart: string | null;
  topPartFaults: number;
  topPartShare: number | null;
  scope: ProblemScope | null;
}

export interface FaultRanking {
  unitId: number;
  serialNumber: string;
  modelName: string;
  fault: string;
  faults: number;
  exposureDays: number;
  ratePerDay: number;
}

export interface FaultBreakdown {
  unitId: number;
  part: string;
  fault: string;
  faults: number;
  pctOfUnitFaults: number;
}

export interface UnitEvent {
  id: number;
  unitId: number;
  eventTime: string;
  type: string | null;
  part: string | null;
  source: string | null;
}

// ---------- Optional endpoints (section 4.2) ----------

export interface ModelStats {
  unitTypeId: number;
  modelName: string;
  windowDays: number;
  unitCount: number;
  baselineUnitCount: number;
  faultsInWindow: number;
  medianRatePerDay: number | null;
  meanRatePerDay: number | null;
  computedAt: string;
}

export interface Summary {
  computedAt: string;
  windowDays: number;
  unitCount: number;
  tiers: Record<Tier, number>;
}

export interface DailyFaultRow {
  date: string;
  part: string;
  fault: string;
  faults: number;
}
