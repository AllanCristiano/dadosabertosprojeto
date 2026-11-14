import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: `
    <div class="aurora" aria-hidden="true"><i></i><i></i><i></i></div>
    <router-outlet />
  `,
})
export class App {}
