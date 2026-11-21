package com.andypeng.guiscalefix.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.math.MathHelper;

/**
 * Integer slider for the GUI scale option.
 * <p>
 * Position 0 means "Auto" (vanilla {@code guiScale == 0}). The upper bound is derived from
 * the current window size so that every single slider position produces a distinct, usable
 * scale factor — unlike the vanilla cycle button, which silently ignored values that the
 * 320x240 guard could not satisfy.
 * <p>
 * The value is committed on mouse release. This also lets vanilla's
 * {@code GuiVideoSettings#mouseReleased} detect the change and re-layout the screen.
 */
public class GuiScaleSlider extends GuiButton {

    private static final int MIN_VIRTUAL_WIDTH = 160;
    private static final int MIN_VIRTUAL_HEIGHT = 120;

    private final GameSettings settings;
    private final int maxValue;

    private boolean dragging;
    private int value;
    private int previewValue;
    private float sliderValue;

    public GuiScaleSlider(int buttonId, int x, int y, int widthIn, int heightIn, GameSettings settings) {
        super(buttonId, x, y, widthIn, heightIn, "");
        this.settings = settings;

        Minecraft mc = Minecraft.getMinecraft();
        this.maxValue = Math.max(1, Math.min(mc.displayWidth / MIN_VIRTUAL_WIDTH,
                mc.displayHeight / MIN_VIRTUAL_HEIGHT));

        int current = Math.max(0, Math.min(settings.guiScale, this.maxValue));
        this.value = current;
        this.previewValue = current;
        this.sliderValue = (float) current / (float) this.maxValue;
        this.updateDisplayString();
    }

    public int getMaxValue() {
        return this.maxValue;
    }

    @Override
    protected int getHoverState(boolean mouseOver) {
        return 0;
    }

    @Override
    protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
        if (!this.visible) {
            return;
        }

        if (this.dragging) {
            this.updateFromMouse(mouseX);
        }

        mc.getTextureManager().bindTexture(BUTTON_TEXTURES);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        int handleX = this.x + (int) (this.sliderValue * (float) (this.width - 8));
        this.drawTexturedModalRect(handleX, this.y, 0, 66, 4, 20);
        this.drawTexturedModalRect(handleX + 4, this.y, 196, 66, 4, 20);
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (super.mousePressed(mc, mouseX, mouseY)) {
            this.dragging = true;
            this.updateFromMouse(mouseX);
            return true;
        }
        return false;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        if (!this.dragging) {
            return;
        }

        this.dragging = false;
        this.value = this.previewValue;
        this.sliderValue = (float) this.value / (float) this.maxValue;
        this.updateDisplayString();

        if (this.settings.guiScale != this.value) {
            this.settings.guiScale = this.value;
            this.settings.saveOptions();
        }
    }

    private void updateFromMouse(int mouseX) {
        float f = (float) (mouseX - (this.x + 4)) / (float) (this.width - 8);
        f = MathHelper.clamp(f, 0.0F, 1.0F);
        this.previewValue = Math.round(f * (float) this.maxValue);
        this.sliderValue = (float) this.previewValue / (float) this.maxValue;
        this.updateDisplayString();
    }

    private void updateDisplayString() {
        int shown = this.dragging ? this.previewValue : this.value;
        this.displayString = I18n.format("options.guiScale") + ": "
                + (shown == 0 ? I18n.format("options.guiScale.auto") : Integer.toString(shown));
    }
}
