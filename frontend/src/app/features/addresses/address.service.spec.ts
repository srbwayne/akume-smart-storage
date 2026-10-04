import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  Address,
  AddressLifecycleRequest,
  CreateAddressRequest,
  MoveAddressRequest,
  RenameAddressRequest,
} from './address.model';
import { AddressService } from './address.service';

const address: Address = {
  id: 'address-1',
  name: 'Office',
  addressTypeId: 'type-1',
  parentId: null,
  active: true,
  version: 0,
};

describe('AddressService', () => {
  let http: HttpTestingController;
  let service: AddressService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(AddressService);
  });

  afterEach(() => http.verify());

  it('creates a root without a parentId own-property', () => {
    const request: CreateAddressRequest = { name: 'Office', addressTypeId: 'type-1' };
    service.create(request).subscribe(result => expect(result).toEqual(address));

    const req = http.expectOne('/api/addresses');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ name: 'Office', addressTypeId: 'type-1' });
    expect(Object.hasOwn(req.request.body, 'parentId')).toBe(false);
    req.flush(address);
  });

  it('creates a child with its parent UUID', () => {
    service.create({ name: 'Cabinet', addressTypeId: 'type-1', parentId: 'parent-1' }).subscribe();

    const req = http.expectOne('/api/addresses');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ name: 'Cabinet', addressTypeId: 'type-1', parentId: 'parent-1' });
    req.flush({ ...address, name: 'Cabinet', parentId: 'parent-1' });
  });

  it('lists all Addresses', () => {
    service.list().subscribe(result => expect(result).toEqual([address]));
    const req = http.expectOne('/api/addresses');
    expect(req.request.method).toBe('GET');
    req.flush([address]);
  });

  it('lists root Addresses', () => {
    service.listRoots().subscribe(result => expect(result).toEqual([address]));
    const req = http.expectOne('/api/addresses/roots');
    expect(req.request.method).toBe('GET');
    req.flush([address]);
  });

  it('gets one Address using the encoded path ID', () => {
    service.get('address/1').subscribe(result => expect(result).toEqual(address));
    const req = http.expectOne('/api/addresses/address%2F1');
    expect(req.request.method).toBe('GET');
    req.flush(address);
  });

  it('lists direct children using the encoded parent ID', () => {
    service.listChildren('parent 1').subscribe(result => expect(result).toEqual([address]));
    const req = http.expectOne('/api/addresses/parent%201/children');
    expect(req.request.method).toBe('GET');
    req.flush([address]);
  });

  it('renames with the caller-provided expectedVersion', () => {
    const request: RenameAddressRequest = { name: 'Office 2', expectedVersion: 7 };
    service.rename('address-1', request).subscribe(result => expect(result.name).toBe('Office 2'));
    const req = http.expectOne('/api/addresses/address-1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ name: 'Office 2', expectedVersion: 7 });
    req.flush({ ...address, name: 'Office 2', version: 8 });
  });

  it('moves to a destination UUID with the caller-provided expectedVersion', () => {
    const request: MoveAddressRequest = { newParentId: 'destination-1', expectedVersion: 3 };
    service.move('address-1', request).subscribe(result => expect(result.parentId).toBe('destination-1'));
    const req = http.expectOne('/api/addresses/address-1/move');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ newParentId: 'destination-1', expectedVersion: 3 });
    req.flush({ ...address, parentId: 'destination-1', version: 4 });
  });

  it('moves to root with an explicit null newParentId own-property', () => {
    const request: MoveAddressRequest = { newParentId: null, expectedVersion: 5 };
    service.move('address-1', request).subscribe(result => expect(result.parentId).toBeNull());
    const req = http.expectOne('/api/addresses/address-1/move');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ newParentId: null, expectedVersion: 5 });
    expect(Object.hasOwn(req.request.body, 'newParentId')).toBe(true);
    req.flush({ ...address, version: 6 });
  });

  it('activates with the caller-provided expectedVersion', () => {
    const request: AddressLifecycleRequest = { expectedVersion: 9 };
    service.activate('address-1', request).subscribe(result => expect(result.active).toBe(true));
    const req = http.expectOne('/api/addresses/address-1/activate');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ expectedVersion: 9 });
    req.flush({ ...address, version: 10 });
  });

  it('deactivates with the caller-provided expectedVersion', () => {
    const request: AddressLifecycleRequest = { expectedVersion: 11 };
    service.deactivate('address-1', request).subscribe(result => expect(result.active).toBe(false));
    const req = http.expectOne('/api/addresses/address-1/deactivate');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ expectedVersion: 11 });
    req.flush({ ...address, active: false, version: 12 });
  });
});
