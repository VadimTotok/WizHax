package dev.wisuazt.wizhax.module;

import dev.wisuazt.wizhax.WizHax;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class AutoSign extends Module {
    public AutoSign() {
        super(WizHax.WIZHAX, "[WHax] AutoSign", "Automatically fills and updates signs.");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDateAppend = settings.createGroup("Date Append");
    private final SettingGroup sgOther = settings.createGroup("Other");

    private final Setting<String> line1 = sgGeneral.add(new StringSetting.Builder()
        .name("line-1")
        .description("The text of the first line of the sign.")
        .defaultValue("")
        .build()
    );

    private final Setting<String> line2 = sgGeneral.add(new StringSetting.Builder()
        .name("line-2")
        .description("The text of the second line of the sign.")
        .defaultValue("")
        .build()
    );

    private final Setting<String> line3 = sgGeneral.add(new StringSetting.Builder()
        .name("line-3")
        .description("The text of the third line of the sign.")
        .defaultValue("")
        .build()
    );

    private final Setting<String> line4 = sgGeneral.add(new StringSetting.Builder()
        .name("line-4")
        .description("The text of the fourth line of the sign.")
        .defaultValue("")
        .build()
    );

    private final Setting<Boolean> dateAppend = sgDateAppend.add(new BoolSetting.Builder()
        .name("date-append")
        .description("Insert the current date into the selected row if it's empty.")
        .defaultValue(false)
        .build()
    );

    private final Setting<DateFormat> dateFormat = sgDateAppend.add(new EnumSetting.Builder<DateFormat>()
        .name("date-format")
        .description("Date format.")
        .defaultValue(DateFormat.DD_MM_YYYY_DOT)
        .visible(dateAppend::get)
        .build()
    );

    private final Setting<SignLine> dateLine = sgDateAppend.add(new EnumSetting.Builder<SignLine>()
        .name("date-line")
        .description("Which row of the sign the date should be inserted into.")
        .defaultValue(SignLine.LINE_4)
        .visible(dateAppend::get)
        .build()
    );

    private final Setting<Boolean> autoClose = sgOther.add(new BoolSetting.Builder()
        .name("auto-close")
        .description("Automatically confirm and close the sign editor after filling it in.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> delay = sgOther.add(new IntSetting.Builder()
        .name("delay")
        .description("Tick delay before sending the sign update packet.")
        .defaultValue(10)
        .range(1, 100)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Boolean> autoUpdate = sgOther.add(new BoolSetting.Builder()
        .name("auto-update")
        .description("Also overwrite the text of already-filled (existing) signs.")
        .defaultValue(false)
        .build()
    );

    public enum DateFormat {
        MM_DD_YYYY_DOT("MM.dd.yyyy"),
        DD_MM_YYYY_DOT("dd.MM.yyyy"),
        MM_DD_YYYY_DASH("MM-dd-yyyy"),
        DD_MM_YYYY_DASH("dd-MM-yyyy"),
        MM_DD_YYYY_SLASH("MM/dd/yyyy"),
        DD_MM_YYYY_SLASH("dd/MM/yyyy"),
        FULL_TEXT_EN("d MMMM yyyy");
        private final String pattern;

        DateFormat(String pattern) {
            this.pattern = pattern;
        }

        public String format(LocalDate date) {
            return date.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
        }

        @Override
        public String toString() {
            return pattern + "  (" + format(LocalDate.now()) + ")";
        }
    }

    public enum SignLine {
        LINE_1, LINE_2, LINE_3, LINE_4;

        public int index() {
            return ordinal();
        }
    }

    private static AutoSign INSTANCE;
    private AbstractSignEditScreen pendingScreen = null;
    private SignBlockEntity pendingBlockEntity = null;
    private boolean pendingFront = false;
    private String[] pendingLines = null;
    private int ticksPassed = 0;

    @Override
    public void onActivate() {
        INSTANCE = this;
    }

    @Override
    public void onDeactivate() {
        INSTANCE = null;
        pendingScreen = null;
        pendingBlockEntity = null;
        pendingLines = null;
        ticksPassed = 0;
    }

    public static void onScreenInit(AbstractSignEditScreen screen, String[] messages, SignBlockEntity blockEntity, boolean front) {
        if (INSTANCE == null || !INSTANCE.isActive()) return;
        if (messages == null || messages.length < 4) return;
        boolean hasExistingText = !INSTANCE.isEmpty(messages);
        if (hasExistingText && !INSTANCE.autoUpdate.get()) return;
        String[] configured = {
            INSTANCE.line1.get(),
            INSTANCE.line2.get(),
            INSTANCE.line3.get(),
            INSTANCE.line4.get()
        };
        String[] result = configured.clone();
        if (INSTANCE.dateAppend.get()) {
            int idx = INSTANCE.dateLine.get().index();
            boolean allLinesFilled = true;
            for (String l : configured) {
                if (l == null || l.isEmpty()) {
                    allLinesFilled = false;
                    break;
                }
            }
            if (!allLinesFilled && (result[idx] == null || result[idx].isEmpty()))
                result[idx] = INSTANCE.dateFormat.get().format(LocalDate.now());
        }
        for (int i = 0; i < 4; i++)
            messages[i] = result[i] == null ? "" : result[i];
        INSTANCE.pendingScreen = screen;
        INSTANCE.pendingBlockEntity = blockEntity;
        INSTANCE.pendingFront = front;
        INSTANCE.pendingLines = result;
        INSTANCE.ticksPassed = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || pendingScreen == null || pendingLines == null) return;
        if (mc.currentScreen != pendingScreen) {
            if (pendingBlockEntity != null) {
                sendUpdatePacket(pendingBlockEntity, pendingFront, pendingLines);
                info("Sign packet sent after screen close.");
            }
            resetState();
            return;
        }
        if (ticksPassed < delay.get()) {
            ticksPassed++;
            return;
        }
        if (pendingBlockEntity != null && pendingLines != null) {
            sendUpdatePacket(pendingBlockEntity, pendingFront, pendingLines);
            info("Sign updated successfully.");
        }
        if (autoClose.get()) pendingScreen.close();
        resetState();
    }

    private void sendUpdatePacket(SignBlockEntity blockEntity, boolean front, String[] lines) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (blockEntity == null || blockEntity.isRemoved()) return;
        UpdateSignC2SPacket packet = new UpdateSignC2SPacket(
            blockEntity.getPos(),
            front,
            lines[0] == null ? "" : lines[0],
            lines[1] == null ? "" : lines[1],
            lines[2] == null ? "" : lines[2],
            lines[3] == null ? "" : lines[3]
        );
        mc.getNetworkHandler().sendPacket(packet);
    }

    private void resetState() {
        pendingScreen = null;
        pendingBlockEntity = null;
        pendingLines = null;
        ticksPassed = 0;
    }

    private boolean isEmpty(String[] lines) {
        for (String l : lines)
            if (l != null && !l.isEmpty()) return false;
        return true;
    }
}
