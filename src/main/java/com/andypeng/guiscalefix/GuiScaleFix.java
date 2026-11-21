package com.andypeng.guiscalefix;

import net.minecraftforge.fml.common.Mod;

/**
 * GUI Scale Fix — makes every GUI scale value actually take effect on non-standard
 * resolutions, and turns the vanilla GUI Scale option into a real slider.
 */
@Mod(
        modid = GuiScaleFix.MODID,
        name = GuiScaleFix.NAME,
        version = GuiScaleFix.VERSION,
        clientSideOnly = true,
        acceptableRemoteVersions = "*"
)
public final class GuiScaleFix {

    public static final String MODID = "guiscalefix";
    public static final String NAME = "GUI Scale Fix / 界面缩放修复";
    public static final String VERSION = "1.0.3";

    // FML instantiates this class reflectively and therefore needs a public constructor.
    public GuiScaleFix() {
    }
}
