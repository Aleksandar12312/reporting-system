import { Routes } from '@angular/router';
import { HelpPage } from './pages/help-page';
import { ModelsPage } from './pages/models-page';
import { PartsPage } from './pages/parts-page';
import { ScoresPage } from './pages/scores-page';
import { TopByFaultPage } from './pages/top-by-fault-page';
import { UnitDetailPage } from './pages/unit-detail-page';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'parts' },
  { path: 'parts', component: PartsPage, title: 'Part problems' },
  { path: 'units', component: ScoresPage, title: 'Unit ranking' },
  { path: 'units/:id', component: UnitDetailPage, title: 'Unit' },
  { path: 'top-by-fault', component: TopByFaultPage, title: 'By fault kind' },
  { path: 'models', component: ModelsPage, title: 'Models' },
  { path: 'help', component: HelpPage, title: 'Help' },
  { path: '**', redirectTo: 'parts' },
];
