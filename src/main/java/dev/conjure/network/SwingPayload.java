package dev.conjure.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-bound payload sent by the client when the player swings while holding a configured
 * {@link dev.conjure.content.item.ConjureItem}. Carries no data — the server handler reads
 * the player's main-hand item directly and runs its behavior script with trigger "swing".
 *
 * <p>Registration is handled by {@link SwingPacketHandler} via {@code @EventBusSubscriber},
 * so neither {@code Conjure.java} nor any other entrypoint needs to be edited.
 */
public record SwingPayload() implements CustomPacketPayload {

    /** Packet id: {@code conjure:swing}. */
    public static final Type<SwingPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("conjure", "swing"));

    /**
     * Codec for the payload — no fields, so nothing is read or written.
     * {@link StreamCodec#unit} decodes by ignoring the buffer and returning the singleton.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, SwingPayload> CODEC =
            StreamCodec.unit(new SwingPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
