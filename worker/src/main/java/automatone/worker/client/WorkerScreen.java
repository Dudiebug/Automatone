package automatone.worker.client;

import automatone.worker.WorkerMenu;
import automatone.worker.WorkerMod;
import automatone.worker.WorkerNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Native widgets and slots over the server-owned controller session. */
@EventBusSubscriber(modid = WorkerMod.MOD_ID, value = Dist.CLIENT)
public final class WorkerScreen extends AbstractContainerScreen<WorkerMenu> {
    static String translate(String key, Object... args) {
        return Component.translatable("gui.automatone_worker." + key, args).getString();
    }
    private enum Page { ROSTER, JOB, INVENTORY, SETTINGS, OVERRIDES, PERSONAL, RECIPIENTS, REQUESTS, INBOX }
    private record Caption(String text, int x, int y, int color, int maxWidth) { }
    private record BlockCell(Block block, Button button, boolean selected) { }
    private record Dialog(String title, List<String> lines, Runnable confirm) { }

    private static final int TEXT = 0xFFE4E8DF;
    private static final int MUTED = 0xFFAFB7AB;
    private static final int GREEN = 0xFF8AAE4E;
    private final List<Caption> captions = new ArrayList<>();
    private final List<BlockCell> blockCells = new ArrayList<>();
    private final Set<String> targets = new LinkedHashSet<>();
    private final Set<UUID> recipients = new LinkedHashSet<>();
    private final List<Block> blocks;
    private final List<String> mods;
    private CompoundTag data = new CompoundTag();
    private Page page;
    private WorkerSettingsPanel settings;
    private Dialog dialog;
    private WorkerNetwork.Action dimensionAction;
    private String dimension = "minecraft:overworld";
    private String search = "";
    private String mod = "";
    private String quantity = "64";
    private String savedQuantity = "64";
    private Set<String> savedTargets = Set.of();
    private String name = "";
    private String message = "";
    private boolean unlimited;
    private boolean savedUnlimited;
    private boolean selectedOnly;
    private boolean initialized;
    private boolean rebuild;
    private int offset;
    private int dialogOffset;
    private int fx;
    private int fy;
    private int fw;
    private int fh;
    private long waiting;
    private long snapshotVersion = -1;
    private long settingsRevision;
    private long jobRevision;
    private WorkerNetwork.Action sentAction;

    public WorkerScreen(WorkerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        page = menu.worker() == null ? Page.ROSTER : Page.JOB;
        if (menu.retired() && menu.worker() != null) { page = Page.INVENTORY; }
        blocks = BuiltInRegistries.BLOCK.stream().filter(block -> !block.defaultBlockState().isAir())
                .sorted(Comparator.comparing(block -> BuiltInRegistries.BLOCK.getKey(block).toString())).toList();
        mods = blocks.stream().map(block -> BuiltInRegistries.BLOCK.getKey(block).getNamespace()).distinct().sorted().toList();
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(WorkerMod.MENU.get(), WorkerScreen::new);
    }

    @Override
    protected void init() {
        imageWidth = Math.min(820, width - 12);
        imageHeight = Math.min(480, height - 12);
        super.init();
        fx = (width - imageWidth) / 2;
        fy = (height - imageHeight) / 2;
        fw = imageWidth;
        fh = imageHeight;
        // Native slot coordinates stay fixed; center their 176px canvas inside our frame.
        leftPos = fx + (fw - 176) / 2;
        topPos = fy + 26;
        build();
    }

    private void build() {
        int focusedIndex = children().indexOf(getFocused());
        String focused = getFocused() instanceof AbstractWidget widget ? widget.getMessage().getString() : "";
        int cursor = getFocused() instanceof EditBox box ? box.getCursorPosition() : -1;
        clearWidgets();
        captions.clear();
        blockCells.clear();
        menu.showInventory(dialog == null && page == Page.INVENTORY);
        if (dialog != null) { buildDialog(); return; }
        if (fw > 420) { label("AUTOMATONE", fx + 32, fy + 9, GREEN, fw - 304); }
        button(translate("workers"), fx + fw - 262, fy + 5, 62, () -> navigateRoster(false));
        button(translate("config"), fx + fw - 198, fy + 5, 56, () -> navigate(Page.PERSONAL));
        button(translate("requests"), fx + fw - 140, fy + 5, 58, () -> navigate(Page.REQUESTS));
        button(translate("inbox"), fx + fw - 80, fy + 5, 54, () -> navigate(Page.INBOX));
        button("X", fx + fw - 24, fy + 5, 18, this::onClose);
        if (!initialized) {
            label(translate("waiting"), fx + 12, fy + 48, MUTED, fw - 24);
            return;
        }
        int x = fx + 10;
        int y = fy + 34;
        if (menu.worker() != null && page != Page.PERSONAL && page != Page.REQUESTS && page != Page.INBOX) {
            label(name, x, y + 5, TEXT, Math.max(65, fw - 210));
            button(translate("job"), fx + fw - 190, y, 54, () -> navigate(Page.JOB));
            button(translate("inventory"), fx + fw - 134, y, 72, () -> navigate(Page.INVENTORY));
            button(translate("settings"), fx + fw - 60, y, 50, () -> navigate(Page.SETTINGS));
        } else {
            label(page == Page.PERSONAL ? translate("personal_configuration") : page == Page.REQUESTS ? translate("deployment_relocation")
                    : page == Page.INBOX ? translate("notifications") : menu.retired() ? translate("retired_workers") : translate("your_workers"),
                    x, y + 5, TEXT, page == Page.INBOX ? fw - 182 : fw - 20);
        }
        switch (page) {
            case ROSTER, RECIPIENTS -> buildRoster();
            case JOB -> buildJob();
            case INVENTORY -> buildInventory();
            case SETTINGS -> buildSettings(false);
            case OVERRIDES -> buildSettingsPanel(false);
            case PERSONAL -> buildSettings(true);
            case REQUESTS -> buildRequests();
            case INBOX -> buildInbox();
        }
        if (waiting > 0) {
            children().forEach(child -> { if (child instanceof AbstractWidget widget) { widget.active = false; } });
        }
        if (!focused.isEmpty()) {
            children().stream().filter(child -> child instanceof AbstractWidget widget
                    && widget.getMessage().getString().equals(focused)).findFirst().ifPresent(child -> {
                        setFocused(child);
                        if (child instanceof EditBox box && cursor >= 0) { box.setCursorPosition(cursor); }
                    });
        } else if (focusedIndex >= 0 && focusedIndex < children().size()) {
            setFocused(children().get(focusedIndex));
        }
    }

    private void buildRoster() {
        List<CompoundTag> rows = rows();
        boolean selecting = page == Page.RECIPIENTS;
        int columns = Math.max(1, Math.min(5, (fw - 20) / 125));
        int cardWidth = (fw - 20) / columns;
        int visibleRows = Math.max(1, (fh - 112) / 72);
        int slots = rows.size() + (!selecting && !menu.retired() && data.getInt("ActiveCount") < 10 ? 1 : 0);
        offset = Math.min(offset, Math.max(0, (slots - 1) / columns - visibleRows + 1));
        for (int index = offset * columns; index < Math.min(slots, (offset + visibleRows) * columns); index++) {
            int x = fx + 10 + index % columns * cardWidth;
            int y = fy + 60 + (index / columns - offset) * 72;
            if (index == rows.size()) {
                button(translate("add_worker"), x, y, cardWidth - 4, 66, () -> chooseDimension(WorkerNetwork.Action.DEPLOY));
                continue;
            }
            CompoundTag row = rows.get(index);
            UUID id = row.getUUID("Worker");
            Button card = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
                if (selecting) {
                    if (!recipients.remove(id)) { recipients.add(id); }
                    rebuild = true;
                } else {
                    guardDiscard(() -> send(WorkerNetwork.Action.OPEN_WORKER, reference(row)));
                }
            }).createNarration(ignored -> Component.literal(row.getString("Name") + ", " + location(row)
                    + ", " + jobSummary(row.getCompound("Job"))))
                    .bounds(x, y, cardWidth - 4, 66).build());
            card.setTooltip(Tooltip.create(Component.literal(row.getString("Name") + "\n" + location(row)
                    + "\n" + jobSummary(row.getCompound("Job")))));
            label((selecting && recipients.contains(id) ? "✓ " : "") + row.getString("Name"), x + 6, y + 5, TEXT, cardWidth - 16);
            label(shortDimension(row.getString("Dimension")), x + 6, y + 17, MUTED, cardWidth - 16);
            BlockPos pos = BlockPos.of(row.getLong("Position"));
            label(pos.getX() + ", " + pos.getY() + ", " + pos.getZ(), x + 6, y + 28, MUTED, cardWidth - 16);
            CompoundTag job = row.getCompound("Job");
            label(job.getList("Targets", Tag.TAG_STRING).stream().map(Tag::getAsString).map(WorkerScreen::shortId)
                    .reduce((a, b) -> a + ", " + b).orElse(translate("no_job")), x + 6, y + 39, TEXT, cardWidth - 16);
            label((row.getBoolean("Pending") ? translate("preparing") : stateName(job.getString("State"))) + " " + progress(job), x + 6, y + 51, GREEN, cardWidth - 16);
        }
        int bottom = fy + fh - 46;
        if (selecting) {
            button(translate("select_all"), fx + 10, bottom, 70, () -> { rows.forEach(row -> recipients.add(row.getUUID("Worker"))); rebuild = true; });
            button(translate("clear"), fx + 82, bottom, 44, () -> { recipients.clear(); rebuild = true; });
            button(translate("continue_count", recipients.size()), fx + fw - 120, bottom, 110, () -> { page = Page.JOB; rebuild = true; });
        } else {
            button(menu.retired() ? translate("active_workers") : translate("retired_workers"), fx + 10, bottom, 94, () -> navigateRoster(!menu.retired()));
            if (!menu.retired()) { button(translate("batch_job"), fx + 106, bottom, 70, () -> navigate(Page.RECIPIENTS)); }
            if (menu.retired() && data.getInt("Count") > 10) {
                button("<", fx + fw - 54, bottom, 20, () -> openRoster(true, Math.max(0, menu.page() - 1)));
                button(">", fx + fw - 32, bottom, 20, () -> openRoster(true, menu.page() + 1));
            }
        }
        if (slots > visibleRows * columns) {
            button("↑", fx + fw - 52, fy + fh - 70, 20, () -> { offset = Math.max(0, offset - 1); rebuild = true; });
            button("↓", fx + fw - 30, fy + fh - 70, 20, () -> { offset++; rebuild = true; });
        }
    }

    private void buildJob() {
        int x = fx + 10;
        int y = fy + 60;
        int area = fw - 20;
        int filterWidth = Math.max(85, area / 4);
        edit(translate("search_blocks"), search, x, y, area - filterWidth - 4, 256, value -> { search = value; offset = 0; rebuild = true; });
        button(mod.isEmpty() ? translate("all_mods") : mod, x + area - filterWidth, y, filterWidth, () -> {
            int next = mods.indexOf(mod) + 1;
            mod = next >= mods.size() ? "" : mods.get(next);
            offset = 0; rebuild = true;
        }).setTooltip(Tooltip.create(Component.literal(translate("filter_by_registry_namespace_search_also_accepts_mod_block_ids"))));
        button((selectedOnly ? "✓ " : "") + translate("selected_count", targets.size()), x, y + 23, 102,
                () -> { selectedOnly = !selectedOnly; offset = 0; rebuild = true; });
        List<String> chips = targets.stream().toList();
        int chipX = x + 106;
        for (String id : chips) {
            int chipWidth = Math.min(110, font.width(shortId(id)) + 22);
            if (chipX + chipWidth > x + area) { break; }
            button(shortId(id) + " ×", chipX, y + 23, chipWidth, () -> { targets.remove(id); rebuild = true; });
            chipX += chipWidth + 2;
        }
        List<Block> found = blocks.stream().filter(block -> {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            return (mod.isEmpty() || id.getNamespace().equals(mod)) && (!selectedOnly || targets.contains(id.toString()))
                    && (id.toString().contains(search.toLowerCase(Locale.ROOT))
                    || block.getName().getString().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)));
        }).toList();
        int columns = Math.max(2, area / 95);
        int cellWidth = area / columns;
        int visibleRows = Math.max(1, (fh - 180) / 36);
        offset = Math.min(offset, Math.max(0, (found.size() - 1) / columns - visibleRows + 1));
        for (int index = offset * columns; index < Math.min(found.size(), (offset + visibleRows) * columns); index++) {
            Block block = found.get(index);
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            boolean selected = targets.contains(id);
            Button cell = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
                        if (!targets.remove(id)) {
                            if (targets.size() < 128) { targets.add(id); }
                            else { message = translate("select_at_most_128_target_blocks"); }
                        }
                        rebuild = true;
                    }).createNarration(ignored -> Component.literal(block.getName().getString() + ", " + id
                            + ", " + translate(selected ? "selected" : "not_selected")))
                    .bounds(x + index % columns * cellWidth, y + 46 + (index / columns - offset) * 36, cellWidth - 3, 34).build());
            cell.setTooltip(Tooltip.create(Component.literal(block.getName().getString() + "\n" + id)));
            blockCells.add(new BlockCell(block, cell, selected));
        }
        if (found.isEmpty()) { label(translate("no_matching_blocks"), x + 4, y + 54, MUTED, area - 8); }
        int bottom = fy + fh - 70;
        edit(translate("quantity"), quantity, x, bottom, 68, 7, value -> quantity = value);
        button((unlimited ? "✓ " : "") + translate("unlimited"), x + 72, bottom, 80, () -> { unlimited = !unlimited; rebuild = true; });
        button(translate("reload"), x + 156, bottom, 54, () -> guardDiscard(() -> { loadJob(); rebuild = true; }));
        button("↑", x + area - 44, bottom, 20, () -> { offset = Math.max(0, offset - 1); rebuild = true; });
        button("↓", x + area - 22, bottom, 20, () -> { offset++; rebuild = true; });
        boolean batch = !recipients.isEmpty() || menu.worker() == null;
        if (batch) {
            button(translate("recipients_count", recipients.size()), x, bottom + 24, 96, () -> { page = Page.RECIPIENTS; rebuild = true; });
            actionButton(translate("apply_settings"), x + 98, bottom + 24, 94, () -> preview(false));
            actionButton(translate("apply_start"), x + 194, bottom + 24, Math.min(96, area - 194), () -> preview(true));
        } else if (menu.retired()) {
            actionButton(translate("reactivate"), x, bottom + 24, 100, () -> chooseDimension(WorkerNetwork.Action.REACTIVATE));
        } else {
            int w = (area - 10) / 6;
            actionButton(translate("start"), x, bottom + 24, w, () -> sendJob(WorkerNetwork.Action.START));
            boolean paused = selectedJob().getString("State").equals("PAUSED");
            actionButton(paused ? translate("resume") : translate("pause"), x + w + 2, bottom + 24, w,
                    () -> send(paused ? WorkerNetwork.Action.RESUME : WorkerNetwork.Action.PAUSE, revision()));
            actionButton(translate("stop"), x + 2 * (w + 2), bottom + 24, w, () -> send(WorkerNetwork.Action.STOP, revision()));
            actionButton(translate("apply"), x + 3 * (w + 2), bottom + 24, w, () -> sendJob(WorkerNetwork.Action.CONFIGURE_JOB));
            button(translate("copy_to"), x + 4 * (w + 2), bottom + 24, w, () -> { page = Page.RECIPIENTS; rebuild = true; });
            actionButton(translate("relocate"), x + 5 * (w + 2), bottom + 24, w, () -> chooseDimension(WorkerNetwork.Action.RELOCATE));
        }
    }

    private void buildInventory() {
        label(menu.retired() ? translate("archived_inventory") : translate("worker_inventory"), leftPos + 8, topPos + 32, TEXT, 220);
        if (!menu.retired()) {
            for (int index = 0; index < 9; index++) {
                int slot = index;
                Button selector = actionButton(Integer.toString(index + 1), leftPos + 8 + index * 18, topPos + 63, 16, () -> {
                    CompoundTag intent = revision(); intent.putInt("Slot", slot); send(WorkerNetwork.Action.SELECT_TOOL, intent);
                });
                selector.setTooltip(Tooltip.create(Component.literal(translate("select_tool_slot", index + 1))));
            }
        }
        if (menu.retired()) { label(translate("your_inventory"), leftPos + 8, topPos + 70, MUTED, 162); }
        if (menu.retired()) { actionButton(translate("reactivate"), fx + 10, fy + fh - 45, 96, () -> chooseDimension(WorkerNetwork.Action.REACTIVATE)); }
    }

    private void buildSettings(boolean personal) {
        if (personal) { buildSettingsPanel(true); return; }
        int x = fx + 10;
        int y = fy + 60;
            edit(translate("worker_name"), name, x, y, Math.max(85, fw - 218), 64, value -> name = value);
            actionButton(translate("rename"), fx + fw - 204, y, 56, () -> { CompoundTag intent = revision(); intent.putString("Name", name); send(WorkerNetwork.Action.RENAME, intent); });
            actionButton(menu.retired() ? translate("reactivate") : translate("relocate"), fx + fw - 146, y, 72,
                    () -> chooseDimension(menu.retired() ? WorkerNetwork.Action.REACTIVATE : WorkerNetwork.Action.RELOCATE));
            if (!menu.retired()) {
                actionButton(translate("retire"), fx + fw - 72, y, 62, () -> confirm(translate("retire_worker"),
                        List.of(translate("the_job_pauses_and_the_worker_is_removed_from_the_world"), translate("identity_settings_and_all_remaining_inventory_are_archived"), translate("you_can_withdraw_items_and_reactivate_later")),
                        () -> send(WorkerNetwork.Action.RETIRE, revision())));
            }
            label(location(data.getCompound("Selected")), x, y + 23, MUTED, fw - 20);
            button(translate("worker_overrides"), x, y + 43, fw - 20, () -> navigate(Page.OVERRIDES));
            label(translate("overrides_inherit_your_personal_profile_until_changed"), x, y + 71, MUTED, fw - 20);
    }

    private void buildSettingsPanel(boolean personal) {
        int x = fx + 10;
        int y = fy + 60;
        if (personal) { button(translate("alert_preferences"), fx + fw - 102, fy + 34, 92, () -> navigate(Page.INBOX)); }
        if (settings == null) {
            settingsRevision = personal ? data.getLong("ProfileRevision") : data.getCompound("Selected").getLong("Revision");
            settings = new WorkerSettingsPanel(font, values(data.getCompound(personal ? "PersonalSettings" : "Overrides")),
                    personal ? Map.of() : values(data.getCompound("PersonalSettings")), this::addRenderableWidget, () -> rebuild = true);
        }
        settings.build(x, y, fw - 20, fy + fh - 48 - y);
        Button apply = button(translate("apply_changes"), fx + fw - 108, fy + fh - 45, 98, () -> {
            if (!settings.error().isEmpty()) { message = settings.error(); return; }
            CompoundTag intent = personal ? new CompoundTag() : revision();
            intent.putLong("Revision", settingsRevision);
            CompoundTag settingsData = new CompoundTag();
            settings.values().forEach(settingsData::putString);
            intent.put("Values", settingsData);
            send(personal ? WorkerNetwork.Action.PERSONAL_SETTINGS : WorkerNetwork.Action.WORKER_SETTINGS, intent);
        });
        apply.active = waiting == 0 && (personal || !data.getBoolean("Pending"));
        button(translate("reload"), fx + 10, fy + fh - 45, 64, () -> guardDiscard(() -> { settings = null; rebuild = true; }));
    }

    private void buildRequests() {
        ListTag requests = data.getList("Relocations", Tag.TAG_COMPOUND);
        int capacity = Math.max(1, (fh - 100) / 36);
        offset = Math.min(offset, Math.max(0, requests.size() - capacity));
        for (int index = offset; index < Math.min(requests.size(), offset + capacity); index++) {
            CompoundTag request = requests.getCompound(requests.size() - 1 - index);
            int y = fy + 62 + (index - offset) * 36;
            label(shortDimension(request.getString("Dimension")) + " — " + stateName(request.getString("State")), fx + 12, y, TEXT, fw - 90);
            label(request.getString("Error").isEmpty() ? translate("attempts", request.getInt("Attempts")) : request.getString("Error"), fx + 12, y + 12, MUTED, fw - 90);
            String state = request.getString("State");
            if (state.equals("PENDING")) {
                button(translate("cancel"), fx + fw - 68, y, 56, () -> {
                    CompoundTag intent = new CompoundTag(); intent.putUUID("Request", request.getUUID("Request"));
                    send(WorkerNetwork.Action.CANCEL_RELOCATION, intent);
                }).active = waiting == 0;
            }
        }
        if (requests.isEmpty()) { label(translate("no_deployment_or_relocation_requests"), fx + 12, fy + 66, MUTED, fw - 24); }
        button("↑", fx + fw - 54, fy + fh - 46, 20, () -> { offset = Math.max(0, offset - 1); rebuild = true; });
        button("↓", fx + fw - 32, fy + fh - 46, 20, () -> { offset++; rebuild = true; });
    }

    private void chooseDimension(WorkerNetwork.Action action) {
        dimensionAction = action;
        dimension = "minecraft:overworld";
        confirm(action == WorkerNetwork.Action.DEPLOY ? translate("add_worker") : action == WorkerNetwork.Action.REACTIVATE ? translate("reactivate_worker") : translate("relocate_worker"),
                List.of(translate("find_a_safe_random_destination_inside_the_world_border"), action == WorkerNetwork.Action.DEPLOY
                        ? translate("new_workers_have_nine_empty_slots_bring_your_own_equipment") : translate("unfinished_jobs_stay_paused_remaining_inventory_is_retained"),
                        translate("preparation_can_be_cancelled_from_requests")), () -> {
                    CompoundTag intent = action == WorkerNetwork.Action.DEPLOY ? new CompoundTag() : revision();
                    intent.putUUID("Request", UUID.randomUUID()); intent.putString("Dimension", dimension);
                    send(action, intent);
                });
    }

    private void confirm(String title, List<String> lines, Runnable accepted) {
        dialog = new Dialog(title, lines, accepted);
        dialogOffset = 0;
        rebuild = true;
    }

    private void buildDialog() {
        int x = fx + 14;
        int y = fy + 18;
        label(dialog.title(), x, y, GREEN, fw - 28);
        List<String> wrapped = new ArrayList<>();
        for (String line : dialog.lines()) {
            for (var text : font.split(Component.literal(line), fw - 32)) {
                // Store wrapped lines as strings so every caption shares the same clipped rendering.
                StringBuilder value = new StringBuilder();
                text.accept((index, style, codePoint) -> { value.appendCodePoint(codePoint); return true; });
                wrapped.add(value.toString());
            }
            wrapped.add("");
        }
        int capacity = Math.max(1, (fh - (dimensionAction == null ? 94 : 116)) / 11);
        dialogOffset = Math.min(dialogOffset, Math.max(0, wrapped.size() - capacity));
        for (int index = dialogOffset; index < Math.min(wrapped.size(), dialogOffset + capacity); index++) {
            label(wrapped.get(index), x, y + 26 + (index - dialogOffset) * 11, TEXT, fw - 28);
        }
        if (wrapped.size() > capacity) {
            button("↑", fx + fw - 60, y - 3, 20, () -> { dialogOffset = Math.max(0, dialogOffset - 1); rebuild = true; });
            button("↓", fx + fw - 38, y - 3, 20, () -> { dialogOffset++; rebuild = true; });
        }
        if (dimensionAction != null) {
            button(translate("dimension", shortDimension(dimension)), x, fy + fh - 70, fw - 28,
                    () -> { dimension = dimension.equals("minecraft:overworld") ? "minecraft:the_nether" : "minecraft:overworld"; rebuild = true; });
        }
        button(translate("cancel"), fx + 14, fy + fh - 42, 86, () -> { dialog = null; dimensionAction = null; rebuild = true; });
        button(translate("confirm"), fx + fw - 100, fy + fh - 42, 86, () -> {
            Runnable accepted = dialog.confirm(); dialog = null; dimensionAction = null; accepted.run(); rebuild = true;
        }).active = waiting == 0;
    }

    private void navigate(Page next) {
        if (next == page) { return; }
        guardDiscard(() -> { page = next; settings = null; offset = 0; loadJob(); rebuild = true; });
    }

    private void navigateRoster(boolean retired) { guardDiscard(() -> openRoster(retired, 0)); }
    private void openRoster(boolean retired, int pageIndex) {
        CompoundTag intent = new CompoundTag(); intent.putBoolean("Retired", retired); intent.putInt("Page", pageIndex);
        send(WorkerNetwork.Action.OPEN_ROSTER, intent);
    }

    private boolean dirty() {
        return jobDirty()
                || (settings != null && settings.dirty()) || (!name.equals(data.getCompound("Selected").getString("Name")) && menu.worker() != null);
    }

    private void buildInbox() {
        button(translate("toasts", translate(data.getBoolean("ShowToasts") ? "on" : "off")), fx + fw - 168, fy + 34, 78,
                () -> notificationPreferences(!data.getBoolean("ShowToasts"), data.getBoolean("PlaySounds")));
        button(translate("sounds", translate(data.getBoolean("PlaySounds") ? "on" : "off")), fx + fw - 88, fy + 34, 78,
                () -> notificationPreferences(data.getBoolean("ShowToasts"), !data.getBoolean("PlaySounds")));
        ListTag inbox = data.getList("Notifications", Tag.TAG_COMPOUND);
        int rowHeight = Math.min(60, (fh - 112) / 5);
        for (int index = 0; index < inbox.size(); index++) {
            CompoundTag entry = inbox.getCompound(index);
            int y = fy + 60 + index * rowHeight;
            String summary = translate("completion_amount", entry.getString("Name"), entry.getLong("Amount"));
            Button row = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
                List<String> lines = new ArrayList<>();
                lines.add(summary); lines.add(notificationTime(entry.getLong("Time")));
                lines.add(entry.getList("Targets", Tag.TAG_STRING).stream().map(Tag::getAsString).reduce((a, b) -> a + ", " + b).orElse(""));
                lines.add(translate("confirm_marks_read"));
                UUID run = entry.getUUID("Run");
                confirm(translate("completion_details"), lines, () -> {
                    CompoundTag intent = new CompoundTag(); intent.putUUID("Run", run); send(WorkerNetwork.Action.READ_NOTIFICATION, intent);
                });
            }).createNarration(ignored -> Component.literal(summary + ", " + translate(entry.getBoolean("Read") ? "read" : "unread")))
                    .bounds(fx + 10, y, fw - 20, rowHeight - 2).build());
            row.setTooltip(Tooltip.create(Component.literal(summary + "\n" + notificationTime(entry.getLong("Time")))));
            label((entry.getBoolean("Read") ? "" : "• ") + summary, fx + 16, y + 3, entry.getBoolean("Read") ? MUTED : GREEN, fw - 32);
            label(notificationTime(entry.getLong("Time")), fx + 16, y + 13, MUTED, fw - 32);
        }
        if (inbox.isEmpty()) { label(translate("no_completions"), fx + 12, fy + 68, MUTED, fw - 24); }
        button(translate("mark_all_read"), fx + 10, fy + fh - 46, 106,
                () -> send(WorkerNetwork.Action.READ_ALL_NOTIFICATIONS, new CompoundTag()));
        int current = data.getInt("NotificationPage");
        label(translate("page_count", current + 1, Math.max(1, (data.getInt("NotificationCount") + 4) / 5)), fx + fw - 160, fy + fh - 40, MUTED, 100);
        button("<", fx + fw - 54, fy + fh - 46, 20, () -> notificationPage(current - 1)).active = current > 0;
        button(">", fx + fw - 32, fy + fh - 46, 20, () -> notificationPage(current + 1)).active = (current + 1) * 5 < data.getInt("NotificationCount");
    }

    private void notificationPreferences(boolean toasts, boolean sounds) {
        CompoundTag intent = new CompoundTag(); intent.putLong("Revision", data.getLong("NotificationRevision"));
        intent.putBoolean("Toasts", toasts); intent.putBoolean("Sounds", sounds); send(WorkerNetwork.Action.NOTIFICATION_PREFERENCES, intent);
    }

    private void notificationPage(int next) { CompoundTag intent = new CompoundTag(); intent.putInt("Page", next); send(WorkerNetwork.Action.NOTIFICATION_PAGE, intent); }
    private static String notificationTime(long time) {
        return java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.SHORT)
                .format(java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault()));
    }

    private boolean jobDirty() { return !targets.equals(savedTargets) || !quantity.equals(savedQuantity) || unlimited != savedUnlimited; }

    private void guardDiscard(Runnable action) {
        if (dirty()) { confirm(translate("discard_unsaved_changes"), List.of(translate("changes_to_this_job_name_or_settings_have_not_been_applied")), action); }
        else { action.run(); }
    }

    @Override
    public void onClose() { guardDiscard(super::onClose); }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == 256 && dialog != null) { dialog = null; dimensionAction = null; rebuild = true; return true; }
        if (getFocused() instanceof EditBox && key != 256) {
            return getFocused().keyPressed(key, scanCode, modifiers);
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (dialog != null) { dialogOffset = Math.max(0, dialogOffset + (vertical < 0 ? 1 : -1)); rebuild = true; return true; }
        if (settings != null && (page == Page.OVERRIDES || page == Page.PERSONAL) && settings.mouseScrolled(vertical)) { return true; }
        if (page == Page.ROSTER || page == Page.RECIPIENTS || page == Page.JOB || page == Page.REQUESTS) {
            offset = Math.max(0, offset + (vertical < 0 ? 1 : -1)); rebuild = true; return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    private void sendJob(WorkerNetwork.Action action) {
        CompoundTag intent = jobIntent();
        if (intent != null) { intent.putLong("Revision", jobRevision); send(action, intent); }
    }

    private CompoundTag jobIntent() {
        int amount;
        try { amount = unlimited ? 0 : Integer.parseInt(quantity); }
        catch (NumberFormatException invalid) { message = translate("enter_a_quantity_from_1_to_1_000_000"); return null; }
        if ((!unlimited && amount < 1) || amount > 1_000_000 || targets.isEmpty()) {
            message = translate("choose_target_blocks_and_a_quantity_from_1_to_1_000_000_or_unlimited"); return null;
        }
        CompoundTag intent = new CompoundTag();
        ListTag targetList = new ListTag(); targets.forEach(id -> targetList.add(StringTag.valueOf(id)));
        intent.put("Targets", targetList); intent.putInt("Quantity", amount); return intent;
    }

    private void preview(boolean start) {
        if (recipients.isEmpty()) { message = translate("select_at_least_one_worker"); return; }
        CompoundTag intent = jobIntent();
        if (intent == null) { return; }
        ListTag refs = new ListTag();
        rows().stream().filter(row -> recipients.contains(row.getUUID("Worker"))).forEach(row -> refs.add(reference(row)));
        intent.put("Recipients", refs); intent.putBoolean("Start", start); send(WorkerNetwork.Action.PREVIEW_APPLY_JOB, intent);
    }

    private void send(WorkerNetwork.Action action, CompoundTag intent) {
        if (waiting != 0) { return; }
        WorkerNetwork.Intent request = menu.intent(action, intent);
        waiting = request.sequence(); sentAction = action; message = translate("waiting");
        PacketDistributor.sendToServer(request); rebuild = true;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        CompoundTag fresh = menu.snapshotVersion() == snapshotVersion ? new CompoundTag() : menu.snapshot();
        snapshotVersion = menu.snapshotVersion();
        if (!fresh.isEmpty()) {
            boolean changed = !fresh.equals(data);
            boolean stateChanged = !fresh.getCompound("Selected").getCompound("Job").getString("State").equals(selectedJob().getString("State"))
                    || fresh.getBoolean("Pending") != data.getBoolean("Pending");
            data = fresh;
            if (!initialized) { initialized = true; loadJob(); rebuild = true; }
            if (waiting > 0 && menu.sequence() >= waiting) {
                waiting = 0;
                CompoundTag response = data.getCompound("Response");
                String kind = response.getString("Kind");
                if (kind.equals("Error")) { message = translate("server_error", response.getString("Error")); }
                else if (kind.equals("Preview")) {
                    List<String> lines = new ArrayList<>();
                    lines.add(response.getBoolean("Start") ? translate("apply_job_and_start_these_workers") : translate("apply_job_without_starting_these_workers"));
                    for (Tag item : response.getList("Recipients", Tag.TAG_COMPOUND)) {
                        CompoundTag row = (CompoundTag) item;
                        lines.add(row.getBoolean("Busy") ? translate("replaces_busy", row.getString("Name")) : row.getString("Name"));
                    }
                    lines.add(translate("worker_setting_overrides_are_preserved"));
                    UUID token = response.getUUID("Confirmation");
                    confirm(translate("confirm_batch_job"), lines, () -> { CompoundTag intent = new CompoundTag(); intent.putUUID("Confirmation", token); send(WorkerNetwork.Action.APPLY_JOB, intent); });
                } else if (kind.equals("BatchResult")) {
                    List<String> outcomes = new ArrayList<>();
                    for (Tag item : response.getList("Recipients", Tag.TAG_COMPOUND)) {
                        CompoundTag row = (CompoundTag) item;
                        String workerName = rows().stream().filter(candidate -> candidate.getUUID("Worker").equals(row.getUUID("Worker")))
                                .map(candidate -> candidate.getString("Name")).findFirst().orElse(row.getUUID("Worker").toString());
                        outcomes.add(workerName + ": " + (row.getString("Error").isEmpty() ? translate("applied") : row.getString("Error")));
                    }
                    confirm(translate("batch_results"), outcomes, () -> { });
                    saveDraft(); message = translate("batch_finished_review_each_result");
                } else {
                    message = translate("applied");
                    if (sentAction == WorkerNetwork.Action.START || sentAction == WorkerNetwork.Action.CONFIGURE_JOB) { saveDraft(); }
                    if (sentAction == WorkerNetwork.Action.RENAME) { name = data.getCompound("Selected").getString("Name"); }
                    if (settings != null && (sentAction == WorkerNetwork.Action.PERSONAL_SETTINGS || sentAction == WorkerNetwork.Action.WORKER_SETTINGS)) {
                        settings.accepted(values(data.getCompound(page == Page.PERSONAL ? "PersonalSettings" : "Overrides")),
                                page == Page.PERSONAL ? Map.of() : values(data.getCompound("PersonalSettings")));
                        settingsRevision = page == Page.PERSONAL ? data.getLong("ProfileRevision") : data.getCompound("Selected").getLong("Revision");
                    }
                }
                rebuild = true;
            } else if (changed && (page == Page.ROSTER || page == Page.REQUESTS || page == Page.INBOX || stateChanged) && dialog == null) { rebuild = true; }
            if (waiting == 0 && !jobDirty() && recipients.isEmpty() && menu.worker() != null) {
                Set<String> previousTargets = Set.copyOf(targets);
                String previousQuantity = quantity;
                boolean previousUnlimited = unlimited;
                loadJobDraft();
                if (!previousTargets.equals(targets) || !previousQuantity.equals(quantity) || previousUnlimited != unlimited) { rebuild = true; }
            }
        }
        if (rebuild) { rebuild = false; build(); }
    }

    private void loadJob() {
        loadJobDraft();
        name = data.getCompound("Selected").getString("Name");
    }

    private void loadJobDraft() {
        CompoundTag job = selectedJob();
        targets.clear(); job.getList("Targets", Tag.TAG_STRING).forEach(target -> targets.add(target.getAsString()));
        quantity = Integer.toString(job.getInt("Requested") > 0 ? job.getInt("Requested") : 64);
        unlimited = !targets.isEmpty() && job.getInt("Requested") == 0;
        saveDraft();
        jobRevision = data.getCompound("Selected").getLong("Revision");
    }

    private void saveDraft() { savedTargets = Set.copyOf(targets); savedQuantity = quantity; savedUnlimited = unlimited; }
    private CompoundTag selectedJob() { return data.getCompound("Selected").getCompound("Job"); }
    private CompoundTag revision() { CompoundTag tag = new CompoundTag(); tag.putLong("Revision", data.getCompound("Selected").getLong("Revision")); return tag; }
    private static CompoundTag reference(CompoundTag row) { CompoundTag ref = new CompoundTag(); ref.putUUID("Worker", row.getUUID("Worker")); ref.putLong("Revision", row.getLong("Revision")); return ref; }
    private List<CompoundTag> rows() { return data.getList(translate("workers"), Tag.TAG_COMPOUND).stream().map(CompoundTag.class::cast).toList(); }
    private static Map<String, String> values(CompoundTag tag) { Map<String, String> result = new LinkedHashMap<>(); tag.getAllKeys().forEach(key -> result.put(key, tag.getString(key))); return result; }
    private static String shortId(String id) { return id.substring(id.indexOf(':') + 1).replace('_', ' '); }
    private static String shortDimension(String id) { return id.equals("minecraft:overworld") ? translate("overworld") : id.equals("minecraft:the_nether") ? translate("nether") : shortId(id); }
    private static String location(CompoundTag row) { BlockPos pos = BlockPos.of(row.getLong("Position")); return row.getString("Dimension") + "  " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ(); }
    private static String progress(CompoundTag job) { return job.getLong("Completed") + " / " + (job.getInt("Requested") == 0 ? "∞" : job.getInt("Requested")); }
    private static String jobSummary(CompoundTag job) { return stateName(job.getString("State")) + " — " + progress(job) + (job.getString("Error").isEmpty() ? "" : " — " + job.getString("Error")); }
    private static String stateName(String state) { return Component.translatableWithFallback("gui.automatone_worker.state_" + state.toLowerCase(Locale.ROOT), state).getString(); }

    private Button actionButton(String text, int x, int y, int size, Runnable action) {
        Button button = button(text, x, y, size, action);
        button.active = waiting == 0 && !data.getBoolean("Pending"); return button;
    }
    private Button button(String text, int x, int y, int size, Runnable action) { return button(text, x, y, size, 20, action); }
    private Button button(String text, int x, int y, int size, int height, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(text), ignored -> action.run()).bounds(x, y, size, height).build());
    }
    private EditBox edit(String title, String value, int x, int y, int size, int maximum, java.util.function.Consumer<String> changed) {
        EditBox box = new EditBox(font, x, y, size, 20, Component.literal(title));
        box.setMaxLength(maximum); box.setValue(value); box.setHint(Component.literal(title)); box.setResponder(changed);
        box.setTooltip(Tooltip.create(Component.literal(title))); return addRenderableWidget(box);
    }
    private void label(String text, int x, int y, int color, int maxWidth) { captions.add(new Caption(text, x, y, color, maxWidth)); }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(fx - 1, fy - 1, fx + fw + 1, fy + fh + 1, 0xFF101411);
        graphics.fill(fx, fy, fx + fw, fy + fh, 0xFF343A35);
        graphics.fill(fx + 3, fy + 3, fx + fw - 3, fy + fh - 3, 0xFF202622);
        graphics.fill(fx + 5, fy + 29, fx + fw - 5, fy + 30, GREEN);
        graphics.fill(fx + 5, fy + fh - 23, fx + fw - 5, fy + fh - 22, 0xFF87928F);
        if (dialog == null) { graphics.renderItem(new ItemStack(WorkerMod.CONTROLLER.get()), fx + 10, fy + 6); }
        for (int x : new int[] {fx + 3, fx + fw - 8}) {
            for (int y : new int[] {fy + 3, fy + fh - 8}) { graphics.fill(x, y, x + 5, y + 5, 0xFF9C5936); }
        }
        if (page == Page.INVENTORY && dialog == null) {
            for (Slot slot : menu.slots) {
                if (!slot.isActive()) { continue; }
                int x = leftPos + slot.x; int y = topPos + slot.y;
                graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF111611);
                graphics.fill(x, y, x + 17, y + 17, 0xFF757E70);
                graphics.fill(x, y, x + 16, y + 16, 0xFF424A40);
                if (slot.index == data.getInt("SelectedSlot") && slot.index < 9 && !menu.retired()) {
                    graphics.renderOutline(x - 1, y - 1, 18, 18, GREEN);
                }
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) { }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        for (Caption caption : captions) { graphics.drawString(font, font.plainSubstrByWidth(caption.text(), caption.maxWidth()), caption.x(), caption.y(), caption.color(), false); }
        for (BlockCell cell : blockCells) {
            int x = cell.button().getX(); int y = cell.button().getY();
            if (cell.selected()) { graphics.renderOutline(x, y, cell.button().getWidth(), cell.button().getHeight(), GREEN); }
            ItemStack stack = new ItemStack(cell.block());
            if (!stack.isEmpty()) { graphics.renderItem(stack, x + 4, y + 3); }
            graphics.drawString(font, font.plainSubstrByWidth(cell.block().getName().getString(), cell.button().getWidth() - 8), x + 4, y + 22, TEXT, false);
            if (cell.selected()) { graphics.drawString(font, "✓", x + cell.button().getWidth() - 14, y + 5, GREEN, false); }
        }
        if (dialog == null && settings != null && (page == Page.OVERRIDES || page == Page.PERSONAL)) { settings.render(graphics, mouseX, mouseY); }
        String status = menu.worker() == null ? translate("active_count", data.getInt("ActiveCount")) : jobSummary(selectedJob());
        if (page == Page.INBOX) { status = translate("unread_count", data.getInt("Unread")); }
        if (data.getBoolean("Pending")) { status = translate("preparing") + " — " + status; }
        if (waiting > 0 || !message.isEmpty()) { status += " | " + (waiting > 0 ? translate("waiting") : message); }
        graphics.drawString(font, font.plainSubstrByWidth(status, fw - 24), fx + 12, fy + fh - 15, MUTED, false);
        if (mouseY >= fy + fh - 22 && mouseY < fy + fh && mouseX >= fx && mouseX <= fx + fw) { graphics.renderTooltip(font, Component.literal(status), mouseX, mouseY); }
        renderTooltip(graphics, mouseX, mouseY);
    }
}
