import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { PoButtonModule, PoFieldModule, PoNotificationService, PoPageModule, PoTableModule } from '@po-ui/ng-components';
import { AddressTypeService } from '../address-type.service';
import { AddressType, CreateAddressType, UpdateAddressType } from '../address-type.model';

@Component({
  selector: 'app-address-type-list',
  standalone: true,
  imports: [CommonModule, FormsModule, PoButtonModule, PoFieldModule, PoPageModule, PoTableModule],
  templateUrl: './address-type-list.html',
  styleUrl: './address-type-list.scss',
})
export class AddressTypeList implements OnInit {
  private readonly service = inject(AddressTypeService);
  private readonly notifications = inject(PoNotificationService);

  items: Array<AddressType & { status: string }> = [];
  loading = true;
  saving = false;
  busyIds = new Set<string>();
  loadError = '';
  formVisible = false;
  editing: AddressType | null = null;
  code = '';
  name = '';
  description = '';
  submitted = false;

  readonly columns = [
    { property: 'code', label: 'Código' },
    { property: 'name', label: 'Nome' },
    { property: 'description', label: 'Descrição' },
    { property: 'status', label: 'Status' },
  ];

  readonly actions = [
    { label: 'Editar', action: (item: AddressType) => this.edit(item) },
    { label: 'Ativar', action: (item: AddressType) => this.setActive(item, true), visible: (item: AddressType) => !item.active },
    { label: 'Desativar', action: (item: AddressType) => this.setActive(item, false), visible: (item: AddressType) => item.active },
  ];

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.loadError = '';
    this.service.list().subscribe({
      next: items => { this.items = items.map(item => ({ ...item, status: item.active ? 'Ativo' : 'Inativo' })); this.loading = false; },
      error: error => { this.loading = false; this.loadError = this.messageFor(error); },
    });
  }

  startCreate(): void {
    this.editing = null;
    this.code = '';
    this.name = '';
    this.description = '';
    this.submitted = false;
    this.formVisible = true;
  }

  edit(item: AddressType): void {
    this.editing = item;
    this.code = item.code;
    this.name = item.name;
    this.description = item.description ?? '';
    this.submitted = false;
    this.formVisible = true;
  }

  closeForm(): void { if (!this.saving) this.formVisible = false; }

  save(): void {
    this.submitted = true;
    if (!this.name.trim() || (!this.editing && !this.code.trim()) || this.saving) return;
    this.saving = true;
    const description = this.description.trim() || null;
    const request = this.editing
      ? this.service.update(this.editing.id, { name: this.name.trim(), description } satisfies UpdateAddressType)
      : this.service.create({ code: this.code.trim(), name: this.name.trim(), description } satisfies CreateAddressType);
    request.subscribe({
      next: result => {
        this.upsert(result);
        this.saving = false;
        this.formVisible = false;
        this.notifications.success(this.editing ? 'Tipo de endereço atualizado com sucesso.' : 'Tipo de endereço criado com sucesso.');
      },
      error: error => { this.saving = false; this.notifications.error(this.messageFor(error)); },
    });
  }

  setActive(item: AddressType, active: boolean): void {
    if (this.busyIds.has(item.id)) return;
    this.busyIds.add(item.id);
    const request = active ? this.service.activate(item.id) : this.service.deactivate(item.id);
    request.subscribe({
      next: result => { this.upsert(result); this.busyIds.delete(item.id); this.notifications.success(active ? 'Tipo de endereço ativado.' : 'Tipo de endereço desativado.'); },
      error: error => { this.busyIds.delete(item.id); this.notifications.error(this.messageFor(error)); },
    });
  }

  private upsert(item: AddressType): void {
    const normalized = { ...item, status: item.active ? 'Ativo' : 'Inativo' };
    const index = this.items.findIndex(existing => existing.id === item.id);
    this.items = index < 0 ? [...this.items, normalized] : this.items.map(existing => existing.id === item.id ? normalized : existing);
  }

  private messageFor(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 400) return 'Confira os dados informados e tente novamente.';
      if (error.status === 404) return 'O tipo de endereço não foi encontrado. Atualize a lista e tente novamente.';
      if (error.status === 409) return 'Este código já está cadastrado.';
    }
    return 'Não foi possível concluir a solicitação. Verifique sua conexão e tente novamente.';
  }
}
