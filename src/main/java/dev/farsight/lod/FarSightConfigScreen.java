package dev.farsight.lod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Simple Mod Menu settings screen for the LOD radius and enable toggle. */
public final class FarSightConfigScreen extends Screen {
    private final Screen parent;

    public FarSightConfigScreen(Screen parent) {
        super(Component.literal("FarSight LOD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;

        this.addRenderableWidget(Button.builder(distanceText(), button -> {
            int current = FarSightConfig.get().chunkDistance;
            int next = current < 64 ? 128
                    : current < 128 ? 256
                    : current < 256 ? 512
                    : current < 512 ? 768
                    : current < 768 ? 1024
                    : current < 1024 ? 1536
                    : current < 1536 ? 2048 : 32;
            FarSightConfig.get().setChunkDistance(next);
            button.setMessage(distanceText());
        }).bounds(centerX - 100, this.height / 2 - 10, 200, 20).build());

        this.addRenderableWidget(Button.builder(enabledText(), button -> {
            FarSightConfig config = FarSightConfig.get();
            config.enabled = !config.enabled;
            config.save();
            button.setMessage(enabledText());
        }).bounds(centerX - 100, this.height / 2 + 16, 200, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> {
            if (this.minecraft != null) this.minecraft.gui.setScreen(this.parent);
        }).bounds(centerX - 100, this.height / 2 + 44, 200, 20).build());
    }

    private Component distanceText() {
        return Component.literal("LOD chunk distance: " + FarSightConfig.get().chunkDistance);
    }

    private Component enabledText() {
        return Component.literal("FarSight LOD: " + (FarSightConfig.get().enabled ? "ON" : "OFF"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 55, 0xFFFFFFFF);
        graphics.centeredText(this.font,
                Component.literal("Target distance in chunks (32–2048)"),
                this.width / 2, this.height / 2 - 32, 0xFFAAAAAA);
    }
}
