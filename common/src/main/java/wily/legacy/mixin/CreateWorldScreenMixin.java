package wily.legacy.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Lifecycle;
import net.minecraft.ChatFormatting;
import net.minecraft.FileUtil;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.commands.PublishCommand;
import net.minecraft.util.HttpUtil;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wily.legacy.Legacy4JClient;
import wily.legacy.client.LegacyOptions;
import wily.legacy.client.LegacyWorldSettings;
import wily.legacy.client.controller.ControllerBinding;
import wily.legacy.client.screen.*;
import wily.legacy.init.LegacySoundEvents;
import wily.legacy.util.ScreenUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static wily.legacy.Legacy4JClient.publishUnloadedServer;
import static wily.legacy.client.screen.ControlTooltip.*;
import static wily.legacy.client.screen.ControlTooltip.CONTROL_ACTION_CACHE;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin extends Screen{
    public ControlTooltip.Renderer controlTooltipRenderer = ControlTooltip.defaultScreen(this);
    @Shadow @Final private Screen lastScreen;
    @Shadow @Final private WorldCreationUiState uiState;
    @Shadow @Final private static Component GAME_MODEL_LABEL;
    @Shadow @Final private static Component PREPARING_WORLD_DATA;
    @Shadow @Final private static Component NAME_LABEL;
    @Shadow @Final private static Logger LOGGER;

    @Shadow protected abstract LevelSettings createLevelSettings(boolean bl);

    protected boolean trustPlayers;
    protected boolean onlineOnStart = false;
    private int port = HttpUtil.getAvailablePort();
    protected Panel panel;

    protected PackSelector resourcePackSelector;

    private Path tempDataPackDir;

    protected CreateWorldScreenMixin(Component component) {
        super(component);
    }
    private CreateWorldScreen self(){
        return (CreateWorldScreen) (Object) this;
    }

    @Inject(method = "<init>",at = @At("RETURN"))
    public void initReturn(Minecraft minecraft, Screen screen, WorldCreationContext worldCreationContext, Optional optional, OptionalLong optionalLong, CallbackInfo ci){
        uiState.setDifficulty(((LegacyOptions)minecraft.options).createWorldDifficulty().get());
        panel = new Panel(p-> (width - (p.width + (ScreenUtil.hasTooltipBoxes() ? 160 : 0))) / 2, p-> (height - p.height) / 2,245,228);
        resourcePackSelector = PackSelector.resources(panel.x + 13, panel.y + 106, 220,45, !ScreenUtil.hasTooltipBoxes());
        controlTooltipRenderer.add(()-> getActiveType().isKeyboard() ? COMPOUND_COMPONENT_FUNCTION.apply(new Component[]{getKeyIcon(InputConstants.KEY_LSHIFT,true), PLUS,getKeyIcon(InputConstants.MOUSE_BUTTON_LEFT,true)}) : ControllerBinding.LEFT_BUTTON.bindingState.getIcon(true), ()-> getFocused() == resourcePackSelector ? CONTROL_ACTION_CACHE.getUnchecked("legacy.action.resource_packs_screen") : null);
    }
    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    public void init(CallbackInfo ci) {
        ci.cancel();
        panel.init();
        addRenderableOnly(panel);
        EditBox nameEdit = new EditBox(font, panel.x + 13, panel.y + 25,220, 20, Component.translatable("selectWorld.enterName"));
        nameEdit.setValue(uiState.getName());
        nameEdit.setResponder(uiState::setName);
        uiState.addListener(worldCreationUiState -> nameEdit.setTooltip(Tooltip.create(Component.translatable("selectWorld.targetFolder", Component.literal(worldCreationUiState.getTargetFolder()).withStyle(ChatFormatting.ITALIC)))));
        setInitialFocus(nameEdit);
        addRenderableWidget(nameEdit);
        LegacySliderButton<WorldCreationUiState.SelectedGameMode> gameModeButton = addRenderableWidget(new LegacySliderButton<>(panel.x + 13, panel.y + 51, 220,16, b -> b.getDefaultMessage(GAME_MODEL_LABEL,b.getObjectValue().displayName),()->Tooltip.create(uiState.getGameMode().getInfo()),uiState.getGameMode(),()-> List.of(WorldCreationUiState.SelectedGameMode.SURVIVAL, WorldCreationUiState.SelectedGameMode.HARDCORE, WorldCreationUiState.SelectedGameMode.CREATIVE), b->uiState.setGameMode(b.objectValue)));
        uiState.addListener(worldCreationUiState -> gameModeButton.active = !worldCreationUiState.isDebug());
        LegacySliderButton<Difficulty> difficultyButton = addRenderableWidget(new LegacySliderButton<>(panel.x + 13, panel.y + 77, 220,16, b -> b.getDefaultMessage(Component.translatable("options.difficulty"),b.getObjectValue().getDisplayName()),()->Tooltip.create(uiState.getDifficulty().getInfo()),uiState.getDifficulty(),()-> Arrays.asList(Difficulty.values()), b->uiState.setDifficulty(b.objectValue)));
        uiState.addListener(worldCreationUiState -> difficultyButton.active = !uiState.isHardcore());
        EditBox portEdit = addRenderableWidget(new EditBox(minecraft.font, panel.x + 124, panel.y + 151,100,20,Component.translatable("lanServer.port")));
        portEdit.visible = onlineOnStart;

        portEdit.setHint(Component.literal("" + this.port).withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(Button.builder(Component.translatable( "createWorld.tab.more.title"), button -> minecraft.setScreen(new WorldMoreOptionsScreen(self(), b-> trustPlayers = b))).bounds(panel.x + 13, panel.y + 172,220,20).build());
        Button createButton = addRenderableWidget(Button.builder(Component.translatable("selectWorld.create"), button -> this.onCreate()).bounds(panel.x + 13, panel.y + 197,220,20).build());
        addRenderableWidget(new TickBox(panel.x+ 14, panel.y+155,100,onlineOnStart, b-> Component.translatable("menu.shareToLan"), b->null, button -> {
            if (!(portEdit.visible = onlineOnStart = button.selected)) {
                createButton.active = true;
                portEdit.setValue("");
            }
        }));
        portEdit.setResponder(string -> {
            Pair<Integer,Component> p = Legacy4JClient.tryParsePort(string);
            if(p.getFirst() != null) port = p.getFirst();
            portEdit.setHint(Component.literal("" + this.port).withStyle(ChatFormatting.DARK_GRAY));
            if (p.getSecond() == null) {
                portEdit.setTextColor(0xE0E0E0);
                portEdit.setTooltip(null);
                createButton.active = true;
            } else {
                portEdit.setTextColor(0xFF5555);
                portEdit.setTooltip(Tooltip.create(p.getSecond()));
                createButton.active = false;
            }
        });
        resourcePackSelector.setX(panel.x + 13);
        resourcePackSelector.setY(panel.y + 106);
        addRenderableWidget(resourcePackSelector);
        this.uiState.onChanged();
    }

    @Override
    public void repositionElements() {
        rebuildWidgets();
    }

    private void onCreate() {
        WorldCreationContext worldCreationContext = this.uiState.getSettings();
        WorldDimensions.Complete complete = worldCreationContext.selectedDimensions().bake(worldCreationContext.datapackDimensions());
        LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess = worldCreationContext.worldgenRegistries().replaceFrom(RegistryLayer.DIMENSIONS, complete.dimensionsRegistryAccess());
        Lifecycle lifecycle = FeatureFlags.isExperimental(worldCreationContext.dataConfiguration().enabledFeatures()) ? Lifecycle.experimental() : Lifecycle.stable();
        Lifecycle lifecycle2 = layeredRegistryAccess.compositeAccess().allRegistriesLifecycle();
        Lifecycle lifecycle3 = lifecycle2.add(lifecycle);
        boolean bl = lifecycle2 == Lifecycle.stable();
        Runnable create = ()-> confirmWorldCreation(this.minecraft, self(), lifecycle3, () -> {
            try {
                this.createNewWorld(complete.specialWorldProperty(), layeredRegistryAccess, lifecycle3);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, bl);
        resourcePackSelector.applyChanges(true, ()->minecraft.reloadResourcePacks().thenRun(create), create);
        onLoad();

    }
    private void onLoad() {
        minecraft.execute(()->{
            if (minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer().isReady()){
                if (onlineOnStart) {
                    MutableComponent component = publishUnloadedServer(minecraft, uiState.getGameMode().gameType, trustPlayers && uiState.isAllowCheats(), this.port) ? PublishCommand.getSuccessMessage(this.port) : Component.translatable("commands.publish.failed");
                    ((LegacyWorldSettings)minecraft.getSingleplayerServer().getWorldData()).setTrustPlayers(trustPlayers);
                    if (resourcePackSelector.hasChanged()) ((LegacyWorldSettings)minecraft.getSingleplayerServer().getWorldData()).setSelectedResourcePacks(resourcePackSelector.getSelectedIds());
                    this.minecraft.gui.getChat().addMessage(component);
                }
        }});
    }
    
    private static void confirmWorldCreation(Minecraft minecraft, CreateWorldScreen createWorldScreen, Lifecycle lifecycle, Runnable runnable, boolean bl2) {
        if (bl2 || lifecycle == Lifecycle.stable()) {
            runnable.run();
        } else if (lifecycle == Lifecycle.experimental()) {
            minecraft.setScreen(new ConfirmationScreen(createWorldScreen, Component.translatable("selectWorld.warning.experimental.title"), Component.translatable("selectWorld.warning.experimental.question"),b->runnable.run()));
        } else {
            minecraft.setScreen(new ConfirmationScreen(createWorldScreen, Component.translatable("selectWorld.warning.deprecated.title"), Component.translatable("selectWorld.warning.deprecated.question"),b->runnable.run()));
        }
    }
    private void createNewWorld(PrimaryLevelData.SpecialWorldProperty specialWorldProperty, LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess, Lifecycle lifecycle) throws IOException {
        minecraft.setScreen(new GenericDirtMessageScreen(PREPARING_WORLD_DATA));
        Optional<LevelStorageSource.LevelStorageAccess> optional = this.createNewWorldDirectory();
        if (optional.isEmpty()) {
            return;
        }
        this.removeTempDataPackDir();
        boolean bl = specialWorldProperty == PrimaryLevelData.SpecialWorldProperty.DEBUG;
        WorldCreationContext worldCreationContext = this.uiState.getSettings();
        LevelSettings levelSettings = this.createLevelSettings(bl);
        PrimaryLevelData worldData = new PrimaryLevelData(levelSettings, worldCreationContext.options(), specialWorldProperty, lifecycle);
        this.minecraft.createWorldOpenFlows().createLevelFromExistingSettings(optional.get(), worldCreationContext.dataPackResources(), layeredRegistryAccess, worldData);
    }
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
        ScreenUtil.renderDefaultBackground(guiGraphics,false);
        resourcePackSelector.renderTooltipBox(guiGraphics,panel);
        controlTooltipRenderer.render(guiGraphics, i, j, f);
    }
    @Override
    public boolean mouseScrolled(double d, double e, double f, double g) {
        if (resourcePackSelector.scrollableRenderer.mouseScrolled(g)) return true;
        return super.mouseScrolled(d, e, f, g);
    }
    public void render(GuiGraphics guiGraphics, int i, int j, float f) {
        this.renderBackground(guiGraphics, i, j, f);
        for (Renderable renderable : this.renderables)
            renderable.render(guiGraphics, i, j, f);
        guiGraphics.drawString(font,NAME_LABEL, panel.x + 14, panel.y + 15, 0x383838,false);
    }

    @Override
    public boolean keyPressed(int i, int j, int k) {
        return super.keyPressed(i,j,k);
    }

    private void removeTempDataPackDir() {
        if (this.tempDataPackDir != null) {
            try (Stream<Path> stream = Files.walk(this.tempDataPackDir);){
                stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException iOException) {
                        LOGGER.warn("Failed to remove temporary file {}", path, iOException);
                    }
                });
            } catch (IOException iOException) {
                LOGGER.warn("Failed to list temporary dir {}", this.tempDataPackDir);
            }
            this.tempDataPackDir = null;
        }
    }

    private static void copyBetweenDirs(Path path, Path path2, Path path3) {
        try {
            Util.copyBetweenDirs(path, path2, path3);
        } catch (IOException iOException) {
            LOGGER.warn("Failed to copy datapack file from {} to {}", path3, path2);
            throw new UncheckedIOException(iOException);
        }
    }

    private Optional<LevelStorageSource.LevelStorageAccess> createNewWorldDirectory() throws IOException {
        String string;
        block12: {
            LevelStorageSource.LevelStorageAccess levelStorageAccess;
            block11: {
                string = this.uiState.getTargetFolder();
                levelStorageAccess = this.minecraft.getLevelSource().createAccess(string);
                if (this.tempDataPackDir != null) break block11;
                return Optional.of(levelStorageAccess);
            }
            Stream<Path> stream = Files.walk(this.tempDataPackDir);
            try {
                Path path3 = levelStorageAccess.getLevelPath(LevelResource.DATAPACK_DIR);
                FileUtil.createDirectoriesSafe(path3);
                stream.filter(path -> !path.equals(this.tempDataPackDir)).forEach(path2 -> copyBetweenDirs(this.tempDataPackDir, path3, path2));
                if (stream == null) break block12;
            } catch (Throwable throwable) {
                try {
                    try {
                        if (stream != null) {
                            try {
                                stream.close();
                            } catch (Throwable throwable2) {
                                throwable.addSuppressed(throwable2);
                            }
                        }
                        throw throwable;
                    } catch (IOException | UncheckedIOException exception) {
                        LOGGER.warn("Failed to copy datapacks to world {}", string, exception);
                        levelStorageAccess.close();
                    }
                } catch (IOException | UncheckedIOException exception2) {
                    LOGGER.warn("Failed to create access for {}", string, exception2);
                }
            }
            stream.close();
        }
        SystemToast.onPackCopyFailure(this.minecraft, string);
        this.popScreen();
        return Optional.empty();
    }
    public void popScreen() {
        this.minecraft.setScreen(lastScreen);
        this.removeTempDataPackDir();
    }

    @Override
    public void onClose() {
        ScreenUtil.playSimpleUISound(LegacySoundEvents.BACK.get(),1.0f);
        popScreen();
    }
}
