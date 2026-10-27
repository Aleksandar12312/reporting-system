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
    modelMedianRate: 0.1, ratioToModel: 1, tier: 'OK', topPart: null, topPartFaults: 0, topPartShare: null, scope: null,
    ...partial,
  };
}

describe('ScoresPage', () => {
  let api: { scores: ReturnType<typeof vi.fn>; problematic: ReturnType<typeof vi.fn>; worst: ReturnType<typeof vi.fn> };

  async function create(url: string) {
    const req = (path: string) => ({ url: path, result$: of([unit({ unitId: 1 }), unit({ unitId: 2, modelName: 'Lite' })]) });
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
