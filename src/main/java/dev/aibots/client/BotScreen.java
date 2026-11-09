package dev.aibots.client;

import dev.aibots.core.BotManager;
import dev.aibots.core.McBot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

import java.util.List;

public class BotScreen extends Screen {
    private static final int MAX_ROWS = 9;
    private static String lastNick = "";
    private static String lastCmd = "";

    private TextFieldWidget nickField;
    private TextFieldWidget cmdField;
    private String info = "";

    public BotScreen() {
        super(Text.literal("Boty AI"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

        nickField = new TextFieldWidget(this.textRenderer, cx - 150, 36, 300, 20, Text.literal("Nick"));
        nickField.setMaxLength(16);
        nickField.setText(lastNick);
        nickField.setPlaceholder(Text.literal("Nick bota (puste = losowy)"));

        cmdField = new TextFieldWidget(this.textRenderer, cx - 150, 60, 300, 20, Text.literal("Komendy"));
        cmdField.setMaxLength(300);
        cmdField.setText(lastCmd);
        cmdField.setPlaceholder(Text.literal("Po wejściu, np. /register haslo haslo;/login haslo"));

        this.addDrawableChild(nickField);
        this.addDrawableChild(cmdField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Dodaj bota"), b -> addBot())
                .dimensions(cx - 150, 84, 148, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Usuń wszystkie"), b -> {
            BotManager.stopAll();
            remember();
            this.clearAndInit();
        }).dimensions(cx + 2, 84, 148, 20).build());

        List<McBot> bots = BotManager.all();
        for (int i = 0; i < Math.min(bots.size(), MAX_ROWS); i++) {
            McBot bot = bots.get(i);
            int y = 118 + i * 22;
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal(bot.isWalking() ? "Spacer: ON" : "Spacer: OFF"), b -> {
                        bot.setWalking(!bot.isWalking());
                        remember();
                        this.clearAndInit();
                    }).dimensions(cx + 30, y, 76, 18).build());
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Usuń"), b -> {
                BotManager.remove(bot);
                remember();
                this.clearAndInit();
            }).dimensions(cx + 110, y, 40, 18).build());
        }
    }

    private void remember() {
        if (nickField != null) lastNick = nickField.getText();
        if (cmdField != null) lastCmd = cmdField.getText();
    }

    private void addBot() {
        ServerInfo server = MinecraftClient.getInstance().getCurrentServerEntry();
        if (server == null) {
            info = "Wejdź na serwer multiplayer, żeby wysłać boty.";
            return;
        }
        remember();
        BotManager.add(server.address, nickField.getText(), cmdField.getText());
        lastNick = "";
        info = "";
        this.clearAndInit();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int cx = this.width / 2;

        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, 14, 0xFFFFFFFF);
        if (!info.isEmpty()) {
            ctx.drawCenteredTextWithShadow(this.textRenderer, info, cx, 108, 0xFFFF5555);
        }

        List<McBot> bots = BotManager.all();
        for (int i = 0; i < Math.min(bots.size(), MAX_ROWS); i++) {
            McBot bot = bots.get(i);
            int y = 118 + i * 22 + 5;
            String line = bot.name() + " – " + bot.status();
            ctx.drawTextWithShadow(this.textRenderer, this.textRenderer.trimToWidth(line, 178),
                    cx - 150, y, color(bot.state()));
        }
        if (bots.size() > MAX_ROWS) {
            ctx.drawTextWithShadow(this.textRenderer, "+" + (bots.size() - MAX_ROWS) + " więcej",
                    cx - 150, 118 + MAX_ROWS * 22 + 5, 0xFFAAAAAA);
        }
    }

    private static int color(McBot.State s) {
        return switch (s) {
            case ONLINE -> 0xFF55FF55;
            case FAILED -> 0xFFFF5555;
            case CONNECTING, WAITING -> 0xFFFFFF55;
            case STOPPED -> 0xFFAAAAAA;
        };
    }
}
