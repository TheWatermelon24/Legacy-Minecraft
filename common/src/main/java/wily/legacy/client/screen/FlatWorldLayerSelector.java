package wily.legacy.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.material.FlowingFluid;
import wily.legacy.inventory.LegacySlotWrapper;
import wily.legacy.util.ScreenUtil;
import wily.legacy.util.Stocker;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public class FlatWorldLayerSelector extends PanelBackgroundScreen {
    public static final Container layerSelectionGrid = new SimpleContainer(50);
    public final List<ItemStack> layerItems = new ArrayList<>();
    protected final Stocker.Sizeable scrolledList = new Stocker.Sizeable(0);
    private final Consumer<FlatWorldLayerSelector> applyLayer;
    protected final int maxLayerHeight;
    protected ItemStack selectedLayer = Items.AIR.getDefaultInstance();

    protected Screen parent;
    protected final List<LegacySlotWrapper> slots = new ArrayList<>();
    protected LegacySlotWrapper hoveredSlot = null;

    public FlatWorldLayerSelector(Screen parent, Consumer<FlatWorldLayerSelector> applyLayer, int maxLayerHeight, Component component) {
        super(325,245, component);
        this.parent = parent;
        this.applyLayer = applyLayer;
        this.maxLayerHeight = maxLayerHeight;
        for (int i = 0; i < layerSelectionGrid.getContainerSize(); i++) {
            slots.add(new LegacySlotWrapper(layerSelectionGrid,i,23 + i % 10 * 27, 24 + i / 10 * 27){
                public int getWidth() {
                    return 27;
                }
                public int getHeight() {
                    return 27;
                }
            });
        }
        BuiltInRegistries.FLUID.forEach(f-> {
            if (f.getBucket() != null && (!(f instanceof FlowingFluid fluid) || fluid.isSame(fluid.getSource()))) layerItems.add(f.getBucket().getDefaultInstance());
        });
        BuiltInRegistries.BLOCK.forEach(b->{
            Item i = Item.BY_BLOCK.getOrDefault(b, null);
            if (i != null) layerItems.add(i.getDefaultInstance());
        });
        scrolledList.max = layerItems.size() <= layerSelectionGrid.getContainerSize() ? 0 : layerItems.size() / layerSelectionGrid.getContainerSize();
    }
    public FlatWorldLayerSelector(Screen parent, FlatLayerInfo editLayer,Consumer<FlatWorldLayerSelector> applyLayer, int maxLayerHeight, Component component) {
        this(parent,applyLayer,maxLayerHeight,component);
        selectedLayer = new ItemStack(editLayer.getBlockState().getBlock().asItem(),editLayer.getHeight());
    }


    public FlatLayerInfo getFlatLayerInfo() {
        return new FlatLayerInfo(selectedLayer.count, selectedLayer.getItem() instanceof BlockItem b ? b.getBlock() : selectedLayer.getItem() instanceof BucketItem bucket ? bucket.arch$getFluid().defaultFluidState().createLegacyBlock().getBlock(): Blocks.AIR);
    }


    public void removed() {

    }
    public void tick() {
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    protected void init() {
        panel.init();
        fillLayerGrid();
        List<Integer> layers = IntStream.rangeClosed(1,maxLayerHeight).boxed().toList();
        addRenderableWidget(new LegacySliderButton<>(panel.x + 21, panel.y + 167, 271, 16, (b)-> Component.translatable("legacy.menu.create_flat_world.layer_height"),()-> null,(Integer) selectedLayer.count, ()-> layers, b-> selectedLayer.setCount(b.getValue())));
        addRenderableWidget(Button.builder(Component.translatable("gui.ok"), b-> {
            applyLayer.accept(this);
            onClose();
        }).bounds(panel.x + 57,panel.y + 216,200,20).build());
    }
    public void fillLayerGrid(){
        for (int i = 0; i < layerSelectionGrid.getContainerSize(); i++) {
            int index = scrolledList.get() * 50 + i;
            layerSelectionGrid.setItem(i,layerItems.size() > index ?  layerItems.get(index) : ItemStack.EMPTY);
        }
    }
    public boolean mouseScrolled(double d, double e, double f, double g) {
        int scroll = (int) -Math.signum(g);
        if (scrolledList.max > 0){
            int lastScrolled = scrolledList.get();
            scrolledList.set(Math.max(0,Math.min(scrolledList.get() + scroll, scrolledList.max)));
            if (lastScrolled != scrolledList.get()) {
                fillLayerGrid();
            }
        }
        return super.mouseScrolled(d,e,f,g);
    }

    @Override
    public boolean mouseClicked(double d, double e, int i) {
        if (hoveredSlot != null) {
            int layerCount = selectedLayer.count;
            selectedLayer = hoveredSlot.getItem().copy();
            selectedLayer.setCount(layerCount);
        }
        return super.mouseClicked(d, e, i);
    }

    public void setHoveredSlot(LegacySlotWrapper hoveredSlot) {
        this.hoveredSlot = hoveredSlot;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int i, int j, float f) {
        super.render(guiGraphics, i, j, f);
        setHoveredSlot(null);
        slots.forEach(s-> {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(panel.x, panel.y,0);
            LegacyIconHolder holder = ScreenUtil.iconHolderRenderer.slotBounds(s);
            holder.render(guiGraphics,i,j,f);
            guiGraphics.pose().translate(s.x, s.y,0);
            guiGraphics.pose().scale(holder.getSelectableWidth() / 16f,holder.getSelectableHeight() / 16f,holder.getSelectableHeight() / 16f);
            if (!s.getItem().isEmpty())
                guiGraphics.renderItem(s.getItem(), 0, 0, s.x + s.y * panel.width);
            if (i >= panel.x + s.x && i < panel.x + s.x + holder.getSelectableWidth() && j >= panel.y + s.y && j < panel.y + s.y + holder.getSelectableHeight()) {
                if (s.isHighlightable()) AbstractContainerScreen.renderSlotHighlight(guiGraphics, 0, 0, 0);
                setHoveredSlot(s);
            }
            guiGraphics.pose().popPose();
        });
        if (hoveredSlot != null && !hoveredSlot.getItem().isEmpty())
            guiGraphics.renderTooltip(font, hoveredSlot.getItem(), i, j);
    }



    @Override
    public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
        ScreenUtil.renderDefaultBackground(guiGraphics,false);
        panel.render(guiGraphics, i, j, f);
        ScreenUtil.renderPanelRecess(guiGraphics,panel.x + 20, panel. y + 187, 275, 27,2f);

        guiGraphics.drawString(this.font, this.title,panel.x + (panel.width - font.width(title)) / 2,panel. y + 8, 0x404040, false);
        Component layerCount = Component.translatable("legacy.menu.create_flat_world.layer_count", selectedLayer.count);
        guiGraphics.drawString(this.font, layerCount,panel.x + 49 - font.width(layerCount),panel. y + 197, 0xFFFFFF, true);
        guiGraphics.drawString(this.font, selectedLayer.getItem().getDescription(),panel.x + 70,panel. y + 197, 0xFFFFFF, true);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(panel.x + 50, panel.y + 190,0);
        guiGraphics.pose().scale(1.25f,1.25f,1.25f);
        guiGraphics.renderItem(selectedLayer,0, 0);
        guiGraphics.pose().popPose();

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(panel.x + 299.5, panel.y + 23, 0f);
        if (scrolledList.max > 0) {
            if (scrolledList.get() != scrolledList.max)
                guiGraphics.blitSprite(RenderableVList.SCROLL_DOWN, 0, 139, 13, 7);
            if (scrolledList.get() > 0)
                guiGraphics.blitSprite(RenderableVList.SCROLL_UP, 0, -11, 13, 7);
        }else guiGraphics.setColor(1.0f,1.0f,1.0f,0.5f);
        RenderSystem.enableBlend();
        guiGraphics.blitSprite(LegacyIconHolder.SIZEABLE_ICON_HOLDER, 0, 0,13,135);
        guiGraphics.pose().translate(-2f, -1f + (scrolledList.max > 0 ? scrolledList.get() * 121.5f / scrolledList.max : 0), 0f);
        ScreenUtil.renderPanel(guiGraphics,0,0, 16,16,3f);
        guiGraphics.setColor(1.0f,1.0f,1.0f,1.0f);
        RenderSystem.disableBlend();
        guiGraphics.pose().popPose();
    }

}
