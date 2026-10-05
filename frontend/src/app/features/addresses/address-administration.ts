import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin, Subscription } from 'rxjs';
import { PoButtonModule, PoFieldModule, PoPageModule, PoTreeViewItem, PoTreeViewModule } from '@po-ui/ng-components';
import { AddressTypeService } from '../address-types/address-type.service';
import { AddressType } from '../address-types/address-type.model';
import { Address, AddressLifecycleRequest, CreateAddressRequest, MoveAddressRequest, RenameAddressRequest } from './address.model';
import { AddressService } from './address.service';

type ChildLoadState = 'unloaded' | 'loading' | 'loaded' | 'error';
type LifecycleAction = 'activate' | 'deactivate';
type LifecycleErrorKind = 'concurrency' | 'domain' | 'generic' | null;

export type AddressTreeItem = PoTreeViewItem & {
  kind: 'address' | 'placeholder';
  addressId?: string;
};

const MIN_TREE_LEVELS_FOR_ROOT_PLACEHOLDER = 2;

@Component({
  selector: 'app-address-administration',
  standalone: true,
  imports: [CommonModule, FormsModule, PoButtonModule, PoFieldModule, PoPageModule, PoTreeViewModule],
  templateUrl: './address-administration.html',
  styleUrl: './address-administration.scss',
})
export class AddressAdministration implements OnInit {
  private readonly service = inject(AddressService);
  private readonly addressTypeService = inject(AddressTypeService);

  roots: Address[] = [];
  treeItems: AddressTreeItem[] = [];
  readonly childStates = new Map<string, ChildLoadState>();
  readonly loadedChildren = new Map<string, Address[]>();
  readonly addresses = new Map<string, Address>();
  readonly expandedIds = new Set<string>();
  private readonly childRequests = new Map<string, Subscription>();
  private rootReadGeneration = 0;
  private moveOptionsRequest: Subscription | null = null;
  selectedAddressId: string | null = null;
  loadingRoots = true;
  rootError = false;
  treeRenderError = false;
  maxLevel = MIN_TREE_LEVELS_FOR_ROOT_PLACEHOLDER;
  createFormVisible = false;
  renameFormVisible = false;
  moveFormVisible = false;
  loadingCreateOptions = false;
  loadingMoveOptions = false;
  savingCreate = false;
  savingRename = false;
  savingMove = false;
  savingLifecycle = false;
  recoveringLifecycle = false;
  createSubmitted = false;
  renameSubmitted = false;
  createName = '';
  createAddressTypeId = '';
  createParentId = '';
  renameName = '';
  createError = '';
  renameError = '';
  moveError = '';
  lifecycleError = '';
  lifecycleErrorKind: LifecycleErrorKind = null;
  lifecycleRecoveryError = '';
  lifecycleStatus = '';
  createOptionsError = '';
  moveOptionsError = '';
  addressTypeOptions: Array<{ label: string; value: string }> = [];
  parentOptions: Array<{ label: string; value: string }> = [{ label: 'Endereço raiz', value: '' }];
  moveDestinationId = '';
  moveDestinationOptions: Array<{ label: string; value: string }> = [{ label: 'Endereço raiz', value: '' }];
  private renameTarget: Address | null = null;
  lifecycleConflictAddressId: string | null = null;
  moveTarget: Address | null = null;
  lifecycleAction: LifecycleAction | null = null;

  get selectedAddress(): Address | null {
    return this.selectedAddressId ? this.addresses.get(this.selectedAddressId) ?? null : null;
  }

  ngOnInit(): void {
    this.loadRoots();
  }

  loadRoots(): void {
    const generation = ++this.rootReadGeneration;
    this.loadingRoots = true;
    this.rootError = false;
    this.cancelChildRequests();
    this.service.listRoots().subscribe({
      next: roots => {
        if (generation !== this.rootReadGeneration) return;
        this.roots = roots;
        this.addresses.clear();
        this.childStates.clear();
        this.loadedChildren.clear();
        for (const root of roots) {
          this.addresses.set(root.id, root);
          this.childStates.set(root.id, 'unloaded');
        }
        if (this.selectedAddressId && !this.addresses.has(this.selectedAddressId)) this.selectedAddressId = null;
        this.loadingRoots = false;
        this.refreshTreeItems();
      },
      error: () => {
        if (generation !== this.rootReadGeneration) return;
        this.loadingRoots = false;
        this.rootError = true;
      },
    });
  }

  onExpanded(item: PoTreeViewItem): void {
    const treeItem = item as AddressTreeItem;
    if (treeItem.kind !== 'address' || !treeItem.addressId) return;

    const id = treeItem.addressId;
    if (treeItem.expanded) {
      this.expandedIds.add(id);
      const state = this.childStates.get(id) ?? 'unloaded';
      if (state === 'unloaded' || state === 'error') this.loadChildren(id);
      else this.refreshTreeItems();
    } else {
      this.expandedIds.delete(id);
      this.refreshTreeItems();
    }
  }

  onSelected(item: PoTreeViewItem): void {
    const treeItem = item as AddressTreeItem;
    if (treeItem.kind === 'address' && treeItem.addressId && this.addresses.has(treeItem.addressId)) {
      this.selectedAddressId = treeItem.addressId;
    }
  }

  startCreate(): void {
    if (this.savingCreate) return;
    this.createFormVisible = true;
    this.createSubmitted = false;
    this.createName = '';
    this.createAddressTypeId = '';
    this.createParentId = '';
    this.createError = '';
    this.loadCreateOptions();
  }

  loadCreateOptions(): void {
    if (this.loadingCreateOptions) return;
    this.loadingCreateOptions = true;
    this.createOptionsError = '';
    forkJoin({
      types: this.addressTypeService.list(),
      addresses: this.service.list(),
    }).subscribe({
      next: ({ types, addresses }) => {
        this.addressTypeOptions = types
          .filter((type: AddressType) => type.active)
          .map(type => ({ label: `${type.code} — ${type.name}`, value: type.id }));
        this.parentOptions = [
          { label: 'Endereço raiz', value: '' },
          ...addresses
            .filter(address => address.active)
            .map(address => ({ label: `${address.name} — ${address.id}`, value: address.id })),
        ];
        this.loadingCreateOptions = false;
      },
      error: () => {
        this.loadingCreateOptions = false;
        this.createOptionsError = 'Não foi possível carregar os dados para criar o endereço.';
      },
    });
  }

  closeCreate(): void {
    if (this.savingCreate) return;
    this.createFormVisible = false;
    this.createError = '';
  }

  saveCreate(): void {
    this.createSubmitted = true;
    if (!this.createName.trim() || !this.createAddressTypeId || this.savingCreate || this.loadingCreateOptions || this.createOptionsError) return;

    const request: CreateAddressRequest = {
      name: this.createName.trim(),
      addressTypeId: this.createAddressTypeId,
    };
    if (this.createParentId) request.parentId = this.createParentId;

    this.savingCreate = true;
    this.createError = '';
    this.service.create(request).subscribe({
      next: created => {
        this.savingCreate = false;
        this.createFormVisible = false;
        this.createName = '';
        this.createAddressTypeId = '';
        this.createParentId = '';
        this.createSubmitted = false;
        if (created.parentId) this.refreshParentChildren(created.parentId);
        else this.loadRoots();
      },
      error: () => {
        this.savingCreate = false;
        this.createError = 'Não foi possível criar o endereço. Verifique os dados e tente novamente.';
      },
    });
  }

  startRename(): void {
    const address = this.selectedAddress;
    if (!address || this.savingRename) return;
    this.renameTarget = address;
    this.renameName = address.name;
    this.renameSubmitted = false;
    this.renameError = '';
    this.renameFormVisible = true;
  }

  closeRename(): void {
    if (this.savingRename) return;
    this.renameFormVisible = false;
    this.renameTarget = null;
    this.renameError = '';
  }

  saveRename(): void {
    this.renameSubmitted = true;
    if (!this.renameTarget || !this.renameName.trim() || this.savingRename) return;

    const target = this.renameTarget;
    const request: RenameAddressRequest = {
      name: this.renameName.trim(),
      expectedVersion: target.version,
    };
    this.savingRename = true;
    this.renameError = '';
    this.service.rename(target.id, request).subscribe({
      next: updated => {
        this.replaceAddressSnapshot(updated);
        this.savingRename = false;
        this.renameFormVisible = false;
        this.renameTarget = null;
        this.renameSubmitted = false;
        this.renameError = '';
      },
      error: () => {
        this.savingRename = false;
        this.renameError = 'Não foi possível renomear o endereço. Verifique os dados e tente novamente.';
      },
    });
  }

  startMove(): void {
    const address = this.selectedAddress;
    if (!address || this.savingMove || this.moveFormVisible) return;
    this.moveTarget = address;
    this.moveDestinationId = address.parentId ?? '';
    this.moveError = '';
    this.moveFormVisible = true;
    this.loadMoveDestinations(address);
  }

  loadMoveDestinations(source: Address = this.moveTarget!): void {
    if (!source || this.loadingMoveOptions) return;
    this.loadingMoveOptions = true;
    this.moveOptionsError = '';
    this.moveOptionsRequest = this.service.list().subscribe({
      next: addresses => {
        if (this.moveTarget?.id !== source.id || !this.moveFormVisible) return;
        this.moveDestinationOptions = [
          { label: 'Endereço raiz', value: '' },
          ...addresses
            .filter(address => address.id !== source.id && (!source.active || address.active))
            .map(address => ({
              label: address.active ? `${address.name} — ${address.id}` : `${address.name} (Inativo) — ${address.id}`,
              value: address.id,
            })),
        ];
        this.loadingMoveOptions = false;
        this.moveOptionsRequest = null;
      },
      error: () => {
        if (this.moveTarget?.id !== source.id || !this.moveFormVisible) return;
        this.loadingMoveOptions = false;
        this.moveOptionsError = 'Não foi possível carregar os destinos disponíveis.';
        this.moveOptionsRequest = null;
      },
    });
  }

  closeMove(): void {
    if (this.savingMove) return;
    this.moveOptionsRequest?.unsubscribe();
    this.moveOptionsRequest = null;
    this.loadingMoveOptions = false;
    this.moveFormVisible = false;
    this.moveTarget = null;
    this.moveError = '';
  }

  saveMove(): void {
    if (!this.moveTarget || this.savingMove || this.loadingMoveOptions || this.moveOptionsError) return;

    const target = this.moveTarget;
    const request: MoveAddressRequest = {
      newParentId: this.moveDestinationId || null,
      expectedVersion: target.version,
    };
    this.savingMove = true;
    this.moveError = '';
    this.service.move(target.id, request).subscribe({
      next: updated => {
        this.replaceAddressSnapshot(updated);
        this.savingMove = false;
        this.moveFormVisible = false;
        this.moveTarget = null;
        this.moveError = '';
        this.reconcileMovedAddress(target, updated);
      },
      error: () => {
        this.savingMove = false;
        this.moveError = 'Não foi possível mover o endereço. Verifique o destino e tente novamente.';
      },
    });
  }

  activateSelectedAddress(): void {
    this.startLifecycle('activate');
  }

  deactivateSelectedAddress(): void {
    this.startLifecycle('deactivate');
  }

  recoverLifecycleConflict(): void {
    const id = this.lifecycleConflictAddressId;
    if (!id || this.recoveringLifecycle || this.savingLifecycle) return;

    this.recoveringLifecycle = true;
    this.lifecycleRecoveryError = '';
    this.service.get(id).subscribe({
      next: current => {
        const previous = this.addresses.get(id) ?? current;
        this.supersedeRootRead();
        this.replaceAddressSnapshot(current);
        if (previous.parentId !== current.parentId) this.reconcileMovedAddress(previous, current);
        else if (current.parentId && this.childRequests.has(current.parentId)) this.invalidateParentChildren(current.parentId);
        this.selectedAddressId = id;
        this.recoveringLifecycle = false;
        this.lifecycleConflictAddressId = null;
        this.lifecycleErrorKind = null;
        this.lifecycleError = '';
        this.lifecycleRecoveryError = '';
        this.lifecycleStatus = 'Estado atualizado. Revise-o antes de iniciar outra ação.';
      },
      error: () => {
        this.recoveringLifecycle = false;
        this.lifecycleRecoveryError = 'Não foi possível atualizar o endereço. Tente novamente.';
      },
    });
  }

  private startLifecycle(action: LifecycleAction): void {
    const selected = this.selectedAddress;
    if (!selected || this.savingLifecycle || this.recoveringLifecycle || this.lifecycleConflictAddressId) return;
    if ((action === 'activate' && selected.active) || (action === 'deactivate' && !selected.active)) return;

    const snapshot = { ...selected };
    const request: AddressLifecycleRequest = { expectedVersion: snapshot.version };
    this.lifecycleAction = action;
    this.savingLifecycle = true;
    this.lifecycleError = '';
    this.lifecycleErrorKind = null;
    this.lifecycleRecoveryError = '';
    this.lifecycleStatus = '';

    const mutation = action === 'activate'
      ? this.service.activate(snapshot.id, request)
      : this.service.deactivate(snapshot.id, request);
    mutation.subscribe({
      next: updated => {
        this.reconcileLifecycleSnapshot(snapshot, updated);
        this.selectedAddressId = snapshot.id;
        this.savingLifecycle = false;
        this.lifecycleAction = null;
        this.lifecycleError = '';
        this.lifecycleErrorKind = null;
        this.lifecycleStatus = action === 'activate' ? 'Endereço ativado.' : 'Endereço desativado.';
      },
      error: error => {
        this.savingLifecycle = false;
        this.lifecycleAction = null;
        this.presentLifecycleError(error, snapshot);
      },
    });
  }

  private presentLifecycleError(error: unknown, snapshot: Address): void {
    const code = error instanceof HttpErrorResponse && typeof error.error?.code === 'string' ? error.error.code : '';
    this.lifecycleStatus = '';
    this.lifecycleRecoveryError = '';
    if (code === 'ADDRESS_CONCURRENT_MODIFICATION') {
      this.lifecycleErrorKind = 'concurrency';
      this.lifecycleConflictAddressId = snapshot.id;
      this.lifecycleError = 'O endereço foi alterado desde o início da ação. Atualize o estado atual antes de tentar novamente.';
      return;
    }

    const domainMessages: Record<string, string> = {
      ADDRESS_HAS_ACTIVE_CHILDREN: 'O endereço não pode ser desativado enquanto tiver endereços filhos ativos.',
      ADDRESS_TYPE_INACTIVE: 'O endereço não pode ser ativado porque seu tipo está inativo.',
      ADDRESS_PARENT_INACTIVE: 'O endereço não pode ser ativado enquanto o endereço pai estiver inativo.',
    };
    if (domainMessages[code]) {
      this.lifecycleErrorKind = 'domain';
      this.lifecycleError = domainMessages[code];
      return;
    }

    this.lifecycleErrorKind = 'generic';
    this.lifecycleError = 'Não foi possível alterar o estado do endereço. Tente novamente.';
  }

  private reconcileLifecycleSnapshot(previous: Address, updated: Address): void {
    this.supersedeRootRead();
    if (previous.parentId !== updated.parentId) {
      this.replaceAddressSnapshot(updated);
      this.reconcileMovedAddress(previous, updated);
      return;
    }

    if (updated.parentId && this.childRequests.has(updated.parentId)) this.invalidateParentChildren(updated.parentId);
    this.replaceAddressSnapshot(updated);
  }

  private supersedeRootRead(): void {
    this.rootReadGeneration += 1;
    if (this.loadingRoots) this.loadingRoots = false;
  }

  private reconcileMovedAddress(previous: Address, updated: Address): void {
    if (previous.parentId === updated.parentId) {
      this.refreshTreeItems();
      return;
    }

    if (previous.parentId) this.invalidateParentChildren(previous.parentId);
    if (updated.parentId) this.invalidateParentChildren(updated.parentId);

    if (previous.parentId === null || updated.parentId === null) this.reloadRootsAfterMove();
  }

  private invalidateParentChildren(parentId: string): void {
    if (!this.childStates.has(parentId)) return;
    this.childRequests.get(parentId)?.unsubscribe();
    this.childRequests.delete(parentId);
    this.loadedChildren.delete(parentId);
    this.childStates.set(parentId, 'unloaded');
    this.refreshTreeItems();
    if (this.expandedIds.has(parentId)) this.loadChildren(parentId);
  }

  private reloadRootsAfterMove(): void {
    const generation = ++this.rootReadGeneration;
    this.roots = [];
    this.treeItems = [];
    this.loadingRoots = true;
    this.rootError = false;
    this.service.listRoots().subscribe({
      next: roots => {
        if (generation !== this.rootReadGeneration) return;
        this.roots = roots;
        for (const root of roots) {
          this.addresses.set(root.id, root);
          if (!this.childStates.has(root.id)) this.childStates.set(root.id, 'unloaded');
        }
        this.loadingRoots = false;
        this.refreshTreeItems();
      },
      error: () => {
        if (generation !== this.rootReadGeneration) return;
        this.loadingRoots = false;
        this.rootError = true;
      },
    });
  }

  private loadChildren(id: string): void {
    if (this.childStates.get(id) === 'loading' || this.childStates.get(id) === 'loaded') return;

    this.childStates.set(id, 'loading');
    this.refreshTreeItems();
    const request = this.service.listChildren(id).subscribe({
      next: children => {
        this.childRequests.delete(id);
        this.loadedChildren.set(id, children);
        this.childStates.set(id, 'loaded');
        for (const child of children) {
          this.addresses.set(child.id, child);
          if (!this.childStates.has(child.id)) this.childStates.set(child.id, 'unloaded');
        }
        this.refreshTreeItems();
      },
      error: () => {
        this.childRequests.delete(id);
        this.childStates.set(id, 'error');
        this.refreshTreeItems();
      },
    });
    this.childRequests.set(id, request);
  }

  private refreshParentChildren(parentId: string): void {
    if (!this.childStates.has(parentId)) return;
    this.childRequests.get(parentId)?.unsubscribe();
    this.childRequests.delete(parentId);
    this.loadedChildren.delete(parentId);
    this.childStates.set(parentId, 'unloaded');
    this.refreshTreeItems();
    if (this.expandedIds.has(parentId)) this.loadChildren(parentId);
  }

  private replaceAddressSnapshot(updated: Address): void {
    this.addresses.set(updated.id, updated);
    this.roots = this.roots.map(address => address.id === updated.id ? updated : address);
    for (const [parentId, children] of this.loadedChildren) {
      this.loadedChildren.set(parentId, children.map(address => address.id === updated.id ? updated : address));
    }
    this.refreshTreeItems();
  }

  private cancelChildRequests(): void {
    for (const request of this.childRequests.values()) request.unsubscribe();
    this.childRequests.clear();
  }

  private refreshTreeItems(): void {
    const items = this.roots.map(root => this.toTreeItem(root));
    if (!items.length) {
      this.treeRenderError = false;
      this.maxLevel = MIN_TREE_LEVELS_FOR_ROOT_PLACEHOLDER;
      this.treeItems = [];
      return;
    }
    const neededLevel = this.deepestLevel(items);
    if (!Number.isSafeInteger(neededLevel) || neededLevel < 1) {
      this.treeRenderError = true;
      return;
    }

    this.treeRenderError = false;
    // Grow the PO UI rendering limit before changing p-items; this is UI depth, not a domain limit.
    this.maxLevel = Math.max(MIN_TREE_LEVELS_FOR_ROOT_PLACEHOLDER, neededLevel);
    this.treeItems = items;
  }

  private toTreeItem(address: Address): AddressTreeItem {
    const state = this.childStates.get(address.id) ?? 'unloaded';
    let subItems: AddressTreeItem[];

    if (state === 'loaded') {
      subItems = (this.loadedChildren.get(address.id) ?? []).map(child => this.toTreeItem(child));
    } else {
      subItems = [this.placeholder(address.id, state)];
    }

    return {
      kind: 'address',
      addressId: address.id,
      label: address.active ? address.name : `${address.name} (Inativo)`,
      value: address.id,
      expanded: this.expandedIds.has(address.id),
      selected: this.selectedAddressId === address.id,
      isSelectable: true,
      subItems,
    };
  }

  private placeholder(addressId: string, state: ChildLoadState): AddressTreeItem {
    const labels: Record<ChildLoadState, string> = {
      unloaded: 'Expandido para carregar filhos.',
      loading: 'Carregando filhos…',
      loaded: '',
      error: 'Falha ao carregar filhos. Recolha e expanda para tentar novamente.',
    };
    return {
      kind: 'placeholder',
      label: labels[state],
      value: `__address-tree-${state}:${addressId}`,
      isSelectable: false,
    };
  }

  private deepestLevel(items: AddressTreeItem[]): number {
    let deepest = 0;
    const pending: Array<{ items: AddressTreeItem[]; level: number }> = [{ items, level: 1 }];
    while (pending.length) {
      const current = pending.pop()!;
      deepest = Math.max(deepest, current.level);
      for (const item of current.items) {
        if (item.subItems?.length) pending.push({ items: item.subItems as AddressTreeItem[], level: current.level + 1 });
      }
    }
    return deepest;
  }
}
