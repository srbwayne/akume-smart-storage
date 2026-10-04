import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Address,
  AddressLifecycleRequest,
  CreateAddressRequest,
  MoveAddressRequest,
  RenameAddressRequest,
} from './address.model';

@Injectable({ providedIn: 'root' })
export class AddressService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/addresses';

  create(request: CreateAddressRequest): Observable<Address> {
    return this.http.post<Address>(this.endpoint, request);
  }

  list(): Observable<Address[]> {
    return this.http.get<Address[]>(this.endpoint);
  }

  listRoots(): Observable<Address[]> {
    return this.http.get<Address[]>(`${this.endpoint}/roots`);
  }

  get(id: string): Observable<Address> {
    return this.http.get<Address>(`${this.endpoint}/${encodeURIComponent(id)}`);
  }

  listChildren(id: string): Observable<Address[]> {
    return this.http.get<Address[]>(`${this.endpoint}/${encodeURIComponent(id)}/children`);
  }

  rename(id: string, request: RenameAddressRequest): Observable<Address> {
    return this.http.put<Address>(`${this.endpoint}/${encodeURIComponent(id)}`, request);
  }

  move(id: string, request: MoveAddressRequest): Observable<Address> {
    return this.http.post<Address>(`${this.endpoint}/${encodeURIComponent(id)}/move`, request);
  }

  activate(id: string, request: AddressLifecycleRequest): Observable<Address> {
    return this.http.post<Address>(`${this.endpoint}/${encodeURIComponent(id)}/activate`, request);
  }

  deactivate(id: string, request: AddressLifecycleRequest): Observable<Address> {
    return this.http.post<Address>(`${this.endpoint}/${encodeURIComponent(id)}/deactivate`, request);
  }
}
