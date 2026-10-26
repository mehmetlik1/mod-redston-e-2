package com.example.materialgenerators;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/**
 * Slots 0-53 = the pocket of the selected bay (take only), 54-80 = player inventory, 81-89 = hotbar.
 * Button ids: 0 = on/off, 1 = items/blocks, 2 = next material, 3 = put the generator on the cursor into the bay
 * (or take the bay's generator out), 4 = stop/start every generator in the hub, 10.. = select a bay.
 * Shift-clicking a generator from your inventory installs it in the first free bay.
 *
 * Opened from a hub ITEM (sneak + right-click), "sourceStack" is that item: the menu works on a temporary copy of
 * the hub's data, keeps all bays running while open, and writes everything back into the item when it closes.
 */
public class GeneratorHubMenu extends AbstractContainerMenu {
    public static final int BUTTON_TOGGLE = 0;
    public static final int BUTTON_MODE = 1;
    public static final int BUTTON_VARIANT = 2;
    public static final int BUTTON_INSTALL = 3;
    /** Stops every generator in the hub if any is running, otherwise starts them all. */
    public static final int BUTTON_TOGGLE_ALL = 4;
    public static final int BUTTON_SELECT = 10;

    private static final int MAX = GeneratorHubBlockEntity.MAX_BAYS;
    /** Synced numbers: [0] bay count, then per bay (generator index + 1, on/off), then 5 details of the selected bay. */
    private static final int DETAILS = 1 + MAX * 2;
    private static final int DATA_COUNT = DETAILS + 5;
    private static final int POCKET = GeneratorHubBlockEntity.POCKET;
    private static final int PLAYER_START = POCKET;
    private static final int PLAYER_END = POCKET + 36;

    @Nullable
    private final GeneratorHubBlockEntity hub;
    private final ContainerData data;
    private final DataSlot selected = DataSlot.standalone();
    @Nullable
    private final ItemStack sourceStack;
    private final Player owner;
    private int saveCounter = 0;

    /** Client side. */
    public GeneratorHubMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, null, new SimpleContainer(POCKET), new SimpleContainerData(DATA_COUNT), null);
    }

    /** Server side, for a placed hub block. */
    public GeneratorHubMenu(int id, Inventory playerInventory, GeneratorHubBlockEntity hub) {
        this(id, playerInventory, hub, null, null, null);
    }

    /** Server side, opened from the hub item "sourceStack". */
    public GeneratorHubMenu(int id, Inventory playerInventory, GeneratorHubBlockEntity hub, ItemStack sourceStack) {
        this(id, playerInventory, hub, null, null, sourceStack);
    }

    private GeneratorHubMenu(int id, Inventory playerInventory, @Nullable GeneratorHubBlockEntity hub,
                             @Nullable Container clientPocket, @Nullable ContainerData clientData,
                             @Nullable ItemStack sourceStack) {
        super(ModRegistry.HUB_MENU.get(), id);
        this.hub = hub;
        this.sourceStack = sourceStack;
        this.owner = playerInventory.player;
        if (sourceStack != null) {
            HeldGenerator.OPEN.add(sourceStack);
        }
        this.data = hub != null ? new HubData(hub, selected) : clientData;
        checkContainerDataCount(this.data, DATA_COUNT);
        Container pocket = hub != null ? new PocketContainer(hub, selected) : clientPocket;

        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new OutputSlot(pocket, col + row * 9, 8 + col * 18, 46 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new CarriedGui.LockableSlot(playerInventory, col + row * 9 + 9, 8 + col * 18, 168 + row * 18, sourceStack));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new CarriedGui.LockableSlot(playerInventory, col, 8 + col * 18, 226, sourceStack));
        }
        addDataSlots(this.data);
        addDataSlot(selected);
    }

    // ---- state the screen reads (client + server) ----

    public int getSelectedBay() {
        return Math.floorMod(selected.get(), MAX);
    }

    /** How many bays this hub has (grows with Hub Expansions). */
    public int getBayCount() {
        return data.get(0);
    }

    /** The generator in that bay, or null if the bay is empty. */
    @Nullable
    public GeneratorBlock bayGenerator(int bay) {
        return ModRegistry.generatorAt(data.get(1 + bay * 2) - 1);
    }

    public boolean bayEnabled(int bay) {
        return data.get(2 + bay * 2) != 0;
    }

    // details of the SELECTED bay

    public int selectedProgress() {
        return data.get(DETAILS);
    }

    public int selectedInterval() {
        return data.get(DETAILS + 1);
    }

    public boolean selectedMakesBlocks() {
        return data.get(DETAILS + 2) != 0;
    }

    public boolean selectedHasBlockForm() {
        return data.get(DETAILS + 3) != 0;
    }

    public int selectedVariant() {
        return data.get(DETAILS + 4);
    }

    // ---- server-side actions ----

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (hub == null) {
            return false;
        }
        if (id >= BUTTON_SELECT && id < BUTTON_SELECT + MAX) {
            if (id - BUTTON_SELECT < hub.getBayCount()) {
                selected.set(id - BUTTON_SELECT);
                return true;
            }
            return false;
        }
        GeneratorHubBlockEntity.Bay bay = hub.bay(selected.get());
        switch (id) {
            case BUTTON_TOGGLE:
                bay.toggleEnabled();
                break;
            case BUTTON_MODE:
                bay.toggleBlockMode();
                break;
            case BUTTON_VARIANT:
                bay.cycleVariant();
                break;
            case BUTTON_INSTALL:
                installOrRemove(player, bay);
                break;
            case BUTTON_TOGGLE_ALL:
                toggleAll();
                break;
            default:
                return false;
        }
        hub.setChanged();
        return true;
    }

    /** One click for the whole hub: if anything is running stop everything, otherwise start everything. */
    private void toggleAll() {
        boolean anyRunning = false;
        for (int i = 0; i < hub.getBayCount(); i++) {
            GeneratorHubBlockEntity.Bay b = hub.bay(i);
            if (!b.isEmpty() && b.enabled) {
                anyRunning = true;
            }
        }
        for (int i = 0; i < hub.getBayCount(); i++) {
            GeneratorHubBlockEntity.Bay b = hub.bay(i);
            if (!b.isEmpty()) {
                b.enabled = !anyRunning;
            }
        }
    }

    private void installOrRemove(Player player, GeneratorHubBlockEntity.Bay bay) {
        ItemStack carried = getCarried();
        if (bay.isEmpty()) {
            if (GeneratorHubBlockEntity.isGeneratorItem(carried)) {
                bay.install(carried.split(1));
                setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            }
            return;
        }
        ItemStack packed = bay.extract();
        if (carried.isEmpty()) {
            setCarried(packed);
        } else {
            player.getInventory().placeItemBackInInventory(packed);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (hub == null || slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();
        if (index < PLAYER_START) {
            // pocket -> player inventory
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // player inventory -> first free bay (the selected one first)
            if (!GeneratorHubBlockEntity.isGeneratorItem(stack)) {
                return ItemStack.EMPTY;
            }
            GeneratorHubBlockEntity.Bay target = hub.bay(selected.get());
            if (!target.isEmpty()) {
                target = null;
                for (int i = 0; i < hub.getBayCount(); i++) {
                    if (hub.bay(i).isEmpty()) {
                        target = hub.bay(i);
                        break;
                    }
                }
            }
            if (target == null) {
                return ItemStack.EMPTY;
            }
            target.install(stack.split(1));
            hub.setChanged();
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        if (sourceStack != null) {
            return CarriedGui.isCarrying(player, sourceStack);
        }
        if (hub == null || hub.getLevel() == null) {
            return true; // client side
        }
        return hub.getLevel().getBlockEntity(hub.getBlockPos()) == hub
                && player.distanceToSqr(hub.getBlockPos().getX() + 0.5, hub.getBlockPos().getY() + 0.5,
                hub.getBlockPos().getZ() + 0.5) <= 64.0;
    }

    /** While opened from an item: keep every bay running and save into the item now and then. */
    @Override
    public void broadcastChanges() {
        if (sourceStack != null && hub != null && !owner.level().isClientSide) {
            GeneratorHubBlockEntity.serverTick(owner.level(), BlockPos.ZERO, hub.getBlockState(), hub);
            if (++saveCounter % 20 == 0) {
                saveToItem();
            }
        }
        super.broadcastChanges();
    }

    private void saveToItem() {
        if (sourceStack != null && hub != null) {
            BlockItem.setBlockEntityData(sourceStack, hub.getType(), hub.saveWithoutMetadata());
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (sourceStack != null) {
            if (!player.level().isClientSide) {
                saveToItem();
            }
            HeldGenerator.OPEN.remove(sourceStack);
        }
    }

    /** The numbers sent to the client (server side). */
    private static final class HubData implements ContainerData {
        private final GeneratorHubBlockEntity hub;
        private final DataSlot selected;

        HubData(GeneratorHubBlockEntity hub, DataSlot selected) {
            this.hub = hub;
            this.selected = selected;
        }

        @Override
        public int get(int index) {
            if (index == 0) {
                return hub.getBayCount();
            }
            if (index < DETAILS) {
                int bay = (index - 1) / 2;
                if (bay >= hub.getBayCount()) {
                    return 0;
                }
                GeneratorHubBlockEntity.Bay b = hub.bay(bay);
                return (index - 1) % 2 == 0 ? (b.isEmpty() ? 0 : b.genIndex + 1) : (b.enabled ? 1 : 0);
            }
            GeneratorHubBlockEntity.Bay s = hub.bay(selected.get());
            int field = index - DETAILS;
            if (s.isEmpty()) {
                return field == 1 ? 1 : 0;
            }
            int interval = s.interval();
            int scale = Math.max(1, (interval + 31999) / 32000); // values travel as 16-bit numbers
            switch (field) {
                case 0:
                    return Math.min(s.progress / scale, 32000);
                case 1:
                    return Math.max(1, interval / scale);
                case 2:
                    return s.makesBlocks() ? 1 : 0;
                case 3:
                    return s.hasBlockForm() ? 1 : 0;
                default:
                    return Math.floorMod(s.variant, s.block.def().variants().size());
            }
        }

        @Override
        public void set(int index, int value) {
            // read-only
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    }

    /** The selected bay's pocket, seen as a 54-slot container (server side). */
    private static final class PocketContainer implements Container {
        private final GeneratorHubBlockEntity hub;
        private final DataSlot selected;

        PocketContainer(GeneratorHubBlockEntity hub, DataSlot selected) {
            this.hub = hub;
            this.selected = selected;
        }

        private NonNullList<ItemStack> items() {
            return hub.bay(selected.get()).items;
        }

        @Override
        public int getContainerSize() {
            return POCKET;
        }

        @Override
        public boolean isEmpty() {
            for (ItemStack stack : items()) {
                if (!stack.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return items().get(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack result = ContainerHelper.removeItem(items(), slot, amount);
            if (!result.isEmpty()) {
                setChanged();
            }
            return result;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return ContainerHelper.takeItem(items(), slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            items().set(slot, stack);
            setChanged();
        }

        @Override
        public void setChanged() {
            hub.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            items().clear();
        }
    }

    /** Copies can be taken out but nothing can be placed here. */
    private static final class OutputSlot extends Slot {
        OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
