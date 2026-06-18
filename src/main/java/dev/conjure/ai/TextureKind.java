package dev.conjure.ai;

/**
 * What a generated texture is for. Steers image-model prompts: a BLOCK wants an opaque,
 * edge-to-edge, tileable surface, whereas an ITEM wants a centered icon on a transparent
 * background. Threaded from each pipeline through {@link dev.conjure.ai.agents.TextureAgent}
 * into {@link ImageModelProvider#generateTexture}.
 */
public enum TextureKind {
    ITEM,
    BLOCK,
    ENTITY,
    FLUID
}
