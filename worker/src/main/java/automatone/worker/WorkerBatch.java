package automatone.worker;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Product queue over the existing terrain preparer. Holds identities and kit copies, never a menu. */
final class WorkerBatch {
    private static final Map<MinecraftServer, WorkerBatch> SERVICES = new HashMap<>();
    private final MinecraftServer server;
    private final WorkerRoster roster;
    private final Map<UUID, List<Child>> histories = new LinkedHashMap<>();

    private static final class Child {
        private final UUID batch;
        private final UUID request;
        private final UUID worker;
        private final ResourceKey<Level> dimension;
        private final List<ItemStack> kit;
        private final List<ResourceLocation> targets;
        private final int quantity;
        private final boolean start;
        private String state = "QUEUED";
        private String error = "";

        private Child(UUID batch, WorkerRoster.Reservation reservation, ResourceKey<Level> dimension,
                      List<ItemStack> kit, List<ResourceLocation> targets, int quantity, boolean start) {
            this.batch = batch;
            this.request = reservation.request();
            this.worker = reservation.worker();
            this.dimension = dimension;
            this.kit = kit.stream().map(ItemStack::copy).toList();
            this.targets = List.copyOf(targets);
            this.quantity = quantity;
            this.start = start;
        }

        private boolean pending() { return state.equals("QUEUED") || state.equals("PREPARING"); }
    }

    private WorkerBatch(MinecraftServer server) {
        this.server = server;
        this.roster = WorkerRoster.get(server);
    }

    static WorkerBatch get(MinecraftServer server) {
        if (!server.isSameThread()) { throw new IllegalStateException("SERVER_THREAD_REQUIRED"); }
        return SERVICES.computeIfAbsent(server, WorkerBatch::new);
    }

    boolean containsRequest(UUID owner, UUID request) {
        return histories.getOrDefault(owner, List.of()).stream().anyMatch(child -> child.request.equals(request));
    }

    void submit(UUID owner, UUID batch, List<List<ItemStack>> kits, ResourceKey<Level> dimension,
                List<ResourceLocation> targets, int quantity, boolean start) {
        List<Child> history = histories.computeIfAbsent(owner, ignored -> new ArrayList<>());
        if (history.stream().anyMatch(child -> child.batch.equals(batch))) { throw new IllegalStateException("REQUEST_FINISHED"); }
        if (kits.size() > roster.freeSlots(owner)) { throw new IllegalStateException("ACTIVE_LIMIT"); }
        List<Child> reserved = new ArrayList<>();
        try {
            for (List<ItemStack> kit : kits) {
                WorkerRoster.Reservation reservation = roster.reserve(owner, UUID.randomUUID(), null);
                reserved.add(new Child(batch, reservation, dimension, kit, targets, quantity, start));
            }
        } catch (RuntimeException failure) {
            reserved.forEach(child -> roster.cancelReservation(owner, child.request));
            throw failure;
        }
        history.addAll(reserved);
        while (history.size() > 100) {
            Child oldest = history.stream().filter(child -> !child.pending()).findFirst().orElseThrow();
            history.remove(oldest);
        }
    }

    static void onServerTick(ServerTickEvent.Post event) {
        WorkerBatch service = SERVICES.get(event.getServer());
        if (service != null) { service.tick(); }
    }

    void tick() {
        WorkerRelocation relocation = WorkerRelocation.get(server);
        histories.forEach((owner, history) -> {
            Player player = WorkerMenu.connectedPlayer(server, owner);
            if (player == null || !player.isAlive()) { cancel(owner, "OWNER_UNAVAILABLE"); return; }
            for (Child child : history) {
                if (child.state.equals("PREPARING")) {
                    WorkerRelocation.Status status = relocation.status(owner, child.request);
                    if (status.state() != WorkerRelocation.State.PENDING) {
                        child.state = switch (status.state()) {
                            case SUCCEEDED -> child.start ? "RUNNING" : "DEPLOYED";
                            case CANCELLED -> "CANCELLED";
                            case FAILED -> "FAILED";
                            default -> throw new IllegalStateException("INVALID_JOB_STATE");
                        };
                        child.error = status.error();
                    }
                }
                if (!child.state.equals("QUEUED") || relocation.freePreparations() == 0) { continue; }
                try {
                    relocation.deployEquipped(owner, child.request, child.dimension, new WorkerRelocation.DeploymentCommit() {
                        @Override
                        public void validate() { requireKit(owner, child.kit); }
                        @Override
                        public void equip(WorkerEntity worker) {
                            Player holder = requireKit(owner, child.kit);
                            // Install each actual stack before removing its exact source count; no callbacks between these operations.
                            int destination = 0;
                            for (ItemStack supply : child.kit) {
                                int remaining = supply.getCount();
                                while (remaining > 0) {
                                    int amount = Math.min(remaining, supply.getMaxStackSize());
                                    worker.setItem(destination++, supply.copyWithCount(amount));
                                    remaining -= amount;
                                }
                                remove(holder, supply);
                            }
                            holder.getInventory().setChanged();
                            worker.setSelectedSlot(0);
                            if (child.start) { worker.startMining(child.targets, child.quantity); }
                            else { worker.configureMining(child.targets, child.quantity); }
                            roster.changed(worker);
                        }
                    });
                    child.state = "PREPARING";
                } catch (RuntimeException failure) {
                    roster.cancelReservation(owner, child.request);
                    child.state = "FAILED";
                    child.error = WorkerMenu.errorCode(failure);
                }
            }
        });
    }

    private Player requireKit(UUID owner, List<ItemStack> kit) {
        Player player = WorkerMenu.connectedPlayer(server, owner);
        if (player == null || !player.isAlive()) { throw new IllegalStateException("OWNER_UNAVAILABLE"); }
        if (!fitsSupplies(player.getInventory().items, kit)) { throw new IllegalStateException("KIT_SHORTAGE"); }
        return player;
    }

    static boolean fitsSupplies(List<ItemStack> inventory, List<ItemStack> required) {
        List<ItemStack> remaining = inventory.stream().map(ItemStack::copy).toList();
        for (ItemStack need : required) {
            int count = need.getCount();
            for (ItemStack stack : remaining) {
                if (!ItemStack.isSameItemSameComponents(stack, need)) { continue; }
                int taken = Math.min(count, stack.getCount());
                stack.shrink(taken);
                count -= taken;
                if (count == 0) { break; }
            }
            if (count != 0) { return false; }
        }
        return true;
    }

    private static void remove(Player player, ItemStack supply) {
        int remaining = supply.getCount();
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!ItemStack.isSameItemSameComponents(stack, supply)) { continue; }
            int amount = Math.min(remaining, stack.getCount());
            player.getInventory().removeItem(slot, amount);
            remaining -= amount;
            if (remaining == 0) { return; }
        }
        throw new IllegalStateException("KIT_SHORTAGE");
    }

    private void cancel(UUID owner, String reason) {
        for (Child child : histories.getOrDefault(owner, List.of())) {
            if (!child.pending()) { continue; }
            if (child.state.equals("PREPARING")) {
                WorkerRelocation.Status status = WorkerRelocation.get(server).cancel(owner, child.request);
                if (status.state() == WorkerRelocation.State.SUCCEEDED) {
                    child.state = child.start ? "RUNNING" : "DEPLOYED";
                    continue;
                }
            }
            roster.cancelReservation(owner, child.request);
            child.state = "CANCELLED";
            child.error = reason;
        }
    }

    static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        MinecraftServer server = event.getEntity().getServer();
        WorkerBatch service = SERVICES.get(server);
        if (service != null) { service.cancel(event.getEntity().getUUID(), "OWNER_DISCONNECTED"); }
    }

    static void stop(ServerStoppingEvent event) {
        WorkerBatch service = SERVICES.remove(event.getServer());
        if (service != null) { service.histories.keySet().forEach(owner -> service.cancel(owner, "SERVER_STOPPING")); }
    }

    ListTag snapshot(UUID owner) {
        ListTag rows = new ListTag();
        for (Child child : histories.getOrDefault(owner, List.of())) {
            CompoundTag row = new CompoundTag();
            row.putUUID("Batch", child.batch);
            row.putUUID("Request", child.request);
            row.putUUID("Worker", child.worker);
            row.putString("State", child.state);
            row.putString("Error", child.error);
            row.putString("Dimension", child.dimension.location().toString());
            row.putInt("Quantity", child.quantity);
            rows.add(row);
        }
        return rows;
    }
}
