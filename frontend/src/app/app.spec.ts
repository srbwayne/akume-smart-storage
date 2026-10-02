import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';

describe('Address Types route', () => {
  it('renders the feature page from the explicit route', async () => {
    await TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter(routes)],
    }).compileComponents();
    const harness = await RouterTestingHarness.create();
    const page = await harness.navigateByUrl('/address-types');
    expect(page).toBeTruthy();
    const request = TestBed.inject(HttpTestingController).expectOne('/api/address-types');
    request.flush([]);
    harness.fixture.changeDetectorRef.detectChanges();
    expect((harness.routeNativeElement as HTMLElement).textContent).toContain('Tipos de endereço');
    expect((harness.routeNativeElement as HTMLElement).textContent).toContain('Novo tipo de endereço');
  });
});
