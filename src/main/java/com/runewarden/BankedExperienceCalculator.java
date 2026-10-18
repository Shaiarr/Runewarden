/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.EnumMap;
import net.runelite.api.Experience;
import net.runelite.api.Skill;

/** Pure resource allocation: no ingredient can be counted twice within a plan. */
public final class BankedExperienceCalculator
{
    public static final class Ingredient
    {
        public int itemId;
        public int quantity;
        public String name;
    }

    public static final class Recipe
    {
        public String id;
        public Skill skill;
        public String name;
        public int icon;
        public int level;
        public double xp;
        public List<Ingredient> ingredients;
        public List<Ingredient> products = Collections.emptyList();
        public String outputSource;
        public String note;
        public String source;
        @Override public String toString() { return name; }
    }

    public static final class Allocation
    {
        public final Recipe recipe;
        public final long actions;
        public final boolean locked;
        Allocation(Recipe recipe, long actions, boolean locked)
        { this.recipe = recipe; this.actions = actions; this.locked = locked; }
        public double xp() { return actions * recipe.xp; }
    }

    public static final class Result
    {
        public final List<Allocation> allocations;
        public final Map<Integer, Long> remaining;
        public final double bankedXp;
        public final Map<Skill,Double> experienceBySkill;
        public final boolean usesOutputs;
        Result(List<Allocation> allocations, Map<Integer, Long> remaining, double xp)
        { this(allocations,remaining,xp,false); }
        Result(List<Allocation> allocations, Map<Integer, Long> remaining, double xp,boolean usesOutputs)
        {
            this.allocations = Collections.unmodifiableList(allocations);
            this.remaining = Collections.unmodifiableMap(remaining); bankedXp = xp;
            Map<Skill,Double> bySkill=new EnumMap<>(Skill.class);
            for (Allocation allocation : allocations) bySkill.merge(allocation.recipe.skill,allocation.xp(),Double::sum);
            experienceBySkill=Collections.unmodifiableMap(bySkill); this.usesOutputs=usesOutputs;
        }
        public double xp(Skill skill) { return experienceBySkill.getOrDefault(skill,0.0); }
        public int projectedLevel(long currentXp)
        { return Math.min(99, Experience.getLevelForXp((int) Math.min(200_000_000, currentXp + bankedXp))); }
    }

    private static final List<Recipe> RECIPES;
    static
    {
        JsonArray data = new JsonParser().parse(new InputStreamReader(
            BankedExperienceCalculator.class.getResourceAsStream("/com/runewarden/banked-xp-recipes.json"),
            StandardCharsets.UTF_8)).getAsJsonArray();
        List<Recipe> loaded = new ArrayList<>();
        for (JsonElement entry : data)
        {
            JsonObject row = entry.getAsJsonObject(); Recipe recipe = new Recipe();
            recipe.id = row.get("id").getAsString(); recipe.skill = Skill.valueOf(row.get("skill").getAsString());
            recipe.name = row.get("name").getAsString(); recipe.icon = row.get("icon").getAsInt();
            recipe.level = row.get("level").getAsInt(); recipe.xp = row.get("xp").getAsDouble();
            recipe.note = row.get("note").getAsString(); recipe.source = row.get("source").getAsString();
            recipe.ingredients = new ArrayList<>();
            for (JsonElement item : row.getAsJsonArray("ingredients"))
            {
                JsonObject value = item.getAsJsonObject(); Ingredient ingredient = new Ingredient();
                ingredient.itemId = value.get("itemId").getAsInt(); ingredient.quantity = value.get("quantity").getAsInt();
                ingredient.name = value.get("name").getAsString(); recipe.ingredients.add(ingredient);
            }
            recipe.products=new ArrayList<>();
            if (row.has("products")) for (JsonElement item : row.getAsJsonArray("products"))
            {
                JsonObject value=item.getAsJsonObject(); Ingredient product=new Ingredient(); product.itemId=value.get("itemId").getAsInt();
                product.quantity=value.get("quantity").getAsInt(); product.name=value.get("name").getAsString(); recipe.products.add(product);
            }
            recipe.outputSource=row.has("outputSource") ? row.get("outputSource").getAsString() : "";
            loaded.add(recipe);
        }
        for (Recipe recipe : loaded)
        {
            if (recipe.id == null || recipe.skill == null || !Double.isFinite(recipe.xp) || recipe.xp < 0 || recipe.ingredients.isEmpty()
                || recipe.xp==0 && recipe.products.isEmpty())
                throw new IllegalStateException("Invalid banked XP recipe");
            for (Ingredient item : recipe.ingredients)
                if (item.itemId <= 0 || item.quantity <= 0) throw new IllegalStateException("Invalid ingredient");
            for (Ingredient item : recipe.products)
                if (item.itemId<=0 || item.quantity<=0 || amounts(recipe).containsKey(item.itemId)) throw new IllegalStateException("Invalid recipe output");
        }
        RECIPES = Collections.unmodifiableList(loaded);
    }

    private BankedExperienceCalculator() { }
    public static List<Recipe> recipes() { return RECIPES; }

    public static long availableActions(Recipe recipe, Map<Integer, Long> bank)
    {
        Map<Integer,Long> required=amounts(recipe);
        if (required.isEmpty()) return 0;
        long available = Long.MAX_VALUE;
        for (Map.Entry<Integer, Long> ingredient : required.entrySet())
            available = Math.min(available, Math.max(0, bank.getOrDefault(ingredient.getKey(), 0L)) / ingredient.getValue());
        return available;
    }

    public static Result allocate(List<Recipe> orderedPlan, Map<Integer, Long> bank, int level)
    {
        Map<Integer, Long> remaining = new LinkedHashMap<>();
        bank.forEach((id, quantity) -> { if (id > 0 && quantity > 0) remaining.put(id, quantity); });
        List<Allocation> allocations = new ArrayList<>(); double xp = 0;
        for (Recipe recipe : orderedPlan)
        {
            boolean locked = recipe.level > level;
            long actions = locked ? 0 : availableActions(recipe, remaining);
            for (Map.Entry<Integer, Long> ingredient : amounts(recipe).entrySet())
                remaining.compute(ingredient.getKey(), (id, count) -> (count == null ? 0 : count) - actions * ingredient.getValue());
            Allocation allocation = new Allocation(recipe, actions, locked);
            allocations.add(allocation); xp += allocation.xp();
        }
        return new Result(allocations, remaining, xp);
    }

    /** Explicit selected conversions are ordered before their consumers. Outputs are forecast stock, never observations. */
    public static Result allocateChained(List<Recipe> selected,Map<Integer,Long> bank,Map<Skill,Integer> levels)
    {
        List<Recipe> ordered=productionOrder(selected); Map<Integer,Long> stock=stock(bank);
        List<Allocation> allocations=new ArrayList<>(); double xp=0;
        for (Recipe recipe : ordered)
        {
            boolean locked=recipe.level>levels.getOrDefault(recipe.skill,1); long actions=locked ? 0 : availableActions(recipe,stock);
            consume(recipe,actions,stock); produce(recipe,actions,stock);
            Allocation allocation=new Allocation(recipe,actions,locked); allocations.add(allocation); xp+=allocation.xp();
        }
        return new Result(allocations,stock,xp,true);
    }

    public static final class ShoppingPlan
    {
        public final List<Allocation> steps;
        public final Map<Integer,Long> purchases,remaining;
        public final Map<Skill,Double> experienceBySkill;
        ShoppingPlan(List<Allocation> steps,Map<Integer,Long> purchases,Map<Integer,Long> remaining)
        {
            this.steps=Collections.unmodifiableList(new ArrayList<>(steps)); this.purchases=Collections.unmodifiableMap(new LinkedHashMap<>(purchases));
            this.remaining=Collections.unmodifiableMap(new LinkedHashMap<>(remaining)); Map<Skill,Double> xp=new EnumMap<>(Skill.class);
            for (Allocation step : steps) xp.merge(step.recipe.skill,step.xp(),Double::sum); experienceBySkill=Collections.unmodifiableMap(xp);
        }
        public double xp(Skill skill) { return experienceBySkill.getOrDefault(skill,0.0); }
    }

    /** Material shopping for an explicitly chosen final method and selected upstream conversions. */
    public static ShoppingPlan shopping(Recipe target,long actions,Map<Integer,Long> bank,List<Recipe> selected,Map<Skill,Integer> levels)
    {
        if (actions<0) throw new IllegalArgumentException("Negative action count");
        if (target.level>levels.getOrDefault(target.skill,1)) throw new IllegalArgumentException("Final method level is not met");
        List<Recipe> ordered=productionOrder(selected); Map<Integer,Recipe> producers=new LinkedHashMap<>();
        for (Recipe recipe : ordered) if (recipe!=target && !recipe.id.equals(target.id) && recipe.level<=levels.getOrDefault(recipe.skill,1))
            for (Ingredient output : recipe.products) producers.putIfAbsent(output.itemId,recipe);
        Planner planner=new Planner(bank,producers); planner.make(target,actions,new LinkedHashSet<>()); return planner.result();
    }

    /** Includes same-skill XP earned by upstream conversions when choosing the smallest final action count. */
    public static ShoppingPlan shoppingForTarget(Recipe target,double remainingXp,Map<Integer,Long> bank,List<Recipe> selected,Map<Skill,Integer> levels)
    {
        if (!Double.isFinite(remainingXp) || remainingXp<0 || target.xp<=0) throw new IllegalArgumentException("Invalid XP target");
        long low=0,high=(long)Math.ceil(remainingXp/target.xp);
        while (low<high)
        {
            long mid=low+(high-low)/2; ShoppingPlan plan=shopping(target,mid,bank,selected,levels);
            if (plan.xp(target.skill)+0.000001>=remainingXp) high=mid; else low=mid+1;
        }
        return shopping(target,low,bank,selected,levels);
    }

    private static final class Planner
    {
        final Map<Integer,Long> stock,purchases=new LinkedHashMap<>();
        final Map<Integer,Recipe> producers;
        final List<Allocation> steps=new ArrayList<>();
        Planner(Map<Integer,Long> bank,Map<Integer,Recipe> producers) { stock=stock(bank); this.producers=producers; }
        void need(int id,long quantity,Set<String> path)
        {
            long held=Math.max(0,stock.getOrDefault(id,0L)),used=Math.min(held,quantity); stock.put(id,held-used); long missing=quantity-used;
            if (missing==0) return;
            Recipe producer=producers.get(id);
            if (producer==null) { purchases.merge(id,missing,Math::addExact); return; }
            long output=products(producer).get(id),actions=missing/output+(missing%output==0 ? 0 : 1);
            make(producer,actions,path);
            stock.compute(id,(key,count) -> Math.subtractExact(count==null ? 0 : count,missing));
        }
        void make(Recipe recipe,long actions,Set<String> path)
        {
            if (actions==0) return;
            if (!path.add(recipe.id)) throw new IllegalArgumentException("Cyclic production plan");
            for (Map.Entry<Integer,Long> input : amounts(recipe).entrySet()) need(input.getKey(),Math.multiplyExact(actions,input.getValue()),path);
            produce(recipe,actions,stock); steps.add(new Allocation(recipe,actions,false)); path.remove(recipe.id);
        }
        ShoppingPlan result() { return new ShoppingPlan(steps,purchases,stock); }
    }

    static List<Recipe> productionOrder(List<Recipe> selected)
    {
        Map<String,Recipe> unique=new LinkedHashMap<>(); for (Recipe recipe : selected) unique.putIfAbsent(recipe.id,recipe);
        List<Recipe> ordered=new ArrayList<>(); Set<String> done=new LinkedHashSet<>(),path=new LinkedHashSet<>();
        for (Recipe recipe : unique.values()) order(recipe,unique.values(),ordered,done,path);
        return ordered;
    }
    private static void order(Recipe recipe,Iterable<Recipe> selected,List<Recipe> ordered,Set<String> done,Set<String> path)
    {
        if (done.contains(recipe.id)) return;
        if (!path.add(recipe.id)) throw new IllegalArgumentException("Cyclic production plan");
        Set<Integer> inputs=amounts(recipe).keySet();
        for (Recipe producer : selected) if (!producer.id.equals(recipe.id))
            for (Ingredient output : producer.products) if (inputs.contains(output.itemId)) { order(producer,selected,ordered,done,path); break; }
        path.remove(recipe.id); done.add(recipe.id); ordered.add(recipe);
    }
    private static Map<Integer,Long> stock(Map<Integer,Long> bank)
    { Map<Integer,Long> result=new LinkedHashMap<>(); bank.forEach((id,n) -> { if (id!=null && n!=null && id>0 && n>0) result.put(id,n); }); return result; }
    private static void consume(Recipe recipe,long actions,Map<Integer,Long> stock)
    { amounts(recipe).forEach((id,n) -> stock.compute(id,(key,count) -> Math.subtractExact(count==null ? 0 : count,Math.multiplyExact(actions,n)))); }
    private static void produce(Recipe recipe,long actions,Map<Integer,Long> stock)
    { products(recipe).forEach((id,n) -> { long count=Math.multiplyExact(actions,n); if (count>0) stock.merge(id,count,Math::addExact); }); }
    private static Map<Integer,Long> products(Recipe recipe)
    { Map<Integer,Long> result=new LinkedHashMap<>(); for (Ingredient output : recipe.products) result.merge(output.itemId,(long)output.quantity,Math::addExact); return result; }

    /** Missing items for a chosen number of actions, after the plan's existing allocation. */
    public static Map<Integer, Long> missing(Recipe recipe, long actions, Map<Integer, Long> bank)
    {
        if (actions < 0) throw new IllegalArgumentException("Negative action count");
        Map<Integer, Long> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, Long> ingredient : amounts(recipe).entrySet())
        {
            long needed = Math.multiplyExact(actions, ingredient.getValue());
            long shortfall = Math.max(0, needed - Math.max(0, bank.getOrDefault(ingredient.getKey(), 0L)));
            if (shortfall > 0) result.put(ingredient.getKey(), shortfall);
        }
        return result;
    }

    private static Map<Integer, Long> amounts(Recipe recipe)
    {
        Map<Integer, Long> amounts = new LinkedHashMap<>();
        for (Ingredient item : recipe.ingredients) amounts.merge(item.itemId, (long) item.quantity, Long::sum);
        return amounts;
    }
}
