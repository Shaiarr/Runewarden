/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.runelite.api.Skill;

/** Exact item transformations for observed action progress and resource hover facts. */
public final class ActionRecipeCatalog
{
    public static final class Ingredient
    {
        public final int itemId;
        public final int quantity;
        public final String name;

        private Ingredient(JsonObject item)
        {
            itemId = item.get("itemId").getAsInt();
            quantity = item.get("quantity").getAsInt();
            name = item.get("name").getAsString();
            if (itemId <= 0 || quantity <= 0 || name.isEmpty())
            {
                throw new IllegalStateException("Invalid action ingredient");
            }
        }
    }

    public static final class Recipe
    {
        public final String id;
        public final Skill skill;
        public final String name;
        public final int icon;
        public final int level;
        public final double xp;
        public final List<Ingredient> ingredients;
        public final List<Ingredient> products;
        public final String outputSource;
        public final String note;
        public final String source;
        private final Map<Integer, Long> required;

        private Recipe(JsonObject row)
        {
            id = row.get("id").getAsString();
            skill = Skill.valueOf(row.get("skill").getAsString());
            name = row.get("name").getAsString();
            icon = row.get("icon").getAsInt();
            level = row.get("level").getAsInt();
            xp = row.get("xp").getAsDouble();
            note = row.get("note").getAsString();
            source = row.get("source").getAsString();
            ingredients = readItems(row.getAsJsonArray("ingredients"));
            products = row.has("products") ? readItems(row.getAsJsonArray("products")) : List.of();
            outputSource = row.has("outputSource") ? row.get("outputSource").getAsString() : "";
            Map<Integer, Long> amounts = new LinkedHashMap<>();
            for (Ingredient ingredient : ingredients)
            {
                amounts.merge(ingredient.itemId, (long) ingredient.quantity, Long::sum);
            }
            required = Collections.unmodifiableMap(amounts);
            if (id.isEmpty() || name.isEmpty() || !Double.isFinite(xp) || xp < 0
                || ingredients.isEmpty() || xp == 0 && products.isEmpty())
            {
                throw new IllegalStateException("Invalid action recipe");
            }
            for (Ingredient product : products)
            {
                if (required.containsKey(product.itemId))
                {
                    throw new IllegalStateException("Action input also appears as an output");
                }
            }
        }

        @Override public String toString()
        {
            return name;
        }
    }

    private static final List<Recipe> RECIPES = load();

    private ActionRecipeCatalog()
    {
    }

    public static List<Recipe> recipes()
    {
        return RECIPES;
    }

    /** Remaining complete actions from current item quantities; no projected or purchased inputs. */
    public static long availableActions(Recipe recipe, Map<Integer, Long> items)
    {
        if (recipe.required.isEmpty())
        {
            return 0;
        }
        long available = Long.MAX_VALUE;
        for (Map.Entry<Integer, Long> ingredient : recipe.required.entrySet())
        {
            long quantity = Math.max(0, items.getOrDefault(ingredient.getKey(), 0L));
            available = Math.min(available, quantity / ingredient.getValue());
        }
        return available;
    }

    private static List<Ingredient> readItems(JsonArray rows)
    {
        List<Ingredient> result = new ArrayList<>();
        for (JsonElement row : rows)
        {
            result.add(new Ingredient(row.getAsJsonObject()));
        }
        return Collections.unmodifiableList(result);
    }

    private static List<Recipe> load()
    {
        try (InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(
            ActionRecipeCatalog.class.getResourceAsStream("/com/runewarden/action-recipes.json"),
            "Missing action recipes"), StandardCharsets.UTF_8))
        {
            JsonArray rows = new JsonParser().parse(reader).getAsJsonArray();
            List<Recipe> result = new ArrayList<>();
            Set<String> identifiers = new HashSet<>();
            for (JsonElement row : rows)
            {
                Recipe recipe = new Recipe(row.getAsJsonObject());
                if (!identifiers.add(recipe.id))
                {
                    throw new IllegalStateException("Duplicate action recipe");
                }
                result.add(recipe);
            }
            return Collections.unmodifiableList(result);
        }
        catch (IOException failure)
        {
            throw new IllegalStateException("Could not read action recipes", failure);
        }
    }
}
