import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AppMetaService } from './core/app-meta.service';
import { DevModeService } from './core/dev-mode.service';
import { UtcDatePipe } from './shared/utc-date.pipe';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule, DatePipe, UtcDatePipe],
  templateUrl: './app.html',
})
export class App {
  private readonly router = inject(Router);
  protected readonly meta = inject(AppMetaService);
  protected readonly dev = inject(DevModeService);

  protected readonly unitId = signal<number | null>(null);

  protected openUnit(): void {
    const id = this.unitId();
    if (id !== null && id !== undefined && String(id) !== '') {
      this.router.navigate(['/units', id]);
    }
  }
}
