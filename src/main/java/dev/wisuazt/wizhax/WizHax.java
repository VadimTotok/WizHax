package dev.wisuazt.wizhax;

import com.mojang.logging.LogUtils;
import dev.wisuazt.wizhax.hud.EntityList;
import dev.wisuazt.wizhax.hud.SpeedKMH;
import dev.wisuazt.wizhax.module.*;
import dev.wisuazt.wizhax.module.AutoPortal;
import dev.wisuazt.wizhax.module.DynamicFPS;
import dev.wisuazt.wizhax.module.Miner;
import dev.wisuazt.wizhax.module.Printer;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class WizHax extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category WIZHAX = new Category("Wizhax");
    public static final HudGroup HUD_GROUP = new HudGroup("WizHax");

    @Override
    public void onInitialize() {
        LOG.info("WizHax loading...");

        Hud.get().register(SpeedKMH.INFO);
        Hud.get().register(EntityList.INFO);

        Modules.get().add(new AFKVanillaFly());
        Modules.get().add(new DynamicFPS());
        Modules.get().add(new AutoPortal());
        Modules.get().add(new ElytraSwap());
        Modules.get().add(new KillEffects());
        Modules.get().add(new CaveAir());
        Modules.get().add(new MineESP());
        Modules.get().add(new AutoLog());
        Modules.get().add(new AutoSign());
        Modules.get().add(new Particles());
        Modules.get().add(new AutoArmor());
        Modules.get().add(new Miner());
        Modules.get().add(new InventoryCleaner());
        Modules.get().add(new MobGearESP());
        Modules.get().add(new AdvancedItemESP());
        Modules.get().add(new CollectibleESP());
        Modules.get().add(new PortalPatternFinder());
        Modules.get().add(new ElytraGlide());
        Modules.get().add(new Printer());
        Modules.get().add(new NoHurtCam());
        Modules.get().add(new AutoTorch());
        Modules.get().add(new AntiInteract());
        Modules.get().add(new DiscordNotifs());
        Modules.get().add(new ScreenshotCleaner());

        LOG.info("WizHax loaded!");
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(WIZHAX);
    }

    @Override
    public String getPackage() {
        return "dev.wisuazt.wizhax";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("Wisuazt", "wizhax");
    }
}
