import { provideHttpClient } from '@angular/common/http';
import { HttpErrorResponse } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PoNotificationService } from '@po-ui/ng-components';
import { ItemCategory } from '../item-category.model';
import { ItemCategoryAdministration } from './item-category-administration';

const activeCategory: ItemCategory = { id: 'cat-1', name: 'Cable', active: true, version: 4 };
const inactiveCategory: ItemCategory = { id: 'cat-2', name: 'Power', active: false, version: 7 };

describe('ItemCategoryAdministration', () => {
  let http: HttpTestingController;
  let component: ItemCategoryAdministration;
  let fixture: ReturnType<typeof TestBed.createComponent<ItemCategoryAdministration>>;
  const feedback = { success: vi.fn(), error: vi.fn(), warning: vi.fn(), information: vi.fn() };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [ItemCategoryAdministration],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: PoNotificationService, useValue: feedback }],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ItemCategoryAdministration);
    component = fixture.componentInstance;
    fixture.changeDetectorRef.detectChanges();
  });

  afterEach(() => http.verify());

  function finishInitialList(items: ItemCategory[] = []): void {
    const req = http.expectOne('/api/item-categories');
    expect(req.request.method).toBe('GET');
    req.flush(items);
    fixture.changeDetectorRef.detectChanges();
  }

  it('loads and represents active and inactive categories', () => {
    finishInitialList([activeCategory, inactiveCategory]);
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(component.items.map(item => item.id)).toEqual(['cat-1', 'cat-2']);
    expect(text).toContain('Cable');
    expect(text).toContain('Ativa');
    expect(text).toContain('Power');
    expect(text).toContain('Inativa');
    expect(component.columns.map(column => column.property)).toEqual(['name', 'status']);
  });

  it('disables PO UI sorting for every category table column', () => {
    finishInitialList();
    expect(component.columns.length).toBeGreaterThan(0);
    expect(component.columns.every(column => column.sortable === false)).toBe(true);
  });

  it('shows an empty state while keeping create available', () => {
    finishInitialList();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Nenhuma categoria de itens cadastrada.');
    expect(text).toContain('Nova categoria de itens');
  });

  it('shows a safe list error and retries only after user action', () => {
    http.expectOne('/api/item-categories').flush('private server detail', { status: 500, statusText: 'Server Error' });
    fixture.changeDetectorRef.detectChanges();
    const html = fixture.nativeElement as HTMLElement;
    expect(html.textContent).toContain('Não foi possível carregar as categorias de itens.');
    expect(html.textContent).not.toContain('private server detail');
    component.load();
    http.expectOne('/api/item-categories').flush([activeCategory]);
    expect(component.items[0].id).toBe(activeCategory.id);
  });

  it('posts only the entered name and uses the created server representation', () => {
    finishInitialList();
    component.startCreate();
    component.name = '  Board  ';
    component.save();
    const req = http.expectOne('/api/item-categories');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ name: '  Board  ' });
    const created: ItemCategory = { id: 'server-id', name: 'Board', active: true, version: 0 };
    req.flush(created, { status: 201, statusText: 'Created' });
    expect(component.items).toEqual([{ ...created, status: 'Ativa' }]);
    expect(component.formVisible).toBe(false);
  });

  it('does not submit a blank create name', () => {
    finishInitialList();
    component.startCreate();
    component.name = '   ';
    component.save();
    expect(component.submitted).toBe(true);
    http.expectNone('/api/item-categories');
  });

  it('renames with the row identity and current expected version, then adopts the response', () => {
    finishInitialList([activeCategory]);
    component.edit(component.items[0]);
    component.name = 'Wire';
    component.save();
    const req = http.expectOne('/api/item-categories/cat-1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ name: 'Wire', expectedVersion: 4 });
    const renamed: ItemCategory = { id: 'cat-1', name: 'Server spelling', active: true, version: 5 };
    req.flush(renamed);
    expect(component.items[0]).toEqual({ ...renamed, status: 'Ativa' });
  });

  it('offers activation only for inactive rows and adopts authoritative activation response', () => {
    finishInitialList([inactiveCategory]);
    const activateAction = component.actions[1];
    expect((activateAction.visible as (item: ItemCategory) => boolean)(inactiveCategory)).toBe(true);
    component.setActive(component.items[0], true);
    const req = http.expectOne('/api/item-categories/cat-2/activate');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ expectedVersion: 7 });
    const activated: ItemCategory = { id: 'cat-2', name: 'Power', active: true, version: 8 };
    req.flush(activated);
    expect(component.items[0]).toEqual({ ...activated, status: 'Ativa' });
  });

  it('offers deactivation only for active rows and adopts authoritative deactivation response', () => {
    finishInitialList([activeCategory]);
    const deactivateAction = component.actions[2];
    expect((deactivateAction.visible as (item: ItemCategory) => boolean)(activeCategory)).toBe(true);
    component.setActive(component.items[0], false);
    const req = http.expectOne('/api/item-categories/cat-1/deactivate');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ expectedVersion: 4 });
    const deactivated: ItemCategory = { id: 'cat-1', name: 'Cable', active: false, version: 5 };
    req.flush(deactivated);
    expect(component.items[0]).toEqual({ ...deactivated, status: 'Inativa' });
  });

  it('surfaces duplicate-name conflict without exposing response details', () => {
    finishInitialList();
    component.startCreate();
    component.name = 'Cable';
    component.save();
    http.expectOne('/api/item-categories').flush(
      { code: 'ITEM_CATEGORY_NAME_ALREADY_EXISTS', message: 'database constraint details' },
      { status: 409, statusText: 'Conflict' },
    );
    expect(feedback.error).toHaveBeenCalledWith('Já existe uma categoria de itens com esse nome.');
    expect(feedback.error).not.toHaveBeenCalledWith(expect.stringContaining('database constraint'));
  });

  it('surfaces stale-version conflict, does not retry, and offers user-controlled reload', () => {
    finishInitialList([activeCategory]);
    component.setActive(component.items[0], false);
    http.expectOne('/api/item-categories/cat-1/deactivate').flush(
      { code: 'ITEM_CATEGORY_CONCURRENT_MODIFICATION' },
      { status: 409, statusText: 'Conflict' },
    );
    expect(feedback.error).toHaveBeenCalledWith('A categoria foi alterada por outra operação. Atualize a lista antes de tentar novamente.');
    expect(component.refreshRequired).toBe(true);
    http.expectNone('/api/item-categories/cat-1/deactivate');
    component.load();
    http.expectOne('/api/item-categories').flush([{ ...activeCategory, version: 5 }]);
    expect(component.refreshRequired).toBe(false);
    expect(component.items[0].version).toBe(5);
  });

  it('keeps a stale row after mutation 404 and offers authoritative manual refresh', () => {
    finishInitialList([activeCategory]);
    component.setActive(component.items[0], false);
    http.expectOne('/api/item-categories/cat-1/deactivate').flush(
      { code: 'ITEM_CATEGORY_NOT_FOUND' },
      { status: 404, statusText: 'Not Found' },
    );
    fixture.changeDetectorRef.detectChanges();

    expect(feedback.error).toHaveBeenCalledWith('A categoria de itens não foi encontrada. Atualize a lista e tente novamente.');
    expect(component.refreshRequired).toBe(true);
    expect(component.items).toEqual([{ ...activeCategory, status: 'Ativa' }]);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Atualizar lista');
    http.expectNone('/api/item-categories/cat-1/deactivate');

    component.load();
    http.expectOne('/api/item-categories').flush([]);
    fixture.changeDetectorRef.detectChanges();

    expect(component.items).toEqual([]);
    expect(component.refreshRequired).toBe(false);
  });

  it('maps invalid input and not-found responses to safe messages', () => {
    finishInitialList();
    const map = (component as unknown as { messageFor(error: unknown): string }).messageFor.bind(component);
    expect(map(new HttpErrorResponse({ status: 400 }))).toContain('Confira os dados');
    expect(map(new HttpErrorResponse({ status: 404 }))).toContain('não foi encontrada');
  });

  it('surfaces unexpected request failure without leaking server detail', () => {
    finishInitialList();
    component.startCreate();
    component.name = 'Cable';
    component.save();
    http.expectOne('/api/item-categories').flush('internal stack trace', { status: 500, statusText: 'Server Error' });
    expect(feedback.error).toHaveBeenCalledWith('Não foi possível concluir a solicitação. Verifique sua conexão e tente novamente.');
    expect(feedback.error).not.toHaveBeenCalledWith(expect.stringContaining('internal stack trace'));
  });
});
