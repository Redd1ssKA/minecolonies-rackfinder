package com.arteen.rackfinder.client.gui;

import com.arteen.rackfinder.client.HighlightState;
import com.arteen.rackfinder.client.RackFinderKeybinds;
import com.arteen.rackfinder.client.TargetItem;
import com.arteen.rackfinder.config.RackFinderConfig;
import com.arteen.rackfinder.data.ItemAggregate;
import com.arteen.rackfinder.data.ItemKey;
import com.arteen.rackfinder.data.RackHit;
import com.arteen.rackfinder.data.RackRecord;
import com.arteen.rackfinder.data.RackStore;
import com.arteen.rackfinder.scan.RackScanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Search window: type a name, pick an item, see every rack that contains it and highlight them.
 * Clicking a rack on the right side highlights that single rack.
 */
public final class RackSearchScreen extends Screen {
    private static final int PANEL_WIDTH = 480;
    private static final int PANEL_HEIGHT = 270;
    private static final int ROW_HEIGHT = 20;
    private static final int RACK_ROW_HEIGHT = 21;
    private static final int LIST_WIDTH = 262;
    private static final int PADDING = 12;
    private static final int MAX_VISIBLE_RACKS = 7;
    private static final int MIN_DETAILS_WIDTH = 150;

    private final RackStore store = RackStore.INSTANCE;
    private final List<RackRow> rackRows = new ArrayList<>();

    private EditBox searchBox;
    private ItemList itemList;
    private List<ItemAggregate> results = List.of();
    private ItemKey selectedKey;
    private ItemAggregate selected;
    private RackRecord focusedRack;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int listWidth;
    private int detailsX;
    private int detailsScroll;
    private int detailsMaxScroll;
    private String query = "";
    private boolean queryInitialized;
    private boolean swallowUntilKeyRelease;

    public RackSearchScreen() {
        super(Component.translatable("rackfinder.gui.title"));
    }

    private record RackRow(int x, int y, int width, int height, RackRecord record) {
    }

    @Override
    protected void init() {
        if (!queryInitialized) {
            query = store.lastQuery();
            queryInitialized = true;
            // Ignore text input until the key that opened the window is released once, so a slow
            // release does not type "r" (or the bound key) into the search field.
            swallowUntilKeyRelease = true;
        }
        panelWidth = Math.min(PANEL_WIDTH, width - 16);
        panelHeight = Math.min(PANEL_HEIGHT, height - 16);
        left = Math.max(4, (width - panelWidth) / 2);
        top = Math.max(4, (height - panelHeight) / 2);
        listWidth = Math.max(120, Math.min(LIST_WIDTH, panelWidth - MIN_DETAILS_WIDTH));
        detailsX = PADDING + listWidth + 12;

        final int clearWidth = 20;
        searchBox = new EditBox(font, left + PADDING, top + 24,
                panelWidth - PADDING * 2 - clearWidth - 2, 18,
                Component.translatable("rackfinder.gui.search"));
        searchBox.setMaxLength(64);
        searchBox.setValue(query);
        searchBox.setResponder(text -> {
            query = text;
            refreshResults();
        });
        addRenderableWidget(searchBox);
        addRenderableWidget(Button.builder(Component.literal("×"), button -> clearQuery())
                .bounds(left + panelWidth - PADDING - clearWidth, top + 24, clearWidth, 18).build());

        itemList = new ItemList(minecraft, left + PADDING, listWidth, panelHeight - 106, top + 50, ROW_HEIGHT);
        itemList.setLeftPos(left + PADDING);
        itemList.setRenderBackground(false);
        itemList.setRenderTopAndBottom(false);
        addRenderableWidget(itemList);

        addRenderableWidget(Button.builder(Component.translatable("rackfinder.gui.rescan"), button -> rescan())
                .bounds(left + PADDING, top + panelHeight - 30, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("rackfinder.gui.clear"), button -> {
            HighlightState.clear();
            TargetItem.clear();
            focusedRack = null;
            onClose();
        }).bounds(left + PADDING + 104, top + panelHeight - 30, 116, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("rackfinder.gui.close"), button -> onClose())
                .bounds(left + panelWidth - PADDING - 96, top + panelHeight - 30, 96, 20).build());

        refreshResults();
        setInitialFocus(searchBox);
    }

    private void clearQuery() {
        query = "";
        if (searchBox != null) {
            searchBox.setValue("");
            setInitialFocus(searchBox);
        }
        refreshResults();
    }

    private void refreshResults() {
        if (itemList == null) {
            return;
        }
        results = store.search(query);
        detailsScroll = 0;
        itemList.setItems(results);
        if (selectedKey != null) {
            ItemAggregate match = null;
            for (final ItemAggregate aggregate : results) {
                if (aggregate.key().equals(selectedKey)) {
                    match = aggregate;
                    break;
                }
            }
            selected = match;
            if (match != null) {
                itemList.selectKey(match.key());
            }
        }

    }

    @Override
    public void renderBackground(final GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, 0x80000000);
        graphics.fill(left - 1, top - 1, left + panelWidth + 1, top + panelHeight + 1, 0xFF3C3C3C);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xFF121212);
        final int separatorX = left + detailsX - 8;
        graphics.fill(separatorX, top + 46, separatorX + 1, top + panelHeight - 40, 0xFF2E2E2E);
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawString(font, title, left + PADDING, top + 7, 0xFFFFFFFF, false);
        if (searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
            graphics.drawString(font, Component.translatable("rackfinder.gui.search").getString(),
                    left + PADDING + 5, top + 29, 0xFF6E6E6E, false);
        }
        graphics.drawString(font, Component.translatable("rackfinder.gui.status",
                        store.rackCount(), store.documentedRackCount(), store.itemCount()).getString(),
                left + PADDING, top + panelHeight - 46, 0xFF9E9E9E, false);

        drawDetails(graphics, mouseX, mouseY);
    }

    private void drawDetails(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        final int x = left + detailsX;
        final int detailsWidth = panelWidth - detailsX - PADDING;
        int y = top + 50;
        rackRows.clear();

        if (selected == null) {
            graphics.drawString(font, Component.translatable("rackfinder.gui.noSelection").getString(),
                    x, y, 0xFFAAAAAA, false);
            y += 12;
            for (final String line : wrap(Component.translatable("rackfinder.gui.howItWorks").getString(), detailsWidth)) {
                graphics.drawString(font, line, x, y, 0xFF777777, false);
                y += 10;
            }
            return;
        }

        graphics.drawString(font, trim(selected.key().displayName(), detailsWidth), x, y, 0xFFFFFFFF, false);
        y += 12;
        graphics.drawString(font, Component.translatable("rackfinder.gui.total",
                        selected.total(), selected.rackCount()).getString(),
                x, y, 0xFFFFD54F, false);
        y += 16;

        final List<RackHit> hits = store.hits(selected, minecraft.player);
        final int areaBottom = top + panelHeight - 58;
        final int availableRows = Math.max(1, (areaBottom - y) / RACK_ROW_HEIGHT);
        final int maxRows = Math.min(availableRows, MAX_VISIBLE_RACKS);
        detailsMaxScroll = Math.max(0, hits.size() - maxRows);
        detailsScroll = net.minecraft.util.Mth.clamp(detailsScroll, 0, detailsMaxScroll);
        final int from = detailsScroll;
        final int to = Math.min(hits.size(), from + maxRows);
        for (int index = from; index < to; index++) {
            final RackHit hit = hits.get(index);
            final RackRecord record = hit.record();
            final boolean rowFocused = focusedRack != null && focusedRack.key().equals(record.key());
            final boolean hovered = mouseX >= x && mouseX <= x + detailsWidth
                    && mouseY >= y && mouseY <= y + RACK_ROW_HEIGHT;
            if (hovered || rowFocused) {
                graphics.fill(x - 2, y - 2, x + detailsWidth, y + RACK_ROW_HEIGHT - 1,
                        rowFocused ? 0x4433D6FF : 0x22FFFFFF);
            }

            final StringBuilder first = new StringBuilder();
            first.append(distanceText(record));
            first.append("  ·  x").append(hit.count());
            if (record.warehouse()) {
                first.append("  ·  ").append(Component.translatable("rackfinder.gui.warehouse").getString());
            }
            if (record.doubleRack()) {
                first.append("  ·  ").append(Component.translatable("rackfinder.gui.double").getString());
            }
            graphics.drawString(font, trim(first.toString(), detailsWidth), x, y,
                    index == 0 ? 0xFFFFC400 : 0xFFE0E0E0, false);
            y += 10;
            final String coords = record.pos().getX() + " / " + record.pos().getY() + " / " + record.pos().getZ()
                    + "  ·  " + RackStore.blockName(record.blockId());
            graphics.drawString(font, trim(coords, detailsWidth - 6), x + 6, y, 0xFF8A8A8A, false);
            y += 11;

            rackRows.add(new RackRow(x - 2, y - 21, detailsWidth + 2, RACK_ROW_HEIGHT, record));

        }
        if (hits.size() > maxRows) {
            graphics.drawString(font, Component.translatable("rackfinder.gui.range",
                            from + 1, to, hits.size()).getString(),
                    x, areaBottom + 2, 0xFF777777, false);
        } else if (!hits.isEmpty()) {
            graphics.drawString(font, Component.translatable("rackfinder.gui.clickHint").getString(),
                    x, areaBottom + 2, 0xFF666666, false);
        }
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        for (final RackRow row : rackRows) {
            if (mouseX >= row.x() && mouseX <= row.x() + row.width()
                    && mouseY >= row.y() && mouseY <= row.y() + row.height()) {
                if (focusedRack != null && focusedRack.key().equals(row.record().key())) {
                    focusedRack = null;
                    if (selected != null) {
                        select(selected);
                    }
                } else {
                    focusedRack = row.record();
                    HighlightState.highlight(List.of(row.record()), selected == null ? "" : selected.key().displayName());
                    HighlightState.setIsolated(true);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double delta) {
        if (selected != null && detailsMaxScroll > 0 && isOverDetails(mouseX, mouseY)) {
            detailsScroll = net.minecraft.util.Mth.clamp(detailsScroll - (int) Math.signum(delta),
                    0, detailsMaxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private boolean isOverDetails(final double mouseX, final double mouseY) {
        return mouseX >= left + detailsX - 8
                && mouseX <= left + panelWidth - PADDING
                && mouseY >= top + 46
                && mouseY <= top + panelHeight - 36;
    }

    @Override
    public void tick() {
        if (swallowUntilKeyRelease && !isOpenKeyDown()) {
            swallowUntilKeyRelease = false;
        }
    }

    @Override
    public boolean charTyped(final char codePoint, final int modifiers) {
        if (swallowUntilKeyRelease) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private static boolean isOpenKeyDown() {
        final KeyMapping mapping = RackFinderKeybinds.OPEN_SEARCH;
        final InputConstants.Key key = mapping.getKey();
        if (key.getType() != InputConstants.Type.KEYSYM) {
            return false;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        return minecraft.getWindow() != null
                && InputConstants.isKeyDown(minecraft.getWindow().getWindow(), key.getValue());
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        // Arrow keys navigate the result list while the search field keeps the focus, so the player
        // can type, scroll to the wanted item and confirm without touching the mouse.
        final boolean editing = searchBox != null && searchBox.isFocused();
        if (editing && (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_UP)) {
            itemList.moveSelection(keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (selected == null && !results.isEmpty()) {
                itemList.selectFirst();
            }
            if (selected != null) {
                select(selected);
                onClose();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Test hook: selects the first search result. Also used by the dev UI smoke test.
     */
    public boolean selectFirstResult() {
        if (itemList == null || itemList.children().isEmpty()) {
            return false;
        }
        itemList.selectFirst();
        if (selected != null) {
            select(selected);
        }
        return selected != null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        RackStore.INSTANCE.setLastQuery(query);
        RackStore.INSTANCE.save();
        super.onClose();
    }

    /**
     * Shows an item in the details panel without touching the world highlight; the highlight is only
     * applied on confirmation (Enter or a click).
     */
    private void preview(final ItemAggregate aggregate) {
        if (selected != aggregate) {
            detailsScroll = 0;
        }
        selected = aggregate;
        selectedKey = aggregate == null ? null : aggregate.key();
    }

    private void select(final ItemAggregate aggregate) {
        detailsScroll = 0;
        selected = aggregate;
        selectedKey = aggregate.key();
        focusedRack = null;
        TargetItem.set(aggregate.key());
        if (RackFinderConfig.HIGHLIGHT_ON_SELECT.get()) {
            final List<RackRecord> records = new ArrayList<>();
            for (final RackHit hit : store.hits(aggregate, minecraft.player)) {
                records.add(hit.record());
            }
            HighlightState.highlight(records, aggregate.key().displayName());
        }
    }

    private void rescan() {
        RackScanner.requestRescan();
        HighlightState.clear();
        focusedRack = null;
        if (minecraft.player != null && RackFinderConfig.CHAT_FEEDBACK.get()) {
            minecraft.player.displayClientMessage(Component.translatable("rackfinder.chat.rescan"), false);
        }
    }

    private String distanceText(final RackRecord record) {
        if (minecraft.player == null || !record.sameDimensionAs(minecraft.player)) {
            return Component.translatable("rackfinder.gui.otherDimension").getString();
        }
        final double distance = record.distanceTo(minecraft.player);
        if (distance >= 1000.0) {
            return String.format(Locale.ROOT, "%.1f km", distance / 1000.0);
        }
        return String.format(Locale.ROOT, "%.0f m", distance);
    }

    private String trim(final String text, final int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String cut = text;
        while (!cut.isEmpty() && font.width(cut + "...") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }

    private List<String> wrap(final String text, final int maxWidth) {
        final List<String> lines = new ArrayList<>();
        final String[] words = text.split(" ");
        final StringBuilder current = new StringBuilder();
        for (final String word : words) {
            final String candidate = current.isEmpty() ? word : current + " " + word;
            if (font.width(candidate) > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            } else {
                current.setLength(0);
                current.append(candidate);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private final class ItemList extends ObjectSelectionList<ItemEntry> {
        private final int rowWidth;
        private final int itemHeight;
        private final int scrollbarX;

        ItemList(final Minecraft minecraft, final int absLeft, final int listWidth, final int listHeight,
                 final int y0, final int itemHeight) {
            super(minecraft, listWidth, listHeight, y0, y0 + listHeight, itemHeight);
            this.rowWidth = listWidth - 14;
            this.itemHeight = itemHeight;
            // AbstractSelectionList#getEntryAtPosition compares the absolute mouse X with
            // getScrollbarPosition(), while the vanilla implementation derives that value from the
            // list width only. That is correct for full-screen lists starting at x = 0, but not for
            // a list placed inside a panel - without this override every click outside the first
            // 255 pixels of the screen is ignored.
            this.scrollbarX = absLeft + listWidth - 2;
        }

        @Override
        public int getRowWidth() {
            return rowWidth;
        }

        @Override
        protected int getScrollbarPosition() {
            return scrollbarX;
        }

        @Override
        public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
            if (super.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return clickFallback(mouseX, mouseY, button);
        }

        /**
         * Independent hit test, so selecting a row does not depend on vanilla internals.
         */
        private boolean clickFallback(final double mouseX, final double mouseY, final int button) {
            if (button != 0 || !isMouseOver(mouseX, mouseY)) {
                return false;
            }
            if (mouseX < getRowLeft() || mouseX > getRowRight()) {
                return false;
            }
            final int row = (int) Math.floor((mouseY - getTop() + getScrollAmount() - 4) / itemHeight);
            final List<ItemEntry> entries = children();
            if (row < 0 || row >= entries.size()) {
                return false;
            }
            final ItemEntry entry = entries.get(row);
            setSelected(entry);
            select(entry.aggregate);
            return true;
        }

        void setItems(final List<ItemAggregate> items) {
            clearEntries();
            final int limit = Math.min(items.size(), 400);
            for (int index = 0; index < limit; index++) {
                addEntry(new ItemEntry(items.get(index)));
            }
        }

        void moveSelection(final int delta) {
            final List<ItemEntry> entries = children();
            if (entries.isEmpty()) {
                return;
            }
            final ItemEntry selectedEntry = getSelected();
            final int current = selectedEntry == null ? -1 : entries.indexOf(selectedEntry);
            int next = current < 0 ? (delta > 0 ? 0 : entries.size() - 1) : current + delta;
            next = net.minecraft.util.Mth.clamp(next, 0, entries.size() - 1);
            final ItemEntry entry = entries.get(next);
            if (entry != selectedEntry) {
                setSelected(entry);
                centerScrollOn(entry);
            }
            preview(entry.aggregate);
        }

        void selectFirst() {
            if (!children().isEmpty()) {
                final ItemEntry entry = children().get(0);
                setSelected(entry);
                centerScrollOn(entry);
                preview(entry.aggregate);
            }
        }

        void selectKey(final ItemKey key) {
            for (final ItemEntry entry : children()) {
                if (entry.aggregate.key().equals(key)) {
                    setSelected(entry);
                    return;
                }
            }
        }
    }

    private final class ItemEntry extends ObjectSelectionList.Entry<ItemEntry> {
        private final ItemAggregate aggregate;

        ItemEntry(final ItemAggregate aggregate) {
            this.aggregate = aggregate;
        }

        @Override
        public void render(final GuiGraphics graphics, final int index, final int rowTop, final int rowLeft,
                           final int rowWidth, final int rowHeight, final int mouseX, final int mouseY,
                           final boolean hovering, final float partialTick) {
            final boolean rowSelected = itemList.getSelected() == this;
            if (hovering || rowSelected) {
                graphics.fill(rowLeft - 2, rowTop - 1, rowLeft + rowWidth + 2, rowTop + rowHeight - 1,
                        rowSelected ? 0x5533D6FF : 0x22FFFFFF);
            }
            final ItemStack icon = aggregate.key().prototype();
            graphics.renderItem(icon, rowLeft, rowTop + 1);
            graphics.drawString(font, trim(aggregate.key().displayName(), rowWidth - 78),
                    rowLeft + 20, rowTop + 2, 0xFFFFFFFF, false);
            final String count = "x" + aggregate.total();
            graphics.drawString(font, count, rowLeft + rowWidth - font.width(count), rowTop + 2, 0xFFFFD54F, false);
            graphics.drawString(font, Component.translatable("rackfinder.gui.racks", aggregate.rackCount()).getString(),
                    rowLeft + 20, rowTop + 11, 0xFF8A8A8A, false);
        }

        @Override
        public Component getNarration() {
            return Component.literal(aggregate.key().displayName());
        }

        @Override
        public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
            itemList.setSelected(this);
            select(aggregate);
            return true;
        }
    }
}
