import { Component, input } from '@angular/core';
import { Tier } from '../core/models';

@Component({
  selector: 'app-tier-badge',
  template: `<span [class]="'badge tier-' + tier()">{{ tier() }}</span>`,
})
export class TierBadge {
  readonly tier = input.required<Tier>();
}
