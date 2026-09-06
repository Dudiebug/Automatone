package automatone.worker;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Owner-directed presentation transport; durable history belongs to the roster. */
public final class WorkerNotifications {
    private static final Map<UUID, Player> LOGIN_SUMMARIES = new HashMap<>();

    private WorkerNotifications() { }

    public static void completed(WorkerEntity worker) {
        var server = Objects.requireNonNull(worker.getServer());
        WorkerRoster roster = WorkerRoster.get(server);
        roster.recordCompletion(worker).ifPresent(completion -> {
            UUID owner = worker.ownerUUID().orElseThrow();
            ServerPlayer player = server.getPlayerList().getPlayer(owner);
            if (player != null) {
                WorkerRoster.NotificationPreferences preferences = roster.notificationPreferences(owner);
                PacketDistributor.sendToPlayer(player, new WorkerNetwork.Notice(completion.run(), false,
                        roster.unread(owner), completion.name(), completion.amount(), preferences.toasts(), preferences.sounds()));
            }
        });
    }

    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) { return; }
        if (player.equals(LOGIN_SUMMARIES.put(player.getUUID(), player))) { return; }
        WorkerRoster roster = WorkerRoster.get(player.server);
        int unread = roster.unread(player.getUUID());
        if (unread > 0) {
            WorkerRoster.NotificationPreferences preferences = roster.notificationPreferences(player.getUUID());
            PacketDistributor.sendToPlayer(player, new WorkerNetwork.Notice(UUID.randomUUID(), true,
                    unread, "", 0, preferences.toasts(), preferences.sounds()));
        }
    }

    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        LOGIN_SUMMARIES.remove(event.getEntity().getUUID(), event.getEntity());
    }

    public static void stop(ServerStoppedEvent event) { LOGIN_SUMMARIES.clear(); }
}
