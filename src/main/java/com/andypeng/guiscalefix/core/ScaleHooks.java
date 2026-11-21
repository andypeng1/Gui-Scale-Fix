package com.andypeng.guiscalefix.core;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.MathHelper;

/**
 * Runtime helper injected into {@code ScaledResolution#<init>(Minecraft)}.
 * <p>
 * Vanilla only ever increases the scale factor while the resulting virtual resolution
 * is at least 320x240, and additionally rounds odd factors down when a Unicode font is
 * active. Both behaviours silently swallow user-selected scale values at non-standard
 * resolutions (for example "Normal" and "Large" can produce the exact same result).
 * <p>
 * We keep vanilla behaviour for "Auto" (320x240 rule and the Unicode even-scale rule, so
 * the default appearance does not change) but honour an explicitly chosen value exactly.
 * The only guard left for manual values is that the scale may not exceed the smaller
 * physical screen dimension, which would yield zero-sized GUIs.
 */
public final class ScaleHooks {

    private ScaleHooks() {
    }

    /**
     * @return { scaleFactor, scaledWidthD, scaledHeightD, scaledWidth, scaledHeight }
     */
    public static double[] compute(Minecraft mc) {
        int width = mc.displayWidth;
        int height = mc.displayHeight;
        int requested = mc.gameSettings.guiScale;

        int scale;
        if (requested <= 0) {
            // 0 == Auto: largest factor that still leaves at least a 320x240 virtual screen.
            scale = 1;
            while (scale < 1000 && width / (scale + 1) >= 320 && height / (scale + 1) >= 240) {
                scale++;
            }
            // Keep the vanilla Unicode rule for Auto so the default look is unchanged.
            if (mc.isUnicode() && scale % 2 != 0 && scale != 1) {
                scale--;
            }
        } else {
            // Explicit choice: honour it exactly (this is the actual fix).
            scale = requested;
            int hardMax = Math.max(1, Math.min(width, height));
            if (scale > hardMax) {
                scale = hardMax;
            }
        }

        if (scale < 1) {
            scale = 1;
        }

        double widthD = (double) width / (double) scale;
        double heightD = (double) height / (double) scale;

        return new double[]{
                scale,
                widthD,
                heightD,
                MathHelper.ceil(widthD),
                MathHelper.ceil(heightD)
        };
    }
}
