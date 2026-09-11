package dev.xyat.taczworkshop.client.gui;

import dev.xyat.kineticcore.api.client.overlay.GuiOverlay;
import dev.xyat.kineticcore.api.client.theme.GuiTheme;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.LayerState;
import dev.xyat.kineticcore.api.client.widget.KineticWidgets.HighZButton;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

final class TaczContextMenu {
    static final class Entry {
        private final Component label;
        private final Component tooltip;
        private final Boolean checked;
        private final Runnable action;
        private final boolean separator;

        private Entry(Component label, Component tooltip, Boolean checked, Runnable action, boolean separator) {
            this.label = label;
            this.tooltip = tooltip;
            this.checked = checked;
            this.action = action;
            this.separator = separator;
        }

        static Entry action(Component label, Component tooltip, Runnable action) {
            return new Entry(label, tooltip, null, action, false);
        }

        static Entry toggle(Component label, Component tooltip, boolean checked, Runnable action) {
            return new Entry(label, tooltip, checked, action, false);
        }

        static Entry danger(Component label, Component tooltip, Runnable action) {
            return new Entry(label, tooltip, null, action, false);
        }

        static Entry separator() {
            return new Entry(Component.empty(), Component.empty(), null, null, true);
        }
    }

    private enum Layer {
        MENU
    }

    private record MenuButton(HighZButton button, Component tooltip) {
    }

    private static final int ITEM_HEIGHT = 20;
    private static final int SEPARATOR_HEIGHT = 5;
    private static final int MIN_WIDTH = 126;
    private static final int PADDING_X = 10;
    private static final int Z_LEVEL = 500;

    private final List<MenuButton> buttons = new ArrayList<>();
    private final LayerState<Layer> layers = new LayerState<>();
    private int x;
    private int y;
    private int width;
    private int height;

    boolean isOpen() {
        return layers.isOpen(Layer.MENU);
    }

    void close() {
        buttons.clear();
        layers.closeAll();
        x = 0;
        y = 0;
        width = 0;
        height = 0;
    }

    void open(
            Font font,
            int requestedX,
            int requestedY,
            int screenWidth,
            int screenHeight,
            List<Entry> nextEntries
    ) {
        close();
        if (font == null || nextEntries == null || nextEntries.isEmpty()) return;

        int measuredWidth = MIN_WIDTH;
        int measuredHeight = 0;
        for (Entry entry : nextEntries) {
            if (entry.separator) {
                measuredHeight += SEPARATOR_HEIGHT;
                continue;
            }
            Component label = displayLabel(entry);
            measuredWidth = Math.max(measuredWidth, font.width(label) + PADDING_X * 2);
            measuredHeight += ITEM_HEIGHT;
        }

        width = Math.min(Math.max(MIN_WIDTH, measuredWidth), Math.max(MIN_WIDTH, screenWidth - 8));
        height = Math.min(Math.max(ITEM_HEIGHT, measuredHeight), Math.max(ITEM_HEIGHT, screenHeight - 8));
        x = Math.max(4, Math.min(requestedX, screenWidth - width - 4));
        y = Math.max(4, Math.min(requestedY, screenHeight - height - 4));
        int cursorY = y;
        for (Entry entry : nextEntries) {
            if (entry.separator) {
                cursorY += SEPARATOR_HEIGHT;
                continue;
            }

            Runnable action = entry.action;
            Component tooltip = entry.tooltip == null ? Component.empty() : entry.tooltip;
            HighZButton button = new HighZButton(
                    x,
                    cursorY,
                    width,
                    ITEM_HEIGHT,
                    displayLabel(entry),
                    ignored -> {
                        close();
                        if (action != null) action.run();
                    },
                    null,
                    Z_LEVEL
            );
            buttons.add(new MenuButton(button, tooltip));
            cursorY += ITEM_HEIGHT;
        }

        if (!buttons.isEmpty()) layers.open(Layer.MENU);
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isOpen() || graphics == null) return;
        for (MenuButton menuButton : buttons) {
            menuButton.button().render(graphics, mouseX, mouseY, partialTick);
        }
    }

    void requestTooltip(GuiGraphics graphics, Font font, int virtualMouseX, int virtualMouseY, int screenMouseX, int screenMouseY) {
        if (!isOpen() || graphics == null || font == null) return;
        Component tooltip = tooltipAt(virtualMouseX, virtualMouseY);
        if (tooltip == null || tooltip.getString().isBlank()) return;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, Z_LEVEL + 600);
        try {
            GuiOverlay.requestFormattedTooltip(font.split(tooltip, 320), screenMouseX, screenMouseY);
        } finally {
            graphics.pose().popPose();
        }
    }

    boolean contains(double mouseX, double mouseY) {
        return isOpen() && GuiTheme.hovering(mouseX, mouseY, x, y, width, height);
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isOpen() || button != 0) return false;
        for (MenuButton menuButton : new ArrayList<>(buttons)) {
            if (menuButton.button().mouseClicked(mouseX, mouseY, button)) return true;
        }
        return false;
    }

    private Component tooltipAt(double mouseX, double mouseY) {
        for (MenuButton menuButton : buttons) {
            HighZButton button = menuButton.button();
            if (GuiTheme.hovering(mouseX, mouseY, button.getX(), button.getY(), button.getWidth(), button.getHeight())) {
                return menuButton.tooltip();
            }
        }
        return null;
    }

    private static Component displayLabel(Entry entry) {
        if (Boolean.TRUE.equals(entry.checked)) {
            return Component.translatable("gui.taczworkshop.context.checked")
                    .append(" ")
                    .append(entry.label);
        }
        return entry.label;
    }
}
