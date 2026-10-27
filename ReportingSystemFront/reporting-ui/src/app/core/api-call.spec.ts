import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { ApiCall, MSG_MISSING_ENDPOINT, MSG_UNREACHABLE, toErrorMessage } from './api-call';

const httpError = (status: number, error: unknown = null, url = 'http://localhost:4200/api/units/scores') =>
  new HttpErrorResponse({ status, error, url });

describe('toErrorMessage', () => {
  it('reports an unreachable backend for 0, 502 and 504', () => {
    for (const status of [0, 502, 504]) {
      expect(toErrorMessage(httpError(status))).toBe(MSG_UNREACHABLE);
    }
  });

  it('shows the backend text for 400', () => {
    expect(toErrorMessage(httpError(400, { error: 'limit must be between 1 and 1000' }))).toBe(
      'limit must be between 1 and 1000',
    );
  });

  it('falls back to a generic 400 text', () => {
    expect(toErrorMessage(httpError(400))).toBe('Bad request (400).');
  });

  it('explains a Spring default 404 on an /api URL as a missing endpoint', () => {
    const springBody = { timestamp: 'x', status: 404, error: 'Not Found', path: '/api/units/scores' };
    expect(toErrorMessage(httpError(404, springBody))).toBe(MSG_MISSING_ENDPOINT);
  });

  it('shows the backend text for a real 404 answer', () => {
    expect(toErrorMessage(httpError(404, { error: 'Unit 17 not found' }, 'http://x/api/units/17'))).toBe(
      'Not found (404): Unit 17 not found.',
    );
  });

  it('keeps the plain 404 text outside /api', () => {
    expect(toErrorMessage(httpError(404, null, 'http://localhost:4200/assets/x.json'))).toBe('Not found (404).');
  });

  it('uses fixed texts for 503 and other 5xx', () => {
    expect(toErrorMessage(httpError(503, { error: 'Database is not available' }))).toBe(
      'Service is temporarily unavailable (503). Please try again later.',
    );
    expect(toErrorMessage(httpError(500, { error: 'NullPointerException at ...' }))).toBe(
      'Server error (500). Please try again later.',
    );
  });

  it('handles errors that are not HTTP errors', () => {
    expect(toErrorMessage(new Error('boom'))).toBe('Unexpected error.');
  });
});

describe('ApiCall', () => {
  const create = <T>() => TestBed.runInInjectionContext(() => new ApiCall<T>());

  it('sets loading, then data', () => {
    const call = create<number[]>();
    const subject = new Subject<number[]>();
    call.run({ url: '/api/x', result$: subject });
    expect(call.loading()).toBe(true);
    expect(call.url()).toBe('/api/x');
    subject.next([1, 2]);
    expect(call.loading()).toBe(false);
    expect(call.data()).toEqual([1, 2]);
    expect(call.error()).toBeNull();
  });

  it('stores the mapped error and clears old data', () => {
    const call = create<number[]>();
    call.run({ url: '/api/x', result$: of([1]) });
    call.run({ url: '/api/x', result$: throwError(() => httpError(400, { error: 'bad' })) });
    expect(call.data()).toBeNull();
    expect(call.error()).toBe('bad');
    expect(call.errorStatus()).toBe(400);
    expect(call.loading()).toBe(false);
  });

  it('cancels the previous request when a new one starts', () => {
    const call = create<string>();
    const first = new Subject<string>();
    const second = new Subject<string>();
    call.run({ url: '/api/1', result$: first });
    call.run({ url: '/api/2', result$: second });
    expect(first.observed).toBe(false);
    second.next('new');
    first.next('late'); // ignored – no longer subscribed
    expect(call.data()).toBe('new');
  });
});
