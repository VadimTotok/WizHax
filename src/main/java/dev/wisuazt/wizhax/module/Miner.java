package dev.wisuazt.wizhax.module;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.pathing.goals.GoalYLevel;
import baritone.api.schematic.FillSchematic;
import baritone.api.schematic.ISchematic;
import baritone.api.utils.BetterBlockPos;
import dev.wisuazt.wizhax.WizHax;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.systems.modules.world.Nuker;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class Miner extends Module {
    private static final int ESCAPE_STALL_TICKS = 100;
    private static final int ESCAPE_TIMEOUT_TICKS = 600;
    private static final int ESCAPE_HEIGHT = 10;
    private static final int MIN_THROWAWAY_STACK = 4;
    private static final int MAX_PLACE_ATTEMPTS = 5;
    private static final int MAX_STATE_TIMEOUT_TICKS = 800;

    public enum Mode { MENDING, TOOL_SWAP }
    public enum MainTool { PICKAXE, SHOVEL, AXE, HOE, SHEARS }

    private enum State {
        MINING, XP_MINING, TOOL_REFILL, MINING_THROWAWAY,
        ESCAPE,
        PREPARE_UNLOAD,
        CLEAR_ENDER_CHEST_SPOT,
        PLACE_ENDER_CHEST, CHECK_ENDER_CHEST_PLACED,
        VERIFY_ENDER_CHEST_CLEARANCE,
        OPEN_ENDER_CHEST, CHECK_ENDER_CHEST_OPENED,
        TAKE_EMPTY_SHULKER, CHECK_SHULKER_TAKEN,
        CLOSE_ENDER_CHEST,
        CLEAR_SHULKER_SPOT,
        PLACE_SHULKER, CHECK_SHULKER_PLACED,
        OPEN_SHULKER, CHECK_SHULKER_OPENED,
        STORE_ITEMS, CLOSE_SHULKER,
        BREAK_SHULKER, CHECK_SHULKER_BROKEN,
        PICKUP_SHULKER,
        OPEN_ENDER_CHEST_AGAIN, CHECK_ENDER_CHEST_OPENED_AGAIN,
        PUT_SHULKER_BACK, CHECK_SHULKER_BACK,
        CLOSE_ENDER_CHEST_AGAIN,
        BREAK_ENDER_CHEST, CHECK_ENDER_CHEST_BROKEN,
        RETURN_TO_MINING,
        MOVE_TO_MIN_Y, WAIT_MIN_Y,
        PLACE_FOUNDATION, WAIT_FOUNDATION,
        DISPERSION,
        IDLE
    }

    private final SettingGroup sgGeneral = settings.createGroup("General");
    private final SettingGroup sgMending = settings.createGroup("Mending");
    private final SettingGroup sgToolSwap = settings.createGroup("Tool Swap");
    private final SettingGroup sgSlots = settings.createGroup("Slots");
    private final SettingGroup sgThrowaway = settings.createGroup("Throwaway");
    private final SettingGroup sgUnload = settings.createGroup("Unload");
    private final SettingGroup sgMisc = settings.createGroup("Misc");

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("MENDING: repair one tool. TOOL_SWAP: cycle tools from inventory toolbox.")
        .defaultValue(Mode.MENDING).build());

    private final Setting<MainTool> mainTool = sgToolSwap.add(new EnumSetting.Builder<MainTool>()
        .name("main-tool")
        .description("Tool type for tool-swap mode.")
        .defaultValue(MainTool.PICKAXE)
        .visible(() -> mode.get() == Mode.TOOL_SWAP).build());

    private final Setting<Integer> toolsPerRefill = sgToolSwap.add(new IntSetting.Builder()
        .name("tools-per-refill")
        .description("How many tools to pull into hotbar per refill.")
        .defaultValue(9).min(1).sliderMax(9)
        .visible(() -> mode.get() == Mode.TOOL_SWAP).build());

    private final Setting<Double> replaceThreshold = sgToolSwap.add(new DoubleSetting.Builder()
        .name("replace-threshold")
        .description("Durability % below which a tool is spent.")
        .defaultValue(5.0).min(0).sliderMax(99)
        .visible(() -> mode.get() == Mode.TOOL_SWAP).build());

    private final Setting<Integer> swapDelay = sgToolSwap.add(new IntSetting.Builder()
        .name("swap-delay")
        .description("Ticks between inventory clicks during tool refill.")
        .defaultValue(3).min(0).sliderMax(10)
        .visible(() -> mode.get() == Mode.TOOL_SWAP).build());

    private final Setting<Boolean> disconnectNoTools = sgToolSwap.add(new BoolSetting.Builder()
        .name("disconnect-no-tools")
        .description("Disconnect when out of usable tools.")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.TOOL_SWAP).build());

    private final Setting<List<Block>> targetBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("target-blocks")
        .description("Blocks to mine.")
        .defaultValue(Collections.singletonList(Blocks.ANCIENT_DEBRIS)).build());

    private final Setting<List<Item>> targetItems = sgGeneral.add(new ItemListSetting.Builder()
        .name("target-items")
        .description("Items to collect and store.")
        .defaultValue(Collections.singletonList(Items.ANCIENT_DEBRIS)).build());

    private final Setting<Integer> actionDelay = sgGeneral.add(new IntSetting.Builder()
        .name("action-delay")
        .description("Base delay between actions (ticks).")
        .defaultValue(20).min(0).sliderMax(100).build());

    private final Setting<Integer> dispersionDelay = sgGeneral.add(new IntSetting.Builder()
        .name("dispersion-delay")
        .description("Random jitter added to action-delay.")
        .defaultValue(4).min(0).sliderMax(20).build());

    private final Setting<Boolean> escapeTrap = sgGeneral.add(new BoolSetting.Builder()
        .name("escape-trap")
        .description("Pillar out of bedrock traps using Baritone.")
        .defaultValue(true).build());

    private final Setting<Integer> escapeHeight = sgGeneral.add(new IntSetting.Builder()
        .name("escape-height")
        .description("Blocks to pillar up when trapped.")
        .defaultValue(ESCAPE_HEIGHT).min(3).sliderMax(30)
        .visible(escapeTrap::get).build());

    private final Setting<Integer> minimumPlaceY = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-place-y")
        .description("Move up to Y+2 above this before placing EC/shulker.")
        .defaultValue(-60).min(-64).max(320).sliderRange(-64, 128).build());

    private final Setting<List<Block>> xpBlocks = sgMending.add(new BlockListSetting.Builder()
        .name("xp-ore-blocks")
        .description("XP-ore blocks to mine for repair.")
        .defaultValue(Arrays.asList(
            Blocks.NETHER_QUARTZ_ORE, Blocks.LAPIS_ORE, Blocks.DIAMOND_ORE,
            Blocks.EMERALD_ORE, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.DEEPSLATE_EMERALD_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.DEEPSLATE_COAL_ORE, Blocks.COAL_ORE, Blocks.NETHER_GOLD_ORE))
        .visible(() -> mode.get() == Mode.MENDING).build());

    private final Setting<Integer> durabilityThreshold = sgMending.add(new IntSetting.Builder()
        .name("durability-threshold")
        .description("Remaining durability % to start repair.")
        .defaultValue(20).min(1).sliderMax(100)
        .visible(() -> mode.get() == Mode.MENDING).build());

    private final Setting<Integer> resumeLevel = sgMending.add(new IntSetting.Builder()
        .name("resume-level")
        .description("Remaining durability % to stop repair.")
        .defaultValue(70).min(1).sliderMax(100)
        .visible(() -> mode.get() == Mode.MENDING).build());

    private final Setting<Integer> toolHotbarSlot = sgSlots.add(new IntSetting.Builder()
        .name("tool-hotbar-slot").description("Hotbar slot for the mining tool (0-8).")
        .defaultValue(0).range(0, 8).sliderRange(0, 8).build());

    private final Setting<Integer> shulkerHotbarSlot = sgSlots.add(new IntSetting.Builder()
        .name("shulker-hotbar-slot").description("Hotbar slot for shulker box (0-8).")
        .defaultValue(1).range(0, 8).sliderRange(0, 8).build());

    private final Setting<Integer> enderChestHotbarSlot = sgSlots.add(new IntSetting.Builder()
        .name("enderchest-hotbar-slot").description("Hotbar slot reserved for ender chest (0-8).")
        .defaultValue(2).range(0, 8).sliderRange(0, 8).build());

    private final Setting<Integer> throwawayHotbarSlot = sgSlots.add(new IntSetting.Builder()
        .name("throwaway-hotbar-slot").description("Hotbar slot for throwaway blocks (0-8).")
        .defaultValue(8).range(0, 8).sliderRange(0, 8).build());

    private final Setting<Boolean> enableThrowaway = sgThrowaway.add(new BoolSetting.Builder()
        .name("enable-throwaway")
        .description("Auto-mine and refill throwaway blocks for foundation placement.")
        .defaultValue(true).build());

    private final Setting<Block> throwawayBlock = sgThrowaway.add(new BlockSetting.Builder()
        .name("throwaway-block")
        .description("Block used as throwaway (e.g. cobbled deepslate).")
        .defaultValue(Blocks.COBBLED_DEEPSLATE)
        .visible(enableThrowaway::get).build());

    private final Setting<List<Block>> throwawayMineBlocks = sgThrowaway.add(new BlockListSetting.Builder()
        .name("throwaway-mine-blocks")
        .description("Blocks to mine when refilling throwaway. Empty = mine throwaway block itself.")
        .defaultValue(Collections.singletonList(Blocks.DEEPSLATE))
        .visible(enableThrowaway::get).build());

    private final Setting<Integer> throwawayStackMinimum = sgThrowaway.add(new IntSetting.Builder()
        .name("throwaway-stack-minimum")
        .description("Minimum throwaway count to trigger refill.")
        .defaultValue(16).range(1, 64).sliderRange(1, 64)
        .visible(enableThrowaway::get).build());

    private final Setting<Integer> throwawayStackGoal = sgThrowaway.add(new IntSetting.Builder()
        .name("throwaway-stack-goal")
        .description("Target throwaway count when refilling.")
        .defaultValue(64).range(1, 320).sliderRange(1, 320)
        .visible(enableThrowaway::get).build());

    private final Setting<Integer> unloadDelay = sgUnload.add(new IntSetting.Builder()
        .name("unload-delay")
        .description("Delay per unload step (ticks).")
        .defaultValue(10).min(1).sliderMax(40).build());

    private final Setting<Boolean> disconnectNoShulkers = sgUnload.add(new BoolSetting.Builder()
        .name("disconnect-no-shulkers")
        .description("Disconnect when EC has no empty shulkers.")
        .defaultValue(false).build());

    private final Setting<Boolean> debug = sgMisc.add(new BoolSetting.Builder()
        .name("debug")
        .description("Print status messages.")
        .defaultValue(false).build());

    private State currentState = State.IDLE;
    private int stateTicks = 0;
    private int nextActionIn = 0;
    private int tickCounter = 0;
    private final Random random = new Random();
    private boolean silkTouchWarned = false;
    private boolean resumeTargetWarned = false;

    private BlockPos enderChestPos, shulkerPos;
    private int exchangedEnderSlot = -1;
    private long ignoreUnloadUntil = 0;
    private long ignoreDispersionUntil = 0;

    private int currentStoreSlot = -1;
    private int storeAttempts = 0;
    private final Set<Integer> failedSlots = new HashSet<>();
    private int itemsMoved = 0;

    private BlockPos lastPlayerPos = null;
    private int stuckTicks = 0;
    private int lastInventoryCount = -1;
    private int shulkerPlaceAttempts = 0;
    private int enderChestPlaceAttempts = 0;

    private BlockPos escapeTarget = null;
    private boolean originalAllowPlace = false;
    private List<Item> originalThrowaway = null;
    private int escapeStartY = 0;
    private int escapeBestY = 0;
    private int escapeStallTicks = 0;

    private boolean pickupInProgress = false;
    private boolean pickupOldAllowBreak;

    private boolean xpDropScanDisabled = false;
    private boolean originalMineScanDroppedItems = true;

    private int repairNoProgress = 0;
    private int repairLastDamage = -1;
    private int repairSampleTimer = 0;
    private int minYTicks = 0;

    private BlockPos foundationPos = null;
    private State stateAfterFoundation = null;
    private boolean foundationFlagsForced = false;
    private boolean foundationSavedPlace, foundationSavedBreak;
    private int foundationTicks = 0;

    private int dispCurrentSlot = -1;
    private int dispDropsLeft = 0;
    private int dispStep = 0;
    private int dispTimer = 0;

    private int refillMoved = 0;
    private int refillTimer = 0;
    private int consecutiveToolFlows = 0;
    private boolean toolRetrieval = false;
    private int storeToolIndex = 0;

    private boolean wasNukerActive = false;

    public Miner() {
        super(WizHax.WIZHAX, "[WHax] Miner",
            "Full auto miner: tool-swap / mending, unload, foundation, escape trap.");
    }

    @Override
    public void onActivate() {
        silkTouchWarned = false;
        resumeTargetWarned = false;
        exchangedEnderSlot = -1;
        ignoreUnloadUntil = 0;
        ignoreDispersionUntil = 0;
        currentState = State.MINING;
        stateTicks = 0;
        lastPlayerPos = mc.player != null ? mc.player.getBlockPos() : null;
        stuckTicks = 0;
        repairNoProgress = 0;
        repairLastDamage = -1;
        repairSampleTimer = 0;
        consecutiveToolFlows = 0;
        toolRetrieval = false;
        storeToolIndex = 0;
        int targetMaxStackCache = -1;
        scheduleNextAction();
        try { BaritoneAPI.getSettings().autoTool.value = false; } catch (Throwable ignored) {}
        ensureToolReady();
        startMiningTargetBlocks();
        pickupInProgress = false;
    }

    @Override
    public void onDeactivate() {
        try {
            getBaritone().getPathingBehavior().cancelEverything();
            getBaritone().getMineProcess().cancel();
        } catch (Throwable ignored) {}
        if (currentState == State.ESCAPE) restoreEscapeSettings();
        restoreFoundationFlags();
        currentState = State.IDLE;
        toolRetrieval = false;
        storeToolIndex = 0;
        finishPickupIfNeeded();
        restoreXpDropScan();
        if (wasNukerActive) {
            Nuker nuker = Modules.get().get(Nuker.class);
            if (nuker != null && !nuker.isActive()) nuker.toggle();
            wasNukerActive = false;
        }
    }

    private void scheduleNextAction() {
        int d = dispersionDelay.get();
        int jitter = d > 0 ? random.nextInt(d * 2 + 1) - d : 0;
        nextActionIn = Math.max(0, actionDelay.get() + jitter);
        tickCounter = 0;
    }

    private void waitTicks(int ticks) { nextActionIn = ticks; tickCounter = 0; }

    private void transitionTo(State newState, int waitTicks) {
        if (currentState == State.PICKUP_SHULKER && newState != State.PICKUP_SHULKER) {
            finishPickupIfNeeded();
        }
        currentState = newState;
        stateTicks = 0;
        waitTicks(waitTicks);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        tickCounter++;
        if (tickCounter < nextActionIn) return;
        processCurrentState();
        stateTicks++;
    }

    private void processCurrentState() {
        boolean waitState = currentState == State.MINING || currentState == State.XP_MINING
            || currentState == State.TOOL_REFILL || currentState == State.MINING_THROWAWAY
            || currentState == State.IDLE || currentState == State.ESCAPE
            || currentState == State.MOVE_TO_MIN_Y || currentState == State.WAIT_MIN_Y
            || currentState == State.DISPERSION;

        if (!waitState && stateTicks > MAX_STATE_TIMEOUT_TICKS) {
            warning("Timeout in state " + currentState + ", aborting.");
            getBaritone().getPathingBehavior().cancelEverything();
            getBaritone().getMineProcess().cancel();
            exchangedEnderSlot = -1;
            toolRetrieval = false;
            storeToolIndex = 0;
            finishPickupIfNeeded();
            restoreFoundationFlags();
            transitionTo(State.MINING, 0);
            startMiningTargetBlocks();
            return;
        }

        switch (currentState) {
            case MINING -> tickMining();
            case XP_MINING -> tickXpMining();
            case TOOL_REFILL -> tickToolRefill();
            case MINING_THROWAWAY -> tickMiningThrowaway();
            case ESCAPE -> tickEscape();
            case PREPARE_UNLOAD -> prepareUnload();
            case CLEAR_ENDER_CHEST_SPOT -> clearEnderChestSpot();
            case PLACE_ENDER_CHEST -> placeEnderChest();
            case CHECK_ENDER_CHEST_PLACED -> checkEnderChestPlaced();
            case VERIFY_ENDER_CHEST_CLEARANCE -> verifyEnderChestClearance();
            case OPEN_ENDER_CHEST -> openEnderChest();
            case CHECK_ENDER_CHEST_OPENED -> checkEnderChestOpened();
            case TAKE_EMPTY_SHULKER -> startTakeShulker();
            case CHECK_SHULKER_TAKEN -> checkShulkerTaken();
            case CLOSE_ENDER_CHEST -> closeEnderChest();
            case CLEAR_SHULKER_SPOT -> clearShulkerSpot();
            case PLACE_SHULKER -> placeShulker();
            case CHECK_SHULKER_PLACED -> checkShulkerPlaced();
            case OPEN_SHULKER -> openShulker();
            case CHECK_SHULKER_OPENED -> checkShulkerOpened();
            case STORE_ITEMS -> storeItems();
            case CLOSE_SHULKER -> closeShulker();
            case BREAK_SHULKER -> breakShulker();
            case CHECK_SHULKER_BROKEN -> checkShulkerBroken();
            case PICKUP_SHULKER -> pickupShulker();
            case OPEN_ENDER_CHEST_AGAIN -> openEnderChestAgain();
            case CHECK_ENDER_CHEST_OPENED_AGAIN -> checkEnderChestOpenedAgain();
            case PUT_SHULKER_BACK -> startPutShulkerBack();
            case CHECK_SHULKER_BACK -> checkShulkerBack();
            case CLOSE_ENDER_CHEST_AGAIN -> closeEnderChestAgain();
            case BREAK_ENDER_CHEST -> breakEnderChest();
            case CHECK_ENDER_CHEST_BROKEN -> checkEnderChestBroken();
            case RETURN_TO_MINING -> returnToMining();
            case MOVE_TO_MIN_Y -> tickMoveToMinY();
            case WAIT_MIN_Y -> tickWaitMinY();
            case PLACE_FOUNDATION -> tickPlaceFoundation();
            case WAIT_FOUNDATION -> tickWaitFoundation();
            case DISPERSION -> tickDispersion();
            case IDLE -> {}
        }
    }

    private boolean isCombatActive() {
        KillAura ka = Modules.get().get(KillAura.class);
        return ka != null && ka.isActive();
    }

    private net.minecraft.registry.tag.TagKey<Item> toolTag() {
        return switch (mainTool.get()) {
            case SHOVEL -> ItemTags.SHOVELS;
            case AXE -> ItemTags.AXES;
            case HOE -> ItemTags.HOES;
            default -> ItemTags.PICKAXES;
        };
    }

    private boolean isMainToolType(ItemStack s) {
        if (s.isEmpty()) return false;
        return mainTool.get() == MainTool.SHEARS
            ? s.getItem() == Items.SHEARS
            : s.isIn(toolTag());
    }

    private int toolTier(ItemStack s) {
        String n = s.getItem().toString().toLowerCase();
        if (n.contains("netherite")) return 5;
        if (n.contains("diamond")) return 4;
        if (n.contains("iron")) return 3;
        if (n.contains("gold")) return 2;
        if (n.contains("stone")) return 1;
        return 0;
    }

    private boolean isUsableTool(ItemStack s) {
        if (s.isEmpty() || !isMainToolType(s) || !s.isDamageable()) return false;
        double pct = 100.0 * (s.getMaxDamage() - s.getDamage()) / s.getMaxDamage();
        return pct > replaceThreshold.get();
    }

    private boolean isReservedSlot(int slot) {
        if (slot == toolHotbarSlot.get()) return true;
        if (slot == shulkerHotbarSlot.get()) return true;
        if (slot == enderChestHotbarSlot.get()) return true;
        return enableThrowaway.get() && slot == throwawayHotbarSlot.get();
    }

    private int findBestUsableSlot(int from, int to, boolean skipReserved) {
        int best = -1, bestTier = -1, bestDura = -1;
        for (int i = from; i <= to; i++) {
            if (skipReserved && isReservedSlot(i)) continue;
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!isUsableTool(s)) continue;
            int tier = toolTier(s);
            int dura = s.getMaxDamage() - s.getDamage();
            if (tier > bestTier || (tier == bestTier && dura > bestDura)) {
                best = i; bestTier = tier; bestDura = dura;
            }
        }
        return best;
    }

    private int findUsableHotbarToolSlot() { return findBestUsableSlot(0, 8, true); }
    private int findUsableToolboxSlot() { return findBestUsableSlot(9, 35, false); }

    private int findFreeToolHotbarSlot() {
        for (int i = 0; i <= 8; i++) {
            if (isReservedSlot(i)) continue;
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty() || (isMainToolType(s) && !isUsableTool(s))) return i;
        }
        return -1;
    }

    private void swapIntoConfigured(int src) {
        int cfg = toolHotbarSlot.get();
        if (src != cfg) {
            mc.interactionManager.clickSlot(
                mc.player.playerScreenHandler.syncId,
                invToPlayerHandlerSlot(src), cfg, SlotActionType.SWAP, mc.player);
        }
    }

    private void ensureToolReady() {
        if (mode.get() == Mode.TOOL_SWAP) {
            int cfg = toolHotbarSlot.get();
            if (!isUsableTool(mc.player.getInventory().getStack(cfg))) {
                int src = findUsableHotbarToolSlot();
                if (src == -1) src = findUsableToolboxSlot();
                if (src != -1) swapIntoConfigured(src);
            }
        }
        InvUtils.swap(toolHotbarSlot.get(), false);
    }

    private void outOfTools() {
        toolRetrieval = false;
        storeToolIndex = 0;
        consecutiveToolFlows = 0;
        if (disconnectNoTools.get()) {
            error("Out of usable tools - disconnecting.");
            toggle();
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().getConnection().disconnect(Text.literal("[Miner] Out of tools"));
            }
        } else {
            warning("Out of usable tools - disabling module.");
            toggle();
        }
    }

    private void tickToolRefill() {
        if (refillTimer > 0) { refillTimer--; return; }
        if (refillMoved < toolsPerRefill.get()) {
            int src = findUsableToolboxSlot();
            int dst = findFreeToolHotbarSlot();
            if (src != -1 && dst != -1) {
                mc.interactionManager.clickSlot(
                    mc.player.playerScreenHandler.syncId,
                    invToPlayerHandlerSlot(src), dst, SlotActionType.SWAP, mc.player);
                refillMoved++;
                refillTimer = swapDelay.get();
                return;
            }
        }
        int slot = findUsableHotbarToolSlot();
        if (slot == -1) {
            int invSlot = findUsableToolboxSlot();
            if (invSlot != -1) {
                swapIntoConfigured(invSlot);
                slot = findUsableHotbarToolSlot();
            }
        }
        if (slot != -1) {
            swapIntoConfigured(slot);
            InvUtils.swap(toolHotbarSlot.get(), false);
            consecutiveToolFlows = 0;
            transitionTo(State.MINING, 0);
            startMiningTargetBlocks();
        } else {
            if (++consecutiveToolFlows > 3) {
                consecutiveToolFlows = 0;
                outOfTools();
            } else {
                debug("No usable tools - retrieving tool shulker from ender chest.");
                startShulkerToolRetrieval();
            }
        }
    }

    private void startShulkerToolRetrieval() {
        getBaritone().getPathingBehavior().cancelEverything();
        Nuker nuker = Modules.get().get(Nuker.class);
        wasNukerActive = nuker != null && nuker.isActive();
        if (wasNukerActive) nuker.toggle();
        toolRetrieval = true;
        storeToolIndex = 0;
        transitionTo(State.PREPARE_UNLOAD, 0);
    }

    private void startMiningTargetBlocks() {
        if (targetBlocks.get().isEmpty()) {
            warning("Target blocks empty.");
            return;
        }
        getBaritone().getMineProcess().mine(targetBlocks.get().toArray(new Block[0]));
    }

    private void tickMining() {
        if (escapeTrap.get() && isStuck()) {
            enterEscapeState();
            return;
        }

        if (mode.get() == Mode.TOOL_SWAP && !isCombatActive()) {
            int cfg = toolHotbarSlot.get();
            if (!isUsableTool(mc.player.getInventory().getStack(cfg))) {
                int src = findUsableHotbarToolSlot();
                if (src == -1) {
                    debug("Hotbar out of usable tools - refilling.");
                    getBaritone().getPathingBehavior().cancelEverything();
                    refillMoved = 0; refillTimer = 0;
                    transitionTo(State.TOOL_REFILL, 0);
                    return;
                }
                swapIntoConfigured(src);
            }
            InvUtils.swap(cfg, false);
        } else if (mode.get() == Mode.MENDING && !isCombatActive()) {
            if (autoMendingNeeded()) {
                if (isMainPickaxeSilkTouch() && !hasAlternativePickaxeForXp()) {
                    if (!silkTouchWarned) {
                        warning("Pickaxe has Silk Touch, no alternative - disabling.");
                        silkTouchWarned = true;
                    }
                    toggle();
                    return;
                }
                info("Starting repair (Mending) - mining XP ores.");
                if (!resumeTargetWarned && resumeLevel.get() <= durabilityThreshold.get()) {
                    warning("resume-level <= durability-threshold, repair will end immediately.");
                    resumeTargetWarned = true;
                }
                disableXpDropScan();
                repairNoProgress = 0;
                repairLastDamage = -1;
                repairSampleTimer = 100;
                getBaritone().getMineProcess().cancel();
                if (!xpBlocks.get().isEmpty())
                    getBaritone().getMineProcess().mine(xpBlocks.get().toArray(new Block[0]));
                transitionTo(State.XP_MINING, 0);
                return;
            }
        }

        if (System.currentTimeMillis() > ignoreUnloadUntil && isInventoryFullOfTargets()) {
            info("Inventory full - starting unload.");
            getBaritone().getMineProcess().cancel();
            getBaritone().getPathingBehavior().cancelEverything();
            transitionTo(State.PREPARE_UNLOAD, 0);
            return;
        }

        if (mc.currentScreen == null
            && System.currentTimeMillis() > ignoreDispersionUntil
            && hasFullTargetStackAndEmptySlot()) {
            transitionTo(State.DISPERSION, 0);
            return;
        }

        if (enableThrowaway.get() && needsThrowawayRefill()) {
            moveThrowawayToSlot();
            if (hotbarThrowawayCount() < throwawayStackGoal.get() && hotbarThrowawayCanGrow()) {
                startMiningThrowaway();
                transitionTo(State.MINING_THROWAWAY, 0);
                return;
            }
        }

        if (!getBaritone().getMineProcess().isActive()) startMiningTargetBlocks();
        pickupNearbyTargetItems();
    }

    private void tickXpMining() {
        if (mc.player == null) return;

        if (escapeTrap.get() && isStuck()) {
            restoreXpDropScan();
            enterEscapeState();
            return;
        }

        int remaining = getPickaxeRemainingDurabilityPercent();
        if (remaining < 0 || remaining >= resumeLevel.get()) {
            info("Pickaxe repaired, returning to mining.");
            restoreXpDropScan();
            getBaritone().getMineProcess().cancel();
            repairNoProgress = 0;
            transitionTo(State.MINING, 0);
            startMiningTargetBlocks();
            return;
        }

        if (repairSampleTimer > 0) {
            repairSampleTimer--;
        } else {
            repairSampleTimer = 100;
            ItemStack pick = mc.player.getMainHandStack();
            if (!pick.isEmpty() && pick.isDamageable()) {
                int dmg = pick.getDamage();
                if (repairLastDamage >= 0 && dmg >= repairLastDamage) {
                    if (++repairNoProgress >= 6) {
                        error("Repair not progressing (no Mending / no XP ores reachable) - disabling.");
                        toggle();
                        return;
                    }
                } else {
                    repairNoProgress = 0;
                }
                repairLastDamage = dmg;
            }
        }

        if (!getBaritone().getMineProcess().isActive() && !xpBlocks.get().isEmpty()) {
            getBaritone().getMineProcess().mine(xpBlocks.get().toArray(new Block[0]));
        }
    }

    private void startMiningThrowaway() {
        List<Block> mineBlocks = throwawayMineBlocks.get();
        if (mineBlocks.isEmpty()) mineBlocks = Collections.singletonList(throwawayBlock.get());
        debug("Refilling throwaway - mining " + mineBlocks.size() + " block type(s).");
        getBaritone().getMineProcess().cancel();
        getBaritone().getMineProcess().mine(mineBlocks.toArray(new Block[0]));
    }

    private void tickMiningThrowaway() {
        if (mc.player == null) return;
        moveThrowawayToSlot();
        int count = hotbarThrowawayCount();
        if (count >= throwawayStackGoal.get() || !hotbarThrowawayCanGrow()) {
            debug("Throwaway refill done (" + count + ").");
            getBaritone().getMineProcess().cancel();
            transitionTo(State.MINING, 0);
            startMiningTargetBlocks();
            return;
        }
        if (!getBaritone().getMineProcess().isActive()) {
            startMiningThrowaway();
        }
    }

    private int hotbarThrowawayCount() {
        Item item = throwawayBlock.get().asItem();
        int total = 0;
        for (int i = 0; i <= 8; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() == item) total += s.getCount();
        }
        return total;
    }

    private boolean hotbarThrowawayCanGrow() {
        Item item = throwawayBlock.get().asItem();
        for (int i = 0; i <= 8; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() == item && s.getCount() < s.getMaxCount()) return true;
            if (s.isEmpty() && !isReservedSlot(i)) return true;
        }
        return false;
    }

    private boolean needsThrowawayRefill() {
        if (!enableThrowaway.get()) return false;
        ItemStack s = mc.player.getInventory().getStack(throwawayHotbarSlot.get());
        if (!s.isEmpty() && s.getItem() != throwawayBlock.get().asItem()) return true;
        return hotbarThrowawayCount() < throwawayStackMinimum.get();
    }

    private void moveThrowawayToSlot() {
        int dst = throwawayHotbarSlot.get();
        Item item = throwawayBlock.get().asItem();
        ItemStack dstStack = mc.player.getInventory().getStack(dst);
        int have = (dstStack.getItem() == item) ? dstStack.getCount() : 0;
        int target = Math.min(64, throwawayStackGoal.get());
        int needed = target - have;
        if (needed <= 0) return;

        if (dstStack.isEmpty() || dstStack.getItem() != item) {
            for (int i = 9; i < 36; i++) {
                if (mc.player.getInventory().getStack(i).getItem() == item) {
                    InvUtils.move().from(i).toHotbar(dst);
                    return;
                }
            }
        }

        for (int i = 9; i < 36 && needed > 0; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() != item) continue;
            int syncId = mc.player.playerScreenHandler.syncId;
            mc.interactionManager.clickSlot(syncId, invToPlayerHandlerSlot(i), 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(syncId, invToPlayerHandlerSlot(dst), 0, SlotActionType.PICKUP, mc.player);
            if (!mc.player.playerScreenHandler.getCursorStack().isEmpty()) {
                mc.interactionManager.clickSlot(syncId, invToPlayerHandlerSlot(i), 0, SlotActionType.PICKUP, mc.player);
            }
            dstStack = mc.player.getInventory().getStack(dst);
            have = dstStack.getItem() == item ? dstStack.getCount() : 0;
            needed = target - have;
        }
    }

    private boolean autoMendingNeeded() {
        int remaining = getPickaxeRemainingDurabilityPercent();
        return remaining >= 0 && remaining <= durabilityThreshold.get();
    }

    private int getPickaxeRemainingDurabilityPercent() {
        if (mc.player == null) return -1;
        int slot = findPickaxeSlot();
        if (slot == -1) return -1;
        ItemStack pickaxe = mc.player.getInventory().getStack(slot);
        if (getEnchantmentLevel(pickaxe, Enchantments.MENDING) <= 0) return -1;
        int max = pickaxe.getMaxDamage();
        int cur = pickaxe.getDamage();
        return (int) (100.0 * (max - cur) / max);
    }

    private int findPickaxeSlot() {
        if (mc.player == null) return -1;
        int fallback = -1;
        for (int i = 0; i < 9; i++) {
            if (isReservedSlot(i)) continue;
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty() || !stack.isIn(ItemTags.PICKAXES)) continue;
            if (getEnchantmentLevel(stack, Enchantments.MENDING) > 0) return i;
            if (fallback == -1) fallback = i;
        }
        return fallback;
    }

    private int getEnchantmentLevel(ItemStack stack, RegistryKey<Enchantment> key) {
        var enchantments = stack.getEnchantments();
        for (RegistryEntry<Enchantment> entry : enchantments.getEnchantments()) {
            if (entry.matchesKey(key)) return enchantments.getLevel(entry);
        }
        return 0;
    }

    private boolean hasSilkTouch(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var ench = stack.getEnchantments();
        for (RegistryEntry<Enchantment> e : ench.getEnchantments()) {
            if (e.matchesKey(Enchantments.SILK_TOUCH)) return true;
        }
        return false;
    }

    private boolean isMainPickaxeSilkTouch() {
        ItemStack main = mc.player.getMainHandStack();
        return main.isIn(ItemTags.PICKAXES) && hasSilkTouch(main);
    }

    private boolean hasAlternativePickaxeForXp() {
        List<Block> xp = xpBlocks.get();
        if (xp.isEmpty()) return true;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty() || !stack.isIn(ItemTags.PICKAXES)) continue;
            if (hasSilkTouch(stack)) continue;
            for (Block block : xp) if (stack.isSuitableFor(block.getDefaultState())) return true;
        }
        return false;
    }

    private void disableXpDropScan() {
        if (!xpDropScanDisabled) {
            originalMineScanDroppedItems = BaritoneAPI.getSettings().mineScanDroppedItems.value;
            BaritoneAPI.getSettings().mineScanDroppedItems.value = false;
            xpDropScanDisabled = true;
        }
    }

    private void restoreXpDropScan() {
        if (xpDropScanDisabled) {
            BaritoneAPI.getSettings().mineScanDroppedItems.value = originalMineScanDroppedItems;
            xpDropScanDisabled = false;
        }
    }

    private boolean hasFullTargetStackAndEmptySlot() {
        boolean full = false;
        int empty = 0;
        for (int i = 9; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) empty++;
            else if (isTargetItem(s.getItem()) && s.getCount() >= s.getMaxCount()) full = true;
        }
        return full && empty > 0;
    }

    private void tickDispersion() {
        if (mc.currentScreen == null) {
            mc.setScreen(new InventoryScreen(mc.player));
            waitTicks(actionDelay.get());
            return;
        }
        if (!(mc.currentScreen instanceof InventoryScreen)) return;

        if (dispTimer > 0) { dispTimer--; return; }
        int syncId = mc.player.playerScreenHandler.syncId;

        if (dispStep == 0) {
            int emptyCount = 0;
            for (int i = 9; i < 36; i++) if (mc.player.getInventory().getStack(i).isEmpty()) emptyCount++;
            int targetSlot = -1, maxCount = 0;
            for (int i = 9; i < 36; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (isTargetItem(s.getItem()) && s.getCount() > maxCount) {
                    maxCount = s.getCount(); targetSlot = i;
                }
            }
            int toDistribute = Math.min(emptyCount, maxCount - 1);
            if (targetSlot != -1 && toDistribute > 0) {
                mc.interactionManager.clickSlot(syncId, invToPlayerHandlerSlot(targetSlot),
                    0, SlotActionType.PICKUP, mc.player);
                dispCurrentSlot = targetSlot;
                dispDropsLeft = toDistribute;
                dispStep = 1;
                dispTimer = dispersionDelay.get();
            } else { finishDispersion(); }
        } else if (dispStep == 1) {
            if (dispDropsLeft <= 0) { dispStep = 2; dispTimer = dispersionDelay.get(); return; }
            for (int i = 9; i < 36; i++) {
                if (i != dispCurrentSlot && mc.player.getInventory().getStack(i).isEmpty()) {
                    mc.interactionManager.clickSlot(syncId, invToPlayerHandlerSlot(i),
                        1, SlotActionType.PICKUP, mc.player);
                    dispDropsLeft--;
                    dispTimer = dispersionDelay.get();
                    return;
                }
            }
            dispStep = 2; dispTimer = dispersionDelay.get();
        } else if (dispStep == 2) {
            mc.interactionManager.clickSlot(syncId, invToPlayerHandlerSlot(dispCurrentSlot),
                0, SlotActionType.PICKUP, mc.player);
            finishDispersion();
        }
    }

    private void finishDispersion() {
        mc.player.closeHandledScreen();
        dispStep = 0; dispCurrentSlot = -1; dispDropsLeft = 0;
        ignoreDispersionUntil = System.currentTimeMillis() + 5000;
        transitionTo(State.MINING, 0);
        startMiningTargetBlocks();
    }

    private void pickupNearbyTargetItems() {
        if (targetItems.get().isEmpty() || mc.player == null || mc.world == null) return;
        for (ItemEntity entity : mc.world.getEntitiesByClass(ItemEntity.class,
            mc.player.getBoundingBox().expand(16),
            e -> targetItems.get().contains(e.getStack().getItem()))) {
            BetterBlockPos pos = new BetterBlockPos(entity.getBlockPos());
            if (!getBaritone().getPathingBehavior().isPathing()
                && !getBaritone().getMineProcess().isActive()) {
                getBaritone().getCustomGoalProcess().setGoalAndPath(new GoalNear(pos, 1));
            }
            break;
        }
    }

    private boolean isStuck() {
        if (mc.player == null) return false;
        BlockPos currentPos = mc.player.getBlockPos();
        int invCount = getTotalInventoryItemCount();
        if (lastPlayerPos == null) {
            lastPlayerPos = currentPos;
            lastInventoryCount = invCount;
            stuckTicks = 0;
            return false;
        }
        boolean positionChanged = !currentPos.equals(lastPlayerPos);
        boolean inventoryChanged = invCount != lastInventoryCount;
        if (positionChanged || inventoryChanged) {
            lastPlayerPos = currentPos;
            lastInventoryCount = invCount;
            stuckTicks = 0;
            return false;
        }
        stuckTicks++;
        return stuckTicks > 200;
    }

    private int getTotalInventoryItemCount() {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < 36; i++) count += mc.player.getInventory().getStack(i).getCount();
        count += mc.player.getOffHandStack().getCount();
        return count;
    }

    private boolean isInventoryFullOfTargets() {
        int reserved = 0;
        boolean anyEmpty = false;
        boolean anyJunk = false;
        int targetFree = 0;

        for (int i = 9; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) {
                anyEmpty = true;
            } else if (isTargetItem(s.getItem())) {
                targetFree += s.getMaxCount() - s.getCount();
            } else if (mode.get() == Mode.TOOL_SWAP && isMainToolType(s) && isUsableTool(s)) {
                reserved++;
            } else if (enableThrowaway.get() && s.getItem() == throwawayBlock.get().asItem()
                && hotbarThrowawayCount() < throwawayStackGoal.get()) {
                reserved++;
            } else {
                anyJunk = true;
            }
        }
        int managed = 27 - reserved;
        return managed > 0 && !anyEmpty && !anyJunk && targetFree == 0;
    }

    private boolean isTargetItem(Item item) {
        if (targetItems.get().contains(item)) return true;
        for (Block block : targetBlocks.get()) if (block.asItem() == item) return true;
        return false;
    }

    private boolean isShulkerBox(ItemStack stack) {
        return stack.isIn(ItemTags.SHULKER_BOXES);
    }

    private boolean isContainerEmpty(ItemStack stack) {
        ContainerComponent c = stack.get(DataComponentTypes.CONTAINER);
        return c == null || c.stream().allMatch(ItemStack::isEmpty);
    }

    private boolean isContainerNotEmpty(ItemStack stack) {
        ContainerComponent c = stack.get(DataComponentTypes.CONTAINER);
        return c != null && c.stream().anyMatch(s -> !s.isEmpty());
    }

    private ItemStack findShulkerWithItemsInInventory() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isShulkerBox(stack) && isContainerNotEmpty(stack)) return stack;
        }
        return null;
    }

    private ItemStack findAnyShulkerInInventory() {
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (isShulkerBox(s)) return s;
        }
        return null;
    }

    private ItemStack findRelevantShulker() {
        return toolRetrieval ? findAnyShulkerInInventory() : findShulkerWithItemsInInventory();
    }

    private void moveShulkerToExchangeSlot(ItemStack shulkerStack) {
        int slot = findInventorySlotOf(shulkerStack);
        if (slot >= 0 && slot != shulkerHotbarSlot.get()) {
            InvUtils.move().from(slot).toHotbar(shulkerHotbarSlot.get());
        }
    }

    private int findInventorySlotOf(ItemStack target) {
        if (target == null || target.isEmpty()) return -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && ItemStack.areItemsAndComponentsEqual(s, target)) return i;
        }
        return -1;
    }

    private List<Item> collectThrowawayBlocksFromInventory() {
        List<Item> items = new ArrayList<>();
        if (mc.player == null) return items;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (!(item instanceof BlockItem)) continue;
            if (stack.getCount() < MIN_THROWAWAY_STACK) continue;
            if (isTargetItem(item)) continue;
            if (isShulkerBox(stack)) continue;
            if (item == Items.ENDER_CHEST) continue;
            if (!items.contains(item)) items.add(item);
        }
        return items;
    }

    private void enterEscapeState() {
        info("Stuck detected. Initiating escape.");
        getBaritone().getPathingBehavior().cancelEverything();
        getBaritone().getMineProcess().cancel();
        List<Item> throwaway = collectThrowawayBlocksFromInventory();
        if (throwaway.isEmpty()) {
            warning("No usable blocks to pillar out with. Disabling module.");
            toggle();
            return;
        }
        currentState = State.ESCAPE;
        stateTicks = 0;
        escapeTarget = null;
        escapeStartY = mc.player.getBlockPos().getY();
        escapeBestY = escapeStartY;
        escapeStallTicks = 0;
        originalAllowPlace = BaritoneAPI.getSettings().allowPlace.value;
        originalThrowaway = new ArrayList<>(BaritoneAPI.getSettings().acceptableThrowawayItems.value);
        BaritoneAPI.getSettings().allowPlace.value = true;
        BaritoneAPI.getSettings().acceptableThrowawayItems.value = throwaway;
        info("Escape started, target Y=" + (escapeStartY + escapeHeight.get()));
    }

    private void restoreEscapeSettings() {
        BaritoneAPI.getSettings().allowPlace.value = originalAllowPlace;
        if (originalThrowaway != null) {
            BaritoneAPI.getSettings().acceptableThrowawayItems.value = originalThrowaway;
            originalThrowaway = null;
        }
    }

    private void tickEscape() {
        if (mc.player == null) return;
        int targetY = escapeStartY + escapeHeight.get();
        int currentY = mc.player.getBlockPos().getY();
        if (currentY >= targetY) { info("Reached escape height."); finishEscape(); return; }
        if (stateTicks > ESCAPE_TIMEOUT_TICKS) { warning("Escape timeout."); finishEscape(); return; }
        if (currentY > escapeBestY) { escapeBestY = currentY; escapeStallTicks = 0; }
        else escapeStallTicks++;
        if (escapeStallTicks > ESCAPE_STALL_TICKS) {
            List<Item> throwaway = collectThrowawayBlocksFromInventory();
            if (throwaway.isEmpty()) {
                warning("Ran out of blocks while escaping. Disabling.");
                finishEscape();
                toggle();
                return;
            }
            BaritoneAPI.getSettings().acceptableThrowawayItems.value = throwaway;
            escapeStallTicks = 0;
            escapeTarget = null;
        }
        if (escapeTarget == null || !getBaritone().getCustomGoalProcess().isActive()) {
            escapeTarget = new BlockPos(mc.player.getBlockPos().getX(), targetY,
                mc.player.getBlockPos().getZ());
            getBaritone().getCustomGoalProcess().setGoalAndPath(new GoalYLevel(targetY));
        }
    }

    private void finishEscape() {
        getBaritone().getCustomGoalProcess().setGoal(null);
        getBaritone().getPathingBehavior().cancelEverything();
        restoreEscapeSettings();
        escapeTarget = null;
        transitionTo(State.MINING, 0);
        startMiningTargetBlocks();
    }

    private void tickMoveToMinY() {
        BlockPos currentPos = mc.player.getBlockPos();
        int targetY = minimumPlaceY.get() + 2;
        if (currentPos.getY() >= targetY) {
            debug("Reached safe Y level (" + currentPos.getY() + ").");
            transitionTo(State.PREPARE_UNLOAD, 0);
            return;
        }
        GoalBlock goal = new GoalBlock(new BlockPos(currentPos.getX(), targetY, currentPos.getZ()));
        getBaritone().getCustomGoalProcess().setGoalAndPath(goal);
        minYTicks = 0;
        transitionTo(State.WAIT_MIN_Y, 0);
    }

    private void tickWaitMinY() {
        minYTicks++;
        int targetY = minimumPlaceY.get() + 2;
        if (mc.player.getBlockPos().getY() >= targetY) {
            getBaritone().getPathingBehavior().cancelEverything();
            transitionTo(State.PREPARE_UNLOAD, 0);
            return;
        }
        if (minYTicks > 200) {
            warning("Min-Y timeout. proceeding anyway.");
            getBaritone().getPathingBehavior().cancelEverything();
            transitionTo(State.PREPARE_UNLOAD, 0);
        }
    }

    private void prepareUnload() {
        getBaritone().getPathingBehavior().cancelEverything();
        getBaritone().getMineProcess().cancel();
        exchangedEnderSlot = -1;
        currentStoreSlot = -1;
        storeAttempts = 0;
        failedSlots.clear();
        itemsMoved = 0;
        shulkerPlaceAttempts = 0;
        enderChestPlaceAttempts = 0;
        storeToolIndex = 0;

        if (mc.player.getBlockPos().getY() < minimumPlaceY.get() + 2) {
            transitionTo(State.MOVE_TO_MIN_Y, 0);
            return;
        }

        BlockPos playerPos = mc.player.getBlockPos();
        Direction facing = mc.player.getHorizontalFacing();
        BlockPos preferred = playerPos.offset(facing);
        enderChestPos = isValidPlacementColumn(preferred) ? preferred : findPlacementSpot(playerPos, null);
        if (enderChestPos == null) {
            skipUnload("No breakable spot for EC.");
            return;
        }
        transitionTo(State.CLEAR_ENDER_CHEST_SPOT, unloadDelay.get());
    }

    private boolean isFullyCleared(BlockPos pos) {
        return mc.world.getBlockState(pos).isAir() && mc.world.getBlockState(pos.up()).isAir()
            && mc.world.getBlockEntity(pos) == null && mc.world.getBlockEntity(pos.up()) == null;
    }

    private boolean isUnbreakableAt(BlockPos pos) {
        if (mc.world == null) return false;
        var state = mc.world.getBlockState(pos);
        if (state.isAir()) return false;
        return state.getHardness(mc.world, pos) < 0;
    }

    private boolean isValidPlacementColumn(BlockPos pos) {
        return !isUnbreakableAt(pos) && !isUnbreakableAt(pos.up());
    }

    private BlockPos findPlacementSpot(BlockPos playerPos, BlockPos exclude) {
        for (int dist = 1; dist <= 3; dist++) {
            for (Direction d : Direction.values()) {
                if (d.getAxis() == Direction.Axis.Y) continue;
                BlockPos candidate = playerPos.offset(d, dist);
                if (exclude != null && candidate.equals(exclude)) continue;
                if (isValidPlacementColumn(candidate)) return candidate;
            }
        }
        return null;
    }

    private void clearColumnWithBaritone(BlockPos pos) {
        if (!getBaritone().getBuilderProcess().isActive()) {
            getBaritone().getBuilderProcess().clearArea(pos, pos.up());
        }
    }

    private void clearSingleBlockWithBaritone(BlockPos pos) {
        if (!getBaritone().getBuilderProcess().isActive()) {
            getBaritone().getBuilderProcess().clearArea(pos, pos);
        }
    }

    private void rightClickBlock(BlockPos pos) {
        Vec3d hit = Vec3d.ofCenter(pos);
        BlockHitResult result = new BlockHitResult(hit, Direction.UP, pos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, result);
    }

    private void rightClickBlockWithRotation(BlockPos pos, Direction face) {
        Vec3d center = Vec3d.ofCenter(pos);
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = center.subtract(eye);
        double horiz = Math.sqrt(look.x * look.x + look.z * look.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) Math.toDegrees(Math.atan2(-look.y, horiz));
        Rotations.rotate(yaw, pitch, 50, () -> {
            BlockHitResult hit = new BlockHitResult(center, face, pos, false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        });
    }

    private boolean isScreenHandlerOfType(ScreenHandlerType<?> type) {
        if (mc.player == null || mc.player.currentScreenHandler == null) return false;
        try { return mc.player.currentScreenHandler.getType() == type; }
        catch (UnsupportedOperationException e) { return false; }
    }

    private void clearEnderChestSpot() {
        if (isFullyCleared(enderChestPos)) {
            getBaritone().getPathingBehavior().cancelEverything();
            transitionTo(State.PLACE_ENDER_CHEST, unloadDelay.get());
            return;
        }
        if (!isValidPlacementColumn(enderChestPos)) {
            warning("EC spot unbreakable, looking for another.");
            BlockPos newSpot = findPlacementSpot(mc.player.getBlockPos(), enderChestPos);
            if (newSpot == null) { skipUnload("No alternative EC spot."); return; }
            enderChestPos = newSpot;
            waitTicks(unloadDelay.get());
            return;
        }
        if (!mc.player.getBlockPos().isWithinDistance(enderChestPos, 3)) {
            getBaritone().getCustomGoalProcess().setGoalAndPath(
                new GoalNear(new BetterBlockPos(enderChestPos), 2));
            waitTicks(unloadDelay.get());
            return;
        }
        getBaritone().getPathingBehavior().cancelEverything();
        clearColumnWithBaritone(enderChestPos);
        waitTicks(unloadDelay.get());
    }

    private void placeEnderChest() {
        int ecSlot = enderChestHotbarSlot.get();
        ItemStack cur = mc.player.getInventory().getStack(ecSlot);
        if (cur.getItem() != Items.ENDER_CHEST) {
            FindItemResult found = InvUtils.find(Items.ENDER_CHEST);
            if (found.found() && found.slot() != ecSlot) {
                InvUtils.move().from(found.slot()).toHotbar(ecSlot);
            }
        }
        InvUtils.swap(ecSlot, false);

        BlockPos below = enderChestPos.down();
        if (!mc.world.getBlockState(below).isSolidBlock(mc.world, below)) {
            if (enableThrowaway.get()) {
                foundationPos = below;
                stateAfterFoundation = State.PLACE_ENDER_CHEST;
                transitionTo(State.PLACE_FOUNDATION, 0);
                return;
            } else {
                skipUnload("No ground for EC and throwaway disabled.");
                return;
            }
        }

        rightClickBlockWithRotation(below, Direction.UP);
        ensureToolReady();
        transitionTo(State.CHECK_ENDER_CHEST_PLACED, unloadDelay.get());
    }

    private void checkEnderChestPlaced() {
        if (mc.world.getBlockState(enderChestPos).getBlock() == Blocks.ENDER_CHEST) {
            enderChestPlaceAttempts = 0;
            transitionTo(State.VERIFY_ENDER_CHEST_CLEARANCE, unloadDelay.get());
            return;
        }
        warning("EC not placed, retrying...");
        enderChestPlaceAttempts++;
        if (enderChestPlaceAttempts >= MAX_PLACE_ATTEMPTS) {
            warning("EC placement failed repeatedly.");
            BlockPos newSpot = findPlacementSpot(mc.player.getBlockPos(), enderChestPos);
            if (newSpot == null) { skipUnload("No alternative EC spot."); return; }
            enderChestPos = newSpot;
            enderChestPlaceAttempts = 0;
            transitionTo(State.CLEAR_ENDER_CHEST_SPOT, unloadDelay.get());
            return;
        }
        transitionTo(State.PLACE_ENDER_CHEST, unloadDelay.get());
    }

    private void verifyEnderChestClearance() {
        if (mc.world.getBlockState(enderChestPos).getBlock() != Blocks.ENDER_CHEST) {
            warning("EC disappeared, replacing...");
            transitionTo(State.CLEAR_ENDER_CHEST_SPOT, unloadDelay.get());
            return;
        }
        BlockPos above = enderChestPos.up();
        boolean aboveClear = mc.world.getBlockState(above).isAir()
            && mc.world.getBlockEntity(above) == null;
        if (aboveClear) { transitionTo(State.OPEN_ENDER_CHEST, unloadDelay.get()); return; }
        if (isUnbreakableAt(above)) {
            warning("Block above EC unbreakable, opening anyway.");
            transitionTo(State.OPEN_ENDER_CHEST, unloadDelay.get());
            return;
        }
        getBaritone().getPathingBehavior().cancelEverything();
        clearSingleBlockWithBaritone(above);
        waitTicks(unloadDelay.get());
    }

    private void openEnderChest() {
        rightClickBlockWithRotation(enderChestPos, Direction.UP);
        transitionTo(State.CHECK_ENDER_CHEST_OPENED, unloadDelay.get());
    }

    private void checkEnderChestOpened() {
        if (isScreenHandlerOfType(ScreenHandlerType.GENERIC_9X3))
            transitionTo(State.TAKE_EMPTY_SHULKER, unloadDelay.get());
        else {
            if (stateTicks % 8 == 0) rightClickBlockWithRotation(enderChestPos, Direction.UP);
            transitionTo(State.OPEN_ENDER_CHEST, unloadDelay.get());
        }
    }

    private void startTakeShulker() {
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (handler == null) {
            warning("GUI lost.");
            mc.player.closeHandledScreen();
            transitionTo(State.MINING, 0);
            return;
        }
        int shulkerSlot = -1;
        for (int i = 0; i < 27; i++) {
            ItemStack s = handler.getSlot(i).getStack();
            if (!isShulkerBox(s)) continue;
            if (toolRetrieval) {
                if (isContainerNotEmpty(s) && hasUsableToolInShulker(s)) { shulkerSlot = i; break; }
            } else {
                if (isContainerEmpty(s)) { shulkerSlot = i; break; }
            }
        }
        if (shulkerSlot == -1) {
            mc.player.closeHandledScreen();
            if (toolRetrieval) {
                toolRetrieval = false;
                storeToolIndex = 0;
                outOfTools();
            } else if (disconnectNoShulkers.get()) {
                error("No empty shulker. disconnecting.");
                toggle();
                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().getConnection().disconnect(
                        Text.literal("[Miner] No empty shulkers"));
                }
            } else {
                warning("No empty shulker in EC.");
                transitionTo(State.BREAK_ENDER_CHEST, unloadDelay.get());
            }
            return;
        }
        exchangedEnderSlot = shulkerSlot;
        mc.interactionManager.clickSlot(handler.syncId, shulkerSlot,
            shulkerHotbarSlot.get(), SlotActionType.SWAP, mc.player);
        transitionTo(State.CHECK_SHULKER_TAKEN, unloadDelay.get());
    }

    private boolean hasUsableToolInShulker(ItemStack shulker) {
        ContainerComponent c = shulker.get(DataComponentTypes.CONTAINER);
        if (c == null) return false;
        return c.stream().anyMatch(this::isUsableTool);
    }

    private void checkShulkerTaken() {
        ItemStack stack = mc.player.getInventory().getStack(shulkerHotbarSlot.get());
        boolean taken = isShulkerBox(stack);
        if (taken) {
            mc.player.closeHandledScreen();
            transitionTo(State.CLOSE_ENDER_CHEST, unloadDelay.get());
        } else {
            warning("Shulker not taken, retrying...");
            transitionTo(State.TAKE_EMPTY_SHULKER, unloadDelay.get());
        }
    }

    private void closeEnderChest() {
        mc.player.closeHandledScreen();
        BlockPos playerPos = mc.player.getBlockPos();
        Direction facing = mc.player.getHorizontalFacing();
        Direction[] offsets = { facing.rotateYClockwise(), facing.rotateYCounterclockwise() };
        shulkerPos = null;
        for (Direction dir : offsets) {
            BlockPos candidate = playerPos.offset(dir);
            if (!candidate.equals(enderChestPos) && isValidPlacementColumn(candidate)) {
                shulkerPos = candidate;
                break;
            }
        }
        if (shulkerPos == null) shulkerPos = findPlacementSpot(playerPos, enderChestPos);
        if (shulkerPos == null) {
            warning("No breakable spot for shulker.");
            transitionTo(State.BREAK_ENDER_CHEST, unloadDelay.get());
            return;
        }
        transitionTo(State.CLEAR_SHULKER_SPOT, unloadDelay.get());
    }

    private void clearShulkerSpot() {
        shulkerPlaceAttempts = 0;
        if (isFullyCleared(shulkerPos)) {
            getBaritone().getPathingBehavior().cancelEverything();
            transitionTo(State.PLACE_SHULKER, unloadDelay.get());
            return;
        }
        if (!isValidPlacementColumn(shulkerPos)) {
            warning("Shulker spot unbreakable, looking for another.");
            BlockPos newSpot = findPlacementSpot(mc.player.getBlockPos(), enderChestPos);
            if (newSpot == null || newSpot.equals(shulkerPos)) {
                warning("No alternative shulker spot.");
                transitionTo(State.BREAK_ENDER_CHEST, unloadDelay.get());
                return;
            }
            shulkerPos = newSpot;
            waitTicks(unloadDelay.get());
            return;
        }
        if (!mc.player.getBlockPos().isWithinDistance(shulkerPos, 3)) {
            getBaritone().getCustomGoalProcess().setGoalAndPath(
                new GoalNear(new BetterBlockPos(shulkerPos), 2));
            waitTicks(unloadDelay.get());
            return;
        }
        getBaritone().getPathingBehavior().cancelEverything();
        clearColumnWithBaritone(shulkerPos);
        waitTicks(unloadDelay.get());
    }

    private void placeShulker() {
        ItemStack shulkerStack = mc.player.getInventory().getStack(shulkerHotbarSlot.get());
        if (!isShulkerBox(shulkerStack)) {
            warning("Shulker lost, aborting.");
            transitionTo(State.MINING, 0);
            return;
        }
        BlockPos below = shulkerPos.down();
        if (!mc.world.getBlockState(below).isSolidBlock(mc.world, below)) {
            if (enableThrowaway.get()) {
                foundationPos = below;
                stateAfterFoundation = State.PLACE_SHULKER;
                transitionTo(State.PLACE_FOUNDATION, 0);
                return;
            } else {
                warning("No ground for shulker and throwaway disabled.");
                transitionTo(State.MINING, 0);
                return;
            }
        }
        InvUtils.swap(shulkerHotbarSlot.get(), false);
        rightClickBlockWithRotation(below, Direction.UP);
        ensureToolReady();
        transitionTo(State.CHECK_SHULKER_PLACED, unloadDelay.get());
    }

    private void checkShulkerPlaced() {
        if (mc.world.getBlockState(shulkerPos).getBlock() instanceof ShulkerBoxBlock) {
            shulkerPlaceAttempts = 0;
            transitionTo(State.OPEN_SHULKER, unloadDelay.get());
            return;
        }
        warning("Shulker not placed, retrying...");
        shulkerPlaceAttempts++;
        if (shulkerPlaceAttempts >= MAX_PLACE_ATTEMPTS) {
            warning("Shulker placement failed repeatedly.");
            transitionTo(State.MINING, 0);
            startMiningTargetBlocks();
            return;
        }
        transitionTo(State.PLACE_SHULKER, unloadDelay.get() * 2);
    }

    private void openShulker() {
        rightClickBlockWithRotation(shulkerPos, Direction.UP);
        transitionTo(State.CHECK_SHULKER_OPENED, unloadDelay.get());
    }

    private void checkShulkerOpened() {
        if (isScreenHandlerOfType(ScreenHandlerType.SHULKER_BOX)) {
            currentStoreSlot = -1;
            storeAttempts = 0;
            failedSlots.clear();
            itemsMoved = 0;
            storeToolIndex = 0;
            transitionTo(State.STORE_ITEMS, unloadDelay.get());
        } else {
            if (stateTicks % 8 == 0) rightClickBlockWithRotation(shulkerPos, Direction.UP);
            transitionTo(State.OPEN_SHULKER, unloadDelay.get());
        }
    }

    private void storeItems() {
        if (!isScreenHandlerOfType(ScreenHandlerType.SHULKER_BOX)) {
            mc.player.closeHandledScreen();
            currentStoreSlot = -1;
            transitionTo(State.MINING, 0);
            return;
        }
        ScreenHandler handler = mc.player.currentScreenHandler;

        if (toolRetrieval) {
            storeToolsFromShulker(handler);
            return;
        }

        if (isShulkerFull(handler)) {
            info("Shulker full, finishing.");
            currentStoreSlot = -1;
            transitionTo(State.CLOSE_SHULKER, unloadDelay.get());
            return;
        }

        if (currentStoreSlot == -1 || failedSlots.contains(currentStoreSlot)) {
            currentStoreSlot = -1;
            storeAttempts = 0;
            for (int i = 27; i <= 62; i++) {
                if (failedSlots.contains(i)) continue;
                ItemStack s = handler.getSlot(i).getStack();
                if (!s.isEmpty() && isTargetItem(s.getItem())) {
                    currentStoreSlot = i; storeAttempts = 0; break;
                }
            }
            if (currentStoreSlot == -1) {
                if (itemsMoved == 0) {
                    warning("No items moved. Disabling to avoid loop.");
                    mc.player.closeHandledScreen();
                    toggle();
                    return;
                }
                info("No target items left to store.");
                transitionTo(State.CLOSE_SHULKER, unloadDelay.get());
                return;
            }
        }

        ItemStack s = handler.getSlot(currentStoreSlot).getStack();
        if (s.isEmpty() || !isTargetItem(s.getItem())) {
            currentStoreSlot = -1;
            storeAttempts = 0;
            waitTicks(unloadDelay.get());
            return;
        }
        if (storeAttempts >= 10) {
            warning("Store failed for slot " + currentStoreSlot + ", skipping.");
            failedSlots.add(currentStoreSlot);
            currentStoreSlot = -1;
            storeAttempts = 0;
            waitTicks(unloadDelay.get() * 2);
            return;
        }
        mc.interactionManager.clickSlot(handler.syncId, currentStoreSlot, 0,
            SlotActionType.QUICK_MOVE, mc.player);
        storeAttempts++;
        itemsMoved++;
        waitTicks(unloadDelay.get() * 2 + random.nextInt(5));
    }

    /**
     * Tool retrieval: shift-click all usable tools from shulker slots 0-26
     * into player inventory. Linear scan, one slot per tick, no re-check to
     * avoid duplicate clicks under 2b2t ping.
     */
    private void storeToolsFromShulker(ScreenHandler handler) {
        if (storeToolIndex >= 27) {
            debug("Tool retrieval complete.");
            storeToolIndex = 0;
            mc.player.closeHandledScreen();
            transitionTo(State.CLOSE_SHULKER, unloadDelay.get());
            return;
        }
        ItemStack s = handler.getSlot(storeToolIndex).getStack();
        if (isUsableTool(s)) {
            mc.interactionManager.clickSlot(handler.syncId, storeToolIndex, 0,
                SlotActionType.QUICK_MOVE, mc.player);
            storeToolIndex++;
            waitTicks(unloadDelay.get());
            return;
        }
        storeToolIndex++;
        waitTicks(1);
    }

    private boolean isShulkerFull(ScreenHandler handler) {
        for (int i = 0; i < 27; i++) if (handler.getSlot(i).getStack().isEmpty()) return false;
        return true;
    }

    private void closeShulker() {
        mc.player.closeHandledScreen();
        transitionTo(State.BREAK_SHULKER, unloadDelay.get());
    }

    private void breakShulker() {
        getBaritone().getPathingBehavior().cancelEverything();
        getBaritone().getMineProcess().cancel();
        transitionTo(State.CHECK_SHULKER_BROKEN, unloadDelay.get());
    }

    private void checkShulkerBroken() {
        if (mc.world.getBlockState(shulkerPos).isAir() && mc.world.getBlockEntity(shulkerPos) == null) {
            getBaritone().getPathingBehavior().cancelEverything();
            transitionTo(State.PICKUP_SHULKER, unloadDelay.get());
            return;
        }
        clearColumnWithBaritone(shulkerPos);
        waitTicks(unloadDelay.get());
    }

    private void pickupShulker() {
        ItemStack picked = findRelevantShulker();
        if (picked != null) {
            finishPickupIfNeeded();
            moveShulkerToExchangeSlot(picked);
            transitionTo(State.OPEN_ENDER_CHEST_AGAIN, unloadDelay.get());
            return;
        }

        if (!pickupInProgress) {
            pickupOldAllowBreak = BaritoneAPI.getSettings().allowBreak.value;
            BaritoneAPI.getSettings().allowBreak.value = false;
            pickupInProgress = true;
            info("Pickup mode: block break disabled.");
        }

        List<ItemEntity> items = mc.world.getEntitiesByClass(ItemEntity.class,
            mc.player.getBoundingBox().expand(3), e -> isShulkerBox(e.getStack()));
        if (!items.isEmpty()) {
            getBaritone().getCustomGoalProcess().setGoalAndPath(
                new GoalBlock(new BetterBlockPos(items.get(0).getBlockPos())));
        }

        picked = findRelevantShulker();
        if (picked != null) {
            getBaritone().getCustomGoalProcess().setGoal(null);
            finishPickupIfNeeded();
            moveShulkerToExchangeSlot(picked);
            transitionTo(State.OPEN_ENDER_CHEST_AGAIN, unloadDelay.get());
        } else {
            waitTicks(unloadDelay.get());
        }
    }

    private void finishPickupIfNeeded() {
        if (pickupInProgress) {
            BaritoneAPI.getSettings().allowBreak.value = pickupOldAllowBreak;
            pickupInProgress = false;
        }
    }

    private void openEnderChestAgain() {
        rightClickBlockWithRotation(enderChestPos, Direction.UP);
        transitionTo(State.CHECK_ENDER_CHEST_OPENED_AGAIN, unloadDelay.get());
    }

    private void checkEnderChestOpenedAgain() {
        if (isScreenHandlerOfType(ScreenHandlerType.GENERIC_9X3))
            transitionTo(State.PUT_SHULKER_BACK, unloadDelay.get());
        else {
            if (stateTicks % 8 == 0) rightClickBlockWithRotation(enderChestPos, Direction.UP);
            transitionTo(State.OPEN_ENDER_CHEST_AGAIN, unloadDelay.get());
        }
    }

    private void startPutShulkerBack() {
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (handler == null) {
            mc.player.closeHandledScreen();
            transitionTo(State.MINING, 0);
            return;
        }
        ItemStack hotbarStack = mc.player.getInventory().getStack(shulkerHotbarSlot.get());

        boolean validShulker = isShulkerBox(hotbarStack)
            && (toolRetrieval || !isContainerEmpty(hotbarStack));

        if (!validShulker) {
            ItemStack filled = findRelevantShulker();
            if (filled == null) {
                warning("Shulker not found.");
                mc.player.closeHandledScreen();
                transitionTo(State.MINING, 0);
                return;
            }
            moveShulkerToExchangeSlot(filled);
            waitTicks(unloadDelay.get());
            return;
        }

        if (!toolRetrieval) {
            ContainerComponent c = hotbarStack.get(DataComponentTypes.CONTAINER);
            int occupied = c == null ? 0 : (int) c.stream().filter(s -> !s.isEmpty()).count();
            if (occupied < 27 && countTargetsInPlayerInv() > 0) {
                mc.player.closeHandledScreen();
                transitionTo(State.CLEAR_SHULKER_SPOT, unloadDelay.get());
                return;
            }
        }

        int targetEnderSlot = exchangedEnderSlot;
        if (targetEnderSlot < 0) {
            for (int i = 0; i < 27; i++) {
                if (!handler.getSlot(i).getStack().isEmpty()) { targetEnderSlot = i; break; }
            }
        }
        if (targetEnderSlot < 0) {
            warning("No target slot in EC.");
            mc.player.closeHandledScreen();
            transitionTo(State.MINING, 0);
            return;
        }
        mc.interactionManager.clickSlot(handler.syncId, targetEnderSlot,
            shulkerHotbarSlot.get(), SlotActionType.SWAP, mc.player);
        transitionTo(State.CHECK_SHULKER_BACK, unloadDelay.get());
    }

    private int countTargetsInPlayerInv() {
        int count = 0;
        for (int i = 9; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (isTargetItem(s.getItem())) count += s.getCount();
        }
        return count;
    }

    private void checkShulkerBack() {
        if (findRelevantShulker() == null) {
            mc.player.closeHandledScreen();
            exchangedEnderSlot = -1;
            transitionTo(State.CLOSE_ENDER_CHEST_AGAIN, unloadDelay.get());
        } else {
            warning("Shulker still in inventory, retrying...");
            transitionTo(State.PUT_SHULKER_BACK, unloadDelay.get());
        }
    }

    private void closeEnderChestAgain() {
        mc.player.closeHandledScreen();
        transitionTo(State.BREAK_ENDER_CHEST, unloadDelay.get());
    }

    private void breakEnderChest() {
        getBaritone().getPathingBehavior().cancelEverything();
        getBaritone().getMineProcess().cancel();
        transitionTo(State.CHECK_ENDER_CHEST_BROKEN, unloadDelay.get());
    }

    private void checkEnderChestBroken() {
        if (mc.world.getBlockState(enderChestPos).isAir() && mc.world.getBlockEntity(enderChestPos) == null) {
            getBaritone().getPathingBehavior().cancelEverything();
            transitionTo(State.RETURN_TO_MINING, unloadDelay.get());
            return;
        }
        clearColumnWithBaritone(enderChestPos);
        waitTicks(unloadDelay.get());
    }

    private void returnToMining() {
        getBaritone().getPathingBehavior().cancelEverything();
        getBaritone().getMineProcess().cancel();
        if (toolRetrieval) {
            consecutiveToolFlows = 0;
            toolRetrieval = false;
            storeToolIndex = 0;
            info("Tool retrieval complete, resuming mining.");
        } else {
            info("Unload complete, resuming mining.");
        }
        ensureToolReady();
        ignoreUnloadUntil = System.currentTimeMillis() + 15000;
        if (wasNukerActive) {
            Nuker nuker = Modules.get().get(Nuker.class);
            if (nuker != null && !nuker.isActive()) nuker.toggle();
            wasNukerActive = false;
        }
        transitionTo(State.MINING, 0);
        startMiningTargetBlocks();
    }

    private void skipUnload(String reason) {
        warning(reason + " Skipping unload for 15s.");
        toolRetrieval = false;
        storeToolIndex = 0;
        ignoreUnloadUntil = System.currentTimeMillis() + 15000;
        transitionTo(State.MINING, 0);
        startMiningTargetBlocks();
    }

    private void tickPlaceFoundation() {
        if (foundationPos == null) {
            restoreFoundationFlags();
            State next = stateAfterFoundation != null ? stateAfterFoundation : State.MINING;
            stateAfterFoundation = null;
            transitionTo(next, actionDelay.get());
            return;
        }
        int slot = throwawayHotbarSlot.get();
        ItemStack s = mc.player.getInventory().getStack(slot);
        if (s.isEmpty() || s.getItem() != throwawayBlock.get().asItem()) {
            moveThrowawayToSlot();
            s = mc.player.getInventory().getStack(slot);
            if (s.isEmpty() || s.getItem() != throwawayBlock.get().asItem()) {
                warning("No throwaway for foundation.");
                restoreFoundationFlags();
                State next = stateAfterFoundation != null ? stateAfterFoundation : State.MINING;
                stateAfterFoundation = null;
                transitionTo(next, actionDelay.get());
                return;
            }
        }
        InvUtils.swap(slot, false);

        Settings settings = BaritoneAPI.getSettings();
        if (!foundationFlagsForced) {
            foundationSavedPlace = settings.allowPlace.value;
            foundationSavedBreak = settings.allowBreak.value;
            foundationFlagsForced = true;
        }
        settings.allowPlace.value = true;
        settings.allowBreak.value = false;

        ISchematic sch = new FillSchematic(1, 1, 1, throwawayBlock.get().getDefaultState());
        getBaritone().getBuilderProcess().build("foundation", sch, foundationPos);
        foundationTicks = 0;
        transitionTo(State.WAIT_FOUNDATION, 0);
    }

    private void tickWaitFoundation() {
        foundationTicks++;
        if (foundationTicks > 100) {
            warning("Foundation timeout.");
            if (getBaritone().getBuilderProcess().isActive()) {
                getBaritone().getPathingBehavior().cancelEverything();
            }
            restoreFoundationFlags();
            State next = stateAfterFoundation != null ? stateAfterFoundation : State.MINING;
            foundationPos = null;
            stateAfterFoundation = null;
            transitionTo(next, actionDelay.get());
            return;
        }
        if (foundationPos != null
            && mc.world.getBlockState(foundationPos).isSolidBlock(mc.world, foundationPos)) {
            if (getBaritone().getBuilderProcess().isActive()) {
                getBaritone().getPathingBehavior().cancelEverything();
            }
            restoreFoundationFlags();
            State next = stateAfterFoundation != null ? stateAfterFoundation : State.MINING;
            foundationPos = null;
            stateAfterFoundation = null;
            transitionTo(next, actionDelay.get());
        }
    }

    private void restoreFoundationFlags() {
        if (foundationFlagsForced) {
            foundationFlagsForced = false;
            Settings settings = BaritoneAPI.getSettings();
            settings.allowPlace.value = foundationSavedPlace;
            settings.allowBreak.value = foundationSavedBreak;
        }
    }

    private int invToPlayerHandlerSlot(int invSlot) {
        return invSlot < 9 ? 36 + invSlot : invSlot;
    }

    public boolean isProtectedItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (isTargetItem(item)) return true;
        if (enableThrowaway.get() && item == throwawayBlock.get().asItem()) return true;
        if (isMainToolType(stack)) return true;
        if (stack.isIn(ItemTags.SHULKER_BOXES) && isContainerEmpty(stack)) return true;
        return item == Items.ENDER_CHEST;
    }

    private IBaritone getBaritone() { return BaritoneAPI.getProvider().getPrimaryBaritone(); }

    private void info(String msg) { ChatUtils.info("[Miner] " + msg); }
    private void warning(String msg) { ChatUtils.warning("[Miner] " + msg); }
    private void error(String msg) { ChatUtils.error("[Miner] " + msg); }
    private void debug(String msg) { if (debug.get()) ChatUtils.info("[Miner] " + msg); }
}
