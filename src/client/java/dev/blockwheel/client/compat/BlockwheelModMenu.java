package dev.blockwheel.client.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.blockwheel.client.BuilderSettingsScreen;

/** Loaded only by Mod Menu's optional client entrypoint. */
public final class BlockwheelModMenu implements ModMenuApi {
    @Override public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return BuilderSettingsScreen::new;
    }
}
