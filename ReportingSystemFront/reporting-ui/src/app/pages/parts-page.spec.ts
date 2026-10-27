import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { ComponentIssue, IssueComponent, IssueStatus, UnitScore } from '../core/models';
import { UnitsApiService } from '../core/units-api.service';
import { PartsPage } from './parts-page';

function issue(component: IssueComponent, status: IssueStatus, ratio: number | null, value = 0.2): ComponentIssue {
  return {
    component, status, ratio, value, metric: component === 'SCANNER' ? 'REJECT_SHARE' : 'FAULTS_PER_DAY',
    modelMedian: 0.08, sample: 100, enoughData: true, countingSince: '2026-07-29T10:00:30',
    lastOkAt: null, lastOkBy: null, lastOkNote: null,
  };
}

function unit(unitId: number, scanner: ComponentIssue, recycler: ComponentIssue, tier: UnitScore['tier'] = 'OK'): UnitScore {
  return {
    unitId, serialNumber: `SN-${unitId}`, modelName: unitId % 2 ? 'Pro' : 'Lite', faults: 10, exposureDays: 90,
    ratePerDay: 0.1, modelMedianRate: 0.1, ratioToModel: 1, tier, topPart: null, topPartFaults: 0,
    topPartShare: null, scope: null, issues: [recycler, scanner], // recycler first on purpose
  };
}

const data: UnitScore[] = [
  unit(1, issue('SCANNER', 'ACTIVE', 2.5), issue('RECYCLER', 'OK', 1), 'CRITICAL'),
  unit(2, issue('SCANNER', 'ACTIVE', 4.0), issue('RECYCLER', 'ACTIVE', 3)),
  unit(3, issue('SCANNER', 'RECURRING', null), issue('RECYCLER', 'OK', 1)),
  unit(4, issue('SCANNER', 'OK', 5.0), issue('RECYCLER', 'RECURRING', 2)),
];

describe('PartsPage', () => {
  let scores: ReturnType<typeof vi.fn>;

  async function create(url = '/parts') {
    scores = vi.fn(() => ({ url: '/api/units/scores', result$: of(data) }));
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'parts', component: PartsPage }]), { provide: UnitsApiService, useValue: { scores } }],
    });
    await TestBed.inject(Router).navigateByUrl(url);
    const fixture = TestBed.createComponent(PartsPage);
    await fixture.whenStable();
    return fixture;
  }

  const ids = (f: { nativeElement: HTMLElement }) =>
    [...f.nativeElement.querySelectorAll('tbody tr td:nth-child(2)')].map((td) => Number(td.textContent?.trim()));

  it('loads all units once from /scores', async () => {
    await create();
    expect(scores).toHaveBeenCalledTimes(1);
  });

  it('lists scanner problems: recurring first, then active by ratio (tier does not matter)', async () => {
    const fixture = await create();
    expect(ids(fixture)).toEqual([3, 2, 1]);
  });

  it('switches to the recycler from the query string', async () => {
    const fixture = await create('/parts?part=recycler');
    expect(ids(fixture)).toEqual([4, 2]);
  });

  it('shows all units with the toggle, problems still first', async () => {
    const fixture = await create('/parts?all=1');
    expect(ids(fixture)).toEqual([3, 2, 1, 4]);
  });

  it('counts units with a problem per part in the tabs', async () => {
    const fixture = await create();
    const counts = [...(fixture.nativeElement as HTMLElement).querySelectorAll('.part-tab-count')].map((e) =>
      e.textContent?.trim(),
    );
    expect(counts).toEqual(['3', '2']);
  });

  it('filters by model', async () => {
    const fixture = await create('/parts?model=Lite');
    expect(ids(fixture)).toEqual([2]);
  });
});
