import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { UnitScore } from '../core/models';
import { UnitsApiService } from '../core/units-api.service';
import { ScoresPage, sortUnits } from './scores-page';

function unit(partial: Partial<UnitScore>): UnitScore {
  return {
    unitId: 1, serialNumber: 'SN-1', modelName: 'Pro', faults: 10, exposureDays: 90, ratePerDay: 0.1,
    modelMedianRate: 0.1, ratioToModel: 1, tier: 'OK', topPart: null, topPartFaults: 0, topPartShare: null, scope: null, issues: [],
    ...partial,
  };
}

describe('ScoresPage', () => {
  let api: { scores: ReturnType<typeof vi.fn>; problematic: ReturnType<typeof vi.fn>; worst: ReturnType<typeof vi.fn> };

  async function create(url: string) {
    const scannerProblem = {
      component: 'SCANNER', status: 'ACTIVE', metric: 'REJECT_SHARE', value: 0.3, modelMedian: 0.08, ratio: 3.75,
      sample: 600, enoughData: true, countingSince: '2026-07-29T10:00:30', lastOkAt: null, lastOkBy: null, lastOkNote: null,
    } as const;
    const req = (path: string) => ({
      url: path,
      result$: of([unit({ unitId: 1 }), unit({ unitId: 2, modelName: 'Lite', issues: [scannerProblem] })]),
    });
    api = {
      scores: vi.fn(() => req('/api/units/scores')),
      problematic: vi.fn(() => req('/api/units/problematic')),
      worst: vi.fn(() => req('/api/units/worst')),
    };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'units', component: ScoresPage }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: UnitsApiService, useValue: { ...api, summary: () => ({ url: '', result$: of(null) }) } },
      ],
    });
    await TestBed.inject(Router).navigateByUrl(url);
    const fixture = TestBed.createComponent(ScoresPage);
    await fixture.whenStable();
    return fixture;
  }

  it('defaults to Problematic', async () => {
    await create('/units');
    expect(api.problematic).toHaveBeenCalledWith(null);
    expect(api.scores).not.toHaveBeenCalled();
  });

  it('reads the mode and tier from the query string', async () => {
    await create('/units?tier=CRITICAL');
    expect(api.problematic).toHaveBeenCalledWith('CRITICAL');
  });

  it('calls /worst with the limit from the query string', async () => {
    await create('/units?mode=worst&limit=5');
    expect(api.worst).toHaveBeenCalledWith(5);
  });

  it('calls /scores for All units and filters by model on the client', async () => {
    const fixture = await create('/units?mode=scores&model=Lite');
    expect(api.scores).toHaveBeenCalled();
    const rows = (fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr');
    expect(rows.length).toBe(1);
  });
});

describe('ScoresPage – Parts', () => {
  it('shows a chip per problem and filters by part from the query string', async () => {
    const problem = {
      component: 'SCANNER', status: 'RECURRING', metric: 'REJECT_SHARE', value: 0.3, modelMedian: 0.08, ratio: 3.75,
      sample: 600, enoughData: true, countingSince: '2026-07-29T10:00:30', lastOkAt: null, lastOkBy: null, lastOkNote: null,
    } as const;
    const data = [unit({ unitId: 1 }), unit({ unitId: 2, issues: [problem] })];
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'units', component: ScoresPage }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: UnitsApiService,
          useValue: {
            scores: () => ({ url: '/api/units/scores', result$: of(data) }),
            summary: () => ({ url: '', result$: of(null) }),
          },
        },
      ],
    });
    await TestBed.inject(Router).navigateByUrl('/units?mode=scores&parts=SCANNER');
    const fixture = TestBed.createComponent(ScoresPage);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('tbody tr').length).toBe(1);
    expect(el.querySelector('.chip')?.textContent?.trim()).toBe('Scanner again');
    expect(el.querySelector('.chip')?.classList).toContain('chip-RECURRING');
  });
});

describe('ScoresPage – default order', () => {
  it('shows part problems first, then keeps the backend (tier) order', async () => {
    const active = {
      component: 'RECYCLER', status: 'ACTIVE', metric: 'FAULTS_PER_DAY', value: 0.5, modelMedian: 0.1, ratio: 5,
      sample: 20, enoughData: true, countingSince: '2026-07-29T10:00:30', lastOkAt: null, lastOkBy: null, lastOkNote: null,
    } as const;
    const data = [
      unit({ unitId: 1, tier: 'CRITICAL' }),
      unit({ unitId: 2, tier: 'WATCH' }),
      unit({ unitId: 3, tier: 'OK', issues: [active] }),
    ];
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'units', component: ScoresPage }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: UnitsApiService,
          useValue: {
            scores: () => ({ url: '/api/units/scores', result$: of(data) }),
            summary: () => ({ url: '', result$: of(null) }),
          },
        },
      ],
    });
    await TestBed.inject(Router).navigateByUrl('/units?mode=scores');
    const fixture = TestBed.createComponent(ScoresPage);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const ids = [...el.querySelectorAll('tbody tr td:first-child')].map((td) => td.textContent?.trim());
    expect(ids).toEqual(['3', '1', '2']);
    const headers = [...el.querySelectorAll('thead th')].map((th) => th.textContent?.trim().replace(/\s+/g, ' '));
    expect(headers.indexOf('Part problems')).toBeLessThan(headers.indexOf('Tier'));
  });
});

describe('sortUnits', () => {
  const units = [
    unit({ unitId: 1, ratioToModel: 2 }),
    unit({ unitId: 2, ratioToModel: null }),
    unit({ unitId: 3, ratioToModel: 5 }),
  ];

  it('sorts descending with nulls last', () => {
    expect(sortUnits(units, 'ratioToModel', 'desc').map((u) => u.unitId)).toEqual([3, 1, 2]);
  });

  it('sorts ascending with nulls still last', () => {
    expect(sortUnits(units, 'ratioToModel', 'asc').map((u) => u.unitId)).toEqual([1, 3, 2]);
  });

  it('sorts tiers by severity', () => {
    const list = [unit({ unitId: 1, tier: 'OK' }), unit({ unitId: 2, tier: 'CRITICAL' }), unit({ unitId: 3, tier: 'WATCH' })];
    expect(sortUnits(list, 'tier', 'asc').map((u) => u.unitId)).toEqual([2, 3, 1]);
  });
});
