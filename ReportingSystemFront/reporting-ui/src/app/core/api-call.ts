import { HttpErrorResponse } from '@angular/common/http';
import { DestroyRef, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { ApiRequest } from './units-api.service';

/**
 * State of one backend call (data, loading, error, URL that was called).
 * Create it in a component field initializer – it cancels itself when the component is destroyed.
 * Starting a new run cancels the previous one, so a late answer never overwrites a newer one.
 */
export class ApiCall<T> {
  readonly data = signal<T | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  /** HTTP status of the last failed call (0 when not an HTTP error), null after success. */
  readonly errorStatus = signal<number | null>(null);
  readonly url = signal<string | null>(null);

  private sub?: Subscription;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.sub?.unsubscribe());
  }

  /** "GET" or "POST" of the last request (for developer mode). */
  readonly method = signal<'GET' | 'POST'>('GET');

  run(
    request: ApiRequest<T>,
    handlers: { onSuccess?: (value: T) => void; onError?: (err: unknown) => void } = {},
  ): void {
    this.sub?.unsubscribe();
    this.url.set(request.url);
    this.method.set(request.method ?? 'GET');
    this.loading.set(true);
    this.error.set(null);
    this.errorStatus.set(null);
    this.sub = request.result$.subscribe({
      next: (value) => {
        this.data.set(value);
        this.loading.set(false);
        handlers.onSuccess?.(value);
      },
      error: (err: unknown) => {
        this.data.set(null);
        this.error.set(toErrorMessage(err));
        this.errorStatus.set(err instanceof HttpErrorResponse ? err.status : 0);
        this.loading.set(false);
        handlers.onError?.(err);
      },
    });
  }

  /** Replaces the data with a value obtained elsewhere (e.g. from a POST answer) and clears errors. */
  setData(value: T | null): void {
    this.sub?.unsubscribe();
    this.loading.set(false);
    this.error.set(null);
    this.errorStatus.set(null);
    this.data.set(value);
  }

  /** Clears error and data without a request. */
  reset(): void {
    this.setData(null);
    this.url.set(null);
  }
}

export const MSG_UNREACHABLE = 'Backend is not reachable. Check that it is running on http://localhost:8080.';
export const MSG_MISSING_ENDPOINT =
  'This API endpoint does not exist on the backend. Backend and frontend versions may not match.';

/** Maps an HTTP error to the text shown to the user (spec section 7). */
export function toErrorMessage(err: unknown): string {
  if (!(err instanceof HttpErrorResponse)) {
    return 'Unexpected error.';
  }
  const backendMessage =
    err.error && typeof err.error === 'object' && 'error' in err.error
      ? String((err.error as { error: unknown }).error)
      : null;

  // 0 = no connection; 502/504 = the ng serve proxy could not reach the backend.
  if (err.status === 0 || err.status === 502 || err.status === 504) {
    return MSG_UNREACHABLE;
  }
  if (err.status === 400) {
    return backendMessage ?? 'Bad request (400).';
  }
  if (err.status === 404) {
    // Spring's own 404 for an unknown URL has the body {"error": "Not Found", ...}.
    // A real API answer (e.g. "Unit 17 not found") is shown as it is.
    const isSpringDefault = !backendMessage || backendMessage === 'Not Found';
    if (isSpringDefault && isApiUrl(err.url)) {
      return MSG_MISSING_ENDPOINT;
    }
    return `Not found (404)${backendMessage ? ': ' + backendMessage : ''}.`;
  }
  if (err.status === 503) {
    return 'Service is temporarily unavailable (503). Please try again later.';
  }
  if (err.status >= 500) {
    return `Server error (${err.status}). Please try again later.`;
  }
  return backendMessage ?? `Error ${err.status}.`;
}

function isApiUrl(url: string | null): boolean {
  if (!url) return true; // HttpClient leaves url empty in some error paths; all our calls are /api calls
  try {
    return new URL(url, 'http://localhost').pathname.startsWith('/api/');
  } catch {
    return url.includes('/api/');
  }
}
