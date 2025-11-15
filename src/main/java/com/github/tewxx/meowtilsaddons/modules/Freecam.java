package com.github.tewxx.meowtilsaddons.modules;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import wtf.tatp.meowtils.gui.Module;
import wtf.tatp.meowtils.modules.advanced.AntiBot;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Freecam with block-face teleportation and phasing utilities via hotbar items.
 */
public class Freecam extends Module {
    public static volatile Freecam INSTANCE;

    private static final float[] SPEED_STEPS = new float[]{1.0F, 2.0F, 3.0F, 4.0F, 5.0F, 10.0F, 25.0F};
    private static final double BASE_MOVE = 0.35;
    private static final double RAYTRACE_RANGE = 180.0;

    private double originX;
    private double originY;
    private double originZ;
    private float originYaw;
    private float originPitch;
    private boolean originAllowFlying;
    private boolean originFlying;
    private float originFlySpeed;
    private float originWalkSpeed;
    private int storedHotbarSlot;
    private final ItemStack[] storedHotbar = new ItemStack[9];
    private boolean storedOrigin;
    private boolean hotbarInjected;

    private ItemStack speedItem;
    private ItemStack teleportItem;
    private ItemStack phaseItem;
    private ItemStack compassItem;

    private int speedIndex;
    private int lastHurtTime;

    private BlockPos previewBlock;
    private EnumFacing previewFace;
    private PreviewType previewType;

    private int playerIndex = -1;
    private final List<EntityPlayer> cachedPlayers = new ArrayList<>();

    private static Field flySpeedField;
    private static Field walkSpeedField;
    private static Field remainingHighlightField;

    public Freecam() {
        super("Freecam", "freecamKey", "freecam", Module.Category.Advanced);
        INSTANCE = this;
        try {
            this.tooltip("Freeze server-side position while exploring client-side.\n"
                    + "Sugar=Speed | Blaze Rod=Teleport | Shears=Phase | Compass=Player cycle");
        } catch (Throwable ignored) {}
    }

    @Override
    public void onEnable() {
        if (this.mc == null || this.mc.thePlayer == null || this.mc.theWorld == null) {
            this.setState(false);
            return;
        }
        storeOrigin();
        copyHotbar();
        giveControlItems();
        this.speedIndex = 0;
        updateSpeedName();
        this.lastHurtTime = this.mc.thePlayer.hurtTime;
        this.previewBlock = null;
        this.previewFace = null;
        this.previewType = null;
        this.playerIndex = -1;
    }

    @Override
    public void onDisable() {
        restorePlayerState();
        restoreHotbar();
        this.previewBlock = null;
        this.previewFace = null;
        this.previewType = null;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!this.getState()) return;
        if (this.mc == null || this.mc.thePlayer == null || this.mc.theWorld == null) {
            failSafeDisable();
            return;
        }

        EntityPlayerSP player = this.mc.thePlayer;
        if (!storedOrigin) storeOrigin();
        if (player.isDead) { failSafeDisable(); return; }

        player.noClip = true;
        player.capabilities.isFlying = true;
        player.capabilities.allowFlying = true;
        player.motionY = 0.0;
        player.fallDistance = 0.0F;
        int slot = Math.max(0, Math.min(speedIndex, SPEED_STEPS.length - 1));
        double speed = BASE_MOVE * SPEED_STEPS[slot];

        float forward = player.movementInput.moveForward;
        float strafe = player.movementInput.moveStrafe;
        if (forward == 0.0F && strafe == 0.0F) {
            player.motionX = 0.0;
            player.motionZ = 0.0;
        } else {
            float yaw = player.rotationYaw;
            if (forward != 0.0F) {
                if (strafe > 0.0F) yaw += (forward > 0.0F ? -45.0F : 45.0F);
                else if (strafe < 0.0F) yaw += (forward > 0.0F ? 45.0F : -45.0F);
                strafe = 0.0F;
                forward = forward > 0.0F ? 1.0F : -1.0F;
            }
            if (strafe != 0.0F) strafe = strafe > 0.0F ? 1.0F : -1.0F;
            double rad = Math.toRadians(yaw + 90.0F);
            player.motionX = forward * speed * Math.cos(rad) + strafe * speed * Math.sin(rad);
            player.motionZ = forward * speed * Math.sin(rad) - strafe * speed * Math.cos(rad);
        }
        if (player.movementInput.jump) player.motionY += speed;
        if (player.movementInput.sneak) player.motionY -= speed;

        float step = SPEED_STEPS[slot];
        setCapabilitySpeed(player.capabilities, true, originFlySpeed * step);
        setCapabilitySpeed(player.capabilities, false, originWalkSpeed * step);
        player.jumpMovementFactor = (float) (speed * 0.3F);
        player.onGround = false;
        player.setSprinting(false);

        if (player.hurtTime > 0 && lastHurtTime == 0) {
            failSafeDisable();
            return;
        }
        lastHurtTime = player.hurtTime;

        // Re-assert control items in the first 4 slots if server/client inventory updates overwrite them
        ensureControlItems();
        updatePreview();
    }

    @SubscribeEvent
    public void onMouseInput(MouseEvent event) {
        if (!this.getState()) return;
        if (this.mc == null || this.mc.thePlayer == null) return;
        if (this.mc.currentScreen != null) return;
        if (!event.buttonstate) return;
        int button = event.button;
        if (button != 0 && button != 1) return;
        event.setCanceled(true);
        ItemStack held = this.mc.thePlayer.getHeldItem();
        if (button == 0) {
            if (held != null && isCompass(held)) cyclePlayer(false);
            return;
        }
        if (held == null) return;
        if (isSpeedSugar(held)) cycleSpeed();
        else if (isTeleportStick(held)) performTeleport();
        else if (isPhaseTool(held)) performPhase();
        else if (isCompass(held)) cyclePlayer(true);
    }

    @SubscribeEvent
    public void onOverlayPre(RenderGameOverlayEvent.Pre event) {
        if (!this.getState()) return;
        if (event.type != RenderGameOverlayEvent.ElementType.HOTBAR) return;
        if (this.mc == null || this.mc.thePlayer == null || this.mc.ingameGUI == null) return;
        ItemStack held = this.mc.thePlayer.getHeldItem();
        if (held == null || !isControlItem(held)) return;
        try {
            if (remainingHighlightField == null) {
                remainingHighlightField = net.minecraft.client.gui.GuiIngame.class.getDeclaredField("remainingHighlightTicks");
                remainingHighlightField.setAccessible(true);
            }
            remainingHighlightField.setInt(this.mc.ingameGUI, 0);
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public void onRenderHotbarName(RenderGameOverlayEvent.Post event) {
        if (!this.getState()) return;
        if (event.type != RenderGameOverlayEvent.ElementType.HOTBAR) return;
        if (this.mc == null || this.mc.thePlayer == null || this.mc.fontRendererObj == null) return;
        ItemStack held = this.mc.thePlayer.getHeldItem();
        if (held == null || !isControlItem(held)) return;
        String name = held.getDisplayName();
        ScaledResolution sr = new ScaledResolution(this.mc);
        int width = this.mc.fontRendererObj.getStringWidth(name);
        int x = (sr.getScaledWidth() - width) / 2;
        int y = sr.getScaledHeight() - 59;
        GlStateManager.pushMatrix();
        this.mc.fontRendererObj.drawStringWithShadow(name, x, y, 0xFFFFFF);
        GlStateManager.popMatrix();
    }

    @SubscribeEvent
    public void onFovUpdate(FOVUpdateEvent event) {
        if (!this.getState()) return;
        event.newfov = 1.0F;
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!this.getState()) return;
        if (this.previewBlock == null || this.previewFace == null || this.previewType == null) return;
        if (this.mc == null || this.mc.getRenderManager() == null) return;
        AxisAlignedBB bb = buildFaceBoundingBox(this.previewBlock, this.previewFace);
        if (bb == null) return;
        double px = this.mc.getRenderManager().viewerPosX;
        double py = this.mc.getRenderManager().viewerPosY;
        double pz = this.mc.getRenderManager().viewerPosZ;
        bb = bb.offset(-px, -py, -pz);
        float r = this.previewType == PreviewType.PHASE ? 0.8F : 1.0F;
        float g = this.previewType == PreviewType.PHASE ? 0.3F : 0.8F;
        float b = this.previewType == PreviewType.PHASE ? 1.0F : 0.3F;
        renderFaceOverlay(bb, r, g, b);
    }

    private void storeOrigin() {
        if (this.mc == null || this.mc.thePlayer == null) return;
        EntityPlayerSP player = this.mc.thePlayer;
        this.originX = player.posX;
        this.originY = player.posY;
        this.originZ = player.posZ;
        this.originYaw = player.rotationYaw;
        this.originPitch = player.rotationPitch;
        this.originAllowFlying = player.capabilities.allowFlying;
        this.originFlying = player.capabilities.isFlying;
        this.originFlySpeed = getCapabilitySpeed(player.capabilities, true, 0.05F);
        this.originWalkSpeed = getCapabilitySpeed(player.capabilities, false, 0.1F);
        this.storedHotbarSlot = player.inventory.currentItem;
        this.storedOrigin = true;
    }

    private void restorePlayerState() {
        if (!this.storedOrigin || this.mc == null || this.mc.thePlayer == null) return;
        EntityPlayerSP player = this.mc.thePlayer;
        player.noClip = false;
        player.capabilities.isFlying = this.originFlying;
        player.capabilities.allowFlying = this.originAllowFlying;
        setCapabilitySpeed(player.capabilities, true, this.originFlySpeed);
        setCapabilitySpeed(player.capabilities, false, this.originWalkSpeed);
        player.motionX = player.motionY = player.motionZ = 0.0;
        player.setPositionAndRotation(this.originX, this.originY, this.originZ, this.originYaw, this.originPitch);
        player.rotationYawHead = this.originYaw;
        player.inventory.currentItem = Math.max(0, Math.min(8, this.storedHotbarSlot));
        this.storedOrigin = false;
    }

    private void copyHotbar() {
        if (this.mc == null || this.mc.thePlayer == null) return;
        ItemStack[] inv = this.mc.thePlayer.inventory.mainInventory;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv[i];
            storedHotbar[i] = stack == null ? null : stack.copy();
            inv[i] = null;
        }
        this.hotbarInjected = true;
        this.mc.thePlayer.inventory.markDirty();
    }

    private void restoreHotbar() {
        if (!this.hotbarInjected || this.mc == null || this.mc.thePlayer == null) return;
        ItemStack[] inv = this.mc.thePlayer.inventory.mainInventory;
        for (int i = 0; i < 9; i++) {
            inv[i] = storedHotbar[i];
            storedHotbar[i] = null;
        }
        this.mc.thePlayer.inventory.markDirty();
        this.hotbarInjected = false;
        this.speedItem = null;
        this.teleportItem = null;
        this.phaseItem = null;
        this.compassItem = null;
    }

    private void giveControlItems() {
        if (this.mc == null || this.mc.thePlayer == null) return;
        ItemStack[] inv = this.mc.thePlayer.inventory.mainInventory;
        this.speedItem = createItem(Items.sugar, currentSpeedName());
        this.teleportItem = createItem(Items.blaze_rod, "&6Teleport Stick");
        this.phaseItem = createItem(Items.shears, "&dPhasing Tool");
        this.compassItem = createItem(Items.compass, "&fPlayer: &7None");
        inv[0] = this.speedItem;
        inv[1] = this.teleportItem;
        inv[2] = this.phaseItem;
        inv[3] = this.compassItem;
        for (int i = 4; i < 9; i++) inv[i] = null;
        this.mc.thePlayer.inventory.markDirty();
        this.mc.thePlayer.inventory.currentItem = 0;
    }

    private void ensureControlItems() {
        if (this.mc == null || this.mc.thePlayer == null) return;
        ItemStack[] inv = this.mc.thePlayer.inventory.mainInventory;
        boolean changed = false;
        // Slot 0: speed
        if (!hasToolMarker(inv[0], "speed")) { inv[0] = this.speedItem = createItem(Items.sugar, currentSpeedName()); changed = true; }
        else { renameItem(inv[0], currentSpeedName()); }
        // Slot 1: teleport
        if (!hasToolMarker(inv[1], "teleport")) { inv[1] = this.teleportItem = createItem(Items.blaze_rod, "&6Teleport Stick"); changed = true; }
        // Slot 2: phase
        if (!hasToolMarker(inv[2], "phase")) { inv[2] = this.phaseItem = createItem(Items.shears, "&dPhasing Tool"); changed = true; }
        // Slot 3: compass
        if (!hasToolMarker(inv[3], "compass")) { inv[3] = this.compassItem = createItem(Items.compass, "&fPlayer: &7None"); changed = true; }
        if (changed) this.mc.thePlayer.inventory.markDirty();
    }

    private ItemStack createItem(Item item, String displayName) {
        ItemStack stack = new ItemStack(item);
        renameItem(stack, displayName);
        // Tag as Freecam control tool so detection survives inventory updates
        setToolMarker(stack, toolIdForItem(item));
        return stack;
    }

    private void renameItem(ItemStack stack, String text) {
        if (stack == null) return;
        stack.setStackDisplayName(EnumChatFormatting.RESET + text.replace('&', '\u00A7'));
    }

    private boolean isSpeedSugar(ItemStack stack) { return hasToolMarker(stack, "speed"); }
    private boolean isTeleportStick(ItemStack stack) { return hasToolMarker(stack, "teleport"); }
    private boolean isPhaseTool(ItemStack stack) { return hasToolMarker(stack, "phase"); }
    private boolean isCompass(ItemStack stack) { return hasToolMarker(stack, "compass"); }
    private boolean isControlItem(ItemStack stack) {
        return stack != null && (isSpeedSugar(stack) || isTeleportStick(stack) || isPhaseTool(stack) || isCompass(stack));
    }

    private static final String TOOL_TAG = "MeowAddonsFreecamTool";
    private static void setToolMarker(ItemStack stack, String id) {
        try {
            if (stack == null || id == null) return;
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null) tag = new NBTTagCompound();
            tag.setString(TOOL_TAG, id);
            stack.setTagCompound(tag);
        } catch (Throwable ignored) {}
    }
    private static boolean hasToolMarker(ItemStack stack, String id) {
        try {
            if (stack == null) return false;
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null) return false;
            String v = tag.getString(TOOL_TAG);
            if (v == null) return false;
            return v.equals(id);
        } catch (Throwable ignored) { return false; }
    }
    private static String toolIdForItem(Item item) {
        if (item == Items.sugar) return "speed";
        if (item == Items.blaze_rod) return "teleport";
        if (item == Items.shears) return "phase";
        if (item == Items.compass) return "compass";
        return "unknown";
    }

    private void cycleSpeed() {
        this.speedIndex = (this.speedIndex + 1) % SPEED_STEPS.length;
        updateSpeedName();
    }

    private void updateSpeedName() {
        if (this.speedItem != null) {
            renameItem(this.speedItem, currentSpeedName());
        }
    }

    private String currentSpeedName() {
        float mult = SPEED_STEPS[Math.max(0, Math.min(speedIndex, SPEED_STEPS.length - 1))];
        return String.format(Locale.ROOT, "&fSpeed: &b%.1fx", mult);
    }

    private void performTeleport() {
        if (this.mc == null || this.mc.thePlayer == null) return;
        MovingObjectPosition hit = this.mc.thePlayer.rayTrace(RAYTRACE_RANGE, 1.0F);
        Vec3 dst = computeTeleportDestination(hit);
        if (dst != null) movePlayer(dst);
    }

    private void performPhase() {
        if (this.mc == null || this.mc.thePlayer == null) return;
        MovingObjectPosition hit = this.mc.thePlayer.rayTrace(RAYTRACE_RANGE, 1.0F);
        Vec3 dst = computePhaseDestination(hit);
        if (dst != null) movePlayer(dst);
    }

    private Vec3 computeTeleportDestination(MovingObjectPosition hit) {
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return null;
        BlockPos pos = hit.getBlockPos();
        EnumFacing face = hit.sideHit;
        if (face == null) return null;
        double x = pos.getX() + 0.5 + face.getFrontOffsetX() * 0.501;
        double z = pos.getZ() + 0.5 + face.getFrontOffsetZ() * 0.501;
        double y = face == EnumFacing.UP ? pos.getY() + 1.02 : (face == EnumFacing.DOWN ? pos.getY() - 0.02 : hit.hitVec.yCoord);
        return new Vec3(x, y, z);
    }

    private Vec3 computePhaseDestination(MovingObjectPosition hit) {
        if (this.mc == null || this.mc.thePlayer == null || this.mc.theWorld == null) return null;
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return null;
        EntityPlayerSP player = this.mc.thePlayer;
        BlockPos current = hit.getBlockPos();
        EnumFacing face = hit.sideHit;
        if (face == null) return null;
        boolean passedSolid = false;
        for (int i = 0; i < 12; i++) {
            current = current.offset(face);
            if (!isPassable(current)) {
                passedSolid = true;
                continue;
            }
            if (!passedSolid || !canStandAt(current)) continue;
            double x = current.getX() + 0.5;
            double z = current.getZ() + 0.5;
            AxisAlignedBB bb = player.getEntityBoundingBox().offset(x - player.posX, 0.0, z - player.posZ);
            if (this.mc.theWorld.getCollidingBoundingBoxes(player, bb).isEmpty()) {
                return new Vec3(x, player.posY, z);
            }
        }
        return null;
    }

    private boolean isPassable(BlockPos pos) {
        return this.mc != null && this.mc.theWorld != null && this.mc.theWorld.isAirBlock(pos);
    }

    private void updatePreview() {
        this.previewBlock = null;
        this.previewFace = null;
        this.previewType = null;
        if (this.mc == null || this.mc.thePlayer == null) return;
        ItemStack held = this.mc.thePlayer.getHeldItem();
        if (held == null) return;
        boolean teleport = isTeleportStick(held);
        boolean phase = isPhaseTool(held);
        if (!teleport && !phase) return;
        MovingObjectPosition hit = this.mc.thePlayer.rayTrace(RAYTRACE_RANGE, 1.0F);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
        if (teleport && computeTeleportDestination(hit) != null) {
            this.previewBlock = hit.getBlockPos();
            this.previewFace = hit.sideHit;
            this.previewType = PreviewType.TELEPORT;
        } else if (phase && computePhaseDestination(hit) != null) {
            this.previewBlock = hit.getBlockPos();
            this.previewFace = hit.sideHit;
            this.previewType = PreviewType.PHASE;
        }
    }

    private void movePlayer(Vec3 vec) {
        if (this.mc == null || this.mc.thePlayer == null || vec == null) return;
        this.mc.thePlayer.setPosition(vec.xCoord, vec.yCoord, vec.zCoord);
        this.mc.thePlayer.motionX = this.mc.thePlayer.motionY = this.mc.thePlayer.motionZ = 0.0;
    }

    private void cyclePlayer(boolean forward) {
        if (this.mc == null || this.mc.theWorld == null || this.mc.thePlayer == null) return;
        rebuildPlayerCache();
        if (this.cachedPlayers.isEmpty()) {
            this.playerIndex = -1;
            updateCompassName(null);
            return;
        }
        if (this.playerIndex < 0 || this.playerIndex >= this.cachedPlayers.size()) {
            this.playerIndex = forward ? 0 : this.cachedPlayers.size() - 1;
        } else {
            this.playerIndex = Math.floorMod(this.playerIndex + (forward ? 1 : -1), this.cachedPlayers.size());
        }
        EntityPlayer target = this.cachedPlayers.get(this.playerIndex);
        updateCompassName(target.getGameProfile().getName());
        movePlayer(new Vec3(target.posX, target.posY + target.height * 0.5, target.posZ));
    }

    private void rebuildPlayerCache() {
        this.cachedPlayers.clear();
        if (this.mc == null || this.mc.theWorld == null || this.mc.thePlayer == null) return;
        for (Object obj : this.mc.theWorld.playerEntities) {
            if (!(obj instanceof EntityPlayer)) continue;
            EntityPlayer ep = (EntityPlayer) obj;
            if (ep == this.mc.thePlayer) continue;
            if (isBot(ep)) continue;
            this.cachedPlayers.add(ep);
        }
        this.cachedPlayers.sort(Comparator.comparing(o -> o.getGameProfile().getName().toLowerCase(Locale.ROOT)));
    }

    private void updateCompassName(String playerName) {
        if (this.compassItem == null) return;
        String suffix = playerName == null ? "None" : playerName;
        renameItem(this.compassItem, "&fPlayer: &7" + suffix);
    }

    private boolean isBot(EntityPlayer player) {
        try {
            return AntiBot.isBot(player);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void failSafeDisable() {
        if (this.getState()) this.setState(false);
    }

    public static boolean shouldSuppressMovement(EntityPlayerSP player) {
        Freecam module = INSTANCE;
        return module != null && module.getState() && module.mc != null && module.mc.thePlayer == player;
    }

    public static boolean shouldBlockMouse() {
        Freecam module = INSTANCE;
        return module != null && module.getState();
    }

    private static float getCapabilitySpeed(net.minecraft.entity.player.PlayerCapabilities caps, boolean fly, float fallback) {
        if (caps == null) return fallback;
        try {
            Field field = getCapabilityField(fly);
            if (field != null) return field.getFloat(caps);
        } catch (Throwable ignored) {}
        return fallback;
    }

    private static void setCapabilitySpeed(net.minecraft.entity.player.PlayerCapabilities caps, boolean fly, float value) {
        if (caps == null) return;
        try {
            Field field = getCapabilityField(fly);
            if (field != null) field.setFloat(caps, value);
        } catch (Throwable ignored) {}
    }

    private static Field getCapabilityField(boolean fly) {
        try {
            Field cached = fly ? flySpeedField : walkSpeedField;
            if (cached == null) {
                cached = net.minecraft.entity.player.PlayerCapabilities.class.getDeclaredField(fly ? "flySpeed" : "walkSpeed");
                cached.setAccessible(true);
                if (fly) flySpeedField = cached; else walkSpeedField = cached;
            }
            return cached;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private AxisAlignedBB buildFaceBoundingBox(BlockPos pos, EnumFacing face) {
        if (pos == null || face == null) return null;
        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();
        double eps = 0.0025;
        double minX = x;
        double minY = y;
        double minZ = z;
        double maxX = x + 1;
        double maxY = y + 1;
        double maxZ = z + 1;
        switch (face) {
            case DOWN: maxY = y + eps; break;
            case UP: minY = y + 1 - eps; break;
            case NORTH: maxZ = z + eps; break;
            case SOUTH: minZ = z + 1 - eps; break;
            case WEST: maxX = x + eps; break;
            case EAST: minX = x + 1 - eps; break;
            default: break;
        }
        return new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private void renderFaceOverlay(AxisAlignedBB bb, float r, float g, float b) {
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.color(r, g, b, 0.6F);
        RenderGlobal.drawSelectionBoundingBox(bb);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    private boolean canStandAt(BlockPos pos) {
        return this.mc != null && this.mc.theWorld != null
                && this.mc.theWorld.isAirBlock(pos)
                && this.mc.theWorld.isAirBlock(pos.up());
    }

    private enum PreviewType {
        TELEPORT,
        PHASE
    }
}
