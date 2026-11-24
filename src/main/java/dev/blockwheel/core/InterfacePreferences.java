package dev.blockwheel.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Local presentation only; hiding feedback never changes sound, narration or inventory behavior. */
public record InterfacePreferences(boolean hideButtonTooltips, boolean hideBuilderMessages, boolean showBuilderButton, boolean showShulkerRefillControls) {
    public InterfacePreferences(boolean tooltips, boolean messages) { this(tooltips, messages, true, true); }
    public static final InterfacePreferences DEFAULT = new InterfacePreferences(false, false);
    public static final Codec<InterfacePreferences> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(1, 1).optionalFieldOf("version", 1).forGetter(value -> 1),
            Codec.BOOL.optionalFieldOf("hide_button_tooltips", false).forGetter(InterfacePreferences::hideButtonTooltips),
            Codec.BOOL.optionalFieldOf("hide_builder_messages", false).forGetter(InterfacePreferences::hideBuilderMessages),
            Codec.BOOL.optionalFieldOf("show_builder_button", true).forGetter(InterfacePreferences::showBuilderButton),
            Codec.BOOL.optionalFieldOf("show_shulker_refill_controls", true).forGetter(InterfacePreferences::showShulkerRefillControls)
    ).apply(instance, (version, tooltips, messages, builder, shulkers) -> new InterfacePreferences(tooltips, messages, builder, shulkers)));
    public InterfacePreferences withButtonTooltipsHidden(boolean value) { return new InterfacePreferences(value, hideBuilderMessages, showBuilderButton, showShulkerRefillControls); }
    public InterfacePreferences withBuilderMessagesHidden(boolean value) { return new InterfacePreferences(hideButtonTooltips, value, showBuilderButton, showShulkerRefillControls); }
    public InterfacePreferences withBuilderButtonShown(boolean value) { return new InterfacePreferences(hideButtonTooltips, hideBuilderMessages, value, showShulkerRefillControls); }
    public InterfacePreferences withShulkerControlsShown(boolean value) { return new InterfacePreferences(hideButtonTooltips, hideBuilderMessages, showBuilderButton, value); }
}
