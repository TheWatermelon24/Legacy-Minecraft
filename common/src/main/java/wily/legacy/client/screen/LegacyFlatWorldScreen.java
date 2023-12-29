package wily.legacy.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import wily.legacy.util.ScreenUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Environment(value=EnvType.CLIENT)
public class LegacyFlatWorldScreen extends PanelBackgroundScreen {
    public static int DEFAULT_MAX_WORLD_HEIGHT = 384;
    private final Consumer<FlatLevelGeneratorSettings> applySettings;
    protected final WorldCreationUiState uiState;
    FlatLevelGeneratorSettings generator;
    protected final TabList tabList = new TabList().addTabButton(30,0,Component.translatable("legacy.menu.create_flat_world.layers"), b-> rebuildWidgets()).addTabButton(30,1,Component.translatable("legacy.menu.create_flat_world.biomes"),  b-> rebuildWidgets()).addTabButton(30,2,Component.translatable("legacy.menu.create_flat_world.properties"), b-> rebuildWidgets());

    protected final RenderableVList displayLayers = new RenderableVList().layoutSpacing(l->0);
    protected final RenderableVList displayBiomes = new RenderableVList().layoutSpacing(l->0);
    protected final RenderableVList displayProperties = new RenderableVList();
    protected final List<Holder<StructureSet>> structuresOverrides;

    public LegacyFlatWorldScreen(Screen screen, WorldCreationUiState uiState, HolderLookup.RegistryLookup<Biome> biomeGetter, HolderLookup.RegistryLookup<StructureSet> structureGetter, Consumer<FlatLevelGeneratorSettings> consumer, FlatLevelGeneratorSettings flatLevelGeneratorSettings) {
        super(282,248,Component.translatable("createWorld.customize.flat.title"));
        this.uiState = uiState;
        parent = screen;
        this.applySettings = consumer;
        this.generator = flatLevelGeneratorSettings;
        structuresOverrides = new ArrayList<>(generator.structureOverrides().orElse(HolderSet.direct()).stream().toList());
        generator.getLayersInfo().forEach(this::addLayer);
        biomeGetter.listElements().forEach(this::addBiome);
        structureGetter.listElements().forEach(this::addStructure);
        displayProperties.addRenderable(new TickBox(0,0,260,12, generator.decoration, b-> Component.translatable("legacy.createWorld.customize.custom.useDecorations"), b-> null, b-> generator.decoration = b.selected));
        displayProperties.addRenderable(new TickBox(0,0,260,12, generator.addLakes, b-> Component.translatable("createWorld.customize.custom.useLavaLakes"), b-> null, b-> generator.addLakes = b.selected));
    }
    public void addStructure(Holder.Reference<StructureSet> structure){
        displayProperties.addRenderable(new TickBox(0,0,260,12,structuresOverrides.contains(structure), b-> Component.translatable("structure."+structure.key().location().toLanguageKey()), b-> null, b-> {
            if (b.selected) structuresOverrides.add(structure);
            else structuresOverrides.remove(structure);
        }));
    }
    public void addBiome(Holder.Reference<Biome> biome){
        displayBiomes.addRenderable(new AbstractButton(0,0,260,30, Component.translatable("biome."+biome.key().location().toLanguageKey())) {
            @Override
            public void onPress() {
                generator.biome = biome;
            }

            @Override
            protected void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
                super.renderWidget(guiGraphics, i, j, f);
                RenderSystem.enableBlend();
                guiGraphics.blitSprite(TickBox.SPRITES[isHoveredOrFocused() ? 1 : 0], this.getX() + 6, this.getY() + (height - 12) / 2, 12, 12);
                if (generator.biome == biome) guiGraphics.blitSprite(TickBox.TICK_SPRITE, this.getX() + 6, this.getY()  + (height - 12) / 2, 14, 12);
                RenderSystem.disableBlend();
            }
            @Override
            protected void renderScrollingString(GuiGraphics guiGraphics, Font font, int i, int j) {
                int k = this.getX() + 54;
                int l = this.getX() + this.getWidth();
                TickBox.renderScrollingString(guiGraphics, font, this.getMessage(), k, this.getY(), l, this.getY() + this.getHeight(), j,true);
            }
            @Override
            protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {

            }
        });
    }
    public void addLayer(FlatLayerInfo flatLayerInfo){
        addLayer(flatLayerInfo,0,false);
    }
    public void addLayer(FlatLayerInfo flatLayerInfo, int index){
        addLayer(flatLayerInfo,index,true);
    }
    public void addLayer(FlatLayerInfo flatLayerInfo, int index,boolean update){
        displayLayers.vRenderables.add(index,new AbstractButton(0,0,260,30,flatLayerInfo.getBlockState().getBlock().getName()) {
            @Override
            protected void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
                super.renderWidget(guiGraphics, i, j, f);
                guiGraphics.drawString(font,Component.translatable("legacy.menu.create_flat_world.layer_count",flatLayerInfo.getHeight()),getX() + 12, getY() + 1 + (height - font.lineHeight) / 2, 0xFFFFFF);
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(getX() + 39, getY() + 5,0);
                guiGraphics.pose().scale(1.25f,1.25f,1.25f);
                guiGraphics.renderItem(flatLayerInfo.getBlockState().getBlock().asItem().getDefaultInstance(),0, 0);
                guiGraphics.pose().popPose();
            }
            @Override
            protected void renderScrollingString(GuiGraphics guiGraphics, Font font, int i, int j) {
                int k = this.getX() + 67;
                int l = this.getX() + this.getWidth();
                TickBox.renderScrollingString(guiGraphics, font, this.getMessage(), k, this.getY(), l, this.getY() + this.getHeight(), j,true);
            }
            @Override
            public void onPress() {
                int allHeight = getAllLayersHeight();
                int layerIndex = displayLayers.vRenderables.indexOf(this);
                minecraft.setScreen(new ConfirmationScreen(LegacyFlatWorldScreen.this,Component.translatable("legacy.menu.create_flat_world.layer_options"), Component.translatable("legacy.menu.create_flat_world.layer_message"),b->{}){
                    @Override
                    protected void initButtons() {
                        addRenderableWidget(Button.builder(Component.translatable("legacy.menu.create_flat_world.edit_layer"), b-> {
                            minecraft.setScreen(new FlatWorldLayerSelector(LegacyFlatWorldScreen.this, flatLayerInfo,f-> {
                                removeLayer(layerIndex);
                                addLayer(f.getFlatLayerInfo(),layerIndex);
                            },DEFAULT_MAX_WORLD_HEIGHT- allHeight + flatLayerInfo.getHeight(),Component.translatable("legacy.menu.create_flat_world.edit_layer")));
                        }).bounds(panel.x + 15, panel.y + panel.height - 74,200,20).build());
                        addRenderableWidget(Button.builder(Component.translatable("legacy.menu.create_flat_world.add_layer"), b-> {
                            if (allHeight < DEFAULT_MAX_WORLD_HEIGHT) minecraft.setScreen(new FlatWorldLayerSelector(LegacyFlatWorldScreen.this,f-> addLayer(f.getFlatLayerInfo(),layerIndex),DEFAULT_MAX_WORLD_HEIGHT- allHeight,Component.translatable("legacy.menu.create_flat_world.add_layer")));
                            else this.onClose();
                        }).bounds(panel.x + 15, panel.y + panel.height - 52,200,20).build());
                        addRenderableWidget(Button.builder(Component.translatable("legacy.menu.create_flat_world.delete_layer"),b-> {
                            removeLayer(layerIndex);
                            this.onClose();
                        }).bounds(panel.x + 15, panel.y + panel.height - 30,200,20).build());
                    }
                });
            }

            @Override
            protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {

            }
        });
        if (update)
            generator.getLayersInfo().add(displayLayers.vRenderables.size() - 1 - index,flatLayerInfo);
    }
    public int getAllLayersHeight(){
        int height = 0;
        for (FlatLayerInfo flatLayerInfo : generator.getLayersInfo()) {
            height += flatLayerInfo.getHeight();
        }
        return height;
    }
    public void removeLayer(int index){
        displayLayers.vRenderables.remove(index);
        generator.getLayersInfo().remove(generator.getLayersInfo().size() - 1 - index);
    }

    public FlatLevelGeneratorSettings settings() {
        return this.generator;
    }

    public void setPreset(FlatLevelGeneratorSettings flatLevelGeneratorSettings) {
        generator = flatLevelGeneratorSettings;
        displayLayers.vRenderables.clear();
        generator.getLayersInfo().forEach(this::addLayer);
    }
    public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
        ScreenUtil.renderDefaultBackground(guiGraphics,false);
    }
    @Override
    protected void init() {
        addRenderableWidget(tabList);
        panel.height = Math.min(height - 48,248);
        super.init();
        addRenderableOnly(((guiGraphics, i, j, f) -> ScreenUtil.renderPanelRecess(guiGraphics, panel.x + 7, panel.y + 7, panel.width - 14, panel.height - 14, 2)));
        getRenderableVList().init(this,panel.x + 11,panel.y + 11,260, panel.height - 5);
        tabList.init(panel.x,panel.y - 24, panel.width);
        this.generator.updateLayers();
    }
    protected RenderableVList getRenderableVList(){
        if (tabList.selectedTab == 2) return displayProperties;
        if (tabList.selectedTab == 1) return displayBiomes;
        return displayLayers;
    }
    @Override
    public boolean mouseScrolled(double d, double e, double f, double g) {
        getRenderableVList().mouseScrolled(d,e,f,g);
        return super.mouseScrolled(d, e, f, g);
    }

    @Override
    public boolean keyPressed(int i, int j, int k) {
        tabList.controlTab(i,j,k);
        if (i == InputConstants.KEY_E)
            minecraft.setScreen(new LegacyFlatPresetsScreen(this,uiState.getSettings().worldgenLoadContext().lookupOrThrow(Registries.FLAT_LEVEL_GENERATOR_PRESET),uiState.getSettings().dataConfiguration().enabledFeatures(), f-> setPreset(f.value().settings())));
        return super.keyPressed(i, j, k);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
        this.generator.updateLayers();
        applySettings.accept(generator);
        generator.structureOverrides = Optional.of(HolderSet.direct(structuresOverrides));
    }

}

