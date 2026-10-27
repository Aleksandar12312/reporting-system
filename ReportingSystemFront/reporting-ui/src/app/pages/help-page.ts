import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MSG_MISSING_ENDPOINT, MSG_UNREACHABLE } from '../core/api-call';
import { AppMetaService } from '../core/app-meta.service';
import { TierBadge } from '../shared/tier-badge';

interface ErrorHelp {
  message: string;
  meaning: string;
  action: string;
}

/** Help – what the Reporting System shows, what every message means and what to do. */
@Component({
  selector: 'app-help-page',
  imports: [RouterLink, TierBadge],
  templateUrl: './help-page.html',
})
export class HelpPage {
  protected readonly meta = inject(AppMetaService);

  protected readonly sections = [
    { id: 'overview', title: 'What the Reporting System does' },
    { id: 'start', title: 'Where to start' },
    { id: 'part-problems', title: 'Scanner and recycler problems' },
    { id: 'mark-ok', title: 'Marking a part as OK' },
    { id: 'tiers', title: 'Tiers' },
    { id: 'numbers', title: 'Numbers and terms' },
    { id: 'pages', title: 'Pages' },
    { id: 'faults', title: 'Fault kinds' },
    { id: 'errors', title: 'Error messages' },
    { id: 'empty', title: 'Empty lists and missing parts' },
    { id: 'troubleshooting', title: 'Troubleshooting' },
  ];

  protected readonly errors: ErrorHelp[] = [
    {
      message: MSG_UNREACHABLE,
      meaning: 'The page cannot reach the backend at all: it is not running, still starting, or runs on another port.',
      action: 'Start the backend (port 8080) and wait until it has started, then press Refresh.',
    },
    {
      message: MSG_MISSING_ENDPOINT,
      meaning: 'The backend is running but does not know this address – usually an older backend version than the frontend.',
      action: 'Update and restart the backend. Turn on Developer mode to see which address was called.',
    },
    {
      message: 'Not found (404): Unit 42 not found.',
      meaning: 'There is no unit with this ID, or it has no statistics yet.',
      action: 'Check the unit ID. A brand-new unit appears after the statistics job has run (about every 5 minutes).',
    },
    {
      message: 'limit must be between 1 and 1000',
      meaning: 'A number field (Show worst, Show top, Newest events to load) is outside the allowed range.',
      action: 'Enter a number from 1 to 1000.',
    },
    {
      message: "Unknown fault 'XYZ' / Parameter 'fault' is required",
      meaning: 'The fault kind in the address or the selection is not one the backend knows.',
      action: 'Pick a fault kind from the list on the By fault kind page.',
    },
    {
      message: "Field 'by' is required (or another text from the Mark as OK dialog)",
      meaning: 'The name is missing or too long (max 100 characters), or the note is longer than 500 characters.',
      action: 'Fill in your name, shorten the note, and try again. The dialog stays open with your input.',
    },
    {
      message: 'Service is temporarily unavailable (503). Please try again later.',
      meaning: 'The backend runs, but its database cannot be reached.',
      action: 'Wait a moment and press Refresh. If it stays, check that MySQL is running.',
    },
    {
      message: 'Server error (500). Please try again later.',
      meaning: 'Something unexpected went wrong in the backend.',
      action: 'Try again. If it repeats, give the time and the page to the person who runs the backend (details are in its log).',
    },
    {
      message: 'Bad request (400).',
      meaning: 'The backend refused the request without saying why.',
      action: 'Check the values you entered; reload the page to start from a clean address.',
    },
    {
      message: 'Unexpected error.',
      meaning: 'A problem in the browser, not in the backend.',
      action: 'Reload the page. If it repeats, try another browser.',
    },
  ];
}
