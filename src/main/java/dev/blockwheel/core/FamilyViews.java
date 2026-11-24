package dev.blockwheel.core;

/** Cached immutable combinations of independent optional material policies. */
public final class FamilyViews {
    private final FamilyCatalog[] catalogs = new FamilyCatalog[4];
    public FamilyViews(FamilyCatalog ordinary, FamilyCatalog experimental) {
        for (int index = 0; index < catalogs.length; index++) {
            var settings = new BuilderSettings(false, ((index & 1) != 0 ? BuilderSettings.Feature.WOOD_TRAPDOORS.bit() : 0)
                    | ((index & 2) != 0 ? BuilderSettings.Feature.EXPERIMENTAL_WOOD.bit() : 0));
            catalogs[index] = ((index & 2) == 0 ? ordinary : experimental).filterShapes(shape -> settings.shapeEnabled(shape.id()));
        }
    }
    public FamilyCatalog get(BuilderSettings settings) { return catalogs[settings.catalogView()]; }
    public FamilyCatalog get(int index) { return catalogs[index]; }
}
