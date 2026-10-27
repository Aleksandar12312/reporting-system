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
  /** Scanner and recycler status; always two entries, but find them by `component`, never by index. */
  issues: ComponentIssue[];
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
  /** Units with an ACTIVE or RECURRING problem, per component. */
  issues?: Record<IssueComponent, number>;
}

export interface DailyFaultRow {
  date: string;
  part: string;
  fault: string;
  faults: number;
}

// ---------- Scanner / recycler problems ----------

export type IssueComponent = 'SCANNER' | 'RECYCLER';
export type IssueStatus = 'OK' | 'ACTIVE' | 'RECURRING';
export type IssueMetric = 'REJECT_SHARE' | 'FAULTS_PER_DAY';

export const ISSUE_COMPONENTS: IssueComponent[] = ['SCANNER', 'RECYCLER'];

export interface ComponentIssue {
  component: IssueComponent;
  status: IssueStatus;
  metric: IssueMetric;
  value: number | null;
  modelMedian: number | null;
  ratio: number | null;
  sample: number;
  enoughData: boolean;
  /** UTC, no zone: pass through utcDate */
  countingSince: string;
  /** UTC, no zone */
  lastOkAt: string | null;
  lastOkBy: string | null;
  lastOkNote: string | null;
}

export interface AckRequest {
  by: string;
  note?: string | null;
}

export interface IssueAck {
  id: number;
  unitId: number;
  component: IssueComponent;
  ackedAt: string;
  ackedBy: string;
  note: string | null;
}
