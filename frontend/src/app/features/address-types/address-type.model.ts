export interface AddressType {
  id: string;
  code: string;
  name: string;
  description: string | null;
  active: boolean;
}

export interface CreateAddressType {
  code: string;
  name: string;
  description?: string | null;
}

export interface UpdateAddressType {
  name: string;
  description?: string | null;
}
