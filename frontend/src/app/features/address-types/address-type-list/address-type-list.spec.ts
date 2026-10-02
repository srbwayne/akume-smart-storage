import { provideHttpClient } from '@angular/common/http';
import { HttpErrorResponse } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PoNotificationService } from '@po-ui/ng-components';
import { AddressType } from '../address-type.model';
import { AddressTypeList } from './address-type-list';

const entry: AddressType = { id: 'a-1', code: 'BIN', name: 'Bin', description: 'Storage bin', active: true };

describe('AddressTypeList', () => {
  let http: HttpTestingController;
  let component: AddressTypeList;
  let fixture: ReturnType<typeof TestBed.createComponent<AddressTypeList>>;
  const feedback = { success: vi.fn(), error: vi.fn(), warning: vi.fn(), information: vi.fn() };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [AddressTypeList],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: PoNotificationService, useValue: feedback }],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AddressTypeList);
    component = fixture.componentInstance;
    fixture.changeDetectorRef.detectChanges();
  });

  afterEach(() => http.verify());

  function finishInitialList(items: AddressType[] = []): void {
    const req = http.expectOne('/api/address-types');
    expect(req.request.method).toBe('GET');
    req.flush(items);
    fixture.changeDetectorRef.detectChanges();
  }

  it('issues list request and renders populated records and active status', () => {
    finishInitialList([entry]);
    const html = fixture.nativeElement as HTMLElement;
    expect(component.items).toHaveLength(1);
    expect(html.textContent).toContain('BIN');
    expect(html.textContent).toContain('Bin');
    expect(html.textContent).toContain('Ativo');
    expect(component.columns.map(column => column.label)).toEqual(['Código', 'Nome', 'Descrição', 'Status']);
  });

  it('handles an empty list', () => {
    finishInitialList();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Nenhum tipo de endereço cadastrado.');
  });

  it('requires code for create', () => {
    finishInitialList();
    component.startCreate(); component.name = 'Shelf'; component.save();
    expect(component.submitted).toBe(true);
    http.expectNone('/api/address-types');
  });

  it('requires name for create', () => {
    finishInitialList();
    component.startCreate(); component.code = 'SHELF'; component.save();
    expect(component.submitted).toBe(true);
    http.expectNone('/api/address-types');
  });

  it('posts only allowed create fields and reflects returned representation', () => {
    finishInitialList();
    component.startCreate(); component.code = 'SHELF'; component.name = 'Shelf'; component.description = 'Tall'; component.save();
    const req = http.expectOne('/api/address-types');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ code: 'SHELF', name: 'Shelf', description: 'Tall' });
    req.flush({ id: 'a-2', code: 'SHELF', name: 'Shelf', description: 'Tall', active: true });
    expect(component.items.map(item => item.code)).toEqual(['SHELF']);
    expect(component.formVisible).toBe(false);
    expect(feedback.success).toHaveBeenCalledWith('Tipo de endereço criado com sucesso.');
  });

  it('does not allow code mutation while editing', () => {
    finishInitialList([entry]); component.edit(entry);
    fixture.changeDetectorRef.detectChanges();
    expect(component.code).toBe('BIN');
    expect((fixture.nativeElement as HTMLElement).querySelector('[name="code"]')).toBeNull();
  });

  it('updates only name and description', () => {
    finishInitialList([entry]); component.edit(entry); component.name = 'Storage bin'; component.description = ''; component.save();
    const req = http.expectOne('/api/address-types/a-1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ name: 'Storage bin', description: null });
    req.flush({ ...entry, name: 'Storage bin' });
    expect(component.items[0].name).toBe('Storage bin');
  });

  it('offers deactivation for active records and activation for inactive records', () => {
    finishInitialList([entry, { ...entry, id: 'a-2', active: false }]);
    const visible = (index: number, item: AddressType) => (component.actions[index].visible as (value: AddressType) => boolean)(item);
    expect(visible(1, entry)).toBe(false);
    expect(visible(2, entry)).toBe(true);
    expect(visible(1, { ...entry, active: false })).toBe(true);
    expect(visible(2, { ...entry, active: false })).toBe(false);
  });

  it('activates through activation endpoint and uses response', () => {
    finishInitialList([{ ...entry, active: false }]);
    component.setActive(component.items[0], true);
    const req = http.expectOne('/api/address-types/a-1/activate');
    expect(req.request.method).toBe('POST'); req.flush(entry);
    expect(component.items[0].active).toBe(true);
  });

  it('deactivates through deactivation endpoint and uses response', () => {
    finishInitialList([entry]); component.setActive(component.items[0], false);
    const req = http.expectOne('/api/address-types/a-1/deactivate');
    expect(req.request.method).toBe('POST'); req.flush({ ...entry, active: false });
    expect(component.items[0].active).toBe(false);
  });

  it('shows understandable duplicate code feedback for 409', () => {
    finishInitialList(); component.startCreate(); component.code = 'BIN'; component.name = 'Bin'; component.save();
    http.expectOne('/api/address-types').flush({ message: 'private backend details' }, { status: 409, statusText: 'Conflict' });
    expect(feedback.error).toHaveBeenCalledWith('Este código já está cadastrado.');
  });

  it('shows safe feedback for unexpected failures', () => {
    finishInitialList(); component.load();
    http.expectOne('/api/address-types').flush('internal stack', { status: 500, statusText: 'Server Error' });
    expect(component.loadError).toContain('Não foi possível concluir');
    expect(component.loadError).not.toContain('internal stack');
  });

  it('maps 400 and 404 to user-facing messages', () => {
    finishInitialList();
    const map = (component as unknown as { messageFor(error: unknown): string }).messageFor.bind(component);
    expect(map(new HttpErrorResponse({ status: 400 }))).toContain('Confira os dados');
    expect(map(new HttpErrorResponse({ status: 404 }))).toContain('não foi encontrado');
  });

  it('prevents repeated lifecycle requests while one is pending', () => {
    finishInitialList([entry]); component.setActive(entry, false); component.setActive(entry, false);
    const requests = http.match('/api/address-types/a-1/deactivate');
    expect(requests).toHaveLength(1);
    requests[0].flush({ ...entry, active: false });
  });
});
