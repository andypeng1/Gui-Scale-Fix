package com.andypeng.guiscalefix.client;

import com.andypeng.guiscalefix.GuiScaleFix;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.GuiOptionsRowList;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Swaps the vanilla "GUI Scale" cycle button for {@link GuiScaleSlider} whenever the
 * Video Settings screen is (re)initialised.
 * <p>
 * 1.12.2 builds the video settings widgets inside {@link GuiOptionsRowList}, which is not
 * part of {@code buttonList}. We therefore locate the row list (and its row widgets) via
 * reflection and rebuild the affected row with our slider. Fields are found by their type
 * so this keeps working in both MCP and SRG environments.
 */
@Mod.EventBusSubscriber(modid = GuiScaleFix.MODID, value = Side.CLIENT)
public final class VideoSettingsHook {

    private static final Logger LOGGER = LogManager.getLogger("guiscalefix");

    private VideoSettingsHook() {
    }

    @SubscribeEvent
    public static void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.getGui() instanceof GuiVideoSettings)) {
            return;
        }

        try {
            GuiVideoSettings screen = (GuiVideoSettings) event.getGui();

            Field rowListField = findFieldOfType(GuiVideoSettings.class, GuiListExtended.class);
            if (rowListField == null) {
                return;
            }
            Object rowList = rowListField.get(screen);
            if (rowList == null) {
                return;
            }

            Field rowsField = findFieldOfType(GuiOptionsRowList.class, List.class);
            if (rowsField == null) {
                return;
            }

            @SuppressWarnings("unchecked")
            List<GuiOptionsRowList.Row> rows = (List<GuiOptionsRowList.Row>) rowsField.get(rowList);
            if (rows == null) {
                return;
            }

            List<Field> buttonFields = findFieldsOfType(GuiOptionsRowList.Row.class, GuiButton.class);
            if (buttonFields.size() != 2) {
                LOGGER.warn("Unexpected GuiOptionsRowList.Row layout ({} button fields)", buttonFields.size());
                return;
            }

            for (int i = 0; i < rows.size(); i++) {
                GuiOptionsRowList.Row row = rows.get(i);
                if (row == null) {
                    continue;
                }

                GuiButton buttonA = (GuiButton) buttonFields.get(0).get(row);
                GuiButton buttonB = (GuiButton) buttonFields.get(1).get(row);
                boolean changed = false;

                if (isGuiScaleButton(buttonA)) {
                    buttonA = createSlider(buttonA);
                    changed = true;
                }
                if (isGuiScaleButton(buttonB)) {
                    buttonB = createSlider(buttonB);
                    changed = true;
                }

                if (changed) {
                    rows.set(i, new GuiOptionsRowList.Row(buttonA, buttonB));
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("Could not install the GUI scale slider", t);
        }
    }

    private static boolean isGuiScaleButton(GuiButton button) {
        return button != null && button.id == GameSettings.Options.GUI_SCALE.ordinal();
    }

    private static GuiButton createSlider(GuiButton old) {
        GameSettings settings = Minecraft.getMinecraft().gameSettings;
        GuiScaleSlider slider = new GuiScaleSlider(old.id, old.x, old.y, old.width, old.height, settings);
        LOGGER.debug("Installed GUI scale slider (range 0-{})", slider.getMaxValue());
        return slider;
    }

    private static Field findFieldOfType(Class<?> owner, Class<?> type) {
        for (Field field : owner.getDeclaredFields()) {
            if (type.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    private static List<Field> findFieldsOfType(Class<?> owner, Class<?> type) {
        List<Field> result = new ArrayList<>(2);
        for (Field field : owner.getDeclaredFields()) {
            if (type.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                result.add(field);
            }
        }
        return result;
    }
}
