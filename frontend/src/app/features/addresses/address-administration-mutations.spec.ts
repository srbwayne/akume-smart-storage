import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { PoTreeViewComponent } from '@po-ui/ng-components';
import { TestBed } from '@angular/core/testing';
import { AddressType } from '../address-types/address-type.model';
import { Address } from './address.model';
import { AddressAdministration, AddressTreeItem } from './address-administration';

function address(id: string, name = `Address ${id}`, parentId: string | null = null, active = true, version = 0): Address {
  return { id, name, addressTypeId: 'type-active', parentId, active, version };
}

const activeType: AddressType = { id: 'type-active', code: 'ROOM', name: 'Room', description: null, active: true };
const inactiveType: AddressType = { id: 'type-inactive', code: 'OLD', name: 'Old', description: null, active: false };

describe('AddressAdministration create and rename', () => {
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

  function openCreate(types: AddressType[] = [activeType, inactiveType], records: Address[] = []): void {
    page.startCreate();
    const typesRequest = http.expectOne('/api/address-types');
    expect(typesRequest.request.method).toBe('GET');
    const addressesRequest = http.expectOne('/api/addresses');
    expect(addressesRequest.request.method).toBe('GET');
    typesRequest.flush(types);
    addressesRequest.flush(records);
    fixture.changeDetectorRef.detectChanges();
  }

  function replaceRoots(roots: Address[]): void {
    page.loadRoots();
    http.expectOne('/api/addresses/roots').flush(roots);
    fixture.changeDetectorRef.detectChanges();
  }

  function findItem(id: string, items: AddressTreeItem[] = page.treeItems): AddressTreeItem | undefined {
    for (const item of items) {
      if (item.addressId === id) return item;
      const nested = findItem(id, (item.subItems ?? []) as AddressTreeItem[]);
      if (nested) return nested;
    }
    return undefined;
  }

  function loadChildren(parent: Address, children: Address[]): void {
    page.onExpanded({ ...findItem(parent.id)!, expanded: true });
    http.expectOne(`/api/addresses/${parent.id}/children`).flush(children);
    fixture.changeDetectorRef.detectChanges();
  }

  it('offers create, validates required values, and only offers active types and parents', () => {
    const root = address('root');
    const unloadedParent = address('hidden-parent', 'Nested parent', 'root');
    const inactiveParent = address('inactive-parent', 'Inactive parent', null, false);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Novo endereço');
    openCreate([activeType, inactiveType], [root, unloadedParent, inactiveParent]);

    expect(page.addressTypeOptions).toEqual([{ label: 'ROOM — Room', value: 'type-active' }]);
    expect(page.parentOptions).toEqual([
      { label: 'Endereço raiz', value: '' },
      { label: 'Address root — root', value: 'root' },
      { label: 'Nested parent — hidden-parent', value: 'hidden-parent' },
    ]);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Endereço pai (opcional)');

    page.saveCreate();
    expect(page.createSubmitted).toBe(true);
    http.expectNone('/api/addresses');
    page.createAddressTypeId = activeType.id;
    page.saveCreate();
    http.expectNone('/api/addresses');
    page.createAddressTypeId = '';
    page.createName = 'Valid name';
    page.saveCreate();
    http.expectNone('/api/addresses');
  });

  it('creates a root without a parentId property and refreshes roots from the server', () => {
    openCreate([activeType], []);
    page.createName = ' Office ';
    page.createAddressTypeId = activeType.id;
    page.createParentId = '';
    page.saveCreate();

    const request = http.expectOne('/api/addresses');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ name: 'Office', addressTypeId: activeType.id });
    expect(Object.prototype.hasOwnProperty.call(request.request.body, 'parentId')).toBe(false);
    const created = address('new-root', 'Office');
    request.flush(created);

    expect(page.createFormVisible).toBe(false);
    const refresh = http.expectOne('/api/addresses/roots');
    expect(refresh.request.method).toBe('GET');
    refresh.flush([created]);
    fixture.changeDetectorRef.detectChanges();
    expect(page.roots).toEqual([created]);
    expect(page.treeItems[0].label).toBe('Office');
  });

  it('uses the complete flat Address list for parent choices and sends the chosen UUID', () => {
    const root = address('root');
    const hiddenParent = address('nested-uuid', 'Nested parent', 'root');
    const inactiveParent = address('inactive-uuid', 'Inactive parent', null, false);
    openCreate([activeType], [root, hiddenParent, inactiveParent]);
    expect(page.addresses.has(hiddenParent.id)).toBe(false);
    expect(page.parentOptions.some(option => option.value === hiddenParent.id)).toBe(true);
    expect(page.parentOptions.some(option => option.value === inactiveParent.id)).toBe(false);

    page.createName = 'Cabinet';
    page.createAddressTypeId = activeType.id;
    page.createParentId = hiddenParent.id;
    page.saveCreate();
    const request = http.expectOne('/api/addresses');
    expect(request.request.body).toEqual({ name: 'Cabinet', addressTypeId: activeType.id, parentId: hiddenParent.id });
    request.flush(address('new-child', 'Cabinet', hiddenParent.id));
    expect(page.roots).toEqual([]);
    http.expectNone('/api/addresses/roots');
  });

  it('invalidates a confirmed leaf and reloads its children after child creation', () => {
    const root = address('root');
    replaceRoots([root]);
    loadChildren(root, []);
    expect(page.childStates.get(root.id)).toBe('loaded');
    expect(page.loadedChildren.get(root.id)).toEqual([]);

    openCreate([activeType], [root]);
    page.createName = 'Drawer';
    page.createAddressTypeId = activeType.id;
    page.createParentId = root.id;
    page.saveCreate();
    const create = http.expectOne('/api/addresses');
    const child = address('child', 'Drawer', root.id);
    create.flush(child);

    expect(page.childStates.get(root.id)).toBe('loading');
    const refresh = http.expectOne('/api/addresses/root/children');
    expect(refresh.request.method).toBe('GET');
    refresh.flush([child]);
    fixture.changeDetectorRef.detectChanges();
    expect(page.childStates.get(root.id)).toBe('loaded');
    expect(page.loadedChildren.get(root.id)).toEqual([child]);
    expect(findItem(child.id)?.addressId).toBe(child.id);
  });

  it('does not retain a stale loaded child cache after creating another child', () => {
    const root = address('root');
    const existing = address('existing', 'Existing', root.id);
    replaceRoots([root]);
    loadChildren(root, [existing]);
    expect(page.loadedChildren.get(root.id)).toEqual([existing]);

    openCreate([activeType], [root, existing]);
    page.createName = 'New drawer';
    page.createAddressTypeId = activeType.id;
    page.createParentId = root.id;
    page.saveCreate();
    const create = http.expectOne('/api/addresses');
    const added = address('added', 'New drawer', root.id);
    create.flush(added);
    const refresh = http.expectOne('/api/addresses/root/children');
    refresh.flush([existing, added]);
    fixture.changeDetectorRef.detectChanges();

    expect(page.loadedChildren.get(root.id)).toEqual([existing, added]);
    expect(findItem('added')?.addressId).toBe('added');
  });

  it('retains create data and shows safe generic feedback after failure', () => {
    openCreate([activeType], []);
    page.createName = 'Desk';
    page.createAddressTypeId = activeType.id;
    page.saveCreate();
    http.expectOne('/api/addresses').flush({ message: 'private server details' }, { status: 500, statusText: 'Server Error' });
    fixture.changeDetectorRef.detectChanges();
    expect(page.createError).toBe('Não foi possível criar o endereço. Verifique os dados e tente novamente.');
    expect(page.createName).toBe('Desk');
    expect(page.createFormVisible).toBe(true);
    expect(page.roots).toEqual([]);
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('private server details');
    http.expectNone('/api/addresses');

    page.saveCreate();
    const retry = http.expectOne('/api/addresses');
    retry.flush(address('retried-root', 'Desk'));
    http.expectOne('/api/addresses/roots').flush([address('retried-root', 'Desk')]);
    expect(page.createFormVisible).toBe(false);
  });

  it('renames only the selected Address name with its snapshot version and preserves loaded structure', () => {
    const root = address('root');
    const child = address('child', 'Old child', root.id, false, 7);
    const grandchild = address('grandchild', 'Grandchild', child.id);
    replaceRoots([root]);
    loadChildren(root, [child]);
    loadChildren(child, [grandchild]);
    const tree = fixture.debugElement.query(By.css('po-tree-view')).componentInstance as PoTreeViewComponent;
    tree.selected.emit(findItem(child.id)!);
    fixture.changeDetectorRef.detectChanges();
    page.startRename();
    fixture.changeDetectorRef.detectChanges();

    expect(page.renameName).toBe('Old child');
    const html = fixture.nativeElement as HTMLElement;
    const renameSection = html.querySelector('#rename-title')?.parentElement;
    expect(renameSection).toBeTruthy();
    expect(renameSection!.querySelector('po-input[name="renameName"]')).not.toBeNull();
    expect(renameSection!.querySelector('po-select')).toBeNull();
    expect(renameSection!.textContent).not.toContain('Tipo de endereço');
    expect(renameSection!.textContent).not.toContain('Endereço pai');

    page.renameName = 'Renamed child';
    page.saveRename();
    const request = http.expectOne('/api/addresses/child');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ name: 'Renamed child', expectedVersion: 7 });
    const updated = { ...child, name: 'Renamed child', version: 8 };
    request.flush(updated);
    fixture.changeDetectorRef.detectChanges();

    expect(page.addresses.get(child.id)).toEqual(updated);
    expect(page.loadedChildren.get(root.id)).toEqual([updated]);
    expect(page.loadedChildren.get(child.id)).toEqual([grandchild]);
    expect(page.childStates.get(child.id)).toBe('loaded');
    expect(findItem(child.id)?.label).toBe('Renamed child (Inativo)');
    expect(findItem(child.id)?.addressId).toBe(child.id);
    expect(page.renameFormVisible).toBe(false);
  });

  it('uses the server-returned version for a subsequent rename of the same Address', () => {
    const root = address('root', 'Original', null, true, 11);
    replaceRoots([root]);
    const tree = fixture.debugElement.query(By.css('po-tree-view')).componentInstance as PoTreeViewComponent;
    tree.selected.emit(findItem(root.id)!);
    fixture.changeDetectorRef.detectChanges();

    page.startRename();
    expect(page.renameName).toBe('Original');
    page.renameName = 'First rename';
    page.saveRename();

    const firstRequest = http.expectOne('/api/addresses/root');
    expect(firstRequest.request.method).toBe('PUT');
    expect(firstRequest.request.body).toEqual({ name: 'First rename', expectedVersion: 11 });
    const firstResponse = { ...root, name: 'First rename', version: 12 };
    firstRequest.flush(firstResponse);
    fixture.changeDetectorRef.detectChanges();

    expect(page.addresses.get(root.id)).toEqual(firstResponse);
    expect(page.selectedAddress).toEqual(firstResponse);

    page.startRename();
    expect(page.renameName).toBe('First rename');
    page.renameName = 'Second rename';
    page.saveRename();

    const secondRequest = http.expectOne('/api/addresses/root');
    expect(secondRequest.request.method).toBe('PUT');
    expect(secondRequest.request.body).toEqual({ name: 'Second rename', expectedVersion: 12 });
    secondRequest.flush({ ...firstResponse, name: 'Second rename', version: 13 });
    fixture.changeDetectorRef.detectChanges();

    expect(page.addresses.get(root.id)?.version).toBe(13);
    expect(page.selectedAddress?.name).toBe('Second rename');
  });

  it('rejects placeholder rename targets and blank rename names', () => {
    const root = address('root');
    replaceRoots([root]);
    const placeholder = page.treeItems[0].subItems![0] as AddressTreeItem;
    page.onSelected(placeholder);
    page.startRename();
    expect(page.selectedAddressId).toBeNull();
    expect(page.renameFormVisible).toBe(false);

    page.onSelected(page.treeItems[0]);
    page.startRename();
    page.renameName = '   ';
    page.saveRename();
    expect(page.renameSubmitted).toBe(true);
    http.expectNone('/api/addresses/root');
  });

  it('keeps rename state unchanged after failure and allows a manual retry', () => {
    const root = address('root', 'Original', null, true, 3);
    replaceRoots([root]);
    page.onSelected(page.treeItems[0]);
    page.startRename();
    page.renameName = 'Wanted name';
    page.saveRename();
    http.expectOne('/api/addresses/root').flush({ message: 'private server details' }, { status: 409, statusText: 'Conflict' });

    expect(page.renameError).toBe('Não foi possível renomear o endereço. Verifique os dados e tente novamente.');
    expect(page.renameName).toBe('Wanted name');
    expect(page.addresses.get(root.id)).toEqual(root);
    expect(page.treeItems[0].label).toBe('Original');
    expect(page.renameFormVisible).toBe(true);
    expect(page.renameError).not.toContain('private server details');
    http.expectNone('/api/addresses/root');

    page.saveRename();
    const retry = http.expectOne('/api/addresses/root');
    expect(retry.request.body).toEqual({ name: 'Wanted name', expectedVersion: 3 });
    retry.flush({ ...root, name: 'Wanted name', version: 4 });
    expect(page.addresses.get(root.id)?.version).toBe(4);
  });
});
