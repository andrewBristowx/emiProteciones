package com.emiprotecciones;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Lightweight client GUI for the common protection actions.
 *
 * Actions are sent to the server through the internal /emiprotecciones manage
 * command, where ownership is validated again before touching Flan.
 */
public final class ProtectionScreen extends Screen {
    private static final int PANEL_WIDTH = 460;
    private static final int PANEL_HEIGHT = 326;
    private static final int ROW_HEIGHT = 23;

    private final BlockPos corePos;
    private final EmiProtecciones.ProtectionTier tier;

    private TextFieldWidget playerField;
    private int panelX;
    private int panelY;
    private String feedback = "Selecciona qué pueden hacer tus invitados dentro de la protección.";
    private int feedbackColor = EmiUiTheme.TEXT_MUTED;

    public ProtectionScreen(BlockPos corePos, EmiProtecciones.ProtectionTier tier) {
        super(Text.literal("Protección " + tier.displayName()));
        this.corePos = corePos.toImmutable();
        this.tier = tier;
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;

        int fieldX = panelX + 22;
        int fieldY = panelY + 68;
        int fieldWidth = 230;

        playerField = new TextFieldWidget(
                textRenderer,
                fieldX,
                fieldY,
                fieldWidth,
                22,
                Text.literal("Nombre del jugador")
        );
        playerField.setMaxLength(16);
        playerField.setDrawsBackground(false);
        addDrawableChild(playerField);

        addDrawableChild(new EmiButton(
                panelX + 264,
                fieldY,
                80,
                22,
                Text.literal("Añadir"),
                EmiButton.Kind.NORMAL,
                button -> managePlayer(true)
        ));

        addDrawableChild(new EmiButton(
                panelX + 352,
                fieldY,
                84,
                22,
                Text.literal("Quitar"),
                EmiButton.Kind.DANGER,
                button -> managePlayer(false)
        ));

        int rowY = panelY + 116;
        addPermissionRow(rowY, "Construcción", false, "break", "place");
        rowY += ROW_HEIGHT;
        addPermissionRow(rowY, "Contenedores", false, "open_container");
        rowY += ROW_HEIGHT;
        addPermissionRow(rowY, "Puertas", false, "door");
        rowY += ROW_HEIGHT;
        addPermissionRow(rowY, "Botones / palancas", false, "button_lever");
        rowY += ROW_HEIGHT;
        addPermissionRow(rowY, "Interactuar con animales", false, "animal_interact");
        rowY += ROW_HEIGHT;
        addPermissionRow(rowY, "PvP en la parcela", true, "hurt_player");
        rowY += ROW_HEIGHT;
        addPermissionRow(rowY, "Explosiones", true, "explosions");

        int bottomY = panelY + PANEL_HEIGHT - 32;
        addDrawableChild(new EmiButton(
                panelX + 22,
                bottomY,
                122,
                22,
                Text.literal("Mostrar límites"),
                EmiButton.Kind.NORMAL,
                button -> {
                    sendManage("preview");
                    setFeedback("✦ Mostrando el perímetro durante unos segundos.", EmiUiTheme.ACCENT_SOFT);
                }
        ));

        addDrawableChild(new EmiButton(
                panelX + 152,
                bottomY,
                142,
                22,
                Text.literal("Flan avanzado"),
                EmiButton.Kind.NORMAL,
                button -> {
                    sendCommand("flan menu");
                    close();
                }
        ));

        addDrawableChild(new EmiButton(
                panelX + 344,
                bottomY,
                92,
                22,
                Text.literal("Cerrar"),
                EmiButton.Kind.NORMAL,
                button -> close()
        ));
    }

    private void addPermissionRow(int y, String label, boolean global, String... permissionKeys) {
        addDrawableChild(new EmiButton(
                panelX + 278,
                y,
                74,
                20,
                Text.literal("Permitir"),
                EmiButton.Kind.ALLOW,
                button -> setPermission(label, global, true, permissionKeys)
        ));

        addDrawableChild(new EmiButton(
                panelX + 360,
                y,
                76,
                20,
                Text.literal("Bloquear"),
                EmiButton.Kind.DANGER,
                button -> setPermission(label, global, false, permissionKeys)
        ));
    }

    private void managePlayer(boolean add) {
        String name = playerField.getText().trim();
        if (!name.matches("[A-Za-z0-9_]{1,16}")) {
            setFeedback("Escribe un nombre de jugador válido.", 0xFFFF8EAE);
            return;
        }

        sendManage((add ? "add " : "remove ") + name);
        setFeedback(
                add ? "✦ Solicitado: permitir a " + name + "." : "✦ Solicitado: quitar a " + name + ".",
                EmiUiTheme.ACCENT_SOFT
        );
    }

    private void setPermission(String label, boolean global, boolean allow, String... keys) {
        String action = global ? "globalperm " : "guestperm ";
        for (String key : keys) {
            sendManage(action + key + " " + allow);
        }

        setFeedback(
                "✦ " + label + ": " + (allow ? "PERMITIDO" : "BLOQUEADO"),
                allow ? 0xFFA9E6C2 : 0xFFFF9DBD
        );
    }

    private void sendManage(String tail) {
        sendCommand("emiprotecciones manage " + positionToken() + " " + tail);
    }

    private String positionToken() {
        return corePos.getX() + "," + corePos.getY() + "," + corePos.getZ();
    }

    private void sendCommand(String command) {
        if (client != null && client.player != null && client.player.networkHandler != null) {
            client.player.networkHandler.sendChatCommand(command);
        }
    }

    private void setFeedback(String feedback, int color) {
        this.feedback = feedback;
        this.feedbackColor = color;
    }

    /**
     * Minecraft 1.21.x applies its vanilla blur/dim layer from Screen before
     * rendering widgets. Our panel is already drawing its own dark overlay, so
     * the vanilla pass only blurs the custom labels/panel that were drawn first.
     * Disabling it keeps the world dimmed by EmiUiTheme.OVERLAY while all GUI
     * text remains perfectly sharp.
     */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Intentionally empty: ProtectionScreen renders its own themed backdrop.
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, EmiUiTheme.OVERLAY);

        // Main dark panel and two-tone border copied from the Emi emote UI identity.
        context.fill(panelX - 2, panelY - 2, panelX + PANEL_WIDTH + 2, panelY + PANEL_HEIGHT + 2, EmiUiTheme.BORDER_SECONDARY);
        context.fill(panelX - 1, panelY - 1, panelX + PANEL_WIDTH + 1, panelY + PANEL_HEIGHT + 1, EmiUiTheme.BORDER);
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, EmiUiTheme.PANEL_DARK);
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 5, EmiUiTheme.ACCENT);

        context.drawText(
                textRenderer,
                Text.literal("✦ Protección " + tier.displayName()),
                panelX + 20,
                panelY + 17,
                EmiUiTheme.ACCENT_SOFT,
                false
        );
        context.drawText(
                textRenderer,
                Text.literal("Área: " + tier.size() + "×" + tier.size() + "  •  Gestión de invitados"),
                panelX + 20,
                panelY + 34,
                EmiUiTheme.TEXT_MUTED,
                false
        );

        context.drawText(textRenderer, Text.literal("Jugador"), panelX + 22, panelY + 55, EmiUiTheme.TEXT, false);

        // Custom field background matching the same purple UI palette.
        int fieldX = panelX + 22;
        int fieldY = panelY + 68;
        context.fill(fieldX - 1, fieldY - 1, fieldX + 231, fieldY + 23, EmiUiTheme.BORDER);
        context.fill(fieldX, fieldY, fieldX + 230, fieldY + 22, EmiUiTheme.PANEL_SOFT);

        context.fill(panelX + 18, panelY + 101, panelX + PANEL_WIDTH - 18, panelY + 102, EmiUiTheme.BORDER_SECONDARY);
        context.drawText(textRenderer, Text.literal("Permisos para invitados"), panelX + 22, panelY + 104, EmiUiTheme.ACCENT, false);

        int labelY = panelY + 122;
        String[] labels = {
                "Construcción",
                "Contenedores",
                "Puertas",
                "Botones / palancas",
                "Interactuar con animales",
                "PvP en la parcela",
                "Explosiones"
        };
        for (String label : labels) {
            context.drawText(textRenderer, Text.literal(label), panelX + 28, labelY, EmiUiTheme.TEXT, false);
            labelY += ROW_HEIGHT;
        }

        super.render(context, mouseX, mouseY, delta);

        int feedbackWidth = textRenderer.getWidth(feedback);
        int maxWidth = PANEL_WIDTH - 44;
        String shown = feedback;
        if (feedbackWidth > maxWidth) {
            shown = textRenderer.trimToWidth(feedback, maxWidth - textRenderer.getWidth("…")) + "…";
        }
        context.drawText(textRenderer, Text.literal(shown), panelX + 22, panelY + PANEL_HEIGHT - 49, feedbackColor, false);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    public static final class EmiButton extends ClickableWidget {
        public enum Kind {
            NORMAL,
            ALLOW,
            DANGER
        }

        @FunctionalInterface
        public interface PressAction {
            void onPress(EmiButton button);
        }

        private final Kind kind;
        private final PressAction pressAction;

        public EmiButton(int x, int y, int width, int height, Text message, Kind kind, PressAction pressAction) {
            super(x, y, width, height, message);
            this.kind = kind;
            this.pressAction = pressAction;
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            boolean hover = active
                    && mouseX >= getX()
                    && mouseX < getX() + getWidth()
                    && mouseY >= getY()
                    && mouseY < getY() + getHeight();

            int background;
            if (!active) {
                background = EmiUiTheme.BUTTON_DISABLED;
            } else if (kind == Kind.ALLOW) {
                background = hover ? EmiUiTheme.ALLOW_HOVER : EmiUiTheme.ALLOW;
            } else if (kind == Kind.DANGER) {
                background = hover ? EmiUiTheme.DENY_HOVER : EmiUiTheme.DENY;
            } else {
                background = hover ? EmiUiTheme.BUTTON_HOVER : EmiUiTheme.BUTTON;
            }

            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();

            context.fill(x, y, x + w, y + h, EmiUiTheme.BORDER);
            context.fill(x + 1, y + 1, x + w - 1, y + h - 1, background);

            if (hover) {
                context.fill(x + 2, y + 2, x + w - 2, y + 3, EmiUiTheme.ACCENT_SOFT);
            }

            int textColor = active ? EmiUiTheme.TEXT_BRIGHT : EmiUiTheme.TEXT_MUTED;
            context.drawCenteredTextWithShadow(
                    MinecraftClient.getInstance().textRenderer,
                    getMessage(),
                    x + w / 2,
                    y + (h - 8) / 2,
                    textColor
            );
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            if (active) {
                pressAction.onPress(this);
            }
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            builder.put(NarrationPart.TITLE, getMessage());
        }
    }
}
