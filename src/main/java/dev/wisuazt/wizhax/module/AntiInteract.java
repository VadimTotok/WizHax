package dev.wisuazt.wizhax.module;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.wisuazt.wizhax.WizHax;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

public class AntiInteract extends Module {
    private static final List<Map.Entry<String, String>> DYE_COLORS = List.of(
        Map.entry("light_blue", "§b"),
        Map.entry("light_gray", "§7"),
        Map.entry("magenta",    "§d"),
        Map.entry("orange",     "§6"),
        Map.entry("yellow",     "§e"),
        Map.entry("lime",       "§a"),
        Map.entry("pink",       "§d"),
        Map.entry("cyan",       "§3"),
        Map.entry("purple",     "§5"),
        Map.entry("blue",       "§9"),
        Map.entry("brown",      "§6"),
        Map.entry("green",      "§2"),
        Map.entry("red",        "§c"),
        Map.entry("black",      "§0"),
        Map.entry("white",      "§f"),
        Map.entry("gray",       "§8")
    );

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final Setting<Boolean> beds = sgGeneral.add(new BoolSetting.Builder()
        .name("beds")
        .description("Stops you from interacting with beds.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> respawnAnchors = sgGeneral.add(new BoolSetting.Builder()
        .name("respawn-anchors")
        .description("Stops you from interacting with respawn anchors.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> coloredMessages = sgGeneral.add(new BoolSetting.Builder()
        .name("colored-messages")
        .description("Color the chat message to match the block's dye color (red bed → red text).")
        .defaultValue(true)
        .build());

    private final Setting<List<Block>> blockList = sgGeneral.add(new BlockListSetting.Builder()
        .name("block-list")
        .description("Additional blocks to stop interacting with.")
        .defaultValue()
        .build());

    public AntiInteract() {
        super(WizHax.WIZHAX, "[WizHax] AntiInteract",
            "Prevents you from interacting with certain blocks e.g. beds and respawn anchors.");
    }

    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (mc.player == null || mc.world == null) return;

        BlockPos pos = event.result.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);
        Block block = state.getBlock();

        boolean blocked;
        if (beds.get() && block instanceof BedBlock) {
            blocked = true;
        } else if (respawnAnchors.get() && block instanceof RespawnAnchorBlock) {
            blocked = true;
        } else {
            blocked = blockList.get().contains(block);
        }

        if (blocked) {
            event.cancel();
            String color = coloredMessages.get() ? colorCodeFor(block) : "§c";
            ChatUtils.info(color + "Prevented interaction with " + block.getName().getString() + "§r");
        }
    }

    private String colorCodeFor(Block block) {
        if (block instanceof RespawnAnchorBlock) return "§5";

        String key = block.getTranslationKey().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> dye : DYE_COLORS) {
            if (key.contains(dye.getKey())) return dye.getValue();
        }
        return "§e";
    }
}
