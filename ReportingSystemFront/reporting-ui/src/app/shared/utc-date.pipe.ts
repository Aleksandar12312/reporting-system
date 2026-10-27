import { Pipe, PipeTransform } from '@angular/core';

/**
 * Backend times are UTC without a zone suffix ("2026-10-26T12:00:48").
 * Turns them into a Date so the date pipe shows local time:
 *   {{ e.eventTime | utcDate | date: 'dd.MM.yyyy HH:mm:ss' }}
 */
@Pipe({ name: 'utcDate' })
export class UtcDatePipe implements PipeTransform {
  transform(value: string | null | undefined): Date | null {
    if (!value) {
      return null;
    }
    const hasZone = /([zZ]|[+-]\d{2}:?\d{2})$/.test(value);
    const date = new Date(hasZone ? value : value + 'Z');
    return isNaN(date.getTime()) ? null : date;
  }
}
