package automatone.worker;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.Event;

import java.util.UUID;

/** Bounded product intents and snapshots. Item stacks travel through Minecraft's native menu packets. */
public final class WorkerNetwork {
    // Covers full supported profiles, while bounding hostile NBT before schema/registry validation.
    // Registered NeoForge payloads use its negotiated native splitter for larger packets.
    public static final long MAX_INTENT_NBT = 4 * 1024 * 1024;
    public static final long MAX_SNAPSHOT_NBT = 8 * 1024 * 1024;

    public enum Action {
        REFRESH, OPEN_WORKER, OPEN_ROSTER, DEPLOY, REACTIVATE, RELOCATE, CANCEL_RELOCATION,
        CONFIGURE_JOB, START, PAUSE, RESUME, STOP, SELECT_TOOL, RENAME, PERSONAL_SETTINGS,
        WORKER_SETTINGS, RETIRE, PREVIEW_APPLY_JOB, APPLY_JOB, NOTIFICATION_PAGE,
        READ_NOTIFICATION, READ_ALL_NOTIFICATIONS, NOTIFICATION_PREFERENCES, COLLECT_ALL, INVENTORY_MANAGEMENT,
        PERSONAL_PICKUP_RULES
    }

    public record Intent(int menuId, UUID session, long sequence, Action action, CompoundTag data)
            implements CustomPacketPayload {
        public static final Type<Intent> TYPE = new Type<>(id("intent"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Intent> CODEC = StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeVarInt(value.menuId());
                    buffer.writeUUID(value.session());
                    buffer.writeLong(value.sequence());
                    buffer.writeEnum(value.action());
                    buffer.writeNbt(value.data());
                }, buffer -> new Intent(buffer.readVarInt(), buffer.readUUID(), buffer.readLong(),
                        buffer.readEnum(Action.class), readTag(buffer, MAX_INTENT_NBT)));

        public Intent { data = data.copy(); }

        @Override
        public CompoundTag data() { return data.copy(); }

        @Override
        public Type<Intent> type() { return TYPE; }
    }

    public record Snapshot(int menuId, UUID session, long sequence, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(id("snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeVarInt(value.menuId());
                    buffer.writeUUID(value.session());
                    buffer.writeLong(value.sequence());
                    buffer.writeNbt(value.data());
                }, buffer -> new Snapshot(buffer.readVarInt(), buffer.readUUID(), buffer.readLong(),
                        readTag(buffer, MAX_SNAPSHOT_NBT)));

        public Snapshot { data = data.copy(); }

        @Override
        public CompoundTag data() { return data.copy(); }

        @Override
        public Type<Snapshot> type() { return TYPE; }
    }

    public record Notice(UUID eventId, boolean summary, int unread, String workerName, long amount, boolean toast, boolean sound)
            implements CustomPacketPayload {
        public static final Type<Notice> TYPE = new Type<>(id("notice"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Notice> CODEC = StreamCodec.of((buffer, notice) -> {
            buffer.writeUUID(notice.eventId()); buffer.writeBoolean(notice.summary()); buffer.writeVarInt(notice.unread());
            buffer.writeUtf(notice.workerName(), 64); buffer.writeLong(notice.amount());
            buffer.writeBoolean(notice.toast()); buffer.writeBoolean(notice.sound());
        }, buffer -> new Notice(buffer.readUUID(), buffer.readBoolean(), buffer.readVarInt(), buffer.readUtf(64),
                buffer.readLong(), buffer.readBoolean(), buffer.readBoolean()));

        public Notice {
            if (eventId == null || workerName == null || workerName.length() > 64 || unread < 0 || unread > WorkerRoster.NOTIFICATION_LIMIT
                    || amount < 0 || amount > MiningSession.MAX_REQUESTED_BLOCKS || (!summary && amount == 0)) {
                throw new IllegalArgumentException("INVALID_NOTICE");
            }
        }

        @Override
        public Type<Notice> type() { return TYPE; }
    }

    /** Client-side subscribers own rendering; no client class is referenced by common transport. */
    public static final class NoticeReceived extends Event {
        private final Notice notice;
        public NoticeReceived(Notice notice) { this.notice = notice; }
        public Notice notice() { return notice; }
    }

    private WorkerNetwork() { }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("3");
        registrar.playToServer(Intent.TYPE, Intent.CODEC, (payload, context) -> {
            if (context.player().containerMenu instanceof WorkerMenu menu) {
                menu.handle(context.player(), payload);
            }
        });
        registrar.playToClient(Snapshot.TYPE, Snapshot.CODEC, (payload, context) -> {
            if (context.player().containerMenu instanceof WorkerMenu menu) {
                menu.receive(payload);
            }
        });
        registrar.playToClient(Notice.TYPE, Notice.CODEC, (payload, context) -> NeoForge.EVENT_BUS.post(new NoticeReceived(payload)));
    }

    private static CompoundTag readTag(RegistryFriendlyByteBuf buffer, long limit) {
        Tag tag = buffer.readNbt(NbtAccounter.create(limit));
        if (!(tag instanceof CompoundTag compound)) {
            throw new IllegalArgumentException("MISSING_DATA");
        }
        return compound;
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(WorkerMod.MOD_ID, path); }
}
