export interface Address {
  id: string;
  name: string;
  addressTypeId: string;
  parentId: string | null;
  active: boolean;
  version: number;
}

export interface CreateAddressRequest {
  name: string;
  addressTypeId: string;
  parentId?: string;
}

export interface RenameAddressRequest {
  name: string;
  expectedVersion: number;
}

export interface MoveAddressRequest {
  newParentId: string | null;
  expectedVersion: number;
}

export interface AddressLifecycleRequest {
  expectedVersion: number;
}
