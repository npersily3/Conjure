package dev.conjure.gen;

import java.util.List;

/**
 * Per-piece context for whole-mod economy generation, passed via a thread-local because generation
 * runs serially on the single {@code conjure-gen} thread. It carries (a) the already-generated
 * ingredient ids this piece should be crafted from, and (b) a slot for the pipeline to report the id
 * it created so the next piece in the chain can reference it.
 *
 * <p>When no context is set (the normal {@code /conjure new} path) pipelines behave exactly as
 * before — they ask {@link dev.conjure.ai.agents.RecipeAgent} for a vanilla-ingredient recipe.
 */
public final class GenerationContext {

    private static final ThreadLocal<GenerationContext> CURRENT = new ThreadLocal<>();

    private final List<String> ingredientIds;
    private final boolean smelt;
    private String createdId;

    /**
     * @param ingredientIds resolved {@code conjure:}/{@code minecraft:} ids this piece is crafted from
     * @param smelt         true to make the recipe a furnace smelt (ore→ingot) rather than a craft
     */
    public GenerationContext(List<String> ingredientIds, boolean smelt) {
        this.ingredientIds = ingredientIds;
        this.smelt = smelt;
    }

    public List<String> ingredientIds() { return ingredientIds; }
    public boolean smelt() { return smelt; }
    public String createdId() { return createdId; }
    public void setCreatedId(String id) { this.createdId = id; }

    public static GenerationContext current() { return CURRENT.get(); }
    public static void set(GenerationContext c) { CURRENT.set(c); }
    public static void clear() { CURRENT.remove(); }

    private GenerationContext() { this(List.of(), false); }
}
