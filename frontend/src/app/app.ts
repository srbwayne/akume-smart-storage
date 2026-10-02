import { Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { PoButtonModule } from '@po-ui/ng-components';

@Component({
  selector: 'app-root',
  imports: [PoButtonModule, RouterLink, RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly title = 'Akumé Smart Storage';
}
