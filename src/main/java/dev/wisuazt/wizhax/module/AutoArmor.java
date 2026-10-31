package dev.wisuazt.wizhax.module;

import dev.wisuazt.wizhax.WizHax;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.ChestSwap;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;

public class AutoArmor extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSlots = settings.createGroup("Slots");
    private final SettingGroup sgElytra = settings.createGroup("Elytra");
    private final SettingGroup sgPiglin = settings.createGroup("Piglin Gold");
    private final SettingGroup sgDurability = settings.createGroup("Durability");

    public AutoArmor() {
        super(WizHax.WIZHAX, "[WHax] AutoArmor", "Automatically equips armor.");
        armorPieces[0] = helmet;
        armorPieces[1] = chestplate;
        armorPieces[2] = leggings;
        armorPieces[3] = boots;
    }

    private final Setting<Protection> preferredProtection = sgGeneral.add(new EnumSetting.Builder<Protection>()
        .name("preferred-protection")
        .description("Which type of protection to prefer.")
        .defaultValue(Protection.Protection)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("swap-delay")
        .description("The delay between equipping armor pieces.")
        .defaultValue(2)
        .min(0)
        .sliderMax(5)
        .build()
    );

    private final Setting<Set<RegistryKey<Enchantment>>> avoidedEnchantments = sgGeneral.add(new EnchantmentListSetting.Builder()
        .name("avoided-enchantments")
        .description("Enchantments that should be avoided.")
        .defaultValue(Enchantments.BINDING_CURSE, Enchantments.FROST_WALKER)
        .build()
    );

    private final Setting<Boolean> blastLeggings = sgGeneral.add(new BoolSetting.Builder()
        .name("blast-prot-leggings")
        .description("Uses blast protection for leggings regardless of preferred protection.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swapHelmet = sgSlots.add(new BoolSetting.Builder()
        .name("swap-helmet")
        .description("Allows AutoArmor to swap the helmet slot.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swapChestplate = sgSlots.add(new BoolSetting.Builder()
        .name("swap-chestplate")
        .description("Allows AutoArmor to swap the chestplate slot.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swapLeggings = sgSlots.add(new BoolSetting.Builder()
        .name("swap-leggings")
        .description("Allows AutoArmor to swap the leggings slot.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swapBoots = sgSlots.add(new BoolSetting.Builder()
        .name("swap-boots")
        .description("Allows AutoArmor to swap the boots slot.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreElytra = sgElytra.add(new BoolSetting.Builder()
        .name("ignore-elytra")
        .description("Will not replace your elytra if you have it equipped. Only applies when Elytra Priority is enabled.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> elytraPriority = sgElytra.add(new BoolSetting.Builder()
        .name("elytra-priority")
        .description("Prefers equipping an elytra from your inventory over a chestplate. If disabled, an equipped elytra will be swapped for a chestplate when available.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> piglinGold = sgPiglin.add(new BoolSetting.Builder()
        .name("piglin-gold")
        .description("Equips a single piece of gold armor while in the Nether to appease piglins.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> durability = sgDurability.add(new IntSetting.Builder()
        .name("durability")
        .description("Durability threshold below which an armor piece will be swapped out. Set to 0 to disable.")
        .defaultValue(5)
        .min(0)
        .sliderMax(100)
        .build()
    );

    private final Object2IntMap<RegistryEntry<Enchantment>> enchantments = new Object2IntOpenHashMap<>();
    private final ArmorPiece[] armorPieces = new ArmorPiece[4];
    private final ArmorPiece helmet = new ArmorPiece(EquipmentSlot.HEAD);
    private final ArmorPiece chestplate = new ArmorPiece(EquipmentSlot.CHEST);
    private final ArmorPiece leggings = new ArmorPiece(EquipmentSlot.LEGS);
    private final ArmorPiece boots = new ArmorPiece(EquipmentSlot.FEET);
    private int timer;

    private static final Item[] GOLD_ARMOR_PRIORITY = {Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS};
    private static final EquipmentSlot[] GOLD_ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    @Override
    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onPreTick(TickEvent.Pre event) {
        if (timer > 0) {
            timer--;
            return;
        }

        if (handlePiglinGold()) return;
        for (ArmorPiece armorPiece : armorPieces) {
            if (armorPiece.isEnabled()) armorPiece.reset();
        }

        for (int i = 0; i < Objects.requireNonNull(mc.player).getInventory().getMainStacks().size(); i++) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);
            if (itemStack.isEmpty() || !isArmor(itemStack)) continue;
            if (piglinGold.get() && inNether() && isGoldArmor(itemStack)) continue;
            if (durability.get() > 0 && itemStack.isDamageable() && itemStack.getMaxDamage() - itemStack.getDamage() <= durability.get()) {
                continue;
            }

            Utils.getEnchantments(itemStack, enchantments);

            if (hasAvoidedEnchantment()) continue;
            switch (getItemSlotId(itemStack)) {
                case 0 -> { if (boots.isEnabled()) boots.add(itemStack, i); }
                case 1 -> { if (leggings.isEnabled()) leggings.add(itemStack, i); }
                case 2 -> { if (chestplate.isEnabled()) chestplate.add(itemStack, i); }
                case 3 -> { if (helmet.isEnabled()) helmet.add(itemStack, i); }
            }
        }

        for (ArmorPiece armorPiece : armorPieces) {
            if (armorPiece.isEnabled()) armorPiece.calculate();
        }

        Arrays.sort(armorPieces, Comparator.comparingInt(ArmorPiece::getSortScore));

        for (ArmorPiece armorPiece : armorPieces) {
            if (armorPiece.isEnabled()) armorPiece.apply();
        }
    }

    private boolean handlePiglinGold() {
        if (!piglinGold.get() || !inNether()) return false;
        for (EquipmentSlot slot : GOLD_ARMOR_SLOTS) {
            assert mc.player != null;
            if (isGoldArmor(mc.player.getEquippedStack(slot))) return false;
        }

        for (int p = 0; p < GOLD_ARMOR_PRIORITY.length; p++) {
            EquipmentSlot targetSlot = GOLD_ARMOR_SLOTS[p];
            if (!isSlotEnabled(targetSlot)) continue;

            for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (!stack.isEmpty() && stack.getItem() == GOLD_ARMOR_PRIORITY[p]) {
                    swap(i, targetSlot.getEntitySlotId());
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isGoldArmor(ItemStack itemStack) {
        if (itemStack.isEmpty()) return false;
        Item item = itemStack.getItem();
        return item == Items.GOLDEN_HELMET || item == Items.GOLDEN_CHESTPLATE || item == Items.GOLDEN_LEGGINGS || item == Items.GOLDEN_BOOTS;
    }

    private boolean inNether() {
        return mc.player != null && mc.player.getEntityWorld().getRegistryKey() == World.NETHER;
    }

    private boolean isSlotEnabled(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> swapHelmet.get();
            case CHEST -> swapChestplate.get();
            case LEGS -> swapLeggings.get();
            case FEET -> swapBoots.get();
            default -> false;
        };
    }

    private boolean hasAvoidedEnchantment() {
        for (RegistryEntry<Enchantment> enchantment : enchantments.keySet()) {
            if (enchantment.matches(avoidedEnchantments.get()::contains)) return true;
        }
        return false;
    }

    private int getItemSlotId(ItemStack itemStack) {
        if (itemStack.contains(DataComponentTypes.GLIDER)) return 2;
        return Objects.requireNonNull(itemStack.get(DataComponentTypes.EQUIPPABLE)).slot().getEntitySlotId();
    }

    private int getScore(ItemStack itemStack) {
        if (itemStack.isEmpty()) return 0;
        if (itemStack.getItem() == Items.ELYTRA) {
            if (elytraPriority.get()) return Integer.MAX_VALUE - 1;
            return 0;
        }

        int score = 0;
        RegistryKey<Enchantment> protection = preferredProtection.get().enchantment;
        if (isArmor(itemStack) && blastLeggings.get() && getItemSlotId(itemStack) == 1) {
            protection = Enchantments.BLAST_PROTECTION;
        }

        score += 3 * Utils.getEnchantmentLevel(enchantments, protection);
        score += Utils.getEnchantmentLevel(enchantments, Enchantments.PROTECTION);
        score += Utils.getEnchantmentLevel(enchantments, Enchantments.BLAST_PROTECTION);
        score += Utils.getEnchantmentLevel(enchantments, Enchantments.FIRE_PROTECTION);
        score += Utils.getEnchantmentLevel(enchantments, Enchantments.PROJECTILE_PROTECTION);
        score += Utils.getEnchantmentLevel(enchantments, Enchantments.UNBREAKING);
        score += 2 * Utils.getEnchantmentLevel(enchantments, Enchantments.MENDING);

        if (itemStack.contains(DataComponentTypes.ATTRIBUTE_MODIFIERS)) {
            AttributeModifiersComponent component = itemStack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
            assert component != null;
            for (AttributeModifiersComponent.Entry modifier : component.modifiers()) {
                if (modifier.attribute() == EntityAttributes.ARMOR || modifier.attribute() == EntityAttributes.ARMOR_TOUGHNESS) {
                    double e = modifier.modifier().value();

                    score += (int) switch (modifier.modifier().operation()) {
                        case ADD_VALUE -> e;
                        case ADD_MULTIPLIED_BASE -> {
                            assert mc.player != null;
                            yield e * mc.player.getAttributeBaseValue(modifier.attribute());
                        }
                        case ADD_MULTIPLIED_TOTAL -> e * score;
                    };
                }
            }
        }
        return score;
    }

    private boolean cannotSwap() {
        return timer > 0;
    }

    private void swap(int from, int armorSlotId) {
        InvUtils.move().from(from).toArmor(armorSlotId);
        timer = delay.get();
    }

    private void moveToEmpty(int armorSlotId) {
        for (int i = 0; i < Objects.requireNonNull(mc.player).getInventory().getMainStacks().size(); i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                InvUtils.move().fromArmor(armorSlotId).to(i);
                timer = delay.get();
                break;
            }
        }
    }

    private boolean isArmor(ItemStack itemStack) {
        if (itemStack.getItem() == Items.ELYTRA) return true;
        return itemStack.isIn(ItemTags.FOOT_ARMOR) || itemStack.isIn(ItemTags.LEG_ARMOR) || itemStack.isIn(ItemTags.CHEST_ARMOR) || itemStack.isIn(ItemTags.HEAD_ARMOR);
    }

    public enum Protection {
        Protection(Enchantments.PROTECTION),
        BlastProtection(Enchantments.BLAST_PROTECTION),
        FireProtection(Enchantments.FIRE_PROTECTION),
        ProjectileProtection(Enchantments.PROJECTILE_PROTECTION);

        private final RegistryKey<Enchantment> enchantment;

        Protection(RegistryKey<Enchantment> enchantment) {
            this.enchantment = enchantment;
        }
    }

    private class ArmorPiece {
        private final EquipmentSlot slot;
        private int bestSlot;
        private int bestScore;
        private int score;
        private int itemDurability;

        public ArmorPiece(EquipmentSlot slot) {
            this.slot = slot;
        }

        public boolean isEnabled() {
            return isSlotEnabled(slot);
        }

        public void reset() {
            bestSlot = -1;
            bestScore = -1;
            score = -1;
            itemDurability = Integer.MAX_VALUE;
        }

        public void add(ItemStack itemStack, int slot) {
            int score = getScore(itemStack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }

        public void calculate() {
            if (cannotSwap()) return;
            assert mc.player != null;
            ItemStack itemStack = mc.player.getEquippedStack(slot);

            if (itemStack.getItem() == Items.ELYTRA
                && ((ignoreElytra.get() && elytraPriority.get()) || Modules.get().isActive(ChestSwap.class))) {
                boolean elytraLowDurability = durability.get() > 0
                    && itemStack.isDamageable()
                    && itemStack.getMaxDamage() - itemStack.getDamage() <= durability.get();

                if (!elytraLowDurability) {
                    score = Integer.MAX_VALUE;
                    return;
                }
            }

            if (piglinGold.get() && inNether() && isGoldArmor(itemStack)) {
                score = Integer.MAX_VALUE;
                return;
            }

            Utils.getEnchantments(itemStack, enchantments);
            if (enchantments.containsKey(Enchantments.BINDING_CURSE)) {
                score = Integer.MAX_VALUE;
                return;
            }

            score = getScore(itemStack);
            score = decreaseScoreByAvoidedEnchantments(score);
            score = applyDurabilityScore(score, itemStack);

            if (!itemStack.isEmpty()) itemDurability = itemStack.getMaxDamage() - itemStack.getDamage();
        }

        public int getSortScore() {
            if (durability.get() > 0 && itemDurability <= durability.get()) return -1;
            return bestScore;
        }

        public void apply() {
            if (cannotSwap() || score == Integer.MAX_VALUE) return;
            if (bestScore > score) swap(bestSlot, slot.getEntitySlotId());
            else if (durability.get() > 0 && itemDurability <= durability.get()) moveToEmpty(slot.getEntitySlotId());
        }

        private int decreaseScoreByAvoidedEnchantments(int score) {
            for (RegistryKey<Enchantment> enchantment : avoidedEnchantments.get()) {
                score -= 2 * enchantments.getInt(enchantment);
            }
            return score;
        }

        private int applyDurabilityScore(int score, ItemStack itemStack) {
            if (durability.get() > 0 && itemStack.isDamageable() && itemStack.getMaxDamage() - itemStack.getDamage() <= durability.get()) {
                return -1;
            }
            return score;
        }
    }
}
