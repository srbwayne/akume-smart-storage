import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AddressType, CreateAddressType, UpdateAddressType } from './address-type.model';

@Injectable({ providedIn: 'root' })
export class AddressTypeService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/address-types';

  list(): Observable<AddressType[]> {
    return this.http.get<AddressType[]>(this.endpoint);
  }

  create(payload: CreateAddressType): Observable<AddressType> {
    return this.http.post<AddressType>(this.endpoint, payload);
  }

  update(id: string, payload: UpdateAddressType): Observable<AddressType> {
    return this.http.put<AddressType>(`${this.endpoint}/${encodeURIComponent(id)}`, payload);
  }

  activate(id: string): Observable<AddressType> {
    return this.http.post<AddressType>(`${this.endpoint}/${encodeURIComponent(id)}/activate`, {});
  }

  deactivate(id: string): Observable<AddressType> {
    return this.http.post<AddressType>(`${this.endpoint}/${encodeURIComponent(id)}/deactivate`, {});
  }
}
