import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { PoTreeViewComponent } from '@po-ui/ng-components';
import { TestBed } from '@angular/core/testing';
import { Address } from './address.model';
import { AddressAdministration, AddressTreeItem } from './address-administration';

function address(id: string, name = `Address ${id}`, parentId: string | null = null, active = true, version = 0): Address {
  return { id, name, addressTypeId: 'type-1', parentId, active, version };
}

describe('AddressAdministration lifecycle', () => {
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

  function roots(records: Address[]): void {
    page.loadRoots();
    http.expectOne('/api/addresses/roots').flush(records);
    fixture.changeDetectorRef.detectChanges();
  }

  function children(parent: Address, records: Address[]): void {
    page.onExpanded({ ...findItem(parent.id)!, expanded: true });
    http.expectOne(`/api/addresses/${parent.id}/children`).flush(records);
    fixture.changeDetectorRef.detectChanges();
  }

  function select(id: string): void {
    const tree = fixture.debugElement.query(By.css('po-tree-view')).componentInstance as PoTreeViewComponent;
    tree.selected.emit(findItem(id)!);
    fixture.changeDetectorRef.detectChanges();
  }

  it('offers only the matching lifecycle action for a real selected Address and ignores placeholders', () => {
    const active = address('active');
    const inactive = address('inactive', 'Inactive', null, false);
    roots([active, inactive]);
    const activePlaceholder = findItem(active.id)!.subItems![0] as AddressTreeItem;
    page.onSelected(activePlaceholder);
    page.activateSelectedAddress();
    page.deactivateSelectedAddress();
    expect(page.selectedAddressId).toBeNull();
    http.expectNone('/api/addresses/active/activate');
    http.expectNone('/api/addresses/active/deactivate');

    select(active.id);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Desativar endereço selecionado');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Ativar endereço selecionado');
    page.deactivateSelectedAddress();
    const deactivate = http.expectOne(`/api/addresses/${active.id}/deactivate`);
    deactivate.flush({ ...active, active: false, version: 1 });
    fixture.changeDetectorRef.detectChanges();

    select(inactive.id);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Ativar endereço selecionado');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Desativar endereço selecionado');
    const placeholder = findItem(inactive.id)!.subItems![0] as AddressTreeItem;
    page.onSelected(placeholder);
    expect(page.selectedAddressId).toBe(inactive.id);
    expect(page.selectedAddress).toEqual(inactive);
  });

  it('sends the selected deactivate snapshot and reconciles the authoritative response without structural reloads', () => {
    const parent = address('parent');
    const other = address('other-parent');
    const selected = address('selected', 'Selected', parent.id, true, 6);
    const descendant = address('descendant', 'Descendant', selected.id);
    const unrelated = address('unrelated-child', 'Unrelated child', other.id);
    roots([parent, other]);
    children(parent, [selected]);
    children(selected, [descendant]);
    children(other, [unrelated]);
    select(selected.id);

    page.deactivateSelectedAddress();
    const request = http.expectOne(`/api/addresses/${selected.id}/deactivate`);
    page.deactivateSelectedAddress();
    http.expectNone(`/api/addresses/${selected.id}/deactivate`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ expectedVersion: 6 });
    const authoritative = { ...selected, name: 'Server name', active: false, version: 31 };
    request.flush(authoritative);
    fixture.changeDetectorRef.detectChanges();

    expect(page.addresses.get(selected.id)).toEqual(authoritative);
    expect(page.selectedAddress).toEqual(authoritative);
    expect(page.loadedChildren.get(parent.id)).toEqual([authoritative]);
    expect(page.loadedChildren.get(selected.id)).toEqual([descendant]);
    expect(page.childStates.get(selected.id)).toBe('loaded');
    expect(page.loadedChildren.get(other.id)).toEqual([unrelated]);
    expect(findItem(selected.id)?.label).toBe('Server name (Inativo)');
    http.expectNone('/api/addresses/roots');
    http.expectNone(`/api/addresses/${other.id}/children`);
  });

  it('activates using the selected snapshot and sends the server-returned version on the next lifecycle action', () => {
    const inactive = address('target', 'Target', null, false, 8);
    roots([inactive]);
    select(inactive.id);

    page.activateSelectedAddress();
    const activate = http.expectOne(`/api/addresses/${inactive.id}/activate`);
    expect(activate.request.body).toEqual({ expectedVersion: 8 });
    const serverActive = { ...inactive, active: true, version: 29 };
    activate.flush(serverActive);
    fixture.changeDetectorRef.detectChanges();
    expect(page.selectedAddress).toEqual(serverActive);

    page.deactivateSelectedAddress();
    const deactivate = http.expectOne(`/api/addresses/${inactive.id}/deactivate`);
    expect(deactivate.request.body).toEqual({ expectedVersion: 29 });
    deactivate.flush({ ...serverActive, active: false, version: 44 });
    fixture.changeDetectorRef.detectChanges();
    expect(page.selectedAddress?.version).toBe(44);
    expect(page.selectedAddress?.active).toBe(false);
  });

  it('keeps the captured expectedVersion when a completed roots read changes the selected snapshot during the mutation', () => {
    const target = address('target', 'Target', null, true, 5);
    roots([target]);
    select(target.id);
    page.deactivateSelectedAddress();
    const mutation = http.expectOne(`/api/addresses/${target.id}/deactivate`);
    page.loadRoots();
    const interveningRead = http.expectOne('/api/addresses/roots');
    const intervening = { ...target, name: 'Read snapshot', version: 12 };
    interveningRead.flush([intervening]);
    fixture.changeDetectorRef.detectChanges();

    expect(page.selectedAddress).toEqual(intervening);
    expect(page.loadingRoots).toBe(false);
    expect(mutation.request.body).toEqual({ expectedVersion: 5 });
    const authoritative = { ...target, name: 'Mutation response', active: false, version: 19 };
    mutation.flush(authoritative);
    fixture.changeDetectorRef.detectChanges();

    expect(page.selectedAddress).toEqual(authoritative);
    expect(page.roots).toEqual([authoritative]);
    expect(page.loadingRoots).toBe(false);
    expect(findItem(target.id)?.label).toBe('Mutation response (Inativo)');
  });

  it('uses explicit concurrency recovery and the refreshed server version without automatic mutation retry', () => {
    const target = address('target', 'Target', null, true, 7);
    roots([target]);
    select(target.id);
    page.deactivateSelectedAddress();
    const mutation = http.expectOne(`/api/addresses/${target.id}/deactivate`);
    mutation.flush(
      { code: 'ADDRESS_CONCURRENT_MODIFICATION', message: 'private conflict detail' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.changeDetectorRef.detectChanges();

    expect(page.selectedAddress).toEqual(target);
    expect(page.lifecycleErrorKind).toBe('concurrency');
    expect(page.lifecycleError).toContain('Atualize o estado atual');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('private conflict detail');
    http.expectNone(`/api/addresses/${target.id}/deactivate`);

    const recoveryButton = [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')]
      .find(button => button.textContent?.includes('Atualizar estado do endereço'));
    expect(recoveryButton).toBeTruthy();
    recoveryButton!.click();
    const recovery = http.expectOne(`/api/addresses/${target.id}`);
    expect(recovery.request.method).toBe('GET');
    const refreshed = { ...target, active: false, version: 22 };
    recovery.flush(refreshed);
    fixture.changeDetectorRef.detectChanges();

    expect(page.selectedAddress).toEqual(refreshed);
    expect(page.lifecycleError).toBe('');
    expect(page.lifecycleConflictAddressId).toBeNull();
    page.activateSelectedAddress();
    const nextMutation = http.expectOne(`/api/addresses/${target.id}/activate`);
    expect(nextMutation.request.body).toEqual({ expectedVersion: 22 });
    nextMutation.flush({ ...refreshed, active: true, version: 36 });
  });

  it('preserves the known Address after recovery GET failure and permits a second user-controlled recovery', () => {
    const target = address('target', 'Target', null, true, 7);
    roots([target]);
    select(target.id);
    page.deactivateSelectedAddress();
    http.expectOne(`/api/addresses/${target.id}/deactivate`).flush(
      { code: 'ADDRESS_CONCURRENT_MODIFICATION', message: 'private conflict detail' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.changeDetectorRef.detectChanges();
    expect(page.selectedAddress).toEqual(target);
    expect(page.lifecycleErrorKind).toBe('concurrency');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('private conflict detail');
    http.expectNone(`/api/addresses/${target.id}/deactivate`);

    const recoveryButton = [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')]
      .find(button => button.textContent?.includes('Atualizar estado do endereço'));
    expect(recoveryButton).toBeTruthy();
    recoveryButton!.click();
    const failedRecovery = http.expectOne(`/api/addresses/${target.id}`);
    failedRecovery.flush({ message: 'private recovery detail' }, { status: 503, statusText: 'Unavailable' });
    fixture.changeDetectorRef.detectChanges();

    expect(page.selectedAddress).toEqual(target);
    expect(page.selectedAddress?.active).toBe(true);
    expect(page.selectedAddress?.version).toBe(7);
    expect(page.roots).toEqual([target]);
    expect(page.lifecycleErrorKind).toBe('concurrency');
    expect(page.lifecycleRecoveryError).toContain('Não foi possível atualizar');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('private recovery detail');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Tentar atualizar novamente');
    http.expectNone(`/api/addresses/${target.id}/deactivate`);

    const retryButton = [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')]
      .find(button => button.textContent?.includes('Tentar atualizar novamente'));
    expect(retryButton).toBeTruthy();
    retryButton!.click();
    const retryRecovery = http.expectOne(`/api/addresses/${target.id}`);
    expect(retryRecovery.request.method).toBe('GET');
    const refreshed = { ...target, name: 'Recovered', active: false, version: 23 };
    retryRecovery.flush(refreshed);
    fixture.changeDetectorRef.detectChanges();

    expect(page.selectedAddress).toEqual(refreshed);
    page.activateSelectedAddress();
    const nextMutation = http.expectOne(`/api/addresses/${target.id}/activate`);
    expect(nextMutation.request.body).toEqual({ expectedVersion: 23 });
    nextMutation.flush({ ...refreshed, active: true, version: 40 });
  });

  it('keeps lifecycle error separate from hierarchy error state after a domain rejection', () => {
    const target = address('target', 'Target', null, true, 3);
    roots([target]);
    select(target.id);
    page.deactivateSelectedAddress();
    http.expectOne(`/api/addresses/${target.id}/deactivate`).flush(
      { code: 'ADDRESS_HAS_ACTIVE_CHILDREN', message: 'raw backend detail' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.changeDetectorRef.detectChanges();

    expect(page.selectedAddress).toEqual(target);
    expect(page.lifecycleErrorKind).toBe('domain');
    expect(page.lifecycleError).toContain('filhos ativos');
    expect(page.rootError).toBe(false);
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('raw backend detail');
    http.expectNone(`/api/addresses/${target.id}/deactivate`);
  });

  it('preserves a pre-existing child hierarchy error and retry flow after lifecycle failure', () => {
    const target = address('target', 'Target', null, true, 3);
    const otherParent = address('other-parent', 'Other parent');
    roots([target, otherParent]);
    select(target.id);

    page.onExpanded({ ...findItem(otherParent.id)!, expanded: true });
    http.expectOne(`/api/addresses/${otherParent.id}/children`).flush(
      { message: 'private hierarchy detail' },
      { status: 500, statusText: 'Server Error' },
    );
    fixture.changeDetectorRef.detectChanges();
    expect(page.childStates.get(otherParent.id)).toBe('error');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Falha ao carregar filhos');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Recolha e expanda');

    page.deactivateSelectedAddress();
    http.expectOne(`/api/addresses/${target.id}/deactivate`).flush(
      { code: 'ADDRESS_HAS_ACTIVE_CHILDREN', message: 'raw backend detail' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.changeDetectorRef.detectChanges();
    expect(page.lifecycleErrorKind).toBe('domain');
    expect(page.childStates.get(otherParent.id)).toBe('error');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Falha ao carregar filhos');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('raw backend detail');
    expect(page.selectedAddress).toEqual(target);
    http.expectNone(`/api/addresses/${target.id}/deactivate`);

    page.onExpanded({ ...findItem(otherParent.id)!, expanded: false });
    page.onExpanded({ ...findItem(otherParent.id)!, expanded: true });
    const retry = http.expectOne(`/api/addresses/${otherParent.id}/children`);
    expect(retry.request.method).toBe('GET');
    const recoveredChild = address('recovered-child', 'Recovered child', otherParent.id);
    retry.flush([recoveredChild]);
    fixture.changeDetectorRef.detectChanges();

    expect(page.childStates.get(otherParent.id)).toBe('loaded');
    expect(page.loadedChildren.get(otherParent.id)).toEqual([recoveredChild]);
    expect(findItem(recoveredChild.id)?.addressId).toBe(recoveredChild.id);
    expect(page.lifecycleErrorKind).toBe('domain');
  });

  it('keeps the Address snapshot and hierarchy intact after generic failure', () => {
    const target = address('target', 'Target', null, true, 14);
    roots([target]);
    select(target.id);
    page.deactivateSelectedAddress();
    http.expectOne(`/api/addresses/${target.id}/deactivate`).flush(
      { message: 'private server details' },
      { status: 500, statusText: 'Server Error' },
    );
    fixture.changeDetectorRef.detectChanges();

    expect(page.selectedAddress).toEqual(target);
    expect(page.roots).toEqual([target]);
    expect(page.lifecycleErrorKind).toBe('generic');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('private server details');
    expect(findItem(target.id)?.label).toBe(target.name);
    http.expectNone(`/api/addresses/${target.id}/deactivate`);
  });

  it('supersedes a pending pre-mutation roots read so it cannot overwrite lifecycle authority', () => {
    const target = address('target', 'Target', null, true, 2);
    roots([target]);
    select(target.id);
    page.loadRoots();
    const oldRootsRead = http.expectOne('/api/addresses/roots');
    page.deactivateSelectedAddress();
    const mutation = http.expectOne(`/api/addresses/${target.id}/deactivate`);
    const authoritative = { ...target, active: false, version: 19 };
    mutation.flush(authoritative);
    fixture.changeDetectorRef.detectChanges();
    expect(page.selectedAddress).toEqual(authoritative);

    oldRootsRead.flush([target]);
    fixture.changeDetectorRef.detectChanges();
    expect(page.selectedAddress).toEqual(authoritative);
    expect(page.roots).toEqual([authoritative]);
    expect(findItem(target.id)?.label).toContain('(Inativo)');
    expect(page.loadingRoots).toBe(false);
  });

  it('cancels a stale child read for the mutated Address parent and reloads it after success', () => {
    const parent = address('parent');
    const target = address('target', 'Target', parent.id, true, 11);
    roots([parent]);
    children(parent, [target]);
    select(target.id);

    page.startCreate();
    http.expectOne('/api/address-types').flush([{ id: 'type-1', code: 'ROOM', name: 'Room', description: null, active: true }]);
    http.expectOne('/api/addresses').flush([parent, target]);
    page.createName = 'New child';
    page.createAddressTypeId = 'type-1';
    page.createParentId = parent.id;
    page.saveCreate();
    http.expectOne('/api/addresses').flush(address('created', 'New child', parent.id));
    const staleChildren = http.expectOne(`/api/addresses/${parent.id}/children`);

    page.deactivateSelectedAddress();
    const mutation = http.expectOne(`/api/addresses/${target.id}/deactivate`);
    const authoritative = { ...target, active: false, version: 25 };
    mutation.flush(authoritative);
    const freshChildren = http.expectOne(`/api/addresses/${parent.id}/children`);
    expect(staleChildren.cancelled).toBe(true);
    freshChildren.flush([authoritative, address('created', 'New child', parent.id)]);
    fixture.changeDetectorRef.detectChanges();

    expect(page.selectedAddress).toEqual(authoritative);
    expect(page.loadedChildren.get(parent.id)?.[0]).toEqual(authoritative);
    expect(page.childStates.get(parent.id)).toBe('loaded');
  });
});
