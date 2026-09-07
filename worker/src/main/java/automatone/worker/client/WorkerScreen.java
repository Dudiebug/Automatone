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
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
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
    private static String errorText(String code) {
        if (code.isEmpty()) { return ""; }
        String key = "error." + code.toLowerCase(Locale.ROOT);
        return net.minecraft.locale.Language.getInstance().has("gui.automatone_worker." + key)
                ? translate(key) : translate("error.unknown", code);
    }

    static String translate(String key, Object... args) {
        return Component.translatable("gui.automatone_worker." + key, args).getString();
    }
    private enum Page { ROSTER, JOB, INVENTORY, COLLECTION, CLEANUP, SETTINGS, OVERRIDES, PERSONAL, RECIPIENTS, KITS, REQUESTS, INBOX }
    private record Caption(String text, int x, int y, int color, int maxWidth) { }
    private record BlockCell(Block block, Button button, boolean selected) { }
    private record ItemCell(ItemStack stack, Button button, boolean selected, long count) { }
    private record Dialog(String title, List<String> lines, Runnable confirm) { }

    private static final int TEXT = 0xFFE4E8DF;
    private static final int MUTED = 0xFFAFB7AB;
    private static final int GREEN = 0xFF8AAE4E;
    private final List<Caption> captions = new ArrayList<>();
    private final List<BlockCell> blockCells = new ArrayList<>();
    private final List<ItemCell> itemCells = new ArrayList<>();
    private final Set<String> targets = new LinkedHashSet<>();
    private final Set<String> discardBlocks = new LinkedHashSet<>();
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
    private boolean pickupOverride;
    private CompoundTag savedCleanup = new CompoundTag();
    private long cleanupRevision;
    private boolean compactInventory;
    private boolean playerInventory;
    private boolean selectedOnly;
    private boolean initialized;
    private boolean rebuild;
    private int offset;
    private int rosterScrollX;
    private int rosterScrollTop;
    private int rosterScrollHeight;
    private int rosterScrollThumb;
    private int rosterScrollMaximum;
    private boolean draggingRosterScroll;
    private double rosterScrollGrab;
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
    private int collectionMode;
    private String collectionDimension = "";
    private String collectionWorker = "";
    private String collectionSearch = "";
    private boolean retireAfterCollection;
    private boolean choosingCollectionWorker;
    private boolean personalPickupRules;
    private boolean collectionMenuInitialized;
    private boolean batchMenuInitialized;
    private int newCount;
    private final Set<Integer> toolSlots = new LinkedHashSet<>();
    private final Map<Integer, Integer> materialSlots = new LinkedHashMap<>();
    private boolean suppliesInitialized;
    private int materialSlot = -1;
    private String materialQuantity = "64";
    private List<CompoundTag> fleetOutcomes = List.of();
    private boolean choosingMaterials;
    private boolean defaultMaterialMissing;

    public WorkerScreen(WorkerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        page = menu.worker() == null ? Page.ROSTER : Page.JOB;
        personalPickupRules = menu.worker() == null;
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
        compactInventory = fw < 384;
        leftPos = fx + (fw - (compactInventory ? 176 : 372)) / 2;
        topPos = fy + 26;
        menu.inventoryLayout(compactInventory, playerInventory);
        build();
    }

    private void build() {
        int focusedIndex = children().indexOf(getFocused());
        String focused = getFocused() instanceof AbstractWidget widget ? widget.getMessage().getString() : "";
        int cursor = getFocused() instanceof EditBox box ? box.getCursorPosition() : -1;
        clearWidgets();
        captions.clear();
        blockCells.clear();
        itemCells.clear();
        menu.showInventory(dialog == null && page == Page.INVENTORY);
        if (dialog != null) { buildDialog(); return; }
        int navWidth = (fw - 34) / 5;
        String[] tabs = { "overview", "batch_jobs", "collection", "retired", "settings" };
        Runnable[] routes = { () -> navigateRoster(false), this::openBatch, this::openCollection,
                () -> navigateRoster(true), () -> navigate(Page.PERSONAL) };
        for (int tab = 0; tab < tabs.length; tab++) {
            button(translate(tabs[tab]), fx + 6 + tab * navWidth, fy + 5, navWidth - 2, routes[tab]);
        }
        button("X", fx + fw - 24, fy + 5, 18, this::onClose);
        if (!initialized) {
            label(translate("waiting"), fx + 12, fy + 48, MUTED, fw - 24);
            return;
        }
        int x = fx + 10;
        int y = fy + 34;
        if (isBatchPage()) {
            Page[] steps = { Page.JOB, Page.RECIPIENTS, Page.KITS, Page.REQUESTS };
            String[] names = { "targets", "workers", "supplies", "progress" };
            int stepWidth = (fw - 20) / 4;
            for (int index = 0; index < steps.length; index++) {
                Page destination = steps[index];
                button(translate(names[index]), x + index * stepWidth, y, stepWidth - 2, () -> batchPage(destination));
            }
        } else if (menu.worker() != null && page != Page.PERSONAL && page != Page.REQUESTS && page != Page.INBOX && page != Page.COLLECTION
                && !(page == Page.CLEANUP && personalPickupRules)) {
            label(name, x, y + 5, TEXT, Math.max(65, fw - 210));
            button(translate("job"), fx + fw - 190, y, 54, () -> navigate(Page.JOB));
            button(translate("inventory"), fx + fw - 134, y, 72, () -> navigate(Page.INVENTORY));
            button(translate("settings"), fx + fw - 60, y, 50, () -> navigate(Page.SETTINGS));
        } else {
            label(page == Page.CLEANUP ? translate("pickup_rules") : page == Page.COLLECTION ? translate("collection") : page == Page.PERSONAL ? translate("personal_configuration") : page == Page.REQUESTS ? translate("deployment_relocation")
                    : page == Page.INBOX ? translate("notifications") : menu.retired() ? translate("retired_workers") : translate("your_workers"),
                    x, y + 5, TEXT, page == Page.INBOX ? fw - 182 : page == Page.PERSONAL ? fw - 210
                            : page == Page.ROSTER && !menu.retired() ? fw - 160 : fw - 20);
        }
        switch (page) {
            case ROSTER, RECIPIENTS -> buildRoster();
            case JOB -> buildJob();
            case INVENTORY -> buildInventory();
            case COLLECTION -> buildCollection();
            case CLEANUP -> buildCleanup();
            case SETTINGS -> buildSettings(false);
            case OVERRIDES -> buildSettingsPanel(false);
            case PERSONAL -> buildSettings(true);
            case REQUESTS -> buildRequests();
            case KITS -> buildKits();
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
        boolean selecting = page == Page.RECIPIENTS;
        boolean overview = page == Page.ROSTER && !menu.retired();
        List<CompoundTag> rows = selecting ? activeRows() : rows();
        int startY = selecting ? 84 : overview ? 74 : 60;
        int area = fw - (overview ? 34 : 20);
        int columns = Math.max(1, Math.min(5, area / 125));
        int cardWidth = area / columns;
        int visibleRows = Math.max(1, (fh - startY - 78) / 72);
        int slots = rows.size();
        offset = Math.min(offset, Math.max(0, (slots - 1) / columns - visibleRows + 1));
        if (selecting) {
            button(translate("select_all"), fx + 10, fy + 60, 66, () -> { rows.forEach(row -> recipients.add(row.getUUID("Worker"))); rebuild = true; });
            button(translate("clear"), fx + 78, fy + 60, 44, () -> { recipients.clear(); rebuild = true; });
            button(translate("fill_to_ten"), fx + 124, fy + 60, 84, () -> {
                recipients.clear(); rows.forEach(row -> recipients.add(row.getUUID("Worker")));
                newCount = data.getInt("FreeSlots"); chooseDefaultSupplies(); rebuild = true;
            });
            label(translate("selected_count", recipients.size()), fx + 212, fy + 66, MUTED, fw - 222);
        } else if (overview) {
            button(translate("select_all"), fx + fw - 144, fy + 34, 76, () -> { rows.forEach(row -> recipients.add(row.getUUID("Worker"))); rebuild = true; });
            button(translate("clear"), fx + fw - 66, fy + 34, 56, () -> { recipients.clear(); rebuild = true; });
            label(translate("showing_workers", slots == 0 ? 0 : offset * columns + 1,
                    Math.min(slots, (offset + visibleRows) * columns), slots), fx + 10, fy + 60, MUTED, fw - 34);
            rosterScrollX = fx + fw - 18;
            rosterScrollTop = fy + startY;
            rosterScrollHeight = Math.max(20, fh - startY - 76);
            rosterScrollMaximum = Math.max(0, (slots + columns - 1) / columns - visibleRows);
            rosterScrollThumb = Math.min(rosterScrollHeight, Math.max(16,
                    rosterScrollHeight * visibleRows / Math.max(visibleRows, (slots + columns - 1) / columns)));
        }
        for (int index = offset * columns; index < Math.min(slots, (offset + visibleRows) * columns); index++) {
            int x = fx + 10 + index % columns * cardWidth + (overview ? 24 : 0);
            int textWidth = cardWidth - (overview ? 40 : 16);
            int y = fy + startY + (index / columns - offset) * 72;
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
                    .bounds(x, y, cardWidth - (overview ? 28 : 4), 66).build());
            card.setTooltip(Tooltip.create(Component.literal(row.getString("Name") + "\n" + location(row)
                    + "\n" + jobSummary(row.getCompound("Job")))));
            if (overview) { button(recipients.contains(id) ? "✓" : "□", x - 24, y + 2, 20, () -> toggleRecipient(id)); }
            label((selecting && recipients.contains(id) ? "✓ " : "") + row.getString("Name"), x + 6, y + 5, TEXT, textWidth);
            label(shortDimension(row.getString("Dimension")), x + 6, y + 17, MUTED, textWidth);
            BlockPos pos = BlockPos.of(row.getLong("Position"));
            CompoundTag job = row.getCompound("Job");
            label(job.getString("Error").isEmpty() ? pos.getX() + ", " + pos.getY() + ", " + pos.getZ() : errorText(job.getString("Error")), x + 6, y + 28, MUTED, textWidth);
            label(job.getList("Targets", Tag.TAG_STRING).stream().map(Tag::getAsString).map(WorkerScreen::shortId)
                    .reduce((a, b) -> a + ", " + b).orElse(translate("no_job")), x + 6, y + 39, TEXT, textWidth);
            label((row.getBoolean("Pending") ? translate("preparing") : !row.getBoolean("Available") ? translate("unavailable")
                    : stateName(job.getString("State"))) + " " + progress(job), x + 6, y + 51, GREEN, textWidth);
        }
        int bottom = fy + fh - 46;
        if (selecting) {
            if (menu.worker() == null) {
                button("−", fx + 10, bottom, 22, () -> { newCount = Math.max(0, newCount - 1); chooseDefaultSupplies(); rebuild = true; });
                label(translate("new_workers_count", newCount), fx + 36, bottom + 6, TEXT, 112);
                button("+", fx + 150, bottom, 22, () -> { newCount = Math.min(data.getInt("FreeSlots"), newCount + 1); chooseDefaultSupplies(); rebuild = true; });
                button(translate("supplies"), fx + fw - 106, bottom, 96, () -> batchPage(Page.KITS));
            } else {
                button(translate("continue_count", recipients.size()), fx + fw - 120, bottom, 110, () -> { page = Page.JOB; rebuild = true; });
            }
        } else if (overview) {
            String[] operations = { "START", "PAUSE", "RESUME", "STOP", "RETIRE" };
            int actionWidth = (fw - 20) / operations.length;
            for (int index = 0; index < operations.length; index++) {
                String operation = operations[index];
                button(translate(operation.toLowerCase(Locale.ROOT)), fx + 10 + index * actionWidth, bottom,
                        actionWidth - 2, () -> previewFleet(operation)).active = !recipients.isEmpty() && waiting == 0;
            }
            button(translate("batch_jobs"), fx + 10, bottom - 24, 110, this::openBatch);
            button(translate("relocate"), fx + 122, bottom - 24, 90, () -> chooseDimension(WorkerNetwork.Action.RELOCATE))
                    .active = !recipients.isEmpty() && waiting == 0;
            label(translate("selected_count", recipients.size()), fx + 216, bottom - 18, MUTED, Math.max(0, fw - 226));
        } else {
            button(translate("collection"), fx + 10, bottom, 94, this::openCollection);
            if (data.getInt("Count") > 10) {
                button("<", fx + fw - 54, bottom, 20, () -> openRoster(true, Math.max(0, menu.page() - 1)));
                button(">", fx + fw - 32, bottom, 20, () -> openRoster(true, menu.page() + 1));
            }
        }
        if (slots == 0) { label(translate("no_workers_in_scope"), fx + 12, fy + startY + 12, MUTED, fw - 24); }
        if (!overview && slots > visibleRows * columns) {
            button("↑", fx + fw - 52, fy + fh - 70, 20, () -> { offset = Math.max(0, offset - 1); rebuild = true; });
            button("↓", fx + fw - 30, fy + fh - 70, 20, () -> { offset++; rebuild = true; });
        }
    }

    private void openCollection() {
        Runnable open = () -> {
            if (menu.worker() != null) { send(WorkerNetwork.Action.OPEN_COLLECTION, new CompoundTag()); return; }
            collectionMode = menu.retired() ? 1 : 0;
            collectionWorker = menu.worker() == null ? "" : menu.worker().toString();
            page = Page.COLLECTION;
            offset = 0;
            queryCollection(0);
        };
        if (menu.worker() != null || (settings != null && settings.dirty()) || (page == Page.CLEANUP && cleanupDirty())) { guardDiscard(open); }
        else { open.run(); }
    }

    private void queryCollection(int nextPage) {
        CompoundTag intent = new CompoundTag();
        intent.putInt("Mode", collectionMode);
        intent.putString("Dimension", collectionDimension);
        intent.putString("Worker", collectionWorker);
        intent.putString("Search", collectionSearch);
        intent.putInt("Page", Math.max(0, nextPage));
        offset = 0;
        send(WorkerNetwork.Action.COLLECTION_QUERY, intent);
    }

    private CompoundTag collectionRevision() {
        CompoundTag intent = new CompoundTag();
        intent.putLong("Revision", data.getCompound("Collection").getLong("Revision"));
        return intent;
    }

    private void buildCollection() {
        CompoundTag collection = data.getCompound("Collection");
        if (choosingCollectionWorker) { buildCollectionWorkers(collection); return; }
        int x = fx + 10;
        int area = fw - 20;
        int third = (area - 4) / 3;
        button(translate(collectionMode == 0 ? "active_workers" : collectionMode == 1 ? "retired_workers" : "all_workers"),
                x, fy + 60, third, () -> { collectionMode = (collectionMode + 1) % 3; collectionWorker = ""; queryCollection(0); });
        button(collectionDimension.isEmpty() ? translate("all_dimensions") : shortDimension(collectionDimension),
                x + third + 2, fy + 60, third, () -> {
                    List<String> dimensions = collection.getList("Dimensions", Tag.TAG_STRING).stream().map(Tag::getAsString).toList();
                    int next = dimensions.indexOf(collectionDimension) + 1;
                    collectionDimension = next >= dimensions.size() ? "" : dimensions.get(next);
                    queryCollection(0);
                });
        List<CompoundTag> sources = collection.getList("Sources", Tag.TAG_COMPOUND).stream().map(CompoundTag.class::cast).toList();
        String workerName = sources.stream().filter(row -> row.getUUID("Worker").toString().equals(collectionWorker))
                .map(row -> row.getString("Name")).findFirst().orElse(translate("all_workers"));
        button(workerName, x + 2 * (third + 2), fy + 60, area - 2 * (third + 2), () -> {
            choosingCollectionWorker = true; offset = 0; rebuild = true;
        }).setTooltip(Tooltip.create(Component.literal(translate("collection_sources", collection.getInt("SourceCount"), collection.getInt("Unavailable"))
                + sources.stream().map(row -> "\n" + row.getString("Name") + " · " + shortDimension(row.getString("Dimension"))
                        + (row.getBoolean("Available") ? "" : " · " + translate("unavailable"))).reduce("", String::concat))));
        edit(translate("search_items"), collectionSearch, x, fy + 84, area - 62, 128, value -> { collectionSearch = value; rebuild = true; });
        button(translate("search"), x + area - 60, fy + 84, 60, () -> queryCollection(0));
        button(translate("select_all"), x, fy + 108, 70, () -> send(WorkerNetwork.Action.COLLECTION_SELECT_ALL, collectionRevision()))
                .active = collectionSearch.toLowerCase(Locale.ROOT).strip().equals(collection.getString("Search"));
        button(translate("clear"), x + 72, fy + 108, 44, () -> send(WorkerNetwork.Action.COLLECTION_CLEAR, collectionRevision()));
        label(translate("collection_selected", collection.getInt("Selected"), collection.getLong("SelectedAmount")),
                x + 120, fy + 114, MUTED, area - 120);
        ListTag items = collection.getList("Items", Tag.TAG_COMPOUND);
        int columns = Math.max(1, area / 34);
        int visibleRows = Math.max(1, (fh - 200) / 32);
        offset = Math.min(offset, Math.max(0, (items.size() - 1) / columns - visibleRows + 1));
        for (int index = offset * columns; index < Math.min(items.size(), (offset + visibleRows) * columns); index++) {
            CompoundTag row = items.getCompound(index);
            ItemStack stack = ItemStack.parseOptional(minecraft.level.registryAccess(), row.getCompound("Stack"));
            Button cell = button("", x + index % columns * 34, fy + 132 + (index / columns - offset) * 32, 32, 30, () -> {
                CompoundTag intent = collectionRevision();
                intent.putUUID("Variant", row.getUUID("Variant"));
                intent.putBoolean("Selected", !row.getBoolean("Selected"));
                send(WorkerNetwork.Action.COLLECTION_SELECT, intent);
            });
            List<String> tooltip = new ArrayList<>(getTooltipFromItem(minecraft, stack).stream().map(Component::getString).toList());
            tooltip.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            for (Tag source : row.getList("Sources", Tag.TAG_COMPOUND)) {
                CompoundTag amount = (CompoundTag) source;
                tooltip.add(amount.getString("Name") + ": " + amount.getInt("Count"));
            }
            int additional = row.getInt("SourceCount") - row.getList("Sources", Tag.TAG_COMPOUND).size();
            if (additional > 0) { tooltip.add(translate("collection_more_sources", additional)); }
            cell.setTooltip(Tooltip.create(Component.literal(String.join("\n", tooltip))));
            itemCells.add(new ItemCell(stack, cell, row.getBoolean("Selected"), row.getLong("Count")));
        }
        if (items.isEmpty()) { label(translate("no_collectible_items"), x + 4, fy + 142, MUTED, area - 8); }
        int bottom = fy + fh - 70;
        button((retireAfterCollection ? "✓ " : "") + translate("retire_after_collection"), x, bottom, area - 48,
                () -> { retireAfterCollection = !retireAfterCollection; rebuild = true; })
                .setTooltip(Tooltip.create(Component.literal(translate("collection_reserve_help"))));
        button("↑", x + area - 44, bottom, 20, () -> { offset = Math.max(0, offset - 1); rebuild = true; });
        button("↓", x + area - 22, bottom, 20, () -> { offset++; rebuild = true; });
        button(translate("transfer_selected"), x, bottom + 24, Math.min(148, area - 92), () ->
                send(retireAfterCollection ? WorkerNetwork.Action.PREVIEW_COLLECTION_RETIRE : WorkerNetwork.Action.COLLECTION_TRANSFER,
                        collectionRevision())).active = collection.getInt("Selected") > 0;
        int current = collection.getInt("Page");
        label((current + 1) + " / " + Math.max(1, (collection.getInt("Count") + 35) / 36), x + area - 90, bottom + 30, MUTED, 42);
        button("<", x + area - 44, bottom + 24, 20, () -> queryCollection(current - 1)).active = current > 0;
        button(">", x + area - 22, bottom + 24, 20, () -> queryCollection(current + 1)).active = (current + 1) * 36 < collection.getInt("Count");
    }

    private void toggleRecipient(UUID id) {
        if (!recipients.remove(id)) { recipients.add(id); }
        rebuild = true;
    }

    private boolean isBatchPage() {
        return menu.worker() == null && (page == Page.JOB || page == Page.RECIPIENTS || page == Page.KITS || page == Page.REQUESTS);
    }

    private void batchPage(Page next) { page = next; settings = null; offset = 0; rebuild = true; }

    private void openBatch() {
        if (menu.worker() != null) { guardDiscard(() -> send(WorkerNetwork.Action.OPEN_BATCH, new CompoundTag())); }
        else { navigate(Page.JOB); }
    }

    private List<CompoundTag> activeRows() {
        return data.getList("ActiveWorkers", Tag.TAG_COMPOUND).stream().map(CompoundTag.class::cast).toList();
    }

    private ListTag recipientRefs() {
        ListTag refs = new ListTag();
        activeRows().stream().filter(row -> recipients.contains(row.getUUID("Worker"))).forEach(row -> refs.add(reference(row)));
        return refs;
    }

    private void previewFleet(String operation) {
        CompoundTag intent = new CompoundTag();
        intent.put("Recipients", recipientRefs()); intent.putString("Operation", operation);
        if (operation.equals("RELOCATE")) { intent.putString("Dimension", dimension); }
        send(WorkerNetwork.Action.PREVIEW_FLEET, intent);
    }

    private void buildCollectionWorkers(CompoundTag collection) {
        ListTag options = collection.getList("WorkerOptions", Tag.TAG_COMPOUND);
        button(translate("all_workers"), fx + 10, fy + 60, fw - 20, () -> {
            collectionWorker = ""; choosingCollectionWorker = false; queryCollection(0);
        });
        int visible = Math.max(1, (fh - 140) / 24);
        offset = Math.min(offset, Math.max(0, options.size() - visible));
        for (int index = offset; index < Math.min(options.size(), offset + visible); index++) {
            CompoundTag row = options.getCompound(index);
            button(row.getString("Name") + " · " + shortDimension(row.getString("Dimension")), fx + 10,
                    fy + 84 + (index - offset) * 24, fw - 20, () -> {
                        collectionWorker = row.getUUID("Worker").toString(); choosingCollectionWorker = false; queryCollection(0);
                    });
        }
        int current = collection.getInt("SourcePage");
        button(translate("cancel"), fx + 10, fy + fh - 46, 80, () -> { choosingCollectionWorker = false; offset = 0; rebuild = true; });
        button("↑", fx + fw - 98, fy + fh - 46, 20, () -> { offset = Math.max(0, offset - 1); rebuild = true; });
        button("↓", fx + fw - 76, fy + fh - 46, 20, () -> { offset++; rebuild = true; });
        button("<", fx + fw - 54, fy + fh - 46, 20, () -> collectionSourcePage(current - 1)).active = current > 0;
        button(">", fx + fw - 32, fy + fh - 46, 20, () -> collectionSourcePage(current + 1)).active = (current + 1) * 36 < collection.getInt("WorkerOptionCount");
    }

    private void collectionSourcePage(int next) {
        CompoundTag intent = new CompoundTag(); intent.putInt("Page", next); offset = 0;
        send(WorkerNetwork.Action.COLLECTION_SOURCE_PAGE, intent);
    }

    private void buildJob() {
        buildBlockPicker(targets);
        int x = fx + 10;
        int area = fw - 20;
        int bottom = fy + fh - 70;
        edit(translate("quantity"), quantity, x, bottom, 68, 7, value -> quantity = value);
        button((unlimited ? "✓ " : "") + translate("unlimited"), x + 72, bottom, 80, () -> { unlimited = !unlimited; rebuild = true; });
        button(translate("reload"), x + 156, bottom, 54, () -> guardDiscard(() -> { loadJob(); rebuild = true; }));
        boolean batch = !recipients.isEmpty();
        if (menu.worker() == null) {
            label(translate("per_worker_total", unlimited ? "∞" : quantity, batchTotal()), x, bottom + 30, MUTED, area - 106);
            button(translate("workers"), x + area - 102, bottom + 24, 102, () -> batchPage(Page.RECIPIENTS));
        } else if (batch) {
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

    private void buildBlockPicker(Set<String> selection) {
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
        button((selectedOnly ? "✓ " : "") + translate("selected_count", selection.size()), x, y + 23, 102,
                () -> { selectedOnly = !selectedOnly; offset = 0; rebuild = true; });
        List<String> chips = selection.stream().toList();
        int chipX = x + 106;
        for (String id : chips) {
            int chipWidth = Math.min(110, font.width(shortId(id)) + 22);
            if (chipX + chipWidth > x + area) { break; }
            button(shortId(id) + " ×", chipX, y + 23, chipWidth, () -> {
                selection.remove(id);
                if (page == Page.CLEANUP) { pickupOverride = true; }
                rebuild = true;
            });
            chipX += chipWidth + 2;
        }
        List<Block> found = blocks.stream().filter(block -> {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            return (page != Page.CLEANUP || !new ItemStack(block).isEmpty())
                    && (mod.isEmpty() || id.getNamespace().equals(mod)) && (!selectedOnly || selection.contains(id.toString()))
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
            boolean selected = selection.contains(id);
            Button cell = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
                        if (!selection.remove(id)) {
                            if (selection.size() < 128) { selection.add(id); }
                            else { message = translate("select_at_most_128_target_blocks"); }
                        }
                        if (page == Page.CLEANUP) { pickupOverride = true; }
                        rebuild = true;
                    }).createNarration(ignored -> Component.literal(block.getName().getString() + ", " + id
                            + ", " + translate(selected ? "selected" : "not_selected")))
                    .bounds(x + index % columns * cellWidth, y + 46 + (index / columns - offset) * 36, cellWidth - 3, 34).build());
            cell.setTooltip(Tooltip.create(Component.literal(block.getName().getString() + "\n" + id)));
            blockCells.add(new BlockCell(block, cell, selected));
        }
        if (found.isEmpty()) { label(translate("no_matching_blocks"), x + 4, y + 54, MUTED, area - 8); }
        int bottom = fy + fh - 70;
        button("↑", x + area - 44, bottom, 20, () -> { offset = Math.max(0, offset - 1); rebuild = true; });
        button("↓", x + area - 22, bottom, 20, () -> { offset++; rebuild = true; });
    }

    private void buildInventory() {
        menu.inventoryLayout(compactInventory, playerInventory);
        int workerX = leftPos + (compactInventory ? 8 : 194);
        String workerLabel = menu.retired() ? translate("archived_inventory") : translate("worker_inventory");
        if (compactInventory) {
            button(translate("your_inventory"), leftPos + 8, topPos + 24, 80,
                    () -> { playerInventory = true; rebuild = true; });
            button(workerLabel, leftPos + 90, topPos + 24, 80,
                    () -> { playerInventory = false; rebuild = true; });
        } else {
            label(translate("your_inventory"), leftPos + 8, topPos + 32, MUTED, 162);
            label(workerLabel, workerX, topPos + 32, TEXT, 162);
        }
        if (!menu.retired() && (!compactInventory || !playerInventory)) {
            for (int index = 0; index < 9; index++) {
                int slot = index;
                Button selector = actionButton(Integer.toString(index + 1), workerX + index * 18, topPos + 126, 16, () -> {
                    CompoundTag intent = revision(); intent.putInt("Slot", slot); send(WorkerNetwork.Action.SELECT_TOOL, intent);
                });
                selector.setTooltip(Tooltip.create(Component.literal(translate("select_tool_slot", index + 1))));
            }
        }
        actionButton(translate("collect_all"), fx + fw - 110, fy + fh - 45, 100,
                () -> send(WorkerNetwork.Action.COLLECT_ALL, revision()));
        if (!menu.retired()) { button(translate("automatic_cleanup"), fx + 10, fy + fh - 45, 132, () -> navigatePickup(false)); }
        if (menu.retired()) { actionButton(translate("reactivate"), fx + 10, fy + fh - 45, 96, () -> chooseDimension(WorkerNetwork.Action.REACTIVATE)); }
    }

    private void buildCleanup() {
        buildBlockPicker(discardBlocks);
        int x = fx + 10;
        int bottom = fy + fh - 70;
        label(translate("cobblestone_reserve"), x, bottom + 4, GREEN, fw - 70);
        button(translate("reload"), x, bottom + 24, 64, () -> guardDiscard(() -> { loadCleanup(); rebuild = true; }));
        if (!personalPickupRules) {
            button(translate(pickupOverride ? "use_global_rules" : "inherited_rules"), x + 68, bottom + 24, 110, () -> {
                discardBlocks.clear();
                data.getCompound("PickupRules").getList("Blocks", Tag.TAG_STRING).forEach(id -> discardBlocks.add(id.getAsString()));
                pickupOverride = false;
                rebuild = true;
            });
        }
        actionButton(translate("apply"), fx + fw - 86, bottom + 24, 76, () -> {
            CompoundTag intent = cleanupDraft();
            intent.putLong("Revision", cleanupRevision);
            send(personalPickupRules ? WorkerNetwork.Action.PERSONAL_PICKUP_RULES : WorkerNetwork.Action.INVENTORY_MANAGEMENT, intent);
        });
    }

    private CompoundTag cleanupDraft() {
        CompoundTag tag = new CompoundTag();
        if (!personalPickupRules) { tag.putBoolean("Override", pickupOverride); }
        ListTag list = new ListTag(); discardBlocks.forEach(id -> list.add(StringTag.valueOf(id)));
        tag.put("Blocks", list);
        return tag;
    }

    private void loadCleanup() {
        savedCleanup = data.getCompound(personalPickupRules ? "PickupRules" : "InventoryManagement").copy();
        savedCleanup.remove("Revision");
        pickupOverride = savedCleanup.getBoolean("Override");
        discardBlocks.clear();
        savedCleanup.getList("Blocks", Tag.TAG_STRING).forEach(id -> discardBlocks.add(id.getAsString()));
        cleanupRevision = data.getCompound(personalPickupRules ? "PickupRules" : "Selected").getLong("Revision");
    }

    private boolean cleanupDirty() {
        if (savedCleanup.isEmpty()) { return false; }
        return !cleanupDraft().equals(savedCleanup);
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
        if (personal) {
            button(translate("pickup_rules"), fx + fw - 198, fy + 34, 92, () -> navigatePickup(true));
            button(translate("alert_preferences"), fx + fw - 102, fy + 34, 92, () -> navigate(Page.INBOX));
        }
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

    private ItemStack supplyStack(CompoundTag row) {
        return ItemStack.parseOptional(minecraft.level.registryAccess(), row.getCompound("Stack"));
    }

    private List<CompoundTag> supplies() {
        return data.getList("Supplies", Tag.TAG_COMPOUND).stream().map(CompoundTag.class::cast).toList();
    }

    private void chooseDefaultSupplies() {
        List<Integer> validTools = supplies().stream().filter(row -> supplyStack(row).has(DataComponents.TOOL)).map(row -> row.getInt("Slot")).toList();
        toolSlots.removeIf(slot -> !validTools.contains(slot));
        while (toolSlots.size() > newCount) { toolSlots.remove(toolSlots.stream().reduce((left, right) -> right).orElseThrow()); }
        for (int slot : validTools) { if (toolSlots.size() < newCount) { toolSlots.add(slot); } }
        if (!suppliesInitialized && newCount > 0) {
            suppliesInitialized = true;
            int cobble = supplies().stream().filter(row -> {
                ItemStack stack = supplyStack(row);
                return stack.is(Items.COBBLESTONE) && stack.getComponentsPatch().isEmpty();
            }).mapToInt(row -> row.getInt("Slot")).findFirst().orElse(-1);
            if (cobble >= 0) { materialSlots.put(cobble, 64); materialSlot = cobble; }
            else { defaultMaterialMissing = true; }
        }
    }

    private String batchTotal() {
        if (unlimited) { return "∞"; }
        try { return Long.toString(Long.parseLong(quantity) * (recipients.size() + newCount)); }
        catch (NumberFormatException invalid) { return "?"; }
    }

    private void buildKits() {
        if (!suppliesInitialized) { chooseDefaultSupplies(); }
        int x = fx + 10;
        int area = fw - 20;
        int third = (area - 4) / 3;
        button((!choosingMaterials ? "✓ " : "") + translate("tools"), x, fy + 60, third, () -> { choosingMaterials = false; offset = 0; rebuild = true; });
        button((choosingMaterials ? "✓ " : "") + translate("materials"), x + third + 2, fy + 60, third,
                () -> { choosingMaterials = true; offset = 0; rebuild = true; });
        button(shortDimension(dimension), x + 2 * (third + 2), fy + 60, third, () -> {
            dimension = dimension.equals("minecraft:overworld") ? "minecraft:the_nether" : "minecraft:overworld"; rebuild = true;
        });
        label(translate("kit_summary", newCount, toolSlots.size(), materialSlots.size()), x, fy + 84, TEXT, area);
        List<CompoundTag> available = supplies().stream().filter(row -> choosingMaterials
                ? supplyStack(row).getItem() instanceof BlockItem : supplyStack(row).has(DataComponents.TOOL)).toList();
        int columns = Math.max(1, area / 34);
        int visibleRows = Math.max(1, (fh - 186) / 32);
        offset = Math.min(offset, Math.max(0, (available.size() - 1) / columns - visibleRows + 1));
        for (int index = offset * columns; index < Math.min(available.size(), (offset + visibleRows) * columns); index++) {
            CompoundTag row = available.get(index);
            int slot = row.getInt("Slot");
            ItemStack stack = supplyStack(row);
            boolean selected = choosingMaterials ? materialSlots.containsKey(slot) : toolSlots.contains(slot);
            Button cell = button("", x + index % columns * 34, fy + 100 + (index / columns - offset) * 32, 32, 30, () -> {
                if (choosingMaterials) {
                    materialSlot = slot;
                    materialQuantity = Integer.toString(materialSlots.getOrDefault(slot, 64));
                } else if (!toolSlots.remove(slot) && toolSlots.size() < newCount) { toolSlots.add(slot); }
                rebuild = true;
            });
            List<String> tooltip = new ArrayList<>(getTooltipFromItem(minecraft, stack).stream().map(Component::getString).toList());
            tooltip.add(translate("inventory_slot", slot + 1));
            if (choosingMaterials) { tooltip.add(translate("per_worker_material", materialSlots.getOrDefault(slot, 0))); }
            cell.setTooltip(Tooltip.create(Component.literal(String.join("\n", tooltip))));
            itemCells.add(new ItemCell(stack, cell, selected, stack.getCount()));
        }
        if (available.isEmpty()) { label(translate("no_kit_items"), x, fy + 108, MUTED, area); }
        int bottom = fy + fh - 70;
        if (choosingMaterials) {
            edit(translate("per_worker_quantity"), materialQuantity, x, bottom, 48, 4, value -> materialQuantity = value);
            button(translate("set"), x + 50, bottom, 38, () -> {
                try {
                    int count = Integer.parseInt(materialQuantity);
                    if (materialSlot < 0 || count < 1 || count > 2048 || (!materialSlots.containsKey(materialSlot) && materialSlots.size() >= 8)) {
                        message = translate("choose_material_quantity"); return;
                    }
                    ItemStack selected = supplies().stream().filter(row -> row.getInt("Slot") == materialSlot).map(this::supplyStack).findFirst().orElse(ItemStack.EMPTY);
                    materialSlots.keySet().removeIf(slot -> slot != materialSlot && supplies().stream().filter(row -> row.getInt("Slot") == slot)
                            .anyMatch(row -> ItemStack.isSameItemSameComponents(supplyStack(row), selected)));
                    materialSlots.put(materialSlot, count); defaultMaterialMissing = false; rebuild = true;
                } catch (NumberFormatException invalid) { message = translate("choose_material_quantity"); }
            });
            button(translate("remove"), x + 90, bottom, 58, () -> { materialSlots.remove(materialSlot); defaultMaterialMissing = false; rebuild = true; });
            button(translate("clear"), x + 150, bottom, 44, () -> { materialSlots.clear(); defaultMaterialMissing = false; rebuild = true; });
        } else {
            button(translate("auto_select_tools"), x, bottom, Math.min(158, area - 48), () -> { toolSlots.clear(); chooseDefaultSupplies(); rebuild = true; });
            label(translate("per_worker_total", unlimited ? "∞" : quantity, batchTotal()), x + 162, bottom + 6, MUTED, area - 212);
        }
        button("↑", x + area - 44, bottom, 20, () -> { offset = Math.max(0, offset - 1); rebuild = true; });
        button("↓", x + area - 22, bottom, 20, () -> { offset++; rebuild = true; });
        int half = (area - 2) / 2;
        button(translate("deploy_only"), x, bottom + 24, half, () -> previewDeployment(false));
        button(translate("deploy_start"), x + half + 2, bottom + 24, half, () -> previewDeployment(true));
        if (defaultMaterialMissing && newCount > 0) { message = translate("default_cobblestone_missing"); }
    }

    private void previewDeployment(boolean start) {
        if (defaultMaterialMissing && newCount > 0) { message = translate("default_cobblestone_missing"); return; }
        CompoundTag intent = jobIntent();
        if (intent == null) { return; }
        intent.put("Recipients", recipientRefs()); intent.putBoolean("Start", start);
        intent.putInt("NewCount", newCount); intent.putString("Dimension", dimension);
        intent.putLong("SupplyRevision", data.getLong("SupplyRevision"));
        ListTag tools = new ListTag();
        if (newCount > 0) { toolSlots.forEach(slot -> tools.add(IntTag.valueOf(slot))); }
        intent.put("ToolSlots", tools);
        ListTag materials = new ListTag();
        if (newCount > 0) {
            materialSlots.forEach((slot, count) -> { CompoundTag row = new CompoundTag(); row.putInt("Slot", slot); row.putInt("Count", count); materials.add(row); });
        }
        intent.put("Materials", materials);
        send(WorkerNetwork.Action.PREVIEW_BATCH, intent);
    }

    private void buildRequests() {
        List<CompoundTag> requests = new ArrayList<>();
        List<CompoundTag> batches = data.getList("Batches", Tag.TAG_COMPOUND).stream().map(CompoundTag.class::cast).toList();
        Set<UUID> batchRequests = batches.stream().map(row -> row.getUUID("Request")).collect(java.util.stream.Collectors.toSet());
        data.getList("Relocations", Tag.TAG_COMPOUND).stream().map(CompoundTag.class::cast)
                .filter(row -> !batchRequests.contains(row.getUUID("Request"))).forEach(requests::add);
        requests.addAll(batches);
        requests.addAll(fleetOutcomes.stream().filter(row -> !row.hasUUID("Request") || !batchRequests.contains(row.getUUID("Request"))).toList());
        int capacity = Math.max(1, (fh - 100) / 36);
        offset = Math.min(offset, Math.max(0, requests.size() - capacity));
        for (int index = offset; index < Math.min(requests.size(), offset + capacity); index++) {
            CompoundTag request = requests.get(requests.size() - 1 - index);
            int y = fy + 62 + (index - offset) * 36;
            CompoundTag worker = activeRows().stream().filter(row -> row.getUUID("Worker").equals(request.getUUID("Worker"))).findFirst().orElse(new CompoundTag());
            String workerName = request.contains("Name") ? request.getString("Name") : worker.isEmpty()
                    ? request.getUUID("Worker").toString().substring(0, 8) : worker.getString("Name");
            label(workerName + " — " + stateName(request.getString("State")), fx + 12, y, TEXT, fw - 90);
            String details = !request.getString("Error").isEmpty() ? errorText(request.getString("Error")) : worker.isEmpty()
                    ? shortDimension(request.getString("Dimension")) : jobSummary(worker.getCompound("Job"));
            label(details, fx + 12, y + 12, MUTED, fw - 90);
            button("?", fx + fw - 32, y, 20, () -> confirm(workerName, List.of(stateName(request.getString("State")), details), () -> { }));
            String state = request.getString("State");
            if (state.equals("PENDING") || state.equals("PREPARING") || state.equals("QUEUED")) {
                button(translate("cancel"), fx + fw - 90, y, 56, () -> {
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
                        ? translate("new_workers_have_36_empty_slots_bring_your_own_equipment") : translate("unfinished_jobs_stay_paused_remaining_inventory_is_retained"),
                        translate("preparation_can_be_cancelled_from_requests")), () -> {
                    if (action == WorkerNetwork.Action.RELOCATE && menu.worker() == null) { previewFleet("RELOCATE"); return; }
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
        if (menu.worker() == null) {
            Runnable change = () -> { page = next; settings = null; offset = 0; rebuild = true; };
            if ((settings != null && settings.dirty()) || (page == Page.CLEANUP && cleanupDirty())) { guardDiscard(change); }
            else { change.run(); }
        } else { guardDiscard(() -> { page = next; settings = null; offset = 0; loadJob(); rebuild = true; }); }
    }

    private void navigateRoster(boolean retired) {
        if (menu.worker() == null && menu.retired() == retired) { navigate(Page.ROSTER); }
        else { guardDiscard(() -> openRoster(retired, 0)); }
    }
    private void openRoster(boolean retired, int pageIndex) {
        CompoundTag intent = new CompoundTag(); intent.putBoolean("Retired", retired); intent.putInt("Page", pageIndex);
        send(WorkerNetwork.Action.OPEN_ROSTER, intent);
    }

    private boolean dirty() {
        return jobDirty() || cleanupDirty()
                || (settings != null && settings.dirty()) || (!name.equals(data.getCompound("Selected").getString("Name")) && menu.worker() != null);
    }

    private void navigatePickup(boolean personal) {
        guardDiscard(() -> {
            personalPickupRules = personal; page = Page.CLEANUP; settings = null; offset = 0; loadCleanup(); rebuild = true;
        });
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
        if (page == Page.COLLECTION && dialog == null && getFocused() instanceof EditBox && (key == 257 || key == 335)) {
            queryCollection(0); return true;
        }
        if (getFocused() instanceof EditBox && key != 256) {
            return getFocused().keyPressed(key, scanCode, modifiers);
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (overviewScrollbar()) {
            if (vertical == 0 || mouseY < rosterScrollTop || mouseY >= rosterScrollTop + rosterScrollHeight
                    || mouseX < fx + 10 || mouseX > fx + fw - 6) { return super.mouseScrolled(mouseX, mouseY, horizontal, vertical); }
            offset = Math.max(0, Math.min(rosterScrollMaximum, offset + (vertical < 0 ? 1 : -1)));
            rebuild = true; return true;
        }
        if (dialog != null) { dialogOffset = Math.max(0, dialogOffset + (vertical < 0 ? 1 : -1)); rebuild = true; return true; }
        if (settings != null && (page == Page.OVERRIDES || page == Page.PERSONAL) && settings.mouseScrolled(vertical)) { return true; }
        if (page == Page.ROSTER || page == Page.RECIPIENTS || page == Page.KITS || page == Page.JOB || page == Page.CLEANUP || page == Page.REQUESTS || page == Page.COLLECTION) {
            offset = Math.max(0, offset + (vertical < 0 ? 1 : -1)); rebuild = true; return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    private boolean overviewScrollbar() { return page == Page.ROSTER && !menu.retired() && dialog == null && waiting == 0; }

    private int rosterThumbY() {
        return rosterScrollTop + (rosterScrollMaximum == 0 ? 0 : (rosterScrollHeight - rosterScrollThumb) * offset / rosterScrollMaximum);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && overviewScrollbar() && rosterScrollMaximum > 0 && mouseX >= rosterScrollX && mouseX < rosterScrollX + 10
                && mouseY >= rosterScrollTop && mouseY < rosterScrollTop + rosterScrollHeight) {
            int thumbY = rosterThumbY();
            rosterScrollGrab = mouseY >= thumbY && mouseY < thumbY + rosterScrollThumb ? mouseY - thumbY : rosterScrollThumb / 2.0;
            draggingRosterScroll = true;
            scrollRosterTo(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void scrollRosterTo(double mouseY) {
        int travel = rosterScrollHeight - rosterScrollThumb;
        offset = travel <= 0 ? 0 : Math.max(0, Math.min(rosterScrollMaximum,
                (int) Math.round((mouseY - rosterScrollTop - rosterScrollGrab) * rosterScrollMaximum / travel)));
        rebuild = true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && draggingRosterScroll && overviewScrollbar()) { scrollRosterTo(mouseY); return true; }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingRosterScroll) { draggingRosterScroll = false; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
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
            recipients.retainAll(activeRows().stream().map(row -> row.getUUID("Worker")).collect(java.util.stream.Collectors.toSet()));
            if (data.getBoolean("OpenCollection") && !collectionMenuInitialized) {
                CompoundTag collection = data.getCompound("Collection");
                collectionMode = collection.getInt("Mode");
                collectionDimension = collection.getString("Dimension");
                collectionWorker = collection.getString("Worker");
                collectionSearch = collection.getString("Search");
                collectionMenuInitialized = true; page = Page.COLLECTION; rebuild = true;
            }
            if (!initialized) { initialized = true; loadJob(); rebuild = true; }
            if (data.getBoolean("OpenBatch") && !batchMenuInitialized) {
                batchMenuInitialized = true; page = Page.JOB;
                CompoundTag draft = data.getCompound("BatchDraft");
                targets.clear(); draft.getList("Targets", Tag.TAG_STRING).forEach(target -> targets.add(target.getAsString()));
                quantity = Integer.toString(draft.getInt("Requested") > 0 ? draft.getInt("Requested") : 64);
                unlimited = !targets.isEmpty() && draft.getInt("Requested") == 0;
                saveDraft(); rebuild = true;
            }
            if (waiting > 0 && menu.sequence() >= waiting) {
                waiting = 0;
                CompoundTag response = data.getCompound("Response");
                String kind = response.getString("Kind");
                if (kind.equals("Error")) { message = translate("server_error", errorText(response.getString("Error"))); }
                else if (kind.equals("DeploymentPreview")) { showDeploymentPreview(response); }
                else if (kind.equals("FleetPreview")) { showFleetPreview(response); }
                else if (kind.equals("DeploymentResult") || kind.equals("FleetResult")) {
                    fleetOutcomes = response.getList("Recipients", Tag.TAG_COMPOUND).stream().map(CompoundTag.class::cast).map(CompoundTag::copy).toList();
                    if (kind.equals("DeploymentResult")) { saveDraft(); newCount = 0; toolSlots.clear(); }
                    message = kind.equals("DeploymentResult") || response.getString("Operation").equals("RELOCATE")
                            ? translate("queued_workers", response.getInt("Queued")) : translate("batch_finished_review_each_result");
                    batchPage(Page.REQUESTS);
                }
                else if (kind.equals("CollectionPreview")) {
                    List<String> lines = new ArrayList<>();
                    lines.add(translate("collection_retire_warning"));
                    for (Tag item : response.getList("Workers", Tag.TAG_COMPOUND)) { lines.add(((CompoundTag) item).getString("Name")); }
                    lines.add(translate("collection_retire_remainder"));
                    UUID token = response.getUUID("Confirmation");
                    confirm(translate("confirm_collection_retirement"), lines, () -> {
                        CompoundTag intent = new CompoundTag(); intent.putUUID("Confirmation", token);
                        send(WorkerNetwork.Action.COLLECTION_RETIRE, intent);
                    });
                } else if (kind.equals("CollectionResult")) {
                    message = translate("collection_result", response.getLong("Collected"), response.getLong("Remaining"), response.getInt("Retired"));
                } else if (kind.equals("Preview")) {
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
                        outcomes.add(workerName + ": " + (row.getString("Error").isEmpty() ? translate("applied") : errorText(row.getString("Error"))));
                    }
                    confirm(translate("batch_results"), outcomes, () -> { });
                    saveDraft(); message = translate("batch_finished_review_each_result");
                } else {
                    message = sentAction == WorkerNetwork.Action.COLLECT_ALL ? translate("collected_count", response.getInt("Collected")) : translate("applied");
                    if (sentAction == WorkerNetwork.Action.INVENTORY_MANAGEMENT || sentAction == WorkerNetwork.Action.PERSONAL_PICKUP_RULES) { loadCleanup(); }
                    if (sentAction == WorkerNetwork.Action.START || sentAction == WorkerNetwork.Action.CONFIGURE_JOB) { saveDraft(); }
                    if (sentAction == WorkerNetwork.Action.RENAME) { name = data.getCompound("Selected").getString("Name"); }
                    if (settings != null && (sentAction == WorkerNetwork.Action.PERSONAL_SETTINGS || sentAction == WorkerNetwork.Action.WORKER_SETTINGS)) {
                        settings.accepted(values(data.getCompound(page == Page.PERSONAL ? "PersonalSettings" : "Overrides")),
                                page == Page.PERSONAL ? Map.of() : values(data.getCompound("PersonalSettings")));
                        settingsRevision = page == Page.PERSONAL ? data.getLong("ProfileRevision") : data.getCompound("Selected").getLong("Revision");
                    }
                }
                rebuild = true;
            } else if (changed && (page == Page.ROSTER || isBatchPage() || page == Page.INBOX || page == Page.COLLECTION || stateChanged) && dialog == null) { rebuild = true; }
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
        loadCleanup();
    }

    private void showDeploymentPreview(CompoundTag response) {
        List<String> lines = new ArrayList<>();
        lines.add(translate("per_worker_total", unlimited ? "∞" : quantity, batchTotal()));
        lines.add(translate("new_workers_count", response.getInt("NewCount")) + " — " + shortDimension(dimension));
        lines.add(translate("tools_selected_required", response.getInt("ToolsSelected"), response.getInt("ToolsRequired")));
        for (Tag tag : response.getList("Supplies", Tag.TAG_COMPOUND)) {
            CompoundTag supply = (CompoundTag) tag;
            ItemStack stack = supplyStack(supply);
            lines.add(String.join(" · ", getTooltipFromItem(minecraft, stack).stream().map(Component::getString).toList()));
            lines.add(translate("supply_required_available", supply.getInt("Required"), supply.getInt("Available")));
        }
        lines.addAll(recipientDescriptions(response));
        if (!response.getBoolean("CanSubmit")) {
            lines.add(errorText(response.getString("Error")));
            confirm(translate("kit_shortage"), lines, () -> { });
            return;
        }
        lines.add(translate("batch_close_continues"));
        UUID token = response.getUUID("Confirmation");
        confirm(translate(response.getBoolean("Start") ? "deploy_start" : "deploy_only"), lines, () -> {
            CompoundTag intent = new CompoundTag(); intent.putUUID("Confirmation", token); send(WorkerNetwork.Action.SUBMIT_BATCH, intent);
        });
    }

    private List<String> recipientDescriptions(CompoundTag response) {
        List<String> descriptions = new ArrayList<>();
        for (Tag tag : response.getList("Recipients", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) tag;
            String line = row.getString("Name");
            if (!row.getString("Error").isEmpty()) { line += ": " + errorText(row.getString("Error")); }
            else if (row.getBoolean("Busy") && (response.getString("Operation").equals("BATCH") || response.getString("Operation").equals("START"))) {
                line = translate("replaces_busy", line);
            }
            else { line += ": " + translate("eligible"); }
            descriptions.add(line);
        }
        return descriptions;
    }

    private void showFleetPreview(CompoundTag response) {
        UUID token = response.getUUID("Confirmation");
        Runnable apply = () -> { CompoundTag intent = new CompoundTag(); intent.putUUID("Confirmation", token); send(WorkerNetwork.Action.APPLY_FLEET, intent); };
        List<String> lines = recipientDescriptions(response);
        if (response.getString("Operation").equals("RELOCATE")) {
            lines.add(0, translate("dimension", shortDimension(response.getString("Dimension"))));
            lines.add(translate("relocation_queue_help"));
        }
        if (response.getString("Operation").equals("RETIRE")) { lines.add(translate("collection_retire_remainder")); }
        if (response.getBoolean("ConfirmationRequired")) {
            confirm(translate(response.getString("Operation").toLowerCase(Locale.ROOT)), lines, apply);
        } else { apply.run(); }
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
    private List<CompoundTag> rows() { return data.getList("Workers", Tag.TAG_COMPOUND).stream().map(CompoundTag.class::cast).toList(); }
    private static Map<String, String> values(CompoundTag tag) { Map<String, String> result = new LinkedHashMap<>(); tag.getAllKeys().forEach(key -> result.put(key, tag.getString(key))); return result; }
    private static String shortId(String id) { return id.substring(id.indexOf(':') + 1).replace('_', ' '); }
    private static String shortDimension(String id) { return id.equals("minecraft:overworld") ? translate("overworld") : id.equals("minecraft:the_nether") ? translate("nether") : shortId(id); }
    private static String location(CompoundTag row) { BlockPos pos = BlockPos.of(row.getLong("Position")); return row.getString("Dimension") + "  " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ(); }
    private static String progress(CompoundTag job) { return job.getLong("Completed") + " / " + (job.getInt("Requested") == 0 ? "∞" : job.getInt("Requested")); }
    private static String jobSummary(CompoundTag job) { return stateName(job.getString("State")) + " — " + progress(job) + (job.getString("Error").isEmpty() ? "" : " — " + errorText(job.getString("Error"))); }
    private static String stateName(String state) { return Component.translatableWithFallback("gui.automatone_worker.state_" + state.toLowerCase(Locale.ROOT), state).getString(); }

    private Button actionButton(String text, int x, int y, int size, Runnable action) {
        Button button = button(text, x, y, size, action);
        button.active = waiting == 0 && !data.getBoolean("Pending"); return button;
    }
    private Button button(String text, int x, int y, int size, Runnable action) { return button(text, x, y, size, 20, action); }
    private Button button(String text, int x, int y, int size, int height, Runnable action) {
        Button button = addRenderableWidget(Button.builder(Component.literal(text), ignored -> action.run()).bounds(x, y, size, height).build());
        if (font.width(text) > size - 6) { button.setTooltip(Tooltip.create(Component.literal(text))); }
        return button;
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
        if (page == Page.ROSTER && !menu.retired() && dialog == null) {
            graphics.fill(rosterScrollX, rosterScrollTop, rosterScrollX + 10, rosterScrollTop + rosterScrollHeight, 0xFF101411);
            int thumbY = rosterThumbY();
            graphics.fill(rosterScrollX + 2, thumbY, rosterScrollX + 8, thumbY + rosterScrollThumb,
                    rosterScrollMaximum > 0 ? GREEN : 0xFF424A40);
        }
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
        for (ItemCell cell : itemCells) {
            int x = cell.button().getX(); int y = cell.button().getY();
            if (cell.selected()) {
                graphics.fill(x + 1, y + 1, x + cell.button().getWidth() - 1, y + cell.button().getHeight() - 1, 0x804C7028);
                graphics.renderOutline(x, y, cell.button().getWidth(), cell.button().getHeight(), GREEN);
            }
            graphics.renderItem(cell.stack(), x + 8, y + 2);
            String count = cell.count() < 10_000 ? Long.toString(cell.count()) : (cell.count() / 1000) + "k";
            graphics.drawString(font, count, x + 30 - font.width(count), y + 20, TEXT, true);
        }
        if (dialog == null && settings != null && (page == Page.OVERRIDES || page == Page.PERSONAL)) { settings.render(graphics, mouseX, mouseY); }
        String status = menu.worker() == null ? translate("active_count", data.getInt("ActiveCount")) : jobSummary(selectedJob());
        if (page == Page.INBOX) { status = translate("unread_count", data.getInt("Unread")); }
        if (page == Page.COLLECTION) { status = translate("collection_sources", data.getCompound("Collection").getInt("SourceCount"), data.getCompound("Collection").getInt("Unavailable")); }
        if (data.getBoolean("Pending")) { status = translate("preparing") + " — " + status; }
        if (waiting > 0 || !message.isEmpty()) { status += " | " + (waiting > 0 ? translate("waiting") : message); }
        graphics.drawString(font, font.plainSubstrByWidth(status, fw - 24), fx + 12, fy + fh - 15, MUTED, false);
        if (mouseY >= fy + fh - 22 && mouseY < fy + fh && mouseX >= fx && mouseX <= fx + fw) { graphics.renderTooltip(font, Component.literal(status), mouseX, mouseY); }
        renderTooltip(graphics, mouseX, mouseY);
    }
}
