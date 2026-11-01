package dev.wisuazt.wizhax.module;

import dev.wisuazt.wizhax.WizHax;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;

import java.io.File;
import java.util.function.Consumer;

public class ScreenshotCleaner extends Module {
    private static ScreenshotCleaner INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> autoOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-only")
        .description("Hide the minimap and waypoints only at the moment of capture. When disabled, hides them the whole time the module is active.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> framesDelay = sgGeneral.add(new IntSetting.Builder()
        .name("frames-delay")
        .description("How many frames to wait before taking the shot. Higher is more reliable but makes the minimap flicker longer.")
        .defaultValue(2)
        .range(1, 10)
        .sliderRange(1, 6)
        .build()
    );

    private static boolean hideNow = false;
    private static boolean capturing = false;
    private static boolean releaseHide = false;
    private static int framesToWait = 0;
    private static PendingScreenshot pending = null;

    public ScreenshotCleaner() {
        super(WizHax.WIZHAX, "[WizHax] ScreenshotCleaner",
            "Hides the minimap and waypoints for clean screenshots (xaero).");
        INSTANCE = this;
    }

    public static ScreenshotCleaner get() {
        return INSTANCE;
    }

    public static boolean isHidden() {
        if (INSTANCE == null || !INSTANCE.isActive()) return false;
        return !INSTANCE.autoOnly.get() || hideNow;
    }

    public static boolean isAutoOnly() {
        return INSTANCE != null && INSTANCE.isActive() && INSTANCE.autoOnly.get();
    }

    public static boolean isCapturing() {
        return capturing;
    }

    public static void queuePendingScreenshot(File dir, String fileName, Framebuffer fb,
                                              int downscale, Consumer<Text> msg) {
        pending = new PendingScreenshot(dir, fileName, fb, downscale, msg);
        hideNow = true;
        framesToWait = INSTANCE != null ? INSTANCE.framesDelay.get() : 2;
    }

    public static void onFrameEnd() {
        if (releaseHide) {
            releaseHide = false;
            hideNow = false;
            return;
        }

        if (pending == null) return;

        if (framesToWait > 0) {
            framesToWait--;
            return;
        }

        PendingScreenshot p = pending;
        pending = null;

        Consumer<Text> wrapped = text -> {
            releaseHide = true;
            p.msg.accept(text);
        };

        try {
            capturing = true;
            ScreenshotRecorder.saveScreenshot(p.dir, p.fileName, p.fb, p.downscale, wrapped);
        } finally {
            capturing = false;
        }
    }

    @Override
    public void onDeactivate() {
        hideNow = false;
        releaseHide = false;
        framesToWait = 0;
        pending = null;
    }

    private record PendingScreenshot(File dir, String fileName, Framebuffer fb, int downscale, Consumer<Text> msg) {

    }
}
