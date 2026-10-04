import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { PoButtonModule, PoPageModule, PoTreeViewItem, PoTreeViewModule } from '@po-ui/ng-components';
import { Address } from './address.model';
import { AddressService } from './address.service';

type ChildLoadState = 'unloaded' | 'loading' | 'loaded' | 'error';

export type AddressTreeItem = PoTreeViewItem & {
  kind: 'address' | 'placeholder';
  addressId?: string;
};

const MIN_TREE_LEVELS_FOR_ROOT_PLACEHOLDER = 2;

@Component({
  selector: 'app-address-administration',
  standalone: true,
  imports: [CommonModule, PoButtonModule, PoPageModule, PoTreeViewModule],
  templateUrl: './address-administration.html',
  styleUrl: './address-administration.scss',
})
export class AddressAdministration implements OnInit {
  private readonly service = inject(AddressService);

  roots: Address[] = [];
  treeItems: AddressTreeItem[] = [];
  readonly childStates = new Map<string, ChildLoadState>();
  readonly loadedChildren = new Map<string, Address[]>();
  readonly addresses = new Map<string, Address>();
  readonly expandedIds = new Set<string>();
  selectedAddressId: string | null = null;
  loadingRoots = true;
  rootError = false;
  treeRenderError = false;
  maxLevel = MIN_TREE_LEVELS_FOR_ROOT_PLACEHOLDER;

  get selectedAddress(): Address | null {
    return this.selectedAddressId ? this.addresses.get(this.selectedAddressId) ?? null : null;
  }

  ngOnInit(): void {
    this.loadRoots();
  }

  loadRoots(): void {
    this.loadingRoots = true;
    this.rootError = false;
    this.service.listRoots().subscribe({
      next: roots => {
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

  private loadChildren(id: string): void {
    if (this.childStates.get(id) === 'loading' || this.childStates.get(id) === 'loaded') return;

    this.childStates.set(id, 'loading');
    this.refreshTreeItems();
    this.service.listChildren(id).subscribe({
      next: children => {
        this.loadedChildren.set(id, children);
        this.childStates.set(id, 'loaded');
        for (const child of children) {
          this.addresses.set(child.id, child);
          if (!this.childStates.has(child.id)) this.childStates.set(child.id, 'unloaded');
        }
        this.refreshTreeItems();
      },
      error: () => {
        this.childStates.set(id, 'error');
        this.refreshTreeItems();
      },
    });
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
