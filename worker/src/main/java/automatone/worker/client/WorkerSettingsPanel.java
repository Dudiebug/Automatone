package automatone.worker.client;

import automatone.worker.WorkerSettings;

import baritone.api.Settings;
import baritone.api.utils.SettingsUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static automatone.worker.client.WorkerScreen.translate;

/** Snapshot-backed editor shared by personal settings and worker overrides. */
public final class WorkerSettingsPanel {
    private static final int ROW_HEIGHT = 24;
    private static final int WIDGET_HEIGHT = 20;
    private static final int RESET_WIDTH = 45;
    private static final Map<String, String> DESCRIPTIONS = loadDescriptions();

    private final Font font;
    private final Consumer<AbstractWidget> add;
    private final List<Definition> definitions;
    private final List<Row> rows = new ArrayList<>();
    private final Map<String, String> baseline = new LinkedHashMap<>();
    private final Map<String, String> draft = new LinkedHashMap<>();
    private final Map<String, String> inherited = new LinkedHashMap<>();

    private EditBox search;
    private Button categoryButton;
    private Button previousButton;
    private Button nextButton;
    private int x;
    private int width;
    private int page;
    private Category category = Category.ALL;
    private String searchText = "";
    private String validationError = "";
    private String invalidKey = "";
    private boolean refreshing;

    public WorkerSettingsPanel(Font font, Map<String, String> initial, Map<String, String> inherited,
                               Consumer<AbstractWidget> add, Runnable rebuild) {
        this.font = Objects.requireNonNull(font, "font");
        this.add = Objects.requireNonNull(add, "add");
        Objects.requireNonNull(rebuild, "rebuild");
        replace(this.baseline, initial);
        replace(this.draft, initial);
        replace(this.inherited, inherited);

        Settings settings = new Settings();
        this.definitions = settings.allSettings.stream()
                .map(Definition::new)
                .sorted(Comparator.comparing((Definition definition) -> definition.category)
                        .thenComparing(definition -> definition.key))
                .toList();
        validateDraft();
    }

    /** Adds this panel's vanilla widgets to the owning screen. */
    public void build(int x, int y, int width, int height) {
        this.x = x;
        this.width = Math.max(288, width);
        rows.clear();
        int panelHeight = Math.max(120, height);
        int categoryWidth = Math.min(112, this.width / 3);

        search = new EditBox(font, x, y, this.width - categoryWidth - 4, WIDGET_HEIGHT,
                Component.literal(translate("search_native_settings")));
        search.setMaxLength(128);
        search.setHint(Component.literal(translate("search_settings")));
        search.setValue(searchText);
        search.setResponder(value -> {
            searchText = value;
            page = 0;
            refreshRows();
        });
        add.accept(search);

        categoryButton = Button.builder(category.label(), ignored -> {
            category = category.next();
            page = 0;
            refreshRows();
        }).bounds(x + this.width - categoryWidth, y, categoryWidth, WIDGET_HEIGHT).build();
        categoryButton.setTooltip(Tooltip.create(Component.literal(translate("filter_settings_by_category"))));
        add.accept(categoryButton);

        int rowCount = Math.max(2, (panelHeight - 45) / ROW_HEIGHT);
        int valueX = x + Math.max(120, this.width * 45 / 100);
        int valueWidth = x + this.width - valueX - RESET_WIDTH - 4;
        for (int i = 0; i < rowCount; i++) {
            int rowY = y + 24 + i * ROW_HEIGHT;
            Row row = new Row(valueX, rowY, valueWidth);
            rows.add(row);
            add.accept(row.editor);
            add.accept(row.toggle);
            add.accept(row.reset);
        }

        int footerY = y + panelHeight - WIDGET_HEIGHT;
        previousButton = Button.builder(Component.literal("<"), ignored -> setPage(page - 1))
                .bounds(x, footerY, 24, WIDGET_HEIGHT).build();
        nextButton = Button.builder(Component.literal(">"), ignored -> setPage(page + 1))
                .bounds(x + 27, footerY, 24, WIDGET_HEIGHT).build();
        Button resetAll = Button.builder(Component.literal(translate("reset_all")), ignored -> {
            draft.clear();
            validateDraft();
            refreshRows();
        }).bounds(x + this.width - 72, footerY, 72, WIDGET_HEIGHT).build();
        resetAll.setTooltip(Tooltip.create(Component.literal(translate("remove_all_overrides_and_resume_inherited_values"))));
        add.accept(previousButton);
        add.accept(nextButton);
        add.accept(resetAll);
        refreshRows();
    }

    public boolean dirty() {
        return !draft.equals(baseline);
    }

    /** Returns only explicit overrides; missing keys inherit from the next settings layer. */
    public Map<String, String> values() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(draft));
    }

    /** Replaces the saved baseline after the server has accepted the submitted snapshot. */
    public void accepted(Map<String, String> values, Map<String, String> inherited) {
        replace(baseline, values);
        replace(draft, values);
        replace(this.inherited, inherited);
        validateDraft();
        refreshRows();
    }

    public String error() {
        return validationError;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        for (Row row : rows) {
            row.render(graphics);
        }
        List<Definition> filtered = filtered();
        int pages = pages(filtered.size());
        String pageText = filtered.isEmpty() ? translate("no_matching_settings") : translate("page_count", page + 1, pages);
        graphics.drawString(font, pageText, x + 57, previousButton.getY() + 6, 0xFFA8B0B8, false);
    }

    public boolean mouseScrolled(double amount) {
        if (amount == 0 || pages(filtered().size()) <= 1) {
            return false;
        }
        setPage(page + (amount > 0 ? -1 : 1));
        return true;
    }

    private void setPage(int requested) {
        int last = pages(filtered().size()) - 1;
        int next = Math.max(0, Math.min(last, requested));
        if (next != page) {
            page = next;
            refreshRows();
        }
    }

    private void refreshRows() {
        if (search == null || refreshing) {
            return;
        }
        refreshing = true;
        List<Definition> filtered = filtered();
        int pages = pages(filtered.size());
        page = Math.min(page, pages - 1);
        categoryButton.setMessage(category.label());
        previousButton.active = page > 0;
        nextButton.active = page + 1 < pages;
        int start = page * rows.size();
        for (int i = 0; i < rows.size(); i++) {
            int index = start + i;
            rows.get(i).show(index < filtered.size() ? filtered.get(index) : null);
        }
        refreshing = false;
    }

    private List<Definition> filtered() {
        String query = searchText.strip().toLowerCase(Locale.ROOT);
        return definitions.stream()
                .filter(definition -> category == Category.ALL || definition.category == category)
                .filter(definition -> query.isEmpty() || definition.searchText.contains(query))
                .toList();
    }

    private int pages(int resultCount) {
        return Math.max(1, (resultCount + Math.max(1, rows.size()) - 1) / Math.max(1, rows.size()));
    }

    private String effective(Definition definition) {
        if (draft.containsKey(definition.key)) {
            return draft.get(definition.key);
        }
        return inherited.getOrDefault(definition.key, definition.defaultValue);
    }

    private String source(Definition definition) {
        if (!definition.unavailable.isEmpty()) {
            return translate("unavailable_reason", definition.unavailable);
        }
        if (definition.key.equals(invalidKey)) {
            return translate("invalid_value");
        }
        if (draft.containsKey(definition.key)) {
            return translate("override");
        }
        if (inherited.containsKey(definition.key)) {
            return translate("inherited");
        }
        return translate("native_default");
    }

    private void changed(Definition definition, String value) {
        if (refreshing) {
            return;
        }
        draft.put(definition.key, value);
        validateDraft();
    }

    private void validateDraft() {
        try {
            WorkerSettings.validate(draft);
            validationError = "";
            invalidKey = "";
        } catch (IllegalArgumentException | IllegalStateException exception) {
            invalidKey = "";
            for (Map.Entry<String, String> entry : draft.entrySet()) {
                try {
                    WorkerSettings.validate(Map.of(entry.getKey(), entry.getValue()));
                } catch (IllegalArgumentException | IllegalStateException invalid) {
                    invalidKey = entry.getKey();
                    validationError = invalidKey + ": " + invalid.getMessage();
                    return;
                }
            }
            validationError = "Settings: " + exception.getMessage();
        }
    }

    private static void replace(Map<String, String> target, Map<String, String> source) {
        target.clear();
        Objects.requireNonNull(source, "settings").forEach((key, value) ->
                target.put(key.toLowerCase(Locale.ROOT), Objects.requireNonNull(value, key)));
    }

    private static Map<String, String> loadDescriptions() {
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(
                WorkerSettingsPanel.class.getResourceAsStream("/assets/automatone_worker/settings-descriptions.json")),
                StandardCharsets.UTF_8)) {
            Map<String, String> result = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : JsonParser.parseReader(reader).getAsJsonObject().entrySet()) {
                result.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue().getAsString());
            }
            return Map.copyOf(result);
        } catch (RuntimeException | java.io.IOException ignored) {
            return Map.of();
        }
    }

    private final class Row {
        private final EditBox editor;
        private final Button toggle;
        private final Button reset;
        private final int rowY;
        private Definition definition;

        private Row(int valueX, int rowY, int valueWidth) {
            this.rowY = rowY;
            editor = new EditBox(font, valueX, rowY, valueWidth, WIDGET_HEIGHT, Component.empty());
            editor.setMaxLength(8192);
            reset = Button.builder(Component.literal(translate("reset")), ignored -> {
                if (definition != null) {
                    draft.remove(definition.key);
                    validateDraft();
                    refreshRows();
                }
            }).bounds(valueX + valueWidth + 4, rowY, RESET_WIDTH, WIDGET_HEIGHT).build();
            editor.setResponder(value -> {
                if (refreshing || definition == null) {
                    return;
                }
                changed(definition, value);
                reset.active = true;
                editor.setTooltip(details(definition));
            });
            toggle = Button.builder(Component.empty(), ignored -> {
                if (definition != null) {
                    changed(definition, Boolean.toString(!Boolean.parseBoolean(effective(definition))));
                    show(definition);
                }
            }).bounds(valueX, rowY, valueWidth, WIDGET_HEIGHT)
                    .createNarration(message -> Component.literal(definition == null ? "Setting: "
                            : definition.name + ": ").append(message.get()))
                    .build();
        }

        private void show(Definition next) {
            definition = next;
            boolean shown = next != null;
            boolean available = shown && next.unavailable.isEmpty();
            boolean bool = available && next.valueClass == Boolean.class;
            editor.visible = available && !bool;
            toggle.visible = shown && (bool || !available);
            toggle.active = available;
            reset.visible = available;
            if (!shown) {
                return;
            }

            String value = effective(next);
            editor.setMessage(Component.literal(next.name));
            Tooltip tooltip = details(next);
            editor.setTooltip(tooltip);
            toggle.setTooltip(tooltip);
            reset.setTooltip(Tooltip.create(Component.literal(translate("remove_this_override_and_resume_inheritance"))));
            reset.active = draft.containsKey(next.key);
            if (bool) {
                toggle.setMessage(Component.literal(Boolean.parseBoolean(value) ? translate("on") : translate("off")));
            } else if (!available) {
                toggle.setMessage(Component.literal(translate("unavailable")));
            } else {
                editor.setValue(value);
            }
        }

        private Tooltip details(Definition next) {
            return Tooltip.create(Component.literal(next.name + "\n" + next.description
                    + "\nType: " + next.type + "\n" + source(next)));
        }

        private void render(GuiGraphics graphics) {
            if (definition == null) {
                return;
            }
            int labelWidth = editor.getX() - x - 5;
            String label = font.plainSubstrByWidth(humanize(definition.name), labelWidth);
            int color = definition.key.equals(invalidKey) ? 0xFFFF7777
                    : definition.unavailable.isEmpty() ? 0xFFE4E7EA : 0xFF8A929A;
            graphics.drawString(font, label, x, rowY + 1, color, false);
            graphics.drawString(font, font.plainSubstrByWidth(source(definition), labelWidth), x, rowY + 12,
                    sourceColor(definition), false);
        }
    }

    private static final class Definition {
        private final String key;
        private final String name;
        private final Class<?> valueClass;
        private final String type;
        private final String defaultValue;
        private final String description;
        private final String unavailable;
        private final Category category;
        private final String searchText;

        private Definition(Settings.Setting<?> setting) {
            this.name = setting.getName();
            this.key = name.toLowerCase(Locale.ROOT);
            this.valueClass = setting.getValueClass();
            this.type = SettingsUtil.settingTypeToString(setting);
            this.defaultValue = defaultValue(setting);
            this.description = Component.translatableWithFallback("setting.automatone_worker." + key + ".description",
                    DESCRIPTIONS.getOrDefault(key, "Native Automatone setting " + name + ".")).getString();
            this.unavailable = WorkerSettings.unavailableReason(setting);
            this.category = Category.of(key);
            this.searchText = (name + " " + key + " " + type + " " + description + " " + unavailable
                    + " " + category.label).toLowerCase(Locale.ROOT);
        }
    }

    private enum Category {
        ALL("All"), MOVEMENT("Movement"), MINING("Mining"), PATHING("Pathing"), AVOIDANCE("Avoidance"),
        CACHE("Cache"), OTHER("Other");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        private Component label() {
            return Component.literal(translate("category", translate("category_" + label.toLowerCase(Locale.ROOT))));
        }

        private Category next() {
            return switch (this) {
                case ALL -> MOVEMENT;
                case MOVEMENT -> MINING;
                case MINING -> PATHING;
                case PATHING -> AVOIDANCE;
                case AVOIDANCE -> CACHE;
                case CACHE -> OTHER;
                case OTHER -> ALL;
            };
        }

        private static Category of(String key) {
            if (containsAny(key, "mine", "ore", "break", "tool", "item", "silk")) {
                return MINING;
            }
            if (containsAny(key, "avoid", "mob", "potion", "liquid")) {
                return AVOIDANCE;
            }
            if (containsAny(key, "cache", "chunk", "region", "repack")) {
                return CACHE;
            }
            if (containsAny(key, "path", "cost", "planning", "goal", "timeout", "lookahead", "splice")) {
                return PATHING;
            }
            if (containsAny(key, "allow", "sprint", "walk", "fall", "movement", "jump", "overshoot", "vines")) {
                return MOVEMENT;
            }
            return OTHER;
        }

        private static boolean containsAny(String value, String... words) {
            for (String word : words) {
                if (value.contains(word)) {
                    return true;
                }
            }
            return false;
        }
    }

    private int sourceColor(Definition definition) {
        if (!definition.unavailable.isEmpty() || definition.key.equals(invalidKey)) {
            return 0xFFFF7777;
        }
        if (draft.containsKey(definition.key)) {
            return 0xFFD69C56;
        }
        return inherited.containsKey(definition.key) ? 0xFF7FCB8A : 0xFFA8B0B8;
    }

    private static String defaultValue(Settings.Setting<?> setting) {
        try {
            return SettingsUtil.settingDefaultToString(setting);
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private static String humanize(String name) {
        String spaced = name.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
