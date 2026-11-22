package com.mekmp.client.gui;

import com.mekmp.MekMorePump;
import com.mekmp.block.entity.TieredElectricPumpBlockEntity;
import com.mekmp.menu.TieredElectricPumpMenu;
import com.mekmp.tier.PumpTier;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TieredElectricPumpScreen extends AbstractContainerScreen<TieredElectricPumpMenu> {
    // Mekanism base GUI textures
    private static final ResourceLocation MEK_BLANK_GUI = ResourceLocation.fromNamespaceAndPath("mekanism", "textures/gui/blank.png");
    private static final ResourceLocation MEK_SLOT_TEX = ResourceLocation.fromNamespaceAndPath("mekanism", "textures/gui/slot_holder.png");
    private static final ResourceLocation MEK_GAUGE_BG = ResourceLocation.fromNamespaceAndPath("mekanism", "textures/gui/gauge/standard.png");
    private static final ResourceLocation MEK_GAUGE_OVERLAY = ResourceLocation.fromNamespaceAndPath("mekanism", "textures/gui/gauge/standard_overlay.png");
    private static final ResourceLocation MEK_BAR_BG = ResourceLocation.fromNamespaceAndPath("mekanism", "textures/gui/bar/bar.png");
    private static final ResourceLocation MEK_DOWN_ARROW = ResourceLocation.fromNamespaceAndPath("mekanism", "textures/gui/progress/down.png");

    // Fallback Mekanism tab frame textures
    private static final ResourceLocation MEK_TAB_LEFT = ResourceLocation.fromNamespaceAndPath("mekanism", "textures/gui/tabs/tab_left.png");
    private static final ResourceLocation MEK_TAB_RIGHT = ResourceLocation.fromNamespaceAndPath("mekanism", "textures/gui/tabs/tab_right.png");

    // Custom tab frame textures in assets/mekmp/gui/ (optional)
    private static final ResourceLocation CUSTOM_TAB_LEFT = ResourceLocation.fromNamespaceAndPath(MekMorePump.MODID, "gui/tab_left.png");
    private static final ResourceLocation CUSTOM_TAB_RIGHT = ResourceLocation.fromNamespaceAndPath(MekMorePump.MODID, "gui/tab_right.png");

    // Custom tab icon textures in assets/mekmp/gui/
    private static final ResourceLocation ICON_INFO = ResourceLocation.fromNamespaceAndPath(MekMorePump.MODID, "gui/info.png");
    private static final ResourceLocation ICON_ENERGY_FE = ResourceLocation.fromNamespaceAndPath(MekMorePump.MODID, "gui/energy_info_fe.png");
    private static final ResourceLocation ICON_ENERGY_J = ResourceLocation.fromNamespaceAndPath(MekMorePump.MODID, "gui/energy_info_j.png");
    private static final ResourceLocation ICON_UPGRADE = ResourceLocation.fromNamespaceAndPath(MekMorePump.MODID, "gui/upgrade.png");
    private static final ResourceLocation ICON_DUMP = ResourceLocation.fromNamespaceAndPath(MekMorePump.MODID, "gui/dump.png");
    private static final ResourceLocation ICON_RS_DISABLED = ResourceLocation.fromNamespaceAndPath(MekMorePump.MODID, "gui/redstone_control_disabled.png");
    private static final ResourceLocation ICON_RS_HIGH = ResourceLocation.fromNamespaceAndPath(MekMorePump.MODID, "gui/redstone_control_high.png");
    private static final ResourceLocation ICON_RS_LOW = ResourceLocation.fromNamespaceAndPath(MekMorePump.MODID, "gui/redstone_control_low.png");
    private static final ResourceLocation ICON_RS_COMMON = ResourceLocation.fromNamespaceAndPath(MekMorePump.MODID, "gui/redstone_control.png");

    private static final int TANK_X = 8;
    private static final int TANK_Y = 16;
    private static final int TANK_W = 18;
    private static final int TANK_H = 58;

    private static final int INFO_X = 49;
    private static final int INFO_Y = 20;
    private static final int INFO_W = 88;
    private static final int INFO_H = 50;

    private static final int ENERGY_BAR_X = 162;
    private static final int ENERGY_BAR_Y = 16;
    private static final int ENERGY_BAR_W = 6;
    private static final int ENERGY_BAR_H = 58;

    private static final int TAB_W = 24;
    private static final int TAB_H = 24;

    private static final int LEFT_TAB_X = -TAB_W;
    private static final int TAB_INFO_Y = 108;
    private static final int TAB_UNIT_Y = 136;

    private static final int RIGHT_TAB_X = 176;
    private static final int TAB_UPGRADE_Y = 6;
    private static final int TAB_DUMP_Y = 34;
    private static final int TAB_REDSTONE_Y = 136;

    private final Map<ResourceLocation, Boolean> resourceCache = new HashMap<>();
    private boolean useJoules = false;

    public TieredElectricPumpScreen(TieredElectricPumpMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = 73;
    }

    private boolean hasResource(ResourceLocation loc) {
        return resourceCache.computeIfAbsent(loc,
                key -> Minecraft.getInstance().getResourceManager().getResource(key).isPresent());
    }

    private boolean blitCustomIcon(GuiGraphics g, int x, int y, int size, ResourceLocation... candidates) {
        for (ResourceLocation loc : candidates) {
            if (hasResource(loc)) {
                RenderSystem.enableBlend();
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                g.blit(loc, x, y, 0, 0, size, size, size, size);
                RenderSystem.disableBlend();
                return true;
            }
        }
        return false;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        renderSideTabs(graphics, x, y);

        if (hasResource(MEK_BLANK_GUI)) {
            graphics.blit(MEK_BLANK_GUI, x, y, 0, 0, imageWidth, imageHeight, 256, 256);
        } else {
            graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);
            graphics.renderOutline(x, y, imageWidth, imageHeight, 0xFF373737);
        }

        if (hasResource(MEK_GAUGE_BG)) {
            graphics.blit(MEK_GAUGE_BG, x + TANK_X, y + TANK_Y, 0, 0, TANK_W, TANK_H, TANK_W, TANK_H);
        } else {
            drawMekanismRecessedBox(graphics, x + TANK_X, y + TANK_Y, TANK_W, TANK_H, 0xFF7D7D7D);
        }

        int fluidAmount = menu.getFluidAmount();
        int maxFluid = menu.getMaxFluid();
        Fluid fluid = menu.getFluidType();
        if (fluidAmount > 0 && fluid != Fluids.EMPTY) {
            int innerW = TANK_W - 2;
            int innerH = TANK_H - 2;
            int fillH = Math.max(1, (int) ((double) fluidAmount / maxFluid * innerH));
            renderFluidSprite(graphics, fluid, x + TANK_X + 1, y + TANK_Y + 1 + innerH - fillH, innerW, fillH);
        }

        if (hasResource(MEK_GAUGE_OVERLAY)) {
            graphics.blit(MEK_GAUGE_OVERLAY, x + TANK_X, y + TANK_Y, 0, 0, TANK_W, TANK_H, TANK_W, TANK_H);
        } else {
            drawGaugeLines(graphics, x + TANK_X + 1, y + TANK_Y + 1, TANK_W - 2, TANK_H - 2);
        }

        int bSlotFrameX = x + TieredElectricPumpMenu.BUCKET_SLOT_X - 1;
        drawSlotFrame(graphics, bSlotFrameX, y + TieredElectricPumpMenu.BUCKET_IN_Y - 1);
        if (hasResource(MEK_DOWN_ARROW)) {
            graphics.blit(MEK_DOWN_ARROW, x + TieredElectricPumpMenu.BUCKET_SLOT_X + 3, y + 36, 0, 0, 10, 12, 10, 12);
        } else {
            drawDownArrow(graphics, x + TieredElectricPumpMenu.BUCKET_SLOT_X + 4, y + 36);
        }
        drawSlotFrame(graphics, bSlotFrameX, y + TieredElectricPumpMenu.BUCKET_OUT_Y - 1);

        drawMekanismScreenBox(graphics, x + INFO_X, y + INFO_Y, INFO_W, INFO_H);

        ResourceLocation[] ghostIds = {
                TieredElectricPumpBlockEntity.SPEED_UPGRADE_ID,
                TieredElectricPumpBlockEntity.ENERGY_UPGRADE_ID,
                TieredElectricPumpBlockEntity.FILTER_UPGRADE_ID
        };

        for (int i = 0; i < 3; i++) {
            int slotX = x + TieredElectricPumpMenu.UPGRADE_SLOT_X - 1;
            int slotY = y + TieredElectricPumpMenu.UPGRADE_SLOT_Y_START - 1 + i * 18;
            drawSlotFrame(graphics, slotX, slotY);

            if (menu.getSlot(i).getItem().isEmpty()) {
                Item ghostItem = BuiltInRegistries.ITEM.get(ghostIds[i]);
                if (ghostItem != Items.AIR) {
                    graphics.renderFakeItem(new ItemStack(ghostItem), slotX + 1, slotY + 1);
                    PoseStack pose = graphics.pose();
                    pose.pushPose();
                    pose.translate(0.0F, 0.0F, 200.0F);
                    RenderSystem.enableBlend();
                    graphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0xB88B8B8B);
                    RenderSystem.disableBlend();
                    pose.popPose();
                }
            }
        }

        drawMekanismEnergyBar(graphics, x + ENERGY_BAR_X, y + ENERGY_BAR_Y, ENERGY_BAR_W, ENERGY_BAR_H);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotFrame(graphics, x + 7 + col * 18, y + 83 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotFrame(graphics, x + 7 + col * 18, y + 141);
        }
    }

    private void renderSideTabs(GuiGraphics g, int left, int top) {
        // 1) Machine Info Tab (Bottom-Left Top): assets/mekmp/gui/info.png
        drawLeftTabFrame(g, left + LEFT_TAB_X, top + TAB_INFO_Y, TAB_W, TAB_H, 0xFFC6C6C6);
        int ix = left + LEFT_TAB_X + 3;
        int iy = top + TAB_INFO_Y + 3;
        if (!blitCustomIcon(g, ix, iy, 18, ICON_INFO)) {
            drawMekanismRecessedBox(g, ix + 1, iy + 1, 16, 16, 0xFF222622);
            g.fill(ix + 6, iy + 3, ix + 12, iy + 15, 0xFF2979FF);
            g.fill(ix + 4, iy + 4, ix + 14, iy + 14, 0xFF2979FF);
            g.fill(ix + 8, iy + 5, ix + 10, iy + 7, 0xFFFFFFFF);
            g.fill(ix + 8, iy + 8, ix + 10, iy + 13, 0xFFFFFFFF);
        }

        // 2) Energy Unit Toggle Tab (Bottom-Left Bottom): assets/mekmp/gui/energy_info_fe.png or energy_info_j.png (no text overlay)
        drawLeftTabFrame(g, left + LEFT_TAB_X, top + TAB_UNIT_Y, TAB_W, TAB_H, 0xFFC6C6C6);
        int ux = left + LEFT_TAB_X + 3;
        int uy = top + TAB_UNIT_Y + 3;
        ResourceLocation energyIcon = useJoules ? ICON_ENERGY_J : ICON_ENERGY_FE;
        if (!blitCustomIcon(g, ux, uy, 18, energyIcon)) {
            drawMekanismRecessedBox(g, ux + 1, uy + 1, 16, 16, 0xFF1F261F);
        }

        // 3) Upgrade Tab (Top-Right Top): assets/mekmp/gui/upgrade.png
        drawRightTabFrame(g, left + RIGHT_TAB_X, top + TAB_UPGRADE_Y, TAB_W, TAB_H, 0xFFC6C6C6);
        int upX = left + RIGHT_TAB_X + 3;
        int upY = top + TAB_UPGRADE_Y + 3;
        if (!blitCustomIcon(g, upX, upY, 18, ICON_UPGRADE)) {
            drawMekanismRecessedBox(g, upX + 1, upY + 1, 16, 16, 0xFF8B8B8B);
            g.fill(upX + 8, upY + 3, upX + 10, upY + 14, 0xFF2D2D2D);
            g.fill(upX + 6, upY + 5, upX + 12, upY + 7, 0xFF2D2D2D);
            g.fill(upX + 5, upY + 7, upX + 13, upY + 8, 0xFF2D2D2D);
        }

        // 4) Dump Tank Tab (Top-Right Middle): assets/mekmp/gui/dump.png
        drawRightTabFrame(g, left + RIGHT_TAB_X, top + TAB_DUMP_Y, TAB_W, TAB_H, 0xFFC87D4B);
        int dx = left + RIGHT_TAB_X + 3;
        int dy = top + TAB_DUMP_Y + 3;
        if (!blitCustomIcon(g, dx, dy, 18, ICON_DUMP)) {
            drawMekanismRecessedBox(g, dx + 1, dy + 1, 16, 16, 0xFF8B8B8B);
            g.fill(dx + 5, dy + 4, dx + 13, dy + 5, 0xFF2D2D2D);
            g.fill(dx + 7, dy + 3, dx + 11, dy + 4, 0xFF2D2D2D);
            g.fill(dx + 6, dy + 6, dx + 12, dy + 14, 0xFF2D2D2D);
        }

        // 5) Redstone Control Tab (Bottom-Right): assets/mekmp/gui/redstone_control_*.png
        drawRightTabFrame(g, left + RIGHT_TAB_X, top + TAB_REDSTONE_Y, TAB_W, TAB_H, 0xFFC93B3B);
        int rx = left + RIGHT_TAB_X + 3;
        int ry = top + TAB_REDSTONE_Y + 3;
        TieredElectricPumpBlockEntity.RedstoneMode mode = menu.getRedstoneMode();
        boolean rsRendered = switch (mode) {
            case IGNORED -> blitCustomIcon(g, rx, ry, 18, ICON_RS_DISABLED, ICON_RS_COMMON);
            case HIGH -> blitCustomIcon(g, rx, ry, 18, ICON_RS_HIGH, ICON_RS_COMMON);
            case LOW -> blitCustomIcon(g, rx, ry, 18, ICON_RS_LOW, ICON_RS_COMMON);
        };
        if (!rsRendered) {
            drawMekanismRecessedBox(g, rx + 1, ry + 1, 16, 16, 0xFF8B8B8B);
            g.fill(rx + 8, ry + 7, rx + 10, ry + 14, 0xFF5C3A21);
            int torchColor = switch (mode) {
                case IGNORED -> 0xFF4A4A4A;
                case HIGH -> 0xFFFF2222;
                case LOW -> 0xFF881111;
            };
            g.fill(rx + 7, ry + 3, rx + 11, ry + 7, torchColor);
        }
    }

    private void drawLeftTabFrame(GuiGraphics g, int x, int y, int w, int h, int bgColor) {
        ResourceLocation tabLeft = hasResource(CUSTOM_TAB_LEFT) ? CUSTOM_TAB_LEFT : (hasResource(MEK_TAB_LEFT) ? MEK_TAB_LEFT : null);
        if (tabLeft != null) {
            float r = ((bgColor >> 16) & 0xFF) / 255.0F;
            float gr = ((bgColor >> 8) & 0xFF) / 255.0F;
            float b = (bgColor & 0xFF) / 255.0F;
            RenderSystem.setShaderColor(r, gr, b, 1.0F);
            g.blit(tabLeft, x, y, 0, 0, w, h, w, h);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            return;
        }
        g.fill(x + 2, y, x + w, y + h, bgColor);
        g.fill(x, y + 2, x + 2, y + h - 2, bgColor);
        g.fill(x + 2, y, x + w, y + 1, 0xFFFFFFFF);
        g.fill(x, y + 2, x + 1, y + h - 2, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 2, y + 2, 0xFFFFFFFF);
        g.fill(x + 2, y + h - 1, x + w, y + h, 0xFF373737);
        g.fill(x + 1, y + h - 2, x + 2, y + h - 1, 0xFF373737);
    }

    private void drawRightTabFrame(GuiGraphics g, int x, int y, int w, int h, int bgColor) {
        ResourceLocation tabRight = hasResource(CUSTOM_TAB_RIGHT) ? CUSTOM_TAB_RIGHT : (hasResource(MEK_TAB_RIGHT) ? MEK_TAB_RIGHT : null);
        if (tabRight != null) {
            float r = ((bgColor >> 16) & 0xFF) / 255.0F;
            float gr = ((bgColor >> 8) & 0xFF) / 255.0F;
            float b = (bgColor & 0xFF) / 255.0F;
            RenderSystem.setShaderColor(r, gr, b, 1.0F);
            g.blit(tabRight, x, y, 0, 0, w, h, w, h);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            return;
        }
        g.fill(x, y, x + w - 2, y + h, bgColor);
        g.fill(x + w - 2, y + 2, x + w, y + h - 2, bgColor);
        g.fill(x, y, x + w - 2, y + 1, 0xFFFFFFFF);
        g.fill(x + w - 2, y + 1, x + w - 1, y + 2, 0xFFFFFFFF);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, 0xFF373737);
        g.fill(x, y + h - 1, x + w - 2, y + h, 0xFF373737);
        g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, 0xFF373737);
    }

    private void drawDownArrow(GuiGraphics g, int x, int y) {
        g.fill(x + 3, y, x + 6, y + 7, 0xFF8B8B8B);
        g.fill(x, y + 7, x + 9, y + 8, 0xFF8B8B8B);
        g.fill(x + 1, y + 8, x + 8, y + 9, 0xFF8B8B8B);
        g.fill(x + 2, y + 9, x + 7, y + 10, 0xFF8B8B8B);
        g.fill(x + 3, y + 10, x + 6, y + 11, 0xFF8B8B8B);
        g.fill(x + 4, y + 11, x + 5, y + 12, 0xFF8B8B8B);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int titleX = (this.imageWidth - this.font.width(this.title)) / 2;
        graphics.drawString(this.font, this.title, titleX, 6, 0x404040, false);
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);

        int maxTextW = INFO_W - 8;
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);

        String energyLine = formatEnergyCompact(menu.getEnergy(), useJoules) + "/" + formatEnergyCompact(menu.getMaxEnergy(), useJoules);
        drawScaledScreenText(graphics, Component.literal(energyLine), INFO_X + 4, INFO_Y + 8, maxTextW, 0.85F, 0xFF3CE64A);

        Component fluidLine;
        if (menu.getFluidAmount() <= 0 || menu.getFluidType() == Fluids.EMPTY) {
            fluidLine = Component.translatable("gui.mekmp.no_fluid");
        } else {
            Component fluidName = new FluidStack(menu.getFluidType(), 1).getHoverName();
            fluidLine = Component.literal(fluidName.getString() + ": " + nf.format(menu.getFluidAmount()) + " mB");
        }
        drawScaledScreenText(graphics, fluidLine, INFO_X + 4, INFO_Y + 21, maxTextW, 0.78F, 0xFF3CE64A);

        int st = menu.getStatus();
        String statusKey = switch (st) {
            case TieredElectricPumpBlockEntity.STATUS_NO_ENERGY -> "gui.mekmp.status.no_energy";
            case TieredElectricPumpBlockEntity.STATUS_TANK_FULL -> "gui.mekmp.status.tank_full";
            case TieredElectricPumpBlockEntity.STATUS_REDSTONE -> "gui.mekmp.status.redstone";
            case TieredElectricPumpBlockEntity.STATUS_IDLE -> "gui.mekmp.status.idle";
            default -> "gui.mekmp.status.running";
        };
        int statusColor = switch (st) {
            case TieredElectricPumpBlockEntity.STATUS_RUNNING -> 0xFF3CE64A;
            case TieredElectricPumpBlockEntity.STATUS_IDLE -> 0xFFB8E63C;
            case TieredElectricPumpBlockEntity.STATUS_TANK_FULL -> 0xFFFFAA00;
            default -> 0xFFFF5555;
        };
        String usageStr = useJoules
                ? nf.format(Math.round(menu.getEnergyUsageFe() * 2.5)) + " J/t"
                : nf.format(menu.getEnergyUsageFe()) + " FE/t";
        String statusText = Component.translatable(statusKey).getString() + " (" + usageStr + ")";
        drawScaledScreenText(graphics, Component.literal(statusText), INFO_X + 4, INFO_Y + 33, maxTextW, 0.75F, statusColor);
    }

    private void drawScaledScreenText(GuiGraphics graphics, Component text, int x, int y, int maxWidth, float preferredScale, int color) {
        int rawWidth = this.font.width(text);
        float scale = preferredScale;
        if (rawWidth * scale > maxWidth && rawWidth > 0) {
            scale = (float) maxWidth / (float) rawWidth;
        }
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(scale, scale, 1.0F);
        graphics.drawString(this.font, text, 0, 0, color, false);
        pose.popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (isHovering(LEFT_TAB_X, TAB_UNIT_Y, TAB_W, TAB_H, mouseX, mouseY)) {
                useJoules = !useJoules;
                playClickSound();
                return true;
            }
            if (isHovering(RIGHT_TAB_X, TAB_DUMP_Y, TAB_W, TAB_H, mouseX, mouseY)) {
                if (this.minecraft != null && this.minecraft.gameMode != null) {
                    this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, TieredElectricPumpMenu.BTN_DUMP_TANK);
                    playClickSound();
                }
                return true;
            }
            if (isHovering(RIGHT_TAB_X, TAB_REDSTONE_Y, TAB_W, TAB_H, mouseX, mouseY)) {
                if (this.minecraft != null && this.minecraft.gameMode != null) {
                    this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, TieredElectricPumpMenu.BTN_CYCLE_REDSTONE);
                    playClickSound();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);

        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);

        if (isHovering(TANK_X, TANK_Y, TANK_W, TANK_H, mouseX, mouseY)) {
            Component fluidName = (menu.getFluidAmount() <= 0 || menu.getFluidType() == Fluids.EMPTY)
                    ? Component.translatable("gui.mekmp.no_fluid")
                    : new FluidStack(menu.getFluidType(), 1).getHoverName();
            graphics.renderTooltip(this.font,
                    Component.literal(fluidName.getString() + ": " + nf.format(menu.getFluidAmount()) + " / " + nf.format(menu.getMaxFluid()) + " mB"),
                    mouseX, mouseY);
        } else if (isHovering(ENERGY_BAR_X, ENERGY_BAR_Y, ENERGY_BAR_W, ENERGY_BAR_H, mouseX, mouseY)) {
            String text = useJoules
                    ? nf.format((long) (menu.getEnergy() * 2.5)) + " / " + nf.format((long) (menu.getMaxEnergy() * 2.5)) + " J"
                    : nf.format(menu.getEnergy()) + " / " + nf.format(menu.getMaxEnergy()) + " FE";
            graphics.renderTooltip(this.font, Component.literal(text), mouseX, mouseY);
        } else if (isHovering(TieredElectricPumpMenu.UPGRADE_SLOT_X, TieredElectricPumpMenu.UPGRADE_SLOT_Y_START, 16, 16, mouseX, mouseY)
                && menu.getSlot(0).getItem().isEmpty()) {
            graphics.renderTooltip(this.font, Component.translatable("gui.mekmp.slot.speed"), mouseX, mouseY);
        } else if (isHovering(TieredElectricPumpMenu.UPGRADE_SLOT_X, TieredElectricPumpMenu.UPGRADE_SLOT_Y_START + 18, 16, 16, mouseX, mouseY)
                && menu.getSlot(1).getItem().isEmpty()) {
            graphics.renderTooltip(this.font, Component.translatable("gui.mekmp.slot.energy"), mouseX, mouseY);
        } else if (isHovering(TieredElectricPumpMenu.UPGRADE_SLOT_X, TieredElectricPumpMenu.UPGRADE_SLOT_Y_START + 36, 16, 16, mouseX, mouseY)
                && menu.getSlot(2).getItem().isEmpty()) {
            graphics.renderTooltip(this.font, Component.translatable("gui.mekmp.slot.filter"), mouseX, mouseY);
        } else if (isHovering(TieredElectricPumpMenu.BUCKET_SLOT_X, TieredElectricPumpMenu.BUCKET_IN_Y, 16, 16, mouseX, mouseY)
                && menu.getSlot(3).getItem().isEmpty()) {
            graphics.renderTooltip(this.font, Component.translatable("gui.mekmp.slot.bucket_in"), mouseX, mouseY);
        } else if (isHovering(TieredElectricPumpMenu.BUCKET_SLOT_X, TieredElectricPumpMenu.BUCKET_OUT_Y, 16, 16, mouseX, mouseY)
                && menu.getSlot(4).getItem().isEmpty()) {
            graphics.renderTooltip(this.font, Component.translatable("gui.mekmp.slot.bucket_out"), mouseX, mouseY);
        } else if (isHovering(LEFT_TAB_X, TAB_INFO_Y, TAB_W, TAB_H, mouseX, mouseY)) {
            PumpTier tier = menu.getPumpTier();
            int mult = tier.getPumpMultiplier();
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.mekmp.tab.info"));
            lines.add(Component.translatable("gui.mekmp.tab.info.amount", nf.format(1000L * mult), mult));
            lines.add(Component.translatable("gui.mekmp.tab.info.ticks", tier.getTicksPerOperation()));
            lines.add(Component.translatable("gui.mekmp.tab.info.capacity", nf.format(tier.getTankCapacity())));
            lines.add(Component.translatable("gui.mekmp.tab.info.pipe_dependent"));
            graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
        } else if (isHovering(LEFT_TAB_X, TAB_UNIT_Y, TAB_W, TAB_H, mouseX, mouseY)) {
            graphics.renderTooltip(this.font,
                    Component.translatable("gui.mekmp.tab.unit", useJoules ? "J (Joules)" : "FE (Forge Energy)"),
                    mouseX, mouseY);
        } else if (isHovering(RIGHT_TAB_X, TAB_UPGRADE_Y, TAB_W, TAB_H, mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.mekmp.tab.upgrades"));
            lines.add(Component.translatable("gui.mekmp.tab.upgrades.speed", menu.getSlot(0).getItem().getCount()));
            lines.add(Component.translatable("gui.mekmp.tab.upgrades.energy", menu.getSlot(1).getItem().getCount()));
            lines.add(Component.translatable("gui.mekmp.tab.upgrades.filter", menu.getSlot(2).getItem().getCount()));
            graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
        } else if (isHovering(RIGHT_TAB_X, TAB_DUMP_Y, TAB_W, TAB_H, mouseX, mouseY)) {
            graphics.renderTooltip(this.font, Component.translatable("gui.mekmp.tab.dump"), mouseX, mouseY);
        } else if (isHovering(RIGHT_TAB_X, TAB_REDSTONE_Y, TAB_W, TAB_H, mouseX, mouseY)) {
            String modeKey = switch (menu.getRedstoneMode()) {
                case IGNORED -> "gui.mekmp.redstone.ignored";
                case HIGH -> "gui.mekmp.redstone.high";
                case LOW -> "gui.mekmp.redstone.low";
            };
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.mekmp.tab.redstone", Component.translatable(modeKey)));
            lines.add(Component.translatable("gui.mekmp.tab.click_toggle"));
            graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
        }
    }

    private static String formatEnergyCompact(int fe, boolean joules) {
        double val = joules ? (fe * 2.5) : fe;
        String suffix = joules ? "J" : "FE";
        if (val >= 1_000_000_000) {
            return String.format(Locale.US, "%.1f G%s", val / 1_000_000_000.0, suffix);
        }
        if (val >= 1_000_000) {
            return String.format(Locale.US, "%.1f M%s", val / 1_000_000.0, suffix);
        }
        if (val >= 1_000) {
            return String.format(Locale.US, "%.1f k%s", val / 1_000.0, suffix);
        }
        return String.format(Locale.US, "%.1f %s", val, suffix);
    }

    private void drawSlotFrame(GuiGraphics g, int x, int y) {
        if (hasResource(MEK_SLOT_TEX)) {
            g.blit(MEK_SLOT_TEX, x, y, 0, 0, 18, 18, 18, 18);
        } else {
            drawMekanismRecessedBox(g, x, y, 18, 18, 0xFF8B8B8B);
        }
    }

    private void drawMekanismRecessedBox(GuiGraphics g, int x, int y, int w, int h, int bgColor) {
        g.fill(x, y, x + w, y + h, bgColor);
        g.fill(x, y, x + w - 1, y + 1, 0xFF373737);
        g.fill(x, y, x + 1, y + h - 1, 0xFF373737);
        g.fill(x + 1, y + h - 1, x + w, y + h, 0xFFFFFFFF);
        g.fill(x + w - 1, y + 1, x + w, y + h, 0xFFFFFFFF);
    }

    private void drawMekanismScreenBox(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF1A1E1A);
        g.fill(x, y, x + w, y + 1, 0xFF101210);
        g.fill(x, y, x + 1, y + h, 0xFF101210);
        g.fill(x + 1, y + h - 1, x + w, y + h, 0xFF555555);
        g.fill(x + w - 1, y + 1, x + w, y + h, 0xFF555555);
    }

    private void drawMekanismEnergyBar(GuiGraphics g, int x, int y, int w, int h) {
        if (hasResource(MEK_BAR_BG)) {
            g.blit(MEK_BAR_BG, x, y, 0, 0, w, h, w, h);
        } else {
            drawMekanismRecessedBox(g, x, y, w, h, 0xFF555555);
        }

        int innerX = x + 1;
        int innerY = y + 1;
        int innerW = w - 2;
        int innerH = h - 2;

        for (int dy = 0; dy < innerH; dy++) {
            for (int dx = 0; dx < innerW; dx++) {
                boolean stripe = ((dx + dy) / 3) % 2 == 0;
                g.fill(innerX + dx, innerY + dy, innerX + dx + 1, innerY + dy + 1,
                        stripe ? 0xFFC7B238 : 0xFF5A5A5A);
            }
        }

        int fillH = (int) ((double) menu.getEnergy() / menu.getMaxEnergy() * innerH);
        if (fillH > 0) {
            g.fillGradient(innerX, innerY + innerH - fillH, innerX + innerW, innerY + innerH,
                    0xFF45F265, 0xFF229E38);
        }
    }

    private void drawGaugeLines(GuiGraphics g, int x, int y, int w, int h) {
        for (int i = 1; i <= 9; i++) {
            int lineY = y + Math.round((float) (h * i) / 10.0F);
            int lineW = (i == 5) ? w : (w / 2);
            g.fill(x, lineY, x + lineW, lineY + 1, 0xFF2B2B2B);
        }
    }

    private void renderFluidSprite(GuiGraphics graphics, Fluid fluid, int x, int y, int width, int height) {
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid);
        ResourceLocation stillTex = ext.getStillTexture();
        if (stillTex == null) return;

        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(stillTex);
        int tint = ext.getTintColor();
        float a = ((tint >> 24) & 0xFF) / 255.0F;
        float r = ((tint >> 16) & 0xFF) / 255.0F;
        float g = ((tint >> 8) & 0xFF) / 255.0F;
        float b = (tint & 0xFF) / 255.0F;
        if (a <= 0.0F) a = 1.0F;

        RenderSystem.setShaderColor(r, g, b, a);
        for (int drawY = y + height; drawY > y; drawY -= 16) {
            int sliceH = Math.min(16, drawY - y);
            graphics.blit(x, drawY - sliceH, 0, width, sliceH, sprite);
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
