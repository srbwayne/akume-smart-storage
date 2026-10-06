import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { PoTreeViewComponent } from '@po-ui/ng-components';
import { TestBed } from '@angular/core/testing';
import { Address } from './address.model';
import { AddressAdministration, AddressTreeItem } from './address-administration';

function address(
  id: string,
  name: string,
  parentId: string | null,
  active = true,
  version = 0,
): Address {
  return { id, name, addressTypeId: 'type-1', parentId, active, version };
}

describe('Address administration acceptance', () => {
  let http: HttpTestingController;
  let fixture: ReturnType<typeof TestBed.createComponent<AddressAdministration>>;
  let page: AddressAdministration;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AddressAdministration],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AddressAdministration);
    page = fixture.componentInstance;
    fixture.changeDetectorRef.detectChanges();
    http.expectOne('/api/addresses/roots').flush([]);
    fixture.changeDetectorRef.detectChanges();
  });

  afterEach(() => http.verify());

  function findItem(id: string, items: AddressTreeItem[] = page.treeItems): AddressTreeItem | undefined {
    for (const item of items) {
      if (item.addressId === id) return item;
      const nested = findItem(id, (item.subItems ?? []) as AddressTreeItem[]);
      if (nested) return nested;
    }
    return undefined;
  }

  function expandAndLoad(parent: Address, children: Address[]): void {
    page.onExpanded({ ...findItem(parent.id)!, expanded: true });
    const request = http.expectOne(`/api/addresses/${parent.id}/children`);
    expect(request.request.method).toBe('GET');
    request.flush(children);
    fixture.changeDetectorRef.detectChanges();
  }

  function select(id: string): void {
    const tree = fixture.debugElement.query(By.css('po-tree-view')).componentInstance as PoTreeViewComponent;
    const item = findItem(id);
    expect(item).toBeDefined();
    tree.selected.emit(item!);
    fixture.changeDetectorRef.detectChanges();
  }

  it('carries a created Address through rename, move, inactive move, and activation without a page reload', () => {
    const firstParent = address('first-parent', 'First parent', null);
    const secondParent = address('second-parent', 'Second parent', null);
    page.loadRoots();
    http.expectOne('/api/addresses/roots').flush([firstParent, secondParent]);
    fixture.changeDetectorRef.detectChanges();
    expandAndLoad(firstParent, []);
    expandAndLoad(secondParent, []);

    page.startCreate();
    http.expectOne('/api/address-types').flush([
      { id: 'type-1', code: 'ROOM', name: 'Room', description: null, active: true },
    ]);
    http.expectOne('/api/addresses').flush([firstParent, secondParent]);
    page.createName = 'Created address';
    page.createAddressTypeId = 'type-1';
    page.createParentId = firstParent.id;
    page.saveCreate();

    const create = http.expectOne('/api/addresses');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual({ name: 'Created address', addressTypeId: 'type-1', parentId: firstParent.id });
    const created = address('created', 'Created address', firstParent.id, true, 3);
    create.flush(created);
    const afterCreate = http.expectOne(`/api/addresses/${firstParent.id}/children`);
    afterCreate.flush([created]);
    fixture.changeDetectorRef.detectChanges();

    select(created.id);
    expect(page.selectedAddress).toEqual(created);
    page.startRename();
    page.renameName = 'Renamed address';
    page.saveRename();
    const rename = http.expectOne(`/api/addresses/${created.id}`);
    expect(rename.request.method).toBe('PUT');
    expect(rename.request.body).toEqual({ name: 'Renamed address', expectedVersion: 3 });
    const renamed = address(created.id, 'Renamed address', firstParent.id, true, 14);
    rename.flush(renamed);
    fixture.changeDetectorRef.detectChanges();
    expect(page.selectedAddress).toEqual(renamed);

    page.startMove();
    http.expectOne('/api/addresses').flush([firstParent, secondParent, renamed]);
    page.moveDestinationId = secondParent.id;
    page.saveMove();
    const firstMove = http.expectOne(`/api/addresses/${created.id}/move`);
    expect(firstMove.request.body).toEqual({ newParentId: secondParent.id, expectedVersion: 14 });
    const moved = address(created.id, 'Renamed address', secondParent.id, true, 27);
    firstMove.flush(moved);
    const afterFirstMoveOldParent = http.expectOne(`/api/addresses/${firstParent.id}/children`);
    const afterFirstMoveNewParent = http.expectOne(`/api/addresses/${secondParent.id}/children`);
    afterFirstMoveOldParent.flush([]);
    afterFirstMoveNewParent.flush([moved]);
    fixture.changeDetectorRef.detectChanges();
    expect(page.selectedAddress).toEqual(moved);
    expect(findItem(created.id)?.addressId).toBe(created.id);

    page.deactivateSelectedAddress();
    const deactivate = http.expectOne(`/api/addresses/${created.id}/deactivate`);
    expect(deactivate.request.body).toEqual({ expectedVersion: 27 });
    const inactive = address(created.id, 'Renamed address', secondParent.id, false, 41);
    deactivate.flush(inactive);
    fixture.changeDetectorRef.detectChanges();
    expect(page.selectedAddress).toEqual(inactive);
    expect(page.loadedChildren.get(secondParent.id)).toEqual([inactive]);
    expect(findItem(created.id)?.label).toContain('(Inativo)');

    page.startMove();
    http.expectOne('/api/addresses').flush([firstParent, secondParent, inactive]);
    expect(page.moveDestinationOptions.some(option => option.value === firstParent.id)).toBe(true);
    page.moveDestinationId = firstParent.id;
    page.saveMove();
    const inactiveMove = http.expectOne(`/api/addresses/${created.id}/move`);
    expect(inactiveMove.request.method).toBe('POST');
    expect(inactiveMove.request.body).toEqual({ newParentId: firstParent.id, expectedVersion: 41 });
    const inactiveMoved = address(created.id, 'Renamed address', firstParent.id, false, 63);
    inactiveMove.flush(inactiveMoved);
    const afterInactiveMoveOldParent = http.expectOne(`/api/addresses/${secondParent.id}/children`);
    const afterInactiveMoveNewParent = http.expectOne(`/api/addresses/${firstParent.id}/children`);
    afterInactiveMoveOldParent.flush([]);
    afterInactiveMoveNewParent.flush([inactiveMoved]);
    fixture.changeDetectorRef.detectChanges();
    expect(page.selectedAddress).toEqual(inactiveMoved);
    expect(findItem(created.id)?.label).toContain('(Inativo)');

    page.activateSelectedAddress();
    const activate = http.expectOne(`/api/addresses/${created.id}/activate`);
    expect(activate.request.body).toEqual({ expectedVersion: 63 });
    const activated = address(created.id, 'Renamed address', firstParent.id, true, 88);
    activate.flush(activated);
    fixture.changeDetectorRef.detectChanges();

    expect(page.selectedAddress).toEqual(activated);
    expect(page.addresses.get(created.id)).toEqual(activated);
    expect(page.loadedChildren.get(firstParent.id)).toEqual([activated]);
    expect(page.roots).toEqual([firstParent, secondParent]);
    expect(findItem(created.id)?.label).toBe('Renamed address');
    http.expectNone('/api/addresses/roots');
    http.expectNone(`/api/addresses/${created.id}/deactivate`);
    http.expectNone(`/api/addresses/${created.id}/move`);
    http.expectNone(`/api/addresses/${created.id}/activate`);
  });
});
