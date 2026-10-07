import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PoButtonModule, PoFieldModule, PoNotificationService, PoPageModule, PoTableModule } from '@po-ui/ng-components';
import { CreateItemCategoryRequest, ItemCategory, RenameItemCategoryRequest } from '../item-category.model';
import { ItemCategoryService } from '../item-category.service';

@Component({
  selector: 'app-item-category-administration',
  standalone: true,
  imports: [CommonModule, FormsModule, PoButtonModule, PoFieldModule, PoPageModule, PoTableModule],
  templateUrl: './item-category-administration.html',
  styleUrl: './item-category-administration.scss',
})
export class ItemCategoryAdministration implements OnInit {
  private readonly service = inject(ItemCategoryService);
  private readonly notifications = inject(PoNotificationService);

  items: Array<ItemCategory & { status: string }> = [];
  loading = true;
  loadError = '';
  formVisible = false;
  editing: ItemCategory | null = null;
  name = '';
  submitted = false;
  saving = false;
  busyIds = new Set<string>();
  refreshRequired = false;

  readonly columns = [
    { property: 'name', label: 'Nome', sortable: false },
    { property: 'status', label: 'Status', sortable: false },
  ];

  readonly actions = [
    { label: 'Renomear', action: (item: ItemCategory) => this.edit(item) },
    { label: 'Ativar', action: (item: ItemCategory) => this.setActive(item, true), visible: (item: ItemCategory) => !item.active },
    { label: 'Desativar', action: (item: ItemCategory) => this.setActive(item, false), visible: (item: ItemCategory) => item.active },
  ];

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.loadError = '';
    this.refreshRequired = false;
    this.service.list().subscribe({
      next: items => {
        this.items = items.map(item => this.withStatus(item));
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.loadError = 'Não foi possível carregar as categorias de itens.';
      },
    });
  }

  startCreate(): void {
    if (this.saving) return;
    this.editing = null;
    this.name = '';
    this.submitted = false;
    this.formVisible = true;
  }

  edit(item: ItemCategory): void {
    if (this.busyIds.has(item.id)) return;
    this.editing = item;
    this.name = item.name;
    this.submitted = false;
    this.formVisible = true;
  }

  closeForm(): void {
    if (!this.saving) this.formVisible = false;
  }

  save(): void {
    this.submitted = true;
    if (!this.name.trim() || this.saving) return;

    this.saving = true;
    const target = this.editing;
    const request = target
      ? this.service.rename(target.id, { name: this.name, expectedVersion: target.version } satisfies RenameItemCategoryRequest)
      : this.service.create({ name: this.name } satisfies CreateItemCategoryRequest);

    request.subscribe({
      next: result => {
        this.upsert(result);
        this.saving = false;
        this.formVisible = false;
        this.notifications.success(target ? 'Categoria de itens renomeada com sucesso.' : 'Categoria de itens criada com sucesso.');
      },
      error: error => {
        this.saving = false;
        this.reportOperationError(error);
      },
    });
  }

  setActive(item: ItemCategory, active: boolean): void {
    if (this.busyIds.has(item.id)) return;
    this.busyIds.add(item.id);
    const request = active
      ? this.service.activate(item.id, { expectedVersion: item.version })
      : this.service.deactivate(item.id, { expectedVersion: item.version });

    request.subscribe({
      next: result => {
        this.upsert(result);
        this.busyIds.delete(item.id);
        this.notifications.success(active ? 'Categoria de itens ativada.' : 'Categoria de itens desativada.');
      },
      error: error => {
        this.busyIds.delete(item.id);
        this.reportOperationError(error);
      },
    });
  }

  private withStatus(item: ItemCategory): ItemCategory & { status: string } {
    return { ...item, status: item.active ? 'Ativa' : 'Inativa' };
  }

  private upsert(item: ItemCategory): void {
    const normalized = this.withStatus(item);
    const index = this.items.findIndex(existing => existing.id === item.id);
    this.items = index < 0
      ? [...this.items, normalized]
      : this.items.map(existing => existing.id === item.id ? normalized : existing);
  }

  private reportOperationError(error: unknown): void {
    const message = this.messageFor(error);
    if (error instanceof HttpErrorResponse && (error.status === 404
      || (error.status === 409 && this.errorCode(error) === 'ITEM_CATEGORY_CONCURRENT_MODIFICATION'))) {
      this.refreshRequired = true;
    }
    this.notifications.error(message);
  }

  private errorCode(error: HttpErrorResponse): string | undefined {
    const body: unknown = error.error;
    if (typeof body === 'object' && body !== null && 'code' in body && typeof body.code === 'string') {
      return body.code;
    }
    return undefined;
  }

  private messageFor(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 400) return 'Confira os dados informados e tente novamente.';
      if (error.status === 404) return 'A categoria de itens não foi encontrada. Atualize a lista e tente novamente.';
      if (error.status === 409 && this.errorCode(error) === 'ITEM_CATEGORY_NAME_ALREADY_EXISTS') {
        return 'Já existe uma categoria de itens com esse nome.';
      }
      if (error.status === 409 && this.errorCode(error) === 'ITEM_CATEGORY_CONCURRENT_MODIFICATION') {
        return 'A categoria foi alterada por outra operação. Atualize a lista antes de tentar novamente.';
      }
    }
    return 'Não foi possível concluir a solicitação. Verifique sua conexão e tente novamente.';
  }
}
