import { ComponentIssue, UnitScore } from '../core/models';
import {
  chipLabel,
  daysSince,
  hasProblem,
  isCollecting,
  issueDetail,
  issueHint,
  issueOf,
  issueTitle,
  markedOkMessage,
  partHint,
  partsSeverity,
  problemsOf,
  sortByPart,
  sortByPartsFirst,
  statusLabel,
} from './issues';

function issue(partial: Partial<ComponentIssue>): ComponentIssue {
  return {
    component: 'SCANNER', status: 'OK', metric: 'REJECT_SHARE', value: 0.05, modelMedian: 0.08, ratio: 0.6,
    sample: 100, enoughData: true, countingSince: '2026-07-29T10:00:30', lastOkAt: null, lastOkBy: null, lastOkNote: null,
    ...partial,
  };
}

const unitWith = (issues: ComponentIssue[]) => ({ unitId: 1, issues }) as unknown as UnitScore;

describe('issue helpers', () => {
  const now = new Date('2026-10-27T12:00:00Z');

  it('finds entries by component, not by position', () => {
    const recycler = issue({ component: 'RECYCLER', metric: 'FAULTS_PER_DAY' });
    const scanner = issue({ component: 'SCANNER' });
    const u = unitWith([recycler, scanner]); // reversed order on purpose
    expect(issueOf(u, 'SCANNER')).toBe(scanner);
    expect(issueOf(u, 'RECYCLER')).toBe(recycler);
    expect(issueOf(null, 'SCANNER')).toBeNull();
  });

  it('lists only ACTIVE and RECURRING, scanner first', () => {
    const u = unitWith([
      issue({ component: 'RECYCLER', status: 'RECURRING' }),
      issue({ component: 'SCANNER', status: 'ACTIVE' }),
    ]);
    expect(problemsOf(u).map((i) => i.component)).toEqual(['SCANNER', 'RECYCLER']);
    expect(problemsOf(unitWith([issue({ status: 'OK' })]))).toEqual([]);
    expect(hasProblem(u, 'RECYCLER')).toBe(true);
  });

  it('copes with a unit without issues', () => {
    expect(problemsOf({ unitId: 1 } as UnitScore)).toEqual([]);
    expect(hasProblem({ unitId: 1 } as UnitScore, 'SCANNER')).toBe(false);
  });

  it('builds the ACTIVE titles', () => {
    expect(issueTitle(issue({ status: 'ACTIVE' }), now)).toBe('Something is wrong with the scanner.');
    expect(issueTitle(issue({ component: 'RECYCLER', status: 'ACTIVE' }), now)).toBe('Something is wrong with the recycler.');
  });

  it('builds the RECURRING title with whole days since lastOkAt', () => {
    const recurring = issue({ status: 'RECURRING', lastOkAt: '2026-10-22T08:00:00' });
    expect(issueTitle(recurring, now)).toBe('Something is wrong with the scanner again, 5 days after it was marked OK.');
    expect(issueTitle(issue({ status: 'RECURRING', lastOkAt: '2026-10-26T10:00:00' }), now)).toContain('again, 1 day after');
  });

  it('formats the scanner detail as whole percent', () => {
    expect(issueDetail(issue({ value: 0.3, modelMedian: 0.08 }))).toBe('Rejects 30% of notes (similar units: 8%).');
  });

  it('formats the recycler detail with two decimals', () => {
    const rc = issue({ component: 'RECYCLER', metric: 'FAULTS_PER_DAY', value: 0.12, modelMedian: 0.1 });
    expect(issueDetail(rc)).toBe('0.12 faults per day (similar units: 0.10).');
  });

  it('leaves out the brackets when the model median is unknown', () => {
    expect(issueDetail(issue({ value: 0.27, modelMedian: null }))).toBe('Rejects 27% of notes.');
  });

  it('counts whole days', () => {
    expect(daysSince('2026-10-25T12:00:01', now)).toBe(1);
    expect(daysSince(null, now)).toBeNull();
  });

  it('detects the quiet "collecting new data" case', () => {
    expect(isCollecting(issue({ status: 'OK', lastOkAt: '2026-10-27T10:00:00', enoughData: false }))).toBe(true);
    expect(isCollecting(issue({ status: 'OK', lastOkAt: null, enoughData: false }))).toBe(false);
    expect(isCollecting(issue({ status: 'ACTIVE', lastOkAt: '2026-10-27T10:00:00', enoughData: false }))).toBe(false);
  });

  it('has the confirmation and chip texts', () => {
    expect(markedOkMessage('SCANNER')).toBe('Scanner marked as OK.');
    expect(markedOkMessage('RECYCLER')).toBe('Recycler marked as OK.');
    expect(markedOkMessage('ALL')).toBe('All problems marked as OK.');
    expect(chipLabel(issue({ status: 'ACTIVE' }))).toBe('Scanner');
    expect(chipLabel(issue({ component: 'RECYCLER', status: 'RECURRING' }))).toBe('Recycler again');
  });

  it('ranks units by part severity: recurring, then number of problems', () => {
    const none = { ...unitWith([issue({ status: 'OK' })]), unitId: 1 };
    const oneActive = { ...unitWith([issue({ status: 'ACTIVE' })]), unitId: 2 };
    const twoActive = {
      ...unitWith([issue({ status: 'ACTIVE' }), issue({ component: 'RECYCLER', status: 'ACTIVE' })]),
      unitId: 3,
    };
    const recurring = { ...unitWith([issue({ component: 'RECYCLER', status: 'RECURRING' })]), unitId: 4 };
    expect(partsSeverity(none)).toBe(0);
    expect(partsSeverity(recurring)).toBeGreaterThan(partsSeverity(twoActive));
    expect(partsSeverity(twoActive)).toBeGreaterThan(partsSeverity(oneActive));
    expect(sortByPartsFirst([none, oneActive, twoActive, recurring]).map((u) => u.unitId)).toEqual([4, 3, 2, 1]);
  });

  it('keeps the incoming (tier) order for units with equal part severity', () => {
    const a = { ...unitWith([]), unitId: 7 };
    const b = { ...unitWith([]), unitId: 3 };
    expect(sortByPartsFirst([a, b]).map((u) => u.unitId)).toEqual([7, 3]);
  });

  it('orders one part by status, then ratio (nulls last), then value', () => {
    const u = (id: number, partial: Partial<ComponentIssue>) => ({ ...unitWith([issue(partial)]), unitId: id });
    const list = [
      u(1, { status: 'ACTIVE', ratio: 2 }),
      u(2, { status: 'OK', ratio: 9 }),
      u(3, { status: 'ACTIVE', ratio: null, value: 0.9 }),
      u(4, { status: 'RECURRING', ratio: 1 }),
      u(5, { status: 'ACTIVE', ratio: 2, value: 0.4 }),
    ];
    expect(sortByPart(list, 'SCANNER').map((x) => x.unitId)).toEqual([4, 5, 1, 3, 2]);
  });

  it('labels statuses', () => {
    expect(statusLabel(issue({ status: 'RECURRING' }))).toBe('Recurring');
    expect(statusLabel(issue({ status: 'ACTIVE' }))).toBe('Active');
    expect(statusLabel(null)).toBe('—');
  });

  it('suggests cleaning the scanner and checking the recycler', () => {
    expect(issueHint(issue({ status: 'ACTIVE' }))).toBe('The scanner may need cleaning.');
    expect(issueHint(issue({ component: 'RECYCLER', status: 'ACTIVE' }))).toBe('The recycler may need to be checked.');
    expect(issueHint(issue({ status: 'RECURRING' }))).toBe(
      'The scanner may need cleaning. The problem came back after the last fix.',
    );
    expect(partHint('SCANNER')).toContain('scanner cleaned');
    expect(partHint('RECYCLER')).toContain('recycler checked');
  });
});
