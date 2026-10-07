import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CreateItemCategoryRequest,
  ItemCategory,
  ItemCategoryLifecycleRequest,
  RenameItemCategoryRequest,
} from './item-category.model';

@Injectable({ providedIn: 'root' })
export class ItemCategoryService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/item-categories';

  list(): Observable<ItemCategory[]> {
    return this.http.get<ItemCategory[]>(this.endpoint);
  }

  create(request: CreateItemCategoryRequest): Observable<ItemCategory> {
    return this.http.post<ItemCategory>(this.endpoint, request);
  }

  rename(id: string, request: RenameItemCategoryRequest): Observable<ItemCategory> {
    return this.http.put<ItemCategory>(`${this.endpoint}/${encodeURIComponent(id)}`, request);
  }

  activate(id: string, request: ItemCategoryLifecycleRequest): Observable<ItemCategory> {
    return this.http.post<ItemCategory>(`${this.endpoint}/${encodeURIComponent(id)}/activate`, request);
  }

  deactivate(id: string, request: ItemCategoryLifecycleRequest): Observable<ItemCategory> {
    return this.http.post<ItemCategory>(`${this.endpoint}/${encodeURIComponent(id)}/deactivate`, request);
  }
}
