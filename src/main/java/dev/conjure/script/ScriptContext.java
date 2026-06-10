package dev.conjure.script;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

/**
 * The whitelisted host object injected into every Conjure behavior script as {@code ctx}.
 *
 * <p>This class is intentionally narrow — it exposes only safe, gameplay-level operations
 * to the AI-authored script. No reflection, no IO, no class-loading. The Rhino ClassShutter
 * in {@link ScriptRuntime} additionally prevents scripts from reaching any Java class at all,
 * so only what is explicitly passed as a script binding is reachable.
 *
 * <p>All methods are server-side only; callers must guard with {@code !level.isClientSide}
 * before constructing one of these.
 */
public final class ScriptContext {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Level level;
    private final Player player;
    private final InteractionHand hand;

    public ScriptContext(Level level, Player player, InteractionHand hand) {
        this.level = level;
        this.player = player;
        this.hand = hand;
    }

    // -------------------------------------------------------------------------
    // ctx API exposed to scripts
    // -------------------------------------------------------------------------

    /**
     * Send a chat message to the interacting player.
     *
     * @param text the message text (plain string)
     */
    public void message(String text) {
        player.displayClientMessage(Component.literal(text), false);
    }

    /**
     * Give {@code count} items with registry id {@code itemId} to the player.
     * If the id is unknown or maps to air the call is silently ignored.
     *
     * @param itemId e.g. {@code "minecraft:diamond"} or {@code "conjure:slot_000"}
     * @param count  number of items (clamped to 1–64)
     */
    public void giveItem(String itemId, int count) {
        ResourceLocation loc = ResourceLocation.tryParse(itemId);
        if (loc == null) {
            LOGGER.warn("[Conjure script] giveItem: invalid ResourceLocation '{}'", itemId);
            return;
        }
        Item item = BuiltInRegistries.ITEM.get(loc);
        if (item == null || item == Items.AIR) {
            LOGGER.warn("[Conjure script] giveItem: unknown item '{}'", itemId);
            return;
        }
        int safeCount = Math.max(1, Math.min(count, 64));
        ItemStack stack = new ItemStack(item, safeCount);
        if (!player.addItem(stack)) {
            // Inventory full — drop at feet
            player.drop(stack, false);
        }
    }

    /**
     * Restore {@code amount} health points to the player (2 points = 1 heart).
     * Negative amounts are ignored.
     *
     * @param amount HP to restore
     */
    public void heal(double amount) {
        if (amount > 0) {
            player.heal((float) amount);
        }
    }

    /**
     * Spawn a burst of {@code HEART} particles at the player's position.
     * Uses ServerLevel.sendParticles so it is only called server-side.
     */
    public void spawnParticleHere() {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    ParticleTypes.HEART,
                    player.getX(), player.getY() + 1.5, player.getZ(),
                    8,   // count
                    0.3, 0.3, 0.3, // spread
                    0.0  // speed
            );
        }
    }

    // -------------------------------------------------------------------------
    // Helpers for scripts that read slot tunables
    // -------------------------------------------------------------------------

    /**
     * Convenience: return the display name of the player.
     */
    public String getPlayerName() {
        return player.getName().getString();
    }

    /**
     * Damage the player for {@code amount} HP (2 = 1 heart).
     * The damage source is the generic "magic" source to avoid looping
     * through armor/enchantment calculations.
     */
    public void damage(double amount) {
        if (amount > 0) {
            player.hurt(player.damageSources().magic(), (float) amount);
        }
    }
}
