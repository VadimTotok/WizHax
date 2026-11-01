package dev.wisuazt.wizhax.module;

import com.mojang.authlib.GameProfile;
import dev.wisuazt.wizhax.WizHax;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static dev.wisuazt.wizhax.util.Utils.sendWebhook;
import static dev.wisuazt.wizhax.util.Utils.sendWebhookPing;

public class DiscordNotifs extends Module {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWebhooks = settings.createGroup("Webhooks");
    private final SettingGroup sgPings = settings.createGroup("Pings");

    private final Setting<String> defaultWebhook = sgGeneral.add(new StringSetting.Builder()
        .name("default-webhook")
        .description("Fallback webhook used by any category that doesn't define its own.")
        .defaultValue("")
        .build());

    private final Setting<Integer> messageDelay = sgGeneral.add(new IntSetting.Builder()
        .name("message-delay")
        .description("Seconds between two consecutive messages.")
        .defaultValue(2).min(0).sliderMax(30)
        .build());

    private final Setting<Boolean> queueMessages = sgGeneral.add(new BoolSetting.Builder()
        .name("queue-messages")
        .description("Queue messages during the cooldown instead of dropping them.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> timestamp = sgGeneral.add(new BoolSetting.Builder()
        .name("timestamp")
        .description("Prepend an [HH:mm] timestamp to every message.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> logAll = sgGeneral.add(new BoolSetting.Builder()
        .name("all-messages")
        .description("Log every incoming message to the default webhook.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> login = sgGeneral.add(new BoolSetting.Builder()
        .name("login")
        .description("Log when you join a server.")
        .defaultValue(false)
        .visible(() -> !logAll.get())
        .build());

    private final Setting<Boolean> disconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("disconnect")
        .description("Log when you leave a server.")
        .defaultValue(false)
        .visible(() -> !logAll.get())
        .build());

    private final Setting<Boolean> playerRange = sgGeneral.add(new BoolSetting.Builder()
        .name("player-range")
        .description("Log players entering/leaving render distance.")
        .defaultValue(false)
        .visible(() -> !logAll.get())
        .build());

    private final Setting<Boolean> queue = sgGeneral.add(new BoolSetting.Builder()
        .name("2b2t-queue")
        .description("Log your position in queue.")
        .defaultValue(false)
        .visible(() -> !logAll.get())
        .build());

    private final Setting<Boolean> whisper = sgGeneral.add(new BoolSetting.Builder()
        .name("whisper")
        .description("Log private messages.")
        .defaultValue(false)
        .visible(() -> !logAll.get())
        .build());

    private final Setting<Boolean> chat = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-messages")
        .description("Log public chat messages.")
        .defaultValue(false)
        .visible(() -> !logAll.get())
        .build());

    private final Setting<Boolean> commands = sgGeneral.add(new BoolSetting.Builder()
        .name("commands-client-info")
        .description("Log commands and client messages.")
        .defaultValue(false)
        .visible(() -> !logAll.get())
        .build());

    private final Setting<Boolean> deathMessages = sgGeneral.add(new BoolSetting.Builder()
        .name("death-messages")
        .description("Log death messages.")
        .defaultValue(false)
        .visible(() -> !logAll.get())
        .build());

    private final Setting<String> loginWebhook = sgWebhooks.add(new StringSetting.Builder()
        .name("login").description("Login webhook (blank = default).")
        .defaultValue("").build());

    private final Setting<String> disconnectWebhook = sgWebhooks.add(new StringSetting.Builder()
        .name("disconnect").description("Disconnect webhook (blank = default).")
        .defaultValue("").build());

    private final Setting<String> playerRangeWebhook = sgWebhooks.add(new StringSetting.Builder()
        .name("player-range").description("Player range webhook (blank = default).")
        .defaultValue("").build());

    private final Setting<String> queueWebhook = sgWebhooks.add(new StringSetting.Builder()
        .name("queue").description("Queue webhook (blank = default).")
        .defaultValue("").build());

    private final Setting<String> whisperWebhook = sgWebhooks.add(new StringSetting.Builder()
        .name("whisper").description("Whisper webhook (blank = default).")
        .defaultValue("").build());

    private final Setting<String> chatWebhook = sgWebhooks.add(new StringSetting.Builder()
        .name("chat").description("Chat webhook (blank = default).")
        .defaultValue("").build());

    private final Setting<String> commandsWebhook = sgWebhooks.add(new StringSetting.Builder()
        .name("commands").description("Commands webhook (blank = default).")
        .defaultValue("").build());

    private final Setting<String> deathWebhook = sgWebhooks.add(new StringSetting.Builder()
        .name("death").description("Death webhook (blank = default).")
        .defaultValue("").build());

    private final Setting<Boolean> pingOnLogin = sgPings.add(new BoolSetting.Builder()
        .name("ping-on-login")
        .description("Ping on server join.")
        .defaultValue(false).build());

    private final Setting<Boolean> pingOnLogout = sgPings.add(new BoolSetting.Builder()
        .name("ping-on-logout")
        .description("Ping on server leave.")
        .defaultValue(false).build());

    private final Setting<String> pingRoleId = sgPings.add(new StringSetting.Builder()
        .name("role-id")
        .description("Discord role ID to ping (blank = skip).")
        .defaultValue("").build());

    private final Setting<String> pingUserId = sgPings.add(new StringSetting.Builder()
        .name("user-id")
        .description("Discord user ID to ping (blank = skip).")
        .defaultValue("").build());

    private final Queue<PendingMessage> messageQueue = new LinkedList<>();
    private final Set<GameProfile> playersInRange = ConcurrentHashMap.newKeySet();
    private int delayTimer = 0;
    private int lastQueuePos = -1;

    public DiscordNotifs() {
        super(WizHax.WIZHAX, "[WHax] DiscordNotifs", "Sends notifications to a Discord webhook.");
    }

    @Override
    public void onActivate() {
        messageQueue.clear();
        playersInRange.clear();
        delayTimer = 0;
        lastQueuePos = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (delayTimer > 0) {
            delayTimer--;
        } else if (queueMessages.get() && !messageQueue.isEmpty()) {
            PendingMessage pending = messageQueue.poll();
            delayTimer = messageDelay.get() * 20;
            flush(pending);
        }

        if (!playerRange.get() || mc.world == null || mc.player == null) return;

        Set<UUID> current = new HashSet<>();
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity player)) continue;
            if (entity.getUuid().equals(mc.player.getUuid())) continue;
            current.add(player.getUuid());
            if (playersInRange.add(player.getGameProfile())) {
                handleMessage(player.getGameProfile().name() + " has entered visual range!",
                    MessageType.PLAYER_RANGE);
            }
        }

        List<GameProfile> departed = new ArrayList<>();
        for (GameProfile profile : playersInRange) {
            if (!current.contains(profile.id())) departed.add(profile);
        }
        for (GameProfile profile : departed) {
            playersInRange.remove(profile);
            handleMessage(profile.name() + " has left visual range!", MessageType.PLAYER_RANGE);
        }
    }

    @EventHandler(priority = 999)
    private void onReceivePacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof SubtitleS2CPacket(Text text))) return;
        String message = text.getString();
        int idx = message.indexOf("Position in queue: ");
        if (idx == -1) return;
        int start = idx + 19;
        int end = start;
        while (end < message.length() && Character.isDigit(message.charAt(end))) end++;
        if (end == start) return;
        int queuePos;
        try {
            queuePos = Integer.parseInt(message.substring(start, end));
        } catch (NumberFormatException ignored) {
            return;
        }
        if (queuePos == lastQueuePos) return;
        handleMessage(message, MessageType.QUEUE);
        lastQueuePos = queuePos;
    }

    @EventHandler(priority = 999)
    private void onMessageReceive(ReceiveMessageEvent event) {
        Text message = event.getMessage();
        for (Text sibling : message.getSiblings()) {
            TextColor color = sibling.getStyle().getColor();
            if (color != null && color.getRgb() == 43690) {
                handleMessage(message.getString(), MessageType.DEATH);
                return;
            }
        }
        handleMessage(message.getString(), MessageType.NORMAL);
    }

    @EventHandler
    private void onGameJoin(GameJoinedEvent event) {
        handleMessage("Joined the game", MessageType.LOGIN);
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        handleMessage("Left the game", MessageType.DISCONNECT);
    }

    private void handleMessage(String message, MessageType type) {
        if (logAll.get()) {
            dispatch(defaultWebhook.get(), "Log", message, false);
            return;
        }
        switch (type) {
            case LOGIN -> {
                if (!login.get()) return;
                dispatch(resolve(loginWebhook), "Login", message, pingOnLogin.get());
            }
            case DISCONNECT -> {
                if (!disconnect.get()) return;
                dispatch(resolve(disconnectWebhook), "Disconnect", message, pingOnLogout.get());
            }
            case PLAYER_RANGE -> {
                if (playerRange.get()) dispatch(resolve(playerRangeWebhook), "Player Range", message, false);
            }
            case QUEUE -> {
                if (queue.get()) dispatch(resolve(queueWebhook), "Queue", message, false);
            }
            case DEATH -> {
                if (deathMessages.get()) dispatch(resolve(deathWebhook), "Death", message, false);
            }
            case NORMAL -> {
                if (whisper.get() && isWhisper(message)) {
                    dispatch(resolve(whisperWebhook), "Whisper", message, false);
                } else if (chat.get() && message.startsWith("<")) {
                    dispatch(resolve(chatWebhook), "Chat", message, false);
                } else if (commands.get()) {
                    dispatch(resolve(commandsWebhook), "Command", message, false);
                }
            }
        }
    }

    private static boolean isWhisper(String message) {
        return message.startsWith("You whisper to ") || message.contains(" whispers to you:");
    }

    private String resolve(Setting<String> categoryWebhook) {
        String url = categoryWebhook.get();
        return url == null || url.isBlank() ? defaultWebhook.get() : url;
    }

    private void dispatch(String url, String title, String message, boolean ping) {
        if (url == null || url.isBlank()) return;

        String content = message;
        if (timestamp.get()) {
            content = "[" + LocalTime.now().format(TIME_FMT) + "] " + content;
        }

        PendingMessage pending = new PendingMessage(url, title, content, ping);
        if (delayTimer > 0) {
            if (queueMessages.get()) messageQueue.offer(pending);
            return;
        }
        delayTimer = messageDelay.get() * 20;
        flush(pending);
    }

    private void flush(PendingMessage msg) {
        String player = playerName();
        new Thread(() -> {
            sendWebhook(msg.url(), msg.title(), msg.content(), null, player);
            if (msg.ping()) sendWebhookPing(msg.url(), pingRoleId.get(), pingUserId.get());
        }).start();
    }

    private String playerName() {
        if (mc.player != null) return mc.player.getGameProfile().name();
        return mc.getSession().getUsername();
    }

    private record PendingMessage(String url, String title, String content, boolean ping) {}

    public enum MessageType {
        NORMAL,
        DEATH,
        QUEUE,
        DISCONNECT,
        LOGIN,
        PLAYER_RANGE
    }
}
