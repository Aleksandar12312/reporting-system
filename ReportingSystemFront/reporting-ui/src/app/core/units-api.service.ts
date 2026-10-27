import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AckRequest,
  DailyFaultRow,
  FaultBreakdown,
  FaultRanking,
  IssueAck,
  IssueComponent,
  ModelStats,
  Summary,
  Tier,
  UnitEvent,
  UnitScore,
} from './models';

/**
 * Relative base URLs. In development, `ng serve` forwards /api to
 * http://localhost:8080 (see proxy.conf.json), so the backend needs no CORS setup.
 */
export const API_ROOT = '/api';
export const API_BASE = `${API_ROOT}/units`;

/** A prepared request: the URL that will be called plus the observable that calls it. */
export interface ApiRequest<T> {
  url: string;
  result$: Observable<T>;
  /** HTTP method, shown in developer mode; GET when left out. */
  method?: 'GET' | 'POST';
}

type Params = Record<string, string | number | null | undefined>;

@Injectable({ providedIn: 'root' })
export class UnitsApiService {
  private readonly http = inject(HttpClient);

  // ---------- Required endpoints (section 4.1) ----------

  /** GET /api/units/scores – all units, highest ratio first */
  scores(): ApiRequest<UnitScore[]> {
    return this.get<UnitScore[]>(`${API_BASE}/scores`);
  }

  /** GET /api/units/problematic?tier= – only WATCH and CRITICAL units */
  problematic(tier?: Tier | null): ApiRequest<UnitScore[]> {
    return this.get<UnitScore[]>(`${API_BASE}/problematic`, { tier });
  }

  /** GET /api/units/worst?limit= – the N worst units */
  worst(limit?: number | null): ApiRequest<UnitScore[]> {
    return this.get<UnitScore[]>(`${API_BASE}/worst`, { limit });
  }

  /** GET /api/units/top-by-fault?fault=&limit= – units with the most faults of one kind */
  topByFault(fault: string, limit?: number | null): ApiRequest<FaultRanking[]> {
    return this.get<FaultRanking[]>(`${API_BASE}/top-by-fault`, { fault, limit });
  }

  /** GET /api/units/{unitId}/faults – faults grouped by component and kind */
  unitFaults(unitId: number | string): ApiRequest<FaultBreakdown[]> {
    return this.get<FaultBreakdown[]>(`${API_BASE}/${encodeURIComponent(unitId)}/faults`);
  }

  /** GET /api/units/{unitId}/fault-events?limit= – newest fault events */
  unitFaultEvents(unitId: number | string, limit?: number | null): ApiRequest<UnitEvent[]> {
    return this.get<UnitEvent[]>(`${API_BASE}/${encodeURIComponent(unitId)}/fault-events`, { limit });
  }

  /** GET /api/units/{unitId}/events?limit= – newest events of every kind */
  unitEvents(unitId: number | string, limit?: number | null): ApiRequest<UnitEvent[]> {
    return this.get<UnitEvent[]>(`${API_BASE}/${encodeURIComponent(unitId)}/events`, { limit });
  }

  // ---------- Optional endpoints (section 4.2) ----------

  /** GET /api/units/{unitId} – one unit, 404 when unknown */
  unit(unitId: number | string): ApiRequest<UnitScore> {
    return this.get<UnitScore>(`${API_BASE}/${encodeURIComponent(unitId)}`);
  }

  /** GET /api/fault-kinds – the fault kinds known to the backend */
  faultKinds(): ApiRequest<string[]> {
    return this.get<string[]>(`${API_ROOT}/fault-kinds`);
  }

  /** GET /api/summary – window length, tier counts, last computation time */
  summary(): ApiRequest<Summary> {
    return this.get<Summary>(`${API_ROOT}/summary`);
  }

  /** GET /api/models – per-model statistics */
  models(): ApiRequest<ModelStats[]> {
    return this.get<ModelStats[]>(`${API_ROOT}/models`);
  }

  /** GET /api/units/{unitId}/faults/daily?days= – faults per day (for a future trend chart) */
  unitFaultsDaily(unitId: number | string, days?: number | null): ApiRequest<DailyFaultRow[]> {
    return this.get<DailyFaultRow[]>(`${API_BASE}/${encodeURIComponent(unitId)}/faults/daily`, { days });
  }

  // ---------- Scanner / recycler problems ----------

  /** POST /api/units/{unitId}/issues/{component}/ack – mark one component as OK */
  markOk(unitId: number | string, component: IssueComponent, body: AckRequest): ApiRequest<UnitScore> {
    return this.post<UnitScore>(`${API_BASE}/${encodeURIComponent(unitId)}/issues/${component}/ack`, body);
  }

  /** POST /api/units/{unitId}/issues/ack – mark every ACTIVE or RECURRING component as OK */
  markAllOk(unitId: number | string, body: AckRequest): ApiRequest<UnitScore> {
    return this.post<UnitScore>(`${API_BASE}/${encodeURIComponent(unitId)}/issues/ack`, body);
  }

  /** GET /api/units/{unitId}/issues/history?limit= – "marked OK" history, newest first */
  ackHistory(unitId: number | string, limit?: number | null): ApiRequest<IssueAck[]> {
    return this.get<IssueAck[]>(`${API_BASE}/${encodeURIComponent(unitId)}/issues/history`, { limit });
  }

  private post<T>(path: string, body: unknown): ApiRequest<T> {
    return {
      url: path,
      method: 'POST',
      result$: this.http.post<T>(path, body, { headers: { 'Content-Type': 'application/json' } }),
    };
  }

  private get<T>(path: string, params: Params = {}): ApiRequest<T> {
    let httpParams = new HttpParams();
    for (const [key, value] of Object.entries(params)) {
      if (value !== null && value !== undefined && value !== '') {
        httpParams = httpParams.set(key, String(value));
      }
    }
    const query = httpParams.toString();
    const url = `${path}${query ? '?' + query : ''}`;
    return { url, result$: this.http.get<T>(path, { params: httpParams }) };
  }
}
