import { Component } from '@angular/core';
import { PoButtonModule } from '@po-ui/ng-components';

@Component({
  selector: 'app-root',
  imports: [PoButtonModule],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly title = 'Akumé Smart Storage';
}
