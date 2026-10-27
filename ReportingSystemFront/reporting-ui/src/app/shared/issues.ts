import { ComponentIssue, ISSUE_COMPONENTS, IssueComponent, UnitScore } from '../core/models';
import { parseUtc } from './utc-date.pipe';

/**
 * Texts for the scanner / recycler messages (spec "Frontend changes — scanner and recycler messages").
 * The backend decides the status; nothing here compares a ratio with a threshold.
 */

export type AckTarget = IssueComponent | 'ALL';

const DAY_MS = 24 * 60 * 60 * 1000;

/** Finds the entry by component (never by index). */
export function issueOf(unit: UnitScore | null | undefined, component: IssueComponent): ComponentIssue | null {
  return unit?.issues?.find((i) => i.component === component) ?? null;
}

/** ACTIVE or RECURRING. */
export function isProblem(issue: ComponentIssue | null | undefined): issue is ComponentIssue {
  return issue?.status === 'ACTIVE' || issue?.status === 'RECURRING';
}

/** The unit's problem entries, scanner first. */
export function problemsOf(unit: UnitScore | null | undefined): ComponentIssue[] {
  return ISSUE_COMPONENTS.map((c) => issueOf(unit, c)).filter(isProblem);
}

export function hasProblem(unit: UnitScore, component: IssueComponent): boolean {
  return isProblem(issueOf(unit, component));
}

export function partName(component: IssueComponent): 'scanner' | 'recycler' {
  return component === 'SCANNER' ? 'scanner' : 'recycler';
}

export function partLabel(component: IssueComponent): 'Scanner' | 'Recycler' {
  return component === 'SCANNER' ? 'Scanner' : 'Recycler';
}

/** Whole days between a UTC time and now; null when the time is missing or invalid. */
export function daysSince(utc: string | null | undefined, now: Date = new Date()): number | null {
  const date = parseUtc(utc);
  if (!date) return null;
  return Math.max(0, Math.floor((now.getTime() - date.getTime()) / DAY_MS));
}

/** "Something is wrong with the scanner." / "... again, 5 days after it was marked OK." */
export function issueTitle(issue: ComponentIssue, now: Date = new Date()): string {
  const part = partName(issue.component);
  if (issue.status === 'RECURRING') {
    const days = daysSince(issue.lastOkAt, now);
    if (days === null) return `Something is wrong with the ${part} again.`;
    return `Something is wrong with the ${part} again, ${days} ${days === 1 ? 'day' : 'days'} after it was marked OK.`;
  }
  return `Something is wrong with the ${part}.`;
}

/** "Rejects 30% of notes (similar units: 8%)." / "0.12 faults per day (similar units: 0.10)." */
export function issueDetail(issue: ComponentIssue): string {
  if (issue.value === null) return 'No data yet.';
  const scanner = issue.metric === 'REJECT_SHARE';
  const fmt = (v: number) => (scanner ? `${Math.round(v * 100)}%` : v.toFixed(2));
  const similar = issue.modelMedian === null ? '' : ` (similar units: ${fmt(issue.modelMedian)})`;
  return scanner ? `Rejects ${fmt(issue.value)} of notes${similar}.` : `${fmt(issue.value)} faults per day${similar}.`;
}

/**
 * Suggestion shown with a problem: "may need", never a repair procedure.
 * A recurring problem adds that the last fix did not last.
 */
export function issueHint(issue: ComponentIssue): string {
  const base =
    issue.component === 'SCANNER' ? 'The scanner may need cleaning.' : 'The recycler may need to be checked.';
  return issue.status === 'RECURRING' ? `${base} The problem came back after the last fix.` : base;
}

/** Same suggestion for a list of units with a problem on one part. */
export function partHint(component: IssueComponent): string {
  return component === 'SCANNER'
    ? 'Units listed here may need their scanner cleaned.'
    : 'Units listed here may need their recycler checked.';
}

/** Status OK, marked OK before, and too little new data yet: show the quiet "collecting" note. */
export function isCollecting(issue: ComponentIssue | null | undefined): issue is ComponentIssue {
  return !!issue && issue.status === 'OK' && !!issue.lastOkAt && !issue.enoughData;
}

/** Confirmation shown after a successful "Mark as OK". */
export function markedOkMessage(target: AckTarget): string {
  if (target === 'ALL') return 'All problems marked as OK.';
  return `${partLabel(target)} marked as OK.`;
}

/** Chip text for the ranking table. */
export function chipLabel(issue: ComponentIssue): string {
  return issue.status === 'RECURRING' ? `${partLabel(issue.component)} again` : partLabel(issue.component);
}

// ---------- Ordering by part problems (the backend's status decides; no thresholds here) ----------

const STATUS_RANK: Record<string, number> = { RECURRING: 2, ACTIVE: 1, OK: 0 };

/** RECURRING 2, ACTIVE 1, OK / missing 0. */
export function statusRank(issue: ComponentIssue | null | undefined): number {
  return issue ? (STATUS_RANK[issue.status] ?? 0) : 0;
}

/**
 * How serious a unit's part problems are, for ordering: the worse status first,
 * then how many parts have a problem. 0 means no part problem.
 */
export function partsSeverity(unit: UnitScore): number {
  const ranks = ISSUE_COMPONENTS.map((c) => statusRank(issueOf(unit, c)));
  const worst = Math.max(...ranks);
  const count = ranks.filter((r) => r > 0).length;
  return worst * 10 + count;
}

/** Part problems first; units with equal severity keep the order they came in (the backend's tier order). */
export function sortByPartsFirst(units: UnitScore[]): UnitScore[] {
  return units
    .map((u, i) => ({ u, i, s: partsSeverity(u) }))
    .sort((a, b) => b.s - a.s || a.i - b.i)
    .map((x) => x.u);
}

/** For one part: RECURRING, then ACTIVE, then OK; inside a status the highest ratio, then value, first. */
export function sortByPart(units: UnitScore[], component: IssueComponent): UnitScore[] {
  const desc = (a: number | null | undefined, b: number | null | undefined) => {
    const na = a === null || a === undefined;
    const nb = b === null || b === undefined;
    if (na || nb) return na === nb ? 0 : na ? 1 : -1;
    return b - a;
  };
  return [...units].sort((a, b) => {
    const ia = issueOf(a, component);
    const ib = issueOf(b, component);
    return (
      statusRank(ib) - statusRank(ia) ||
      desc(ia?.ratio, ib?.ratio) ||
      desc(ia?.value, ib?.value) ||
      a.unitId - b.unitId
    );
  });
}

/** "Active" / "Recurring" / "OK" for tables. */
export function statusLabel(issue: ComponentIssue | null | undefined): string {
  switch (issue?.status) {
    case 'RECURRING':
      return 'Recurring';
    case 'ACTIVE':
      return 'Active';
    case 'OK':
      return 'OK';
    default:
      return '—';
  }
}
