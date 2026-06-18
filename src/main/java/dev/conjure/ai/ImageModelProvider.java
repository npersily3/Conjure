package dev.conjure.ai;

/**
 * A texture generator. For Conjure the default is a LOCAL open-source image backend
 * (ComfyUI), since Anthropic models don't emit images.
 *
 * <p>A second, dependency-free strategy worth keeping: have the text model emit a small
 * pixel array directly (16x16 textures are tiny) and encode it to PNG in-process. That path
 * implements this same interface without any image backend running.
 */
public interface ImageModelProvider {

    /**
     * @param prompt description of the desired texture
     * @param size   edge length in pixels (typically 16)
     * @param kind   what the texture is for; steers the prompt (a BLOCK wants an opaque tileable
     *               surface, an ITEM a centered icon on a transparent background)
     * @return raw PNG bytes to drop into the dynamic resource pack
     */
    byte[] generateTexture(String prompt, int size, TextureKind kind) throws Exception;

    String id();
}
