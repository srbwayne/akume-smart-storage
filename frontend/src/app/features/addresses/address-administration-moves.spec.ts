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
  name = `Address ${id}`,
  parentId: string | null = null,
  active = true,
  version = 0,
): Address {
  return { id, name, parentId, addressTypeId: 'type-1', active, version };
}

describe('AddressAdministration move', () => {
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

  function loadRoots(roots: Address[]): void {
    page.loadRoots();
    http.expectOne('/api/addresses/roots').flush(roots);
    fixture.changeDetectorRef.detectChanges();
  }

  function loadChildren(parent: Address, children: Address[]): void {
    page.onExpanded({ ...findItem(parent.id)!, expanded: true });
    http.expectOne(`/api/addresses/${parent.id}/children`).flush(children);
    fixture.changeDetectorRef.detectChanges();
  }

  function selectAddress(id: string): void {
    const tree = fixture.debugElement.query(By.css('po-tree-view')).componentInstance as PoTreeViewComponent;
    tree.selected.emit(findItem(id)!);
    fixture.changeDetectorRef.detectChanges();
  }

  function openMove(source: Address, catalogue: Address[]): void {
    selectAddress(source.id);
    page.startMove();
    const request = http.expectOne('/api/addresses');
    expect(request.request.method).toBe('GET');
    request.flush(catalogue);
    fixture.changeDetectorRef.detectChanges();
  }

  it('requires a real selected Address, uses the flat destination catalogue, excludes self, and offers root', () => {
    const source = address('source');
    const hiddenActive = address('hidden-destination', 'Nested destination', 'not-materialized');
    const inactive = address('inactive-destination', 'Inactive destination', null, false);
    loadRoots([source]);
    const placeholder = page.treeItems[0].subItems![0] as AddressTreeItem;
    page.onSelected(placeholder);
    page.startMove();
    expect(page.moveFormVisible).toBe(false);
    http.expectNone('/api/addresses');

    openMove(source, [source, hiddenActive, inactive]);
    expect(page.moveDestinationOptions).toEqual([
      { label: 'Endereço raiz', value: '' },
      { label: 'Nested destination — hidden-destination', value: 'hidden-destination' },
    ]);
    expect(page.addresses.has(hiddenActive.id)).toBe(false);
    expect(page.moveDestinationOptions.some(option => option.value === source.id)).toBe(false);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Mover endereço');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(source.name);
  });

  it('allows inactive sources to offer inactive destinations without offering itself', () => {
    const inactiveSource = address('inactive-source', 'Inactive source', null, false, 4);
    const activeDestination = address('active-destination');
    const inactiveDestination = address('inactive-destination', 'Inactive destination', null, false);
    loadRoots([inactiveSource]);
    openMove(inactiveSource, [inactiveSource, activeDestination, inactiveDestination]);
    expect(page.moveDestinationOptions.map(option => option.value)).toEqual([
      '', activeDestination.id, inactiveDestination.id,
    ]);
  });

  it('keeps the source snapshot fixed while destination candidates are loading', () => {
    const firstSource = address('first-source', 'First source');
    const secondSource = address('second-source', 'Second source');
    loadRoots([firstSource, secondSource]);
    selectAddress(firstSource.id);
    page.startMove();
    selectAddress(secondSource.id);
    page.startMove();

    expect(page.moveTarget).toEqual(firstSource);
    const catalogueRequest = http.expectOne('/api/addresses');
    catalogueRequest.flush([firstSource, secondSource]);
    fixture.changeDetectorRef.detectChanges();
    expect(page.moveDestinationOptions.some(option => option.value === firstSource.id)).toBe(false);
    expect(page.moveDestinationOptions.some(option => option.value === secondSource.id)).toBe(true);
    http.expectNone('/api/addresses');
  });

  it('moves a child to a destination using the captured version and preserves its loaded descendants', () => {
    const oldParent = address('old-parent');
    const newParent = address('new-parent', 'New parent');
    const source = address('source', 'Source', oldParent.id, true, 6);
    const descendant = address('descendant', 'Descendant', source.id);
    loadRoots([oldParent, newParent]);
    loadChildren(oldParent, [source]);
    loadChildren(source, [descendant]);
    loadChildren(newParent, []);
    openMove(source, [oldParent, newParent, source, descendant]);
    page.moveDestinationId = newParent.id;
    page.saveMove();

    const request = http.expectOne(`/api/addresses/${source.id}/move`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ newParentId: newParent.id, expectedVersion: 6 });
    const moved = { ...source, parentId: newParent.id, version: 12 };
    request.flush(moved);

    expect(page.addresses.get(source.id)).toEqual(moved);
    expect(page.selectedAddress).toEqual(moved);
    expect(page.loadedChildren.get(source.id)).toEqual([descendant]);
    expect(page.childStates.get(source.id)).toBe('loaded');
    expect(page.loadedChildren.has(oldParent.id)).toBe(false);
    expect(page.loadedChildren.has(newParent.id)).toBe(false);
    expect(page.childStates.get(oldParent.id)).toBe('loading');
    expect(page.childStates.get(newParent.id)).toBe('loading');
    expect(page.moveFormVisible).toBe(false);

    http.expectOne(`/api/addresses/${oldParent.id}/children`).flush([]);
    http.expectOne(`/api/addresses/${newParent.id}/children`).flush([moved]);
    fixture.changeDetectorRef.detectChanges();
    expect(findItem(source.id)?.addressId).toBe(source.id);
    expect((findItem(source.id)?.subItems?.[0] as AddressTreeItem | undefined)?.addressId).toBe(descendant.id);
  });

  it('uses the real root destination for child-to-root movement and reloads roots', () => {
    const parent = address('parent');
    const source = address('source', 'Source', parent.id, true, 8);
    loadRoots([parent]);
    loadChildren(parent, [source]);
    openMove(source, [parent, source]);
    page.moveDestinationId = '';
    page.saveMove();

    const request = http.expectOne(`/api/addresses/${source.id}/move`);
    expect(request.request.body).toEqual({ newParentId: null, expectedVersion: 8 });
    const moved = { ...source, parentId: null, version: 10 };
    request.flush(moved);

    const oldParentRefresh = http.expectOne(`/api/addresses/${parent.id}/children`);
    const rootsRefresh = http.expectOne('/api/addresses/roots');
    expect(page.roots).toEqual([]);
    expect(page.loadingRoots).toBe(true);
    oldParentRefresh.flush([]);
    rootsRefresh.flush([parent, moved]);
    fixture.changeDetectorRef.detectChanges();
    expect(page.roots).toEqual([parent, moved]);
    expect(page.addresses.get(source.id)).toEqual(moved);
    expect(page.selectedAddress).toEqual(moved);
  });

  it('moves a root under a parent, refreshes roots and destination children, and preserves subtree state', () => {
    const source = address('source', 'Source', null, true, 2);
    const destination = address('destination');
    const descendant = address('descendant', 'Descendant', source.id);
    loadRoots([source, destination]);
    loadChildren(source, [descendant]);
    loadChildren(destination, []);
    openMove(source, [source, destination, descendant]);
    page.moveDestinationId = destination.id;
    page.saveMove();

    const request = http.expectOne(`/api/addresses/${source.id}/move`);
    expect(request.request.body).toEqual({ newParentId: destination.id, expectedVersion: 2 });
    const moved = { ...source, parentId: destination.id, version: 9 };
    request.flush(moved);
    const rootsRefresh = http.expectOne('/api/addresses/roots');
    const destinationRefresh = http.expectOne(`/api/addresses/${destination.id}/children`);
    expect(page.roots).toEqual([]);
    rootsRefresh.flush([destination]);
    destinationRefresh.flush([moved]);
    fixture.changeDetectorRef.detectChanges();

    expect(findItem(source.id)?.addressId).toBe(source.id);
    expect(findItem(descendant.id)?.addressId).toBe(descendant.id);
    expect(page.loadedChildren.get(source.id)).toEqual([descendant]);
    expect(page.addresses.get(source.id)).toEqual(moved);
    expect(page.selectedAddress).toEqual(moved);
  });

  it('does not fetch children solely for a flat-list destination absent from the materialized tree', () => {
    const source = address('source', 'Source', null, true, 1);
    const unmaterializedDestination = address('unseen-destination', 'Unseen destination');
    loadRoots([source]);
    openMove(source, [source, unmaterializedDestination]);
    page.moveDestinationId = unmaterializedDestination.id;
    page.saveMove();

    const request = http.expectOne(`/api/addresses/${source.id}/move`);
    expect(request.request.body).toEqual({ newParentId: unmaterializedDestination.id, expectedVersion: 1 });
    const moved = { ...source, parentId: unmaterializedDestination.id, version: 4 };
    request.flush(moved);
    http.expectOne('/api/addresses/roots').flush([unmaterializedDestination]);
    fixture.changeDetectorRef.detectChanges();

    expect(page.roots).toEqual([unmaterializedDestination]);
    expect(page.addresses.get(source.id)).toEqual(moved);
    http.expectNone(`/api/addresses/${unmaterializedDestination.id}/children`);
  });

  it('allows child-to-same-parent as an idempotent move and preserves authoritative response and caches', () => {
    const parent = address('parent');
    const source = address('source', 'Source', parent.id, true, 14);
    const descendant = address('descendant', 'Descendant', source.id);
    loadRoots([parent]);
    loadChildren(parent, [source]);
    loadChildren(source, [descendant]);
    openMove(source, [parent, source]);
    expect(page.moveDestinationId).toBe(parent.id);
    page.saveMove();

    const request = http.expectOne(`/api/addresses/${source.id}/move`);
    expect(request.request.body).toEqual({ newParentId: parent.id, expectedVersion: 14 });
    const response = { ...source, name: 'Server authority', version: 21 };
    request.flush(response);
    fixture.changeDetectorRef.detectChanges();

    expect(page.addresses.get(source.id)).toEqual(response);
    expect(page.selectedAddress).toEqual(response);
    expect(page.loadedChildren.get(parent.id)).toEqual([response]);
    expect(page.loadedChildren.get(source.id)).toEqual([descendant]);
    expect(page.childStates.get(source.id)).toBe('loaded');
    expect(findItem(source.id)?.label).toBe('Server authority');
    http.expectNone('/api/addresses/roots');
    http.expectNone(`/api/addresses/${parent.id}/children`);
    http.expectNone(`/api/addresses/${source.id}/children`);
  });

  it('allows root-to-root as an idempotent move with explicit null and no structural reload', () => {
    const source = address('source', 'Root source', null, false, 5);
    loadRoots([source]);
    openMove(source, [source]);
    expect(page.moveDestinationId).toBe('');
    page.saveMove();

    const request = http.expectOne(`/api/addresses/${source.id}/move`);
    expect(request.request.body).toEqual({ newParentId: null, expectedVersion: 5 });
    const response = { ...source, name: 'Returned root name', version: 8 };
    request.flush(response);
    fixture.changeDetectorRef.detectChanges();
    expect(Object.prototype.hasOwnProperty.call(request.request.body, 'newParentId')).toBe(true);
    expect(request.request.body.newParentId).toBeNull();
    expect(page.roots).toEqual([response]);
    expect(page.addresses.get(source.id)).toEqual(response);
    expect(page.selectedAddress).toEqual(response);
    expect(page.treeItems[0].label).toBe('Returned root name (Inativo)');
    http.expectNone('/api/addresses/roots');
  });

  it('keeps the captured source version when the hierarchy refreshes while the move form is open', () => {
    const source = address('source', 'Source', null, true, 3);
    const destination = address('destination');
    loadRoots([source, destination]);
    openMove(source, [source, destination]);
    page.loadRoots();
    http.expectOne('/api/addresses/roots').flush([{ ...source, version: 20 }, destination]);
    fixture.changeDetectorRef.detectChanges();
    page.moveDestinationId = destination.id;
    page.saveMove();

    const request = http.expectOne(`/api/addresses/${source.id}/move`);
    expect(request.request.body).toEqual({ newParentId: destination.id, expectedVersion: 3 });
    request.flush({ ...source, parentId: destination.id, version: 21 });
    http.expectOne('/api/addresses/roots').flush([destination]);
  });

  it('shows safe move failure, retains inputs, and does not mutate or retry the tree', () => {
    const parent = address('parent');
    const source = address('source', 'Source', parent.id, true, 7);
    loadRoots([parent]);
    loadChildren(parent, [source]);
    const beforeTree = page.treeItems;
    const beforeChildren = page.loadedChildren.get(parent.id);
    openMove(source, [parent, source]);
    page.moveDestinationId = '';
    page.saveMove();
    http.expectOne(`/api/addresses/${source.id}/move`).flush(
      { message: 'private server details' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.changeDetectorRef.detectChanges();

    expect(page.moveError).toBe('Não foi possível mover o endereço. Verifique o destino e tente novamente.');
    expect(page.moveError).not.toContain('private server details');
    expect(page.moveFormVisible).toBe(true);
    expect(page.moveDestinationId).toBe('');
    expect(page.addresses.get(source.id)).toEqual(source);
    expect(page.loadedChildren.get(parent.id)).toEqual(beforeChildren);
    expect(page.treeItems).toEqual(beforeTree);
    http.expectNone(`/api/addresses/${source.id}/move`);
  });

  it('invalidates old and new parent caches before failed post-move refreshes and leaves retry available', () => {
    const oldParent = address('old-parent');
    const newParent = address('new-parent');
    const source = address('source', 'Source', oldParent.id, true, 2);
    loadRoots([oldParent, newParent]);
    loadChildren(oldParent, [source]);
    loadChildren(newParent, []);
    openMove(source, [oldParent, newParent, source]);
    page.moveDestinationId = newParent.id;
    page.saveMove();
    const moved = { ...source, parentId: newParent.id, version: 3 };
    http.expectOne(`/api/addresses/${source.id}/move`).flush(moved);

    expect(page.loadedChildren.has(oldParent.id)).toBe(false);
    expect(page.loadedChildren.has(newParent.id)).toBe(false);
    const oldRefresh = http.expectOne(`/api/addresses/${oldParent.id}/children`);
    const newRefresh = http.expectOne(`/api/addresses/${newParent.id}/children`);
    oldRefresh.flush({ message: 'private old-parent failure' }, { status: 500, statusText: 'Server Error' });
    newRefresh.flush({ message: 'private new-parent failure' }, { status: 500, statusText: 'Server Error' });
    fixture.changeDetectorRef.detectChanges();

    expect(page.moveError).toBe('');
    expect(page.childStates.get(oldParent.id)).toBe('error');
    expect(page.childStates.get(newParent.id)).toBe('error');
    expect(page.loadedChildren.has(oldParent.id)).toBe(false);
    expect(page.loadedChildren.has(newParent.id)).toBe(false);
    expect(findItem(source.id)).toBeUndefined();
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('private old-parent failure');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('private new-parent failure');

    const oldItem = findItem(oldParent.id)!;
    page.onExpanded({ ...oldItem, expanded: false });
    page.onExpanded({ ...oldItem, expanded: true });
    http.expectOne(`/api/addresses/${oldParent.id}/children`).flush([]);
    fixture.changeDetectorRef.detectChanges();
    expect(page.childStates.get(oldParent.id)).toBe('loaded');
  });

  it('does not restore stale roots if root refresh after a successful transition fails', () => {
    const parent = address('parent');
    const source = address('source', 'Source', parent.id, true, 1);
    loadRoots([parent]);
    loadChildren(parent, [source]);
    openMove(source, [parent, source]);
    page.moveDestinationId = '';
    page.saveMove();
    const moved = { ...source, parentId: null, version: 2 };
    http.expectOne(`/api/addresses/${source.id}/move`).flush(moved);

    http.expectOne(`/api/addresses/${parent.id}/children`).flush([]);
    http.expectOne('/api/addresses/roots').flush(
      { message: 'private roots failure' },
      { status: 500, statusText: 'Server Error' },
    );
    fixture.changeDetectorRef.detectChanges();

    expect(page.moveError).toBe('');
    expect(page.loadingRoots).toBe(false);
    expect(page.rootError).toBe(true);
    expect(page.roots).toEqual([]);
    expect(page.addresses.get(source.id)).toEqual(moved);
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('private roots failure');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Não foi possível carregar os endereços.');
  });

  it('ignores a pre-move roots success after the newer post-move roots read succeeds', () => {
    const source = address('source', 'Root source', null, true, 4);
    const destination = address('destination');
    const descendant = address('descendant', 'Descendant', source.id);
    loadRoots([source, destination]);
    loadChildren(source, [descendant]);
    openMove(source, [source, destination]);
    page.moveDestinationId = destination.id;

    page.loadRoots();
    const oldRootRequest = http.expectOne('/api/addresses/roots');
    page.saveMove();
    const moveRequest = http.expectOne(`/api/addresses/${source.id}/move`);
    expect(moveRequest.request.body).toEqual({ newParentId: destination.id, expectedVersion: 4 });
    const moved = { ...source, parentId: destination.id, version: 15 };
    moveRequest.flush(moved);

    const newRootRequest = http.expectOne('/api/addresses/roots');
    newRootRequest.flush([destination]);
    fixture.changeDetectorRef.detectChanges();
    expect(page.roots).toEqual([destination]);
    expect(page.addresses.get(source.id)).toEqual(moved);

    page.onExpanded({ ...findItem(destination.id)!, expanded: true });
    http.expectOne(`/api/addresses/${destination.id}/children`).flush([moved]);
    fixture.changeDetectorRef.detectChanges();
    expect(page.loadedChildren.get(destination.id)).toEqual([moved]);
    expect(page.loadedChildren.get(source.id)).toEqual([descendant]);

    oldRootRequest.flush([source, destination]);
    fixture.detectChanges();
    expect(page.roots).toEqual([destination]);
    expect(page.addresses.get(source.id)).toEqual(moved);
    expect(page.loadedChildren.get(destination.id)).toEqual([moved]);
    expect(page.loadedChildren.get(source.id)).toEqual([descendant]);
    expect(page.childStates.get(destination.id)).toBe('loaded');
    expect(findItem(source.id)?.addressId).toBe(source.id);
  });

  it('ignores a stale roots error after a newer roots success', () => {
    page.loadRoots();
    const oldRequest = http.expectOne('/api/addresses/roots');
    page.loadRoots();
    http.expectOne('/api/addresses/roots').flush([address('current-root')]);
    fixture.changeDetectorRef.detectChanges();

    oldRequest.flush({ message: 'stale private error' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(page.roots).toEqual([address('current-root')]);
    expect(page.rootError).toBe(false);
    expect(page.loadingRoots).toBe(false);
    expect(page.treeItems[0].addressId).toBe('current-root');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('stale private error');
  });

  it('invalidates collapsed materialized old and new parents without fetching until expansion', () => {
    const oldParent = address('old-parent');
    const newParent = address('new-parent');
    const source = address('source', 'Source', oldParent.id, true, 9);
    const sibling = address('sibling', 'Sibling', oldParent.id);
    loadRoots([oldParent, newParent]);
    loadChildren(oldParent, [source, sibling]);
    const tree = fixture.debugElement.query(By.css('po-tree-view')).componentInstance as PoTreeViewComponent;
    tree.expanded.emit({ ...findItem(oldParent.id)!, expanded: false });
    fixture.changeDetectorRef.detectChanges();
    openMove(source, [oldParent, newParent, source, sibling]);
    page.moveDestinationId = newParent.id;
    page.saveMove();

    const moved = { ...source, parentId: newParent.id, version: 12 };
    http.expectOne(`/api/addresses/${source.id}/move`).flush(moved);
    fixture.changeDetectorRef.detectChanges();

    for (const parent of [oldParent, newParent]) {
      expect(page.childStates.get(parent.id)).toBe('unloaded');
      expect(page.loadedChildren.has(parent.id)).toBe(false);
      expect(page.expandedIds.has(parent.id)).toBe(false);
      expect(findItem(source.id)).toBeUndefined();
      http.expectNone(`/api/addresses/${parent.id}/children`);
    }

    page.onExpanded({ ...findItem(oldParent.id)!, expanded: true });
    http.expectOne(`/api/addresses/${oldParent.id}/children`).flush([sibling]);
    fixture.changeDetectorRef.detectChanges();
    expect(findItem(source.id)).toBeUndefined();
    expect(page.loadedChildren.get(oldParent.id)).toEqual([sibling]);

    page.onExpanded({ ...findItem(newParent.id)!, expanded: true });
    http.expectOne(`/api/addresses/${newParent.id}/children`).flush([moved]);
    fixture.changeDetectorRef.detectChanges();
    expect(findItem(source.id)?.addressId).toBe(source.id);
    expect(page.loadedChildren.get(newParent.id)).toEqual([moved]);
  });

  it('invalidates a collapsed destination with previously loaded children and reloads on later expansion', () => {
    const oldParent = address('old-parent');
    const destination = address('destination');
    const movedAddress = address('moving-address', 'Moving address', oldParent.id, true, 17);
    const existingChild = address('existing-child', 'Existing child', destination.id);
    loadRoots([oldParent, destination]);
    loadChildren(oldParent, [movedAddress]);
    loadChildren(destination, [existingChild]);

    expect(page.childStates.get(destination.id)).toBe('loaded');
    expect(page.loadedChildren.get(destination.id)).toEqual([existingChild]);
    expect(findItem(existingChild.id)?.addressId).toBe(existingChild.id);

    const tree = fixture.debugElement.query(By.css('po-tree-view')).componentInstance as PoTreeViewComponent;
    tree.expanded.emit({ ...findItem(destination.id)!, expanded: false });
    fixture.changeDetectorRef.detectChanges();
    expect(page.expandedIds.has(destination.id)).toBe(false);
    expect(page.childStates.get(destination.id)).toBe('loaded');
    expect(page.loadedChildren.get(destination.id)).toEqual([existingChild]);

    openMove(movedAddress, [oldParent, destination, movedAddress, existingChild]);
    page.moveDestinationId = destination.id;
    page.saveMove();
    const moveRequest = http.expectOne(`/api/addresses/${movedAddress.id}/move`);
    expect(moveRequest.request.method).toBe('POST');
    expect(moveRequest.request.body).toEqual({ newParentId: destination.id, expectedVersion: 17 });
    const movedResponse = { ...movedAddress, parentId: destination.id, version: 24 };
    moveRequest.flush(movedResponse);
    fixture.changeDetectorRef.detectChanges();

    expect(page.childStates.get(destination.id)).toBe('unloaded');
    expect(page.loadedChildren.has(destination.id)).toBe(false);
    expect(findItem(existingChild.id)).toBeUndefined();
    expect(findItem(movedAddress.id)).toBeUndefined();
    http.expectOne(`/api/addresses/${oldParent.id}/children`).flush([]);
    http.expectNone(`/api/addresses/${destination.id}/children`);

    tree.expanded.emit({ ...findItem(destination.id)!, expanded: true });
    const freshRequest = http.expectOne(`/api/addresses/${destination.id}/children`);
    expect(freshRequest.request.method).toBe('GET');
    freshRequest.flush([existingChild, movedResponse]);
    fixture.changeDetectorRef.detectChanges();

    expect(page.childStates.get(destination.id)).toBe('loaded');
    expect(page.loadedChildren.get(destination.id)).toEqual([existingChild, movedResponse]);
    expect(findItem(existingChild.id)?.addressId).toBe(existingChild.id);
    expect(findItem(movedAddress.id)?.addressId).toBe(movedAddress.id);
  });
});
