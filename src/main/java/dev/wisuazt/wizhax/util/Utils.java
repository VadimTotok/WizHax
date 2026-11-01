package dev.wisuazt.wizhax.util;

import dev.wisuazt.wizhax.mixin.accessor.PlayerInventoryAccessor;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownServiceException;
import java.time.Instant;
import java.util.Random;
import javax.net.ssl.HttpsURLConnection;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.common.ClientOptionsC2SPacket;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public class Utils {
    private static final Random RANDOM = new Random();
    public static final boolean XAERO_AVAILABLE = FabricLoader.getInstance().isModLoaded("xaeroworldmap")
        && FabricLoader.getInstance().isModLoaded("xaerominimap");

    public static String rCC() {
        String color = "§7";
        TextColor[] colors = TextColor.values();
        while (color.equals("§0") || color.equals("§8") || color.equals("§7")) {
            int luckyIndex = RANDOM.nextInt(colors.length);
            color = colors[luckyIndex].label;
        }
        return color;
    }

    public static void illegalDisconnect(boolean disableAutoReconnect, IllegalDisconnectMethod method) {
        if (!meteordevelopment.meteorclient.utils.Utils.canUpdate()) return;
        if (disableAutoReconnect) disableAutoReconnect();

        MinecraftClient mc = MeteorClient.mc;
        try {
            switch (method) {
                case Slot -> {
                    if (mc.getNetworkHandler() != null) {
                        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(-69));
                    }
                }
                case Chat -> {
                    if (mc.getNetworkHandler() != null) {
                        mc.getNetworkHandler().sendPacket(new ChatMessageC2SPacket(
                            "§", Instant.now(), 0L, null, null
                        ));
                    }
                }
                case Interact -> {
                    if (mc.getNetworkHandler() != null && mc.player != null) {
                        mc.getNetworkHandler().sendPacket(
                            PlayerInteractEntityC2SPacket.interact(mc.player, false, Hand.MAIN_HAND)
                        );
                    }
                }
                case Movement -> {
                    if (mc.getNetworkHandler() != null) {
                        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                            Double.NaN, Double.NaN, Double.NaN, false, false
                        ));
                    }
                }
                case SequenceBreak -> {
                    if (mc.getNetworkHandler() != null) {
                        mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(
                            Hand.MAIN_HAND, -420, 13.37F, 69.69F
                        ));
                    }
                }
                case InvalidSettings -> {
                    if (mc.getNetworkHandler() != null) {
                        SyncedClientOptions opts = mc.options.getSyncedOptions();
                        mc.getNetworkHandler().sendPacket(new ClientOptionsC2SPacket(opts));
                    }
                }
            }
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().getConnection().disconnect(Text.literal("[WizHax] Illegal Disconnect"));
            }
        } catch (Exception e) {
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().getConnection().disconnect(Text.literal("[WizHax] Disconnect"));
            }
        }
    }

    public static void disableAutoReconnect() {
        Modules mods = Modules.get();
        if (mods != null) {
            AutoReconnect atrc = mods.get(AutoReconnect.class);
            if (atrc != null && atrc.isActive()) atrc.toggle();
        }
    }

    public static int firework(MinecraftClient mc, boolean elytraRequired) {
        if (mc.player == null || mc.interactionManager == null) return -1;

        int elytraSwapSlot = -1;
        if (elytraRequired && !mc.player.getInventory().getStack(38).isOf(Items.ELYTRA)) {
            FindItemResult itemResult = InvUtils.findInHotbar(Items.ELYTRA);
            if (!itemResult.found()) return -1;
            elytraSwapSlot = itemResult.slot();
            InvUtils.swap(itemResult.slot(), true);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            InvUtils.swapBack();
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(
                    mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING
                ));
            }
        }

        FindItemResult itemResult = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
        if (itemResult.found()) {
            if (itemResult.isOffhand()) {
                mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
                mc.player.swingHand(Hand.OFF_HAND);
            } else {
                InvUtils.swap(itemResult.slot(), true);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
                InvUtils.swapBack();
            }
            return elytraSwapSlot != -1 ? elytraSwapSlot : 200;
        }

        int movedSlot = -1;
        for (int n = 9; n < 36; n++) {
            Item item = mc.player.getInventory().getStack(n).getItem();
            if (item == Items.FIREWORK_ROCKET) {
                InvUtils.move().from(n).to(((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot());
                movedSlot = n;
                break;
            }
        }
        if (movedSlot != -1) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
            InvUtils.move().from(((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot()).to(movedSlot);
            return elytraSwapSlot != -1 ? elytraSwapSlot : 200;
        }
        return -1;
    }

    public static void setPressed(KeyBinding key, boolean pressed) {
        key.setPressed(pressed);
        Input.setKeyState(key, pressed);
    }

    public static Vec3d positionInDirection(Vec3d pos, double yaw, double distance) {
        Vec3d offset = yawToDirection(yaw).multiply(distance);
        return pos.add(offset);
    }

    public static Vec3d yawToDirection(double yaw) {
        yaw = yaw * Math.PI / 180.0;
        double x = -Math.sin(yaw);
        double z = Math.cos(yaw);
        return new Vec3d(x, 0.0, z);
    }

    public static double distancePointToDirection(Vec3d point, Vec3d direction, @Nullable Vec3d start) {
        if (start == null) start = Vec3d.ZERO;
        point = point.multiply(new Vec3d(1.0, 0.0, 1.0));
        start = start.multiply(new Vec3d(1.0, 0.0, 1.0));
        direction = direction.multiply(new Vec3d(1.0, 0.0, 1.0));
        Vec3d directionVec = point.subtract(start);
        double projectionLength = directionVec.dotProduct(direction) / direction.lengthSquared();
        Vec3d projection = direction.multiply(projectionLength);
        Vec3d perp = directionVec.subtract(projection);
        return perp.length();
    }

    public static double angleOnAxis(double yaw) {
        if (yaw < 0.0) yaw += 360.0;
        return Math.round(yaw / 45.0) * 45L;
    }

    public static float smoothRotation(double current, double target, double rotationScaling) {
        double difference = angleDifference(target, current);
        return (float) (current + difference * rotationScaling);
    }

    public static double angleDifference(double target, double current) {
        double diff = (target - current + 180.0) % 360.0 - 180.0;
        return diff < -180.0 ? diff + 360.0 : diff;
    }

    public static void sendWebhook(String webhookURL, String title, String message, String pingID, String playerName) {
        String json = "{\"embeds\": [{\"title\": \"" + title
            + "\",\"description\": \"" + message
            + "\",\"color\": 15258703,\"footer\": {\"text\": \"From: "
            + playerName + "\"}}]}";
        sendRequest(webhookURL, json);
        if (pingID != null) {
            json = "{\"content\": \"<@" + pingID + ">\"}";
            sendRequest(webhookURL, json);
        }
    }

    public static void sendWebhookPing(String webhookURL, String roleId, String userId) {
        StringBuilder sb = new StringBuilder();
        if (roleId != null && !roleId.isBlank()) sb.append("<@&").append(roleId.trim()).append(">");
        if (userId != null && !userId.isBlank()) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append("<@").append(userId.trim()).append('>');
        }
        if (sb.isEmpty()) return;
        String json = "{\"content\": \"" + sb + "\"}";
        sendRequest(webhookURL, json);
    }

    private static void sendRequest(String webhookURL, String json) {
        try {
            URL url = URI.create(webhookURL).toURL();
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            con.addRequestProperty("Content-Type", "application/json");
            con.addRequestProperty("User-Agent", "Mozilla");
            con.setDoOutput(true);
            con.setRequestMethod("POST");
            OutputStream stream = con.getOutputStream();
            stream.write(json.getBytes());
            stream.flush();
            stream.close();
            con.getInputStream().close();
            con.disconnect();
        } catch (MalformedURLException | UnknownServiceException var5) {
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public enum IllegalDisconnectMethod {
        Slot, Chat, Interact, Movement, SequenceBreak, InvalidSettings;
    }

    public enum RainbowColor {
        Reds(new String[]{"§c", "§4"}),
        Yellows(new String[]{"§e", "§6"}),
        Greens(new String[]{"§a", "§2"}),
        Cyans(new String[]{"§b", "§3"}),
        Blues(new String[]{"§9", "§1"}),
        Purples(new String[]{"§d", "§5"});

        public final String[] labels;

        RainbowColor(String[] labels) {
            this.labels = labels;
        }

        public static RainbowColor getFirst() {
            return values()[RANDOM.nextInt(values().length)];
        }

        public static RainbowColor getNext(RainbowColor previous) {
            return switch (previous) {
                case Reds -> Yellows;
                case Yellows -> Greens;
                case Greens -> Cyans;
                case Cyans -> Blues;
                case Blues -> Purples;
                case Purples -> Reds;
            };
        }
    }

    public enum TextColor {
        Black("§0"), White("§f"), Gray("§8"), Light_Gray("§7"),
        Dark_Green("§2"), Green("§a"), Dark_Aqua("§3"), Aqua("§b"),
        Dark_Blue("§1"), Blue("§9"), Dark_Red("§4"), Red("§c"),
        Dark_Purple("§5"), Purple("§d"), Gold("§6"), Yellow("§e"),
        Random("");

        public final String label;

        TextColor(String label) {
            this.label = label;
        }
    }

    public enum TextFormat {
        Plain(""), Italic("§o"), Bold("§l"), Underline("§n"),
        Strikethrough("§m"), Obfuscated("§k");

        public final String label;

        TextFormat(String label) {
            this.label = label;
        }
    }
}
