export interface ItemCategory {
  id: string;
  name: string;
  active: boolean;
  version: number;
}

export interface CreateItemCategoryRequest {
  name: string;
}

export interface RenameItemCategoryRequest {
  name: string;
  expectedVersion: number;
}

export interface ItemCategoryLifecycleRequest {
  expectedVersion: number;
}
