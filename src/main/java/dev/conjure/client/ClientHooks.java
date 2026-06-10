package dev.conjure.client;

import net.minecraft.client.Minecraft;

/**
 * Client-only side effects. Never referenced on a dedicated server (the caller dist-guards),
 * so the client-only {@link Minecraft} import never loads server-side.
 */
public final class ClientHooks {

    /** Re-applies all resource packs on the render thread, pulling in newly generated assets. */
    public static void reloadResources() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(mc::reloadResourcePacks);
    }

    private ClientHooks() {}
}
