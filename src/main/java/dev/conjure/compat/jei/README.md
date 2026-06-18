# dev.conjure.compat.jei — JEI integration

Exposes Conjure's generated **workbench** recipes in [JEI](https://www.curseforge.com/minecraft/mc-mods/jei).
Vanilla/datapack recipes Conjure writes (the material families and obtainability recipes) already
appear in JEI automatically; this package adds a custom category for the bespoke workbench recipes
that have no vanilla recipe type.

JEI re-runs plugin registration on a client resource reload, and Conjure reloads resources after
every generation, so newly conjured workbenches appear here without a relaunch. JEI is an optional
dependency: these classes are only loaded when JEI is present (it discovers `@JeiPlugin` by scanning).

## Files

| File | Purpose |
|------|---------|
| `ConjureJeiPlugin.java` | `@JeiPlugin` entry point. Registers the workbench category, feeds it `WorkbenchRecipes.all()`, and registers each workbench block item as a recipe catalyst so the block shows its recipe in JEI. |
| `WorkbenchRecipeCategory.java` | `IRecipeCategory<WorkbenchRecipe>`. Lays out one input slot per ingredient (3 per row), an optional fuel slot, and an output slot — mirroring the in-game dynamic GUI — and draws the block name + processing time. Background is drawn programmatically (blank drawable), so no category texture is needed. |
