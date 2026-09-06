package automatone.worker;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A holder-owned roster session with a fixed inventory binding for the lifetime of its menu id. */
public final class WorkerMenu extends AbstractContainerMenu {
    private final Player player;
    private final UUID owner;
    private final UUID session;
    private final UUID worker;
    private final boolean retired;
    private final int page;
    private final WorkerRoster roster;
    private final WorkerActions actions;
    private WorkerCollection collection;
    private final Container inventory;
    private long boundRevision;
    private long sequence;
    private long clientSequence;
    private long snapshotVersion;
    private int snapshotTick = -20;
    private int notificationPage;
    private CompoundTag clientData = new CompoundTag();
    private CompoundTag response = new CompoundTag();
    private boolean inventoryVisible;
    private boolean compactInventory;
    private boolean showPlayerInventory;
    private boolean openCollection;

    /** Client constructor: no entity lookup and no server-owned inventory references. */
    public WorkerMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(id, playerInventory, buffer.readUUID(), buffer.readBoolean() ? buffer.readUUID() : null,
                buffer.readBoolean(), buffer.readVarInt(), buffer.readLong(), null);
    }

    private WorkerMenu(int id, Inventory playerInventory, UUID session, UUID worker, boolean retired,
                       int page, long revision, WorkerRoster roster) {
        super(WorkerMod.MENU.get(), id);
        this.player = playerInventory.player;
        this.owner = player.getUUID();
        this.session = session;
        this.worker = worker;
        this.retired = retired;
        this.page = page;
        this.boundRevision = revision;
        this.roster = roster;
        this.actions = roster == null ? null : new WorkerActions(this);
        this.inventory = roster == null || worker == null ? new SimpleContainer(WorkerEntity.INVENTORY_SIZE)
                : retired ? roster.archivedContainer(owner, worker) : roster.active(owner, worker).inventory();
        for (int index = 0; index < WorkerEntity.INVENTORY_SIZE; index++) {
            addSlot(workerSlot(index));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(playerSlot(playerInventory, col + row * 9 + 9, 8 + col * 18, 48 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(playerSlot(playerInventory, col, 8 + col * 18, 106));
        }
    }

    private Slot workerSlot(int index) {
        int x = (compactInventory ? 8 : 194) + (index % 9) * 18;
        int y = index < 9 ? 106 : 48 + (index / 9 - 1) * 18;
        return new Slot(inventory, index, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) { return !retired && canUseWorkerInventory(); }
            @Override
            public boolean mayPickup(Player holder) { return holder.equals(player) && canUseWorkerInventory(); }
            @Override
            public void onTake(Player holder, ItemStack stack) {
                super.onTake(holder, stack);
                refreshRevision();
            }
            @Override
            public boolean isActive() {
                return worker != null && inventoryVisible && (!compactInventory || !showPlayerInventory);
            }
        };
    }

    private Slot playerSlot(Inventory playerInventory, int index, int x, int y) {
        return new Slot(playerInventory, index, x, y) {
            @Override
            public boolean isActive() { return inventoryVisible && (!compactInventory || showPlayerInventory); }
        };
    }

    /** Client layout changes preserve all native slot ids and authoritative container bindings. */
    public void inventoryLayout(boolean compact, boolean playerOnly) {
        if (roster != null) { return; }
        showPlayerInventory = playerOnly;
        if (compactInventory == compact) { return; }
        compactInventory = compact;
        for (int index = 0; index < WorkerEntity.INVENTORY_SIZE; index++) {
            Slot replacement = workerSlot(index);
            replacement.index = index;
            slots.set(index, replacement);
        }
    }

    static boolean isOpenFor(MinecraftServer server, UUID worker) {
        return server.getPlayerList().getPlayers().stream().anyMatch(player ->
                player.containerMenu instanceof WorkerMenu menu && worker.equals(menu.worker()));
    }

    /** Validated callers always open a new menu id when selecting another worker. */
    public static void open(Player holder, UUID worker, boolean retired, int requestedPage) {
        if (!(holder instanceof ServerPlayer serverPlayer) || holder.isSpectator() || !hasController(holder)) {
            throw new IllegalStateException("CONTROLLER_REQUIRED");
        }
        MinecraftServer server = Objects.requireNonNull(holder.getServer());
        if (!server.isSameThread()) { throw new IllegalStateException("SERVER_THREAD_REQUIRED"); }
        WorkerRoster roster = WorkerRoster.get(server);
        long revision = 0;
        int page = Math.max(0, requestedPage);
        if (worker != null) {
            WorkerRoster.View view = roster.view(holder.getUUID(), worker);
            if (view.retired() != retired) { throw new IllegalStateException("WORKER_UNAVAILABLE"); }
            if (!retired) { roster.active(holder.getUUID(), worker); }
            revision = view.revision();
        } else {
            int size = roster.list(holder.getUUID(), retired).size();
            page = Math.min(page, Math.max(0, (size - 1) / 10));
        }
        UUID token = UUID.randomUUID();
        long openingRevision = revision;
        int openingPage = page;
        serverPlayer.openMenu(new SimpleMenuProvider((id, inv, player) ->
                new WorkerMenu(id, inv, token, worker, retired, openingPage, openingRevision, roster),
                Component.translatable("item.automatone_worker.controller")), buffer -> {
                    buffer.writeUUID(token);
                    buffer.writeBoolean(worker != null);
                    if (worker != null) { buffer.writeUUID(worker); }
                    buffer.writeBoolean(retired);
                    buffer.writeVarInt(openingPage);
                    buffer.writeLong(openingRevision);
                });
        if (holder.containerMenu instanceof WorkerMenu menu) { menu.sendSnapshot(); }
    }

    public static boolean hasController(Player player) {
        if (player.getOffhandItem().is(WorkerMod.CONTROLLER.get()) || player.containerMenu.getCarried().is(WorkerMod.CONTROLLER.get())) {
            return true;
        }
        return player.getInventory().items.stream().anyMatch(stack -> stack.is(WorkerMod.CONTROLLER.get()));
    }

    static void openCollection(Player holder, boolean archives, UUID selected) {
        open(holder, null, false, 0);
        if (holder.containerMenu instanceof WorkerMenu menu) {
            menu.collection().query(archives ? 1 : 0, "", selected, "", 0);
            menu.openCollection = true;
            menu.sendSnapshot();
        }
    }

    @Override
    public boolean stillValid(Player holder) {
        if (roster == null) { return true; }
        if (!holder.equals(player) || holder.containerMenu != this || !holder.isAlive() || holder.isSpectator() || !hasController(holder)) { return false; }
        if (worker == null) { return true; }
        try {
            WorkerRoster.View view = roster.view(owner, worker);
            if (view.retired() != retired) { return false; }
            if (!retired && !roster.active(owner, worker).inventory().equals(inventory)) { return false; }
            return true;
        } catch (IllegalStateException unavailable) {
            return false;
        }
    }

    /** Called before vanilla can mutate slots; vanilla otherwise accepts stale state ids. */
    public boolean acceptsClick(Player holder, int stateId) {
        return stateId == getStateId() && stillValid(holder) && (worker == null || canUseWorkerInventory());
    }

    private boolean canUseWorkerInventory() {
        if (worker == null) { return false; }
        if (roster == null) { return !clientData.getBoolean("Pending"); }
        return stillValid(player) && roster.view(owner, worker).revision() == boundRevision && !relocation().pending(worker);
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player holder) {
        if (roster != null && (!stillValid(holder) || (worker != null && !canUseWorkerInventory()))) { return; }
        if ((slot < -1 && slot != -999) || slot >= slots.size() || !validButton(type, button)) { return; }
        if (slot < 0 && type != ClickType.PICKUP && type != ClickType.QUICK_MOVE && type != ClickType.QUICK_CRAFT) { return; }
        if (slot >= 0 && slot < WorkerEntity.INVENTORY_SIZE && (worker == null || type == ClickType.CLONE)) { return; }
        List<ItemStack> before = contents();
        super.clicked(slot, button, type, holder);
        if (roster != null && worker != null && !retired && !ItemStack.listMatches(before, contents())) {
            roster.changed(roster.active(owner, worker));
        }
        refreshRevision();
    }

    private static boolean validButton(ClickType type, int button) {
        return switch (type) {
            case PICKUP, QUICK_MOVE, THROW, PICKUP_ALL -> button == 0 || button == 1;
            case SWAP -> (button >= 0 && button < 9) || button == 40;
            case CLONE -> button == 2;
            case QUICK_CRAFT -> button >= 0 && button < 11 && (button & 3) != 3;
        };
    }

    @Override
    public ItemStack quickMoveStack(Player holder, int index) {
        if (index < 0 || index >= slots.size() || worker == null || !canUseWorkerInventory()
                || !holder.equals(player) || (index >= WorkerEntity.INVENTORY_SIZE && retired)) { return ItemStack.EMPTY; }
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(holder)) { return ItemStack.EMPTY; }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean fromWorker = index < WorkerEntity.INVENTORY_SIZE;
        if (!moveItemStackTo(stack, fromWorker ? WorkerEntity.INVENTORY_SIZE : 0,
                fromWorker ? slots.size() : WorkerEntity.INVENTORY_SIZE, fromWorker)) { return ItemStack.EMPTY; }
        if (stack.isEmpty()) { slot.setByPlayer(ItemStack.EMPTY); }
        else { slot.setChanged(); }
        slot.onTake(holder, stack);
        refreshRevision();
        return original;
    }

    private List<ItemStack> contents() {
        List<ItemStack> result = new ArrayList<>(WorkerEntity.INVENTORY_SIZE);
        for (int index = 0; index < WorkerEntity.INVENTORY_SIZE; index++) { result.add(inventory.getItem(index).copy()); }
        return result;
    }

    /** Move only what fits; native stack merging leaves every remainder in the source slot. */
    int collectAll() {
        if (!canUseWorkerInventory()) { throw new IllegalStateException("WORKER_UNAVAILABLE"); }
        int collected = 0;
        List<Integer> eligible = WorkerCollection.eligible(contents(), retired);
        for (int index = 0; index < WorkerEntity.INVENTORY_SIZE; index++) {
            int accepted = insertCollectionStack(inventory.getItem(index).copyWithCount(eligible.get(index)));
            if (accepted > 0) { inventory.removeItem(index, accepted); }
            collected += accepted;
        }
        if (collected > 0 && !retired) { roster.changed(roster.active(owner, worker)); }
        refreshRevision();
        return collected;
    }

    int insertCollectionStack(ItemStack stack) {
        int before = stack.getCount();
        if (!stack.isEmpty()) { moveItemStackTo(stack, WorkerEntity.INVENTORY_SIZE, slots.size(), false); }
        return before - stack.getCount();
    }

    WorkerCollection collection() {
        if (worker != null) { throw new IllegalStateException("GLOBAL_MENU_REQUIRED"); }
        if (collection == null) { collection = new WorkerCollection(this); }
        return collection;
    }

    public void handle(Player holder, WorkerNetwork.Intent intent) {
        if (roster == null || !server().isSameThread()) { throw new IllegalStateException("SERVER_THREAD_REQUIRED"); }
        if (!stillValid(holder) || intent.menuId() != containerId || !intent.session().equals(session)) { return; }
        if (intent.sequence() != sequence + 1 || intent.sequence() <= 0) {
            response = failure("INVALID_SEQUENCE");
            sendSnapshot();
            return;
        }
        sequence = intent.sequence();
        try {
            response = actions.execute(intent.action(), intent.data());
        } catch (RuntimeException failure) {
            response = failure(errorCode(failure));
        }
        refreshRevision();
        if (holder.containerMenu.equals(this)) {
            sendAllDataToRemote();
            sendSnapshot();
        }
    }

    @Override
    public void broadcastChanges() {
        if (roster != null && worker != null && player.containerMenu.equals(this) && player.isAlive() && hasController(player)) {
            try {
                WorkerRoster.View current = roster.view(owner, worker);
                if (current.retired() != retired || (!retired && !roster.active(owner, worker).inventory().equals(inventory))) {
                    open(player, worker, current.retired(), page);
                    return;
                }
            } catch (IllegalStateException unavailable) {
                // Vanilla closes unavailable selections after broadcastChanges returns.
            }
        }
        super.broadcastChanges();
        if (roster != null && player.containerMenu.equals(this) && server().getTickCount() - snapshotTick >= 10) {
            if (worker != null && stillValid(player) && boundRevision != roster.view(owner, worker).revision()) {
                refreshRevision();
                sendAllDataToRemote();
            }
            sendSnapshot();
        }
    }

    private void refreshRevision() {
        if (roster != null && worker != null && stillValid(player)) { boundRevision = roster.view(owner, worker).revision(); }
    }

    private void sendSnapshot() {
        if (!(player instanceof ServerPlayer serverPlayer) || !stillValid(player)) { return; }
        snapshotTick = server().getTickCount();
        PacketDistributor.sendToPlayer(serverPlayer, new WorkerNetwork.Snapshot(containerId, session, sequence, snapshot()));
    }

    /** Bounded product pages; descriptive collection icons never authorize an inventory mutation. */
    public CompoundTag snapshot() {
        if (roster == null) { return clientData.copy(); }
        CompoundTag data = new CompoundTag();
        List<WorkerRoster.View> rows = roster.list(owner, retired);
        data.putInt("Count", rows.size());
        data.putInt("ActiveCount", roster.list(owner, false).size());
        data.putInt("Page", page);
        data.putBoolean("Retired", retired);
        ListTag workers = new ListTag();
        int from = Math.min(rows.size(), page * 10);
        for (WorkerRoster.View view : rows.subList(from, Math.min(rows.size(), from + 10))) { workers.add(row(view)); }
        data.put("Workers", workers);
        WorkerRoster.ProfileView profile = roster.profile(owner);
        data.putLong("ProfileRevision", profile.revision());
        data.put("PersonalSettings", WorkerSettings.save(profile.settings()));
        data.put("PickupRules", roster.pickupRules(owner));
        if (worker != null) {
            WorkerRoster.View view = roster.view(owner, worker);
            data.put("Selected", row(view));
            data.put("Overrides", WorkerSettings.save(roster.overrides(owner, worker)));
            data.putBoolean("Pending", relocation().pending(worker));
            data.putInt("SelectedSlot", retired ? -1 : roster.active(owner, worker).selectedSlot());
            if (!retired) { data.put("InventoryManagement", roster.active(owner, worker).inventoryManagementSettings()); }
        }
        ListTag pending = new ListTag();
        for (WorkerRelocation.Status status : relocation().requests(owner)) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Request", status.request());
            entry.putUUID("Worker", status.worker());
            entry.putString("State", status.state().name());
            entry.putString("Error", status.error());
            entry.putInt("Attempts", status.attempts());
            entry.putString("Dimension", status.dimension().toString());
            if (status.destination() != null) { entry.putLong("Position", status.destination().asLong()); }
            pending.add(entry);
        }
        data.put("Relocations", pending);
        data.put("Response", response.copy());
        data.putBoolean("OpenCollection", openCollection);
        if (collection != null) { data.put("Collection", collection.snapshot()); }
        List<WorkerRoster.Completion> inbox = roster.notifications(owner);
        notificationPage = Math.min(notificationPage, Math.max(0, (inbox.size() - 1) / 5));
        ListTag notifications = new ListTag();
        int newest = inbox.size() - 1 - notificationPage * 5;
        for (int index = newest; index >= Math.max(0, newest - 4); index--) { notifications.add(inbox.get(index).save()); }
        data.put("Notifications", notifications);
        data.putInt("NotificationCount", inbox.size()); data.putInt("NotificationPage", notificationPage);
        data.putInt("Unread", roster.unread(owner));
        WorkerRoster.NotificationPreferences preferences = roster.notificationPreferences(owner);
        data.putLong("NotificationRevision", preferences.revision());
        data.putBoolean("ShowToasts", preferences.toasts()); data.putBoolean("PlaySounds", preferences.sounds());
        return data;
    }

    private CompoundTag row(WorkerRoster.View view) {
        CompoundTag row = new CompoundTag();
        row.putUUID("Worker", view.worker());
        row.putLong("Revision", view.revision());
        row.putString("Name", view.name());
        row.putString("Dimension", view.dimension());
        row.putLong("Position", view.position().asLong());
        row.putBoolean("Retired", view.retired());
        row.putBoolean("Pending", relocation().pending(view.worker()));
        row.put("Job", roster.jobData(owner, view.worker()));
        return row;
    }

    public WorkerNetwork.Intent intent(WorkerNetwork.Action action, CompoundTag data) {
        return new WorkerNetwork.Intent(containerId, session, ++clientSequence, action, data);
    }

    public void receive(WorkerNetwork.Snapshot snapshot) {
        if (roster == null && snapshot.menuId() == containerId && snapshot.session().equals(session)) {
            clientData = snapshot.data();
            snapshotVersion++;
            sequence = snapshot.sequence();
            clientSequence = Math.max(clientSequence, sequence);
        }
    }

    public UUID session() { return session; }
    public UUID worker() { return worker; }
    public boolean retired() { return retired; }
    public int page() { return page; }
    public long sequence() { return sequence; }
    public long snapshotVersion() { return snapshotVersion; }
    void notificationPage(int page) { notificationPage = page; }
    public void showInventory(boolean visible) { inventoryVisible = visible; }
    Player player() { return player; }
    UUID owner() { return owner; }
    WorkerRoster roster() { return Objects.requireNonNull(roster); }
    MinecraftServer server() { return Objects.requireNonNull(player.getServer()); }
    WorkerRelocation relocation() { return WorkerRelocation.get(server()); }

    static String errorCode(RuntimeException failure) {
        String message = failure.getMessage();
        if (message != null && message.matches("[A-Z_]+(: .*)?")) { return message.split(":", 2)[0]; }
        return "ACTION_FAILED";
    }

    private static CompoundTag failure(String code) {
        CompoundTag result = new CompoundTag();
        result.putString("Kind", "Error");
        result.putString("Error", code);
        return result;
    }
}
