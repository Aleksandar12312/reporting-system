import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { UnitsApiService } from './units-api.service';

describe('UnitsApiService', () => {
  let api: UnitsApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(UnitsApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const cases: [string, () => { url: string; result$: { subscribe: () => unknown } }, string][] = [
    ['scores', () => api.scores(), '/api/units/scores'],
    ['problematic without tier', () => api.problematic(null), '/api/units/problematic'],
    ['problematic with tier', () => api.problematic('CRITICAL'), '/api/units/problematic?tier=CRITICAL'],
    ['worst without limit', () => api.worst(null), '/api/units/worst'],
    ['worst with limit', () => api.worst(5), '/api/units/worst?limit=5'],
    ['top-by-fault', () => api.topByFault('JAMMED', 10), '/api/units/top-by-fault?fault=JAMMED&limit=10'],
    ['top-by-fault without limit', () => api.topByFault('FAIL'), '/api/units/top-by-fault?fault=FAIL'],
    ['unit faults', () => api.unitFaults(17), '/api/units/17/faults'],
    ['unit fault events', () => api.unitFaultEvents(17, 50), '/api/units/17/fault-events?limit=50'],
    ['unit events', () => api.unitEvents('17', null), '/api/units/17/events'],
    ['unit', () => api.unit(17), '/api/units/17'],
    ['fault kinds', () => api.faultKinds(), '/api/fault-kinds'],
    ['summary', () => api.summary(), '/api/summary'],
    ['models', () => api.models(), '/api/models'],
    ['faults daily', () => api.unitFaultsDaily(17, 90), '/api/units/17/faults/daily?days=90'],
  ];

  for (const [name, make, expected] of cases) {
    it(`builds the URL for ${name}`, () => {
      const req = make();
      expect(req.url).toBe(expected);
      req.result$.subscribe();
      const call = http.expectOne((r) => r.urlWithParams === expected);
      expect(call.request.method).toBe('GET');
      call.flush([]);
    });
  }

  it('leaves empty parameters out of the query string', () => {
    const req = api.problematic('' as never);
    expect(req.url).toBe('/api/units/problematic');
    req.result$.subscribe();
    http.expectOne('/api/units/problematic').flush([]);
  });

  it('encodes the unit id', () => {
    expect(api.unitEvents('a/b', 1).url).toBe('/api/units/a%2Fb/events?limit=1');
  });
});
