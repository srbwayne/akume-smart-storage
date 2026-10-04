import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { PoTreeViewComponent } from '@po-ui/ng-components';
import { TestBed } from '@angular/core/testing';
import { Address } from './address.model';
import { AddressAdministration, AddressTreeItem } from './address-administration';

function address(id: string, parentId: string | null = null, active = true): Address {
  return { id, parentId, name: `Address ${id}`, addressTypeId: 'type-1', active, version: 0 };
}

describe('AddressAdministration', () => {
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
  });

  afterEach(() => http.verify());

  function start(roots: Address[] = [address('root')]): void {
    fixture.changeDetectorRef.detectChanges();
    const request = http.expectOne('/api/addresses/roots');
    expect(request.request.method).toBe('GET');
    request.flush(roots);
    fixture.changeDetectorRef.detectChanges();
  }

  function expand(id: string): void {
    const item = findItem(id);
    expect(item).toBeDefined();
    const tree = fixture.debugElement.query(By.css('po-tree-view')).componentInstance as PoTreeViewComponent;
    tree.expanded.emit({ ...item!, expanded: true });
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

  it('loads roots at startup and shows loading, populated, inactive, and empty states', () => {
    fixture.changeDetectorRef.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Carregando endereços');
    const request = http.expectOne('/api/addresses/roots');
    request.flush([address('root'), address('inactive-root', null, false)]);
    fixture.changeDetectorRef.detectChanges();

    expect(page.treeItems.map(item => item.label)).toEqual(['Address root', 'Address inactive-root (Inativo)']);
    expect(page.maxLevel).toBeGreaterThanOrEqual(2);
    page.loadRoots();
    const emptyRequest = http.expectOne('/api/addresses/roots');
    emptyRequest.flush([]);
    fixture.changeDetectorRef.detectChanges();
    expect(page.loadingRoots).toBe(false);
    expect(page.rootError).toBe(false);
    expect(page.roots).toEqual([]);
    expect(page.treeItems).toEqual([]);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Nenhum endereço cadastrado.');
  });

  it('shows a safe root error and retries only after user action', () => {
    fixture.changeDetectorRef.detectChanges();
    http.expectOne('/api/addresses/roots').error(new ProgressEvent('error'));
    fixture.changeDetectorRef.detectChanges();
    expect(page.rootError).toBe(true);
    expect(page.loadingRoots).toBe(false);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Não foi possível carregar os endereços.');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Tentar novamente');

    page.loadRoots();
    http.expectOne('/api/addresses/roots').flush([address('root')]);
    expect(page.roots).toHaveLength(1);
  });

  it('loads direct children on first expansion, prevents duplicate requests, then uses the cache', () => {
    start();
    expect(http.match('/api/addresses/root/children')).toHaveLength(0);
    expand('root');
    expect(page.childStates.get('root')).toBe('loading');
    const requests = http.match('/api/addresses/root/children');
    expect(requests).toHaveLength(1);
    expect(findItem('root')?.subItems?.[0].label).toBe('Carregando filhos…');

    expand('root');
    expect(http.match('/api/addresses/root/children')).toHaveLength(0);
    requests[0].flush([address('child', 'root')]);
    fixture.changeDetectorRef.detectChanges();
    expect(page.childStates.get('root')).toBe('loaded');
    expect(page.loadedChildren.get('root')).toEqual([address('child', 'root')]);
    expect(findItem('child')?.value).toBe('child');

    expand('root');
    expect(http.match('/api/addresses/root/children')).toHaveLength(0);
  });

  it('confirms an empty child response as a leaf', () => {
    start();
    expand('root');
    http.expectOne('/api/addresses/root/children').flush([]);
    fixture.changeDetectorRef.detectChanges();
    expect(page.childStates.get('root')).toBe('loaded');
    expect(page.treeItems[0].subItems).toEqual([]);
  });

  it('preserves a node after child failure and retries on collapse then re-expansion', () => {
    start();
    expand('root');
    http.expectOne('/api/addresses/root/children').error(new ProgressEvent('error'));
    fixture.changeDetectorRef.detectChanges();
    expect(page.childStates.get('root')).toBe('error');
    expect(findItem('root')?.addressId).toBe('root');
    expect(findItem('root')?.subItems?.[0].label).toContain('Falha ao carregar');
    expect(findItem('root')?.subItems?.[0].isSelectable).toBe(false);

    const tree = fixture.debugElement.query(By.css('po-tree-view')).componentInstance as PoTreeViewComponent;
    tree.expanded.emit({ ...findItem('root')!, expanded: false });
    fixture.changeDetectorRef.detectChanges();
    expand('root');
    http.expectOne('/api/addresses/root/children').flush([]);
    expect(page.childStates.get('root')).toBe('loaded');
  });

  it('keeps inactive Addresses in place and loads their children', () => {
    start([address('inactive-parent', null, false)]);
    expect(page.treeItems[0].label).toContain('(Inativo)');
    expand('inactive-parent');
    http.expectOne('/api/addresses/inactive-parent/children').flush([address('child', 'inactive-parent')]);
    fixture.changeDetectorRef.detectChanges();
    expect(findItem('inactive-parent')?.addressId).toBe('inactive-parent');
    expect(findItem('child')?.addressId).toBe('child');
    expect(page.addresses.has('child')).toBe(true);
  });

  it('selects real Addresses only and keeps placeholders non-selectable', () => {
    start();
    const placeholder = page.treeItems[0].subItems![0] as AddressTreeItem;
    expect(placeholder.kind).toBe('placeholder');
    expect(placeholder.isSelectable).toBe(false);
    const tree = fixture.debugElement.query(By.css('po-tree-view')).componentInstance as PoTreeViewComponent;
    tree.selected.emit(placeholder);
    expect(page.selectedAddressId).toBeNull();

    tree.selected.emit(page.treeItems[0]);
    fixture.changeDetectorRef.detectChanges();
    expect(page.selectedAddressId).toBe('root');
    expect(page.selectedAddress?.id).toBe('root');
  });

  it('grows maxLevel before passing deep items and loads beyond level four', () => {
    start([address('level-1')]);
    const levels = 6;
    for (let level = 1; level < levels; level++) {
      const parentId = `level-${level}`;
      expand(parentId);
      http.expectOne(`/api/addresses/${parentId}/children`).flush([address(`level-${level + 1}`, parentId)]);
      fixture.changeDetectorRef.detectChanges();
    }

    expect(page.maxLevel).toBeGreaterThan(4);
    expect(findItem('level-6')?.addressId).toBe('level-6');
    expect(page.childStates.get('level-6')).toBe('unloaded');
    expect((findItem('level-6')?.subItems?.[0] as AddressTreeItem).kind).toBe('placeholder');
    fixture.changeDetectorRef.detectChanges();
    const tree = fixture.debugElement.query(By.css('po-tree-view')).componentInstance as PoTreeViewComponent;
    expect(tree.maxLevel).toBe(page.maxLevel);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Address level-6');

    expand('level-6');
    const deeper = http.expectOne('/api/addresses/level-6/children');
    deeper.flush([address('level-7', 'level-6')]);
    fixture.changeDetectorRef.detectChanges();
    expect(findItem('level-7')?.addressId).toBe('level-7');
  });
});
