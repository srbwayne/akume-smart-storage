import { TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router, Routes } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';
import { App } from './app';

@Component({ standalone: true, template: '' })
class StartPage {}

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

  it('resolves /addresses to the lazy Address administration page', async () => {
    await TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter(routes)],
    }).compileComponents();
    const harness = await RouterTestingHarness.create();
    const page = await harness.navigateByUrl('/addresses');
    expect(page).toBeTruthy();
    TestBed.inject(HttpTestingController).expectOne('/api/addresses/roots').flush([]);
    harness.fixture.changeDetectorRef.detectChanges();
    expect((harness.routeNativeElement as HTMLElement).textContent).toContain('Endereços');
  });

  it('exposes Cadastros navigation and navigates to Address Types', async () => {
    const testRoutes: Routes = [{ path: 'start', component: StartPage }, ...routes];
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter(testRoutes)],
    }).compileComponents();
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/start');
    fixture.detectChanges();

    const shell = fixture.nativeElement as HTMLElement;
    const link = shell.querySelector('nav a[href="/address-types"]') as HTMLAnchorElement;
    const addressesLink = shell.querySelector('nav a[href="/addresses"]') as HTMLAnchorElement;
    expect(shell.querySelector('nav')?.textContent).toContain('Cadastros');
    expect(link.textContent).toContain('Tipos de endereço');
    expect(addressesLink.textContent).toContain('Endereços');
    expect(addressesLink.getAttribute('href')).toBe('/addresses');
    link.dispatchEvent(new MouseEvent('click', { button: 0, bubbles: true, cancelable: true }));
    await fixture.whenStable();
    fixture.detectChanges();

    expect(router.url).toBe('/address-types');
    TestBed.inject(HttpTestingController).expectOne('/api/address-types').flush([]);
  });
});
