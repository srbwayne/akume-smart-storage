import { Routes } from '@angular/router';
import { AddressTypeList } from './features/address-types/address-type-list/address-type-list';

export const routes: Routes = [
  { path: 'addresses', loadComponent: () => import('./features/addresses/address-administration').then(page => page.AddressAdministration) },
  { path: 'address-types', component: AddressTypeList },
  { path: '', pathMatch: 'full', redirectTo: 'address-types' },
  { path: '**', redirectTo: 'address-types' },
];
