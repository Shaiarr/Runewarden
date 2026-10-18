/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Authored, ordered learning milestones. Native phase observations never mark an unobserved milestone complete. */
final class MinigameLessonCatalog
{
    static final class Milestone
    {
        final String id, title, instruction;
        Milestone(String id, String title, String instruction) { this.id=id; this.title=title; this.instruction=instruction; }
    }
    static final class Lesson
    {
        final MinigameActivityCatalog.Activity activity;
        final String title, source;
        final List<Milestone> milestones;
        Lesson(MinigameActivityCatalog.Activity activity,String title,String source,List<Milestone> milestones)
        { this.activity=activity;this.title=title;this.source=source;this.milestones=Collections.unmodifiableList(new ArrayList<>(milestones)); }
        @Override public String toString(){ return title; }
    }
    private static final Map<MinigameActivityCatalog.Activity,Lesson> LESSONS=build();
    static Lesson get(MinigameActivityCatalog.Activity activity){return LESSONS.get(activity);}
    static List<Lesson> all(){return Collections.unmodifiableList(new ArrayList<>(LESSONS.values()));}
    private static Milestone step(String id,String title,String instruction){return new Milestone(id,title,instruction);}
    private static void add(Map<MinigameActivityCatalog.Activity,Lesson> target,MinigameActivityCatalog.Activity activity,String title,String source,Milestone... steps)
    {target.put(activity,new Lesson(activity,title,source,java.util.Arrays.asList(steps)));}
    private static Map<MinigameActivityCatalog.Activity,Lesson> build()
    {
        Map<MinigameActivityCatalog.Activity,Lesson> map=new EnumMap<>(MinigameActivityCatalog.Activity.class);
        add(map,MinigameActivityCatalog.Activity.WINTERTODT,"Wintertodt","https://oldschool.runescape.wiki/w/Wintertodt",
            step("prepare","Bring the winter kit","Take an axe, tinderbox or bruma torch, knife if fletching, and a hammer if repairing. Wear four warm items and bring your chosen warmth supplies."),
            step("gather","Chop bruma roots","Chop the bruma roots beside your chosen brazier. Leave room for the batch you intend to feed."),
            step("fletch","Choose roots or kindling","Fletch roots for extra points and Fletching XP, or feed roots for a faster Firemaking loop. This is a method choice, not a required fletch step."),
            step("repair","Keep the brazier working","Repair a broken brazier with a hammer, then relight it with a tinderbox or bruma torch."),
            step("feed","Feed a lit brazier","Feed roots or kindling to a burning brazier. Resume after an interruption and monitor your warmth."),
            step("support","Help the pyromancer","Use a rejuvenation potion on an incapacitated pyromancer. Prepare the potion from bruma herb and an unfinished rejuvenation potion when needed."),
            step("reward","Finish and resupply","Check your native round points, take your reward and refill supplies before the next round."));
        add(map,MinigameActivityCatalog.Activity.GOTR,"Guardians of the Rift","https://oldschool.runescape.wiki/w/Guardians_of_the_Rift",
            step("prepare","Prepare pouches and tools","Bring a pickaxe, chisel and your available essence pouches. Choose the altar unlocks and fragment batch that fit your account."),
            step("gather","Mine guardian fragments","Mine remains up to your chosen fragment target. The large-remains shortcut requires 56 Agility; the huge-remains shelf is reached through its active portal."),
            step("craft","Make guardian essence","Use the workbench to turn fragments into guardian essence. Fill usable pouches and keep inventory space for your crafting trip."),
            step("altar","Use an active altar","Enter an available elemental or catalytic rift that you can access, then craft the guardian essence and pouch contents."),
            step("empower","Empower the Great Guardian","Bring your guardian stones back to the Great Guardian. Keep elemental and catalytic contributions in balance for reward searches."),
            step("cells","Place your charged cell","Use your charged cell to help a barrier or guardian according to the team needs. Take another uncharged cell for your next trip."),
            step("repeat","Repeat and claim rewards","Use portal trips when available, repeat the craft-and-empower loop, and check the reward guardian after the round."));
        add(map,MinigameActivityCatalog.Activity.TEMPOROSS,"Tempoross","https://oldschool.runescape.wiki/w/Tempoross",
            step("prepare","Prepare encounter supplies","Take a harpoon, rope, hammer and buckets. Keep water available; spirit angler equipment can replace a carried rope when its set effect applies."),
            step("gather","Catch harpoonfish","Catch fish at a live shoal. The special shoal offers a chance of a double catch; take the opportunity when it is available."),
            step("cook","Cook your batch","Cook raw harpoonfish at the shrine. Cooked fish drain more energy when fired from a cannon."),
            step("load","Load the ammunition crate","Load your prepared fish into a ship ammunition crate. Check the energy display before planning another full fishing batch."),
            step("hazard","Handle the encounter hazards","Tether to a mast or totem when the wave warning appears. Extinguish blocking fires with water and repair a broken tether point before using it."),
            step("spirit","Fish the spirit pool","When Tempoross loses all energy, fish the spirit pool to reduce its essence. Resume the ordinary loop if another energy phase begins."),
            step("reward","Claim permits and resupply","After the encounter, check your permits at the reward pool and replace supplies before another game."));
        add(map,MinigameActivityCatalog.Activity.GIANTS_FOUNDRY,"Giants' Foundry","https://oldschool.runescape.wiki/w/Giants%27_Foundry",
            step("commission","Take a commission","Ask Kovac for a commission. Read its requested sword characteristics before choosing your mould."),
            step("metal","Load the crucible","Load 28 metal units from your chosen alloy. Equipment contributes according to its Foundry value; check the actual crucible contents."),
            step("mould","Set a matching mould","Choose tip, blade and forte parts that best match the commission, then pour the metal into the mould."),
            step("preform","Collect the preform","Collect the cooled preform from the mould jig and check the first tool segment on the progress bar."),
            step("temperature","Match the current heat band","Use the lava pool or waterfall until the native temperature indicator is in the current tool band."),
            step("work","Work the indicated tool","Work the trip hammer, grindstone or polishing wheel shown by the current native progress segment. Recheck heat as progress advances."),
            step("reward","Return the completed sword","Give the completed sword to Kovac. Check quality, XP and reputation before starting the next commission."));
        add(map,MinigameActivityCatalog.Activity.MIXOLOGY,"Mastering Mixology","https://oldschool.runescape.wiki/w/Mastering_Mixology",
            step("paste","Stock your paste","Add herbs to the hopper to maintain Mox, Aga and Lye. Select an order that matches your level and desired reward points."),
            step("recipe","Read the three-part recipe","Read the current order and pull its three matching paste levers. Each potion uses the displayed Mox, Aga and Lye combination."),
            step("collect","Collect the unfinished potion","Take the unfinished potion from the mixing vessel. Match its name to an active order."),
            step("process","Use the requested station","Use the retort, agitator or alembic named by that order. Respond to the station animation yourself."),
            step("deliver","Deliver a finished order","Place the finished potion on the conveyor belt. Re-read the new order list before preparing the next potion."),
            step("reward","Review your points","Check the Mox, Aga and Lye point balance against your chosen reward and replenish paste as needed."));
        add(map,MinigameActivityCatalog.Activity.MAHOGANY_HOMES,"Mahogany Homes","https://oldschool.runescape.wiki/w/Mahogany_Homes",
            step("contract","Take the right contract tier","Choose the contract tier you can supply. Bring its planks, a hammer, saw and steel bars when needed."),
            step("travel","Travel to your customer","Read the assigned customer and town, then travel to the house. Use your selected transport and bank only for the current contract supplies."),
            step("remove","Remove damaged furniture","Inside the customer house, remove the furniture that offers a Remove action. Leave completed furniture alone."),
            step("build","Build or repair the furniture","Repair matching furniture and build replacements at the highlighted actionable hotspots. Work through every room of this house."),
            step("complete","Speak to the customer","After all repairs, talk to the customer to finish the contract. Accept tea when useful, then take the next contract."),
            step("reward","Use your contract points","Check your next reward goal and restock the tier supplies before continuing."));
        add(map,MinigameActivityCatalog.Activity.PEST_CONTROL,"Pest Control","https://oldschool.runescape.wiki/w/Pest_Control",
            step("prepare","Prepare for the selected boat","Choose a boat you can enter and bring a combat setup for portals. Repair supplies are optional if you plan to help the defences."),
            step("defend","Protect the Void Knight","Keep enemies away from the knight and close gates behind you. Watch the native knight health and your activity."),
            step("portal","Attack an open portal","Choose a currently unshielded portal. The live coach reads observed portal forms and health; shielded portals cannot be damaged."),
            step("spinner","Stop portal healing","Kill spinners that are healing the portal you are attacking, then return to damaging the portal."),
            step("finish","Destroy the remaining portals","Move to another open portal after one dies. Keep contributing while the team finishes the island."),
            step("reward","Review commendations","After the game, check your commendation reward goal before boarding again."));
        add(map,MinigameActivityCatalog.Activity.BLAST_FURNACE,"Blast Furnace","https://oldschool.runescape.wiki/w/Blast_Furnace",
            step("prepare","Prepare the batch","Choose the bar method and bring its ore and coal. Maintain the coffer and the foreman payment when your level requires it."),
            step("coal","Supply coal if required","Deposit coal for your chosen alloy. Use the coal bag if available and check native stored coal before adding another batch."),
            step("load","Load the conveyor","Put the ore on the conveyor belt, keeping the batch consistent with your chosen bar method."),
            step("cool","Cool the bars","Wear ice gloves or use water when the dispenser contains hot bars. Check that the bars are ready to collect."),
            step("collect","Collect the bars","Collect bars from the dispenser, then bank them. Keep goldsmith gauntlet timing in mind when using the gold method."),
            step("repeat","Bank and repeat","Restock the next ore batch and recheck coal, coffer and stamina supplies."));
        add(map,MinigameActivityCatalog.Activity.TITHE_FARM,"Tithe Farm","https://oldschool.runescape.wiki/w/Tithe_Farm",
            step("prepare","Choose a manageable planting circuit","Take the seed tier you can use, a seed dibber, spade and enough filled watering cans. Start with a circuit you can consistently water."),
            step("plant","Plant and water immediately","Plant one patch and water it before moving on. Follow the same circuit order on later watering passes."),
            step("water","Keep the plants watered","Water dry plants before expanding the circuit. Avoid planting more than you can maintain."),
            step("harvest","Harvest the mature plants","Harvest grown fruit, keeping enough time to finish the remaining watering pass."),
            step("deposit","Deposit fruit in the sack","Deposit your harvested fruit and watch the native round score. Refill watering cans before another circuit."),
            step("recover","Clear dead plants and reset","Clear any dead plants, reduce the circuit if needed, and select the next reward milestone."));
        add(map,MinigameActivityCatalog.Activity.BARBARIAN_ASSAULT,"Barbarian Assault","https://oldschool.runescape.wiki/w/Barbarian_Assault",
            step("prepare","Choose a role and collect supplies","Choose Attacker, Collector, Defender or Healer and take the role supplies. Agree who fills each role before starting."),
            step("call","Send the current partner call","Use your horn to send the native Call to send value. Repeat when the instruction changes."),
            step("role","Follow the call you receive","Attacker: use the called style. Collector: collect the called egg colour. Defender: bait runners with the called food. Healer: use the called poison food."),
            step("support","Maintain the role loop","Load collected eggs, repair defender traps, top up poison supplies or heal teammates as your role requires."),
            step("wave","Finish the wave as a team","Continue calling while the remaining penance are cleared. Use loaded cannons to help the team where appropriate."),
            step("queen","Complete the queen hand-off","On wave ten, follow the team egg chain: Collector to Healer to Attacker to Defender and back to Collector, then load and fire the omega egg."),
            step("reward","Review role points","Choose the next role or reward goal after the queen kill and restock before another game."));
        add(map,MinigameActivityCatalog.Activity.NIGHTMARE_ZONE,"Nightmare Zone","https://oldschool.runescape.wiki/w/Nightmare_Zone",
            step("prepare","Choose your dream method","Select the bosses and difficulty for your account. Take your chosen training weapon and prayer or absorption supplies."),
            step("enter","Enter the chosen dream","Pay Dominic and enter your selected dream. Confirm that your expected bosses and supplies are present."),
            step("protect","Apply your protection method","Use absorption or the prayer method you prepared. Manage health consistently with that method."),
            step("train","Maintain combat and supplies","Keep attacking with your chosen style. Monitor absorption, health, prayer and combat boosts."),
            step("finish","Leave and review points","Finish when your supplies or planned session run out. Check your banked reward points and restock."));
        add(map,MinigameActivityCatalog.Activity.PYRAMID_PLUNDER,"Pyramid Plunder","https://oldschool.runescape.wiki/w/Pyramid_Plunder",
            step("prepare","Choose your highest eligible room","Bring food and poison protection. Choose the room you can enter at your current Thieving level."),
            step("trap","Pass the entrance trap","Pass the spear trap at the start of each room before looting or searching the exits."),
            step("advance","Advance through the lower rooms","Search for the correct door and move toward your planned looting room while watching the native timer."),
            step("loot","Loot your chosen room","Search urns in your chosen room. Include the golden chest or sarcophagus according to your selected XP or sceptre method."),
            step("finish","Leave and restore supplies","Leave before your planned cutoff, bank or trade loot, and restore health and poison protection before re-entering."));
        add(map,MinigameActivityCatalog.Activity.MAGE_TRAINING_ARENA,"Mage Training Arena","https://oldschool.runescape.wiki/w/Mage_Training_Arena",
            step("prepare","Choose a reward and room","Check which Pizazz point types your reward needs and bring the runes for the room you will train."),
            step("telekinetic","Telekinetic Theatre","Stand in the direction you want the maze guardian pulled and cast Telekinetic Grab. Repeat until it reaches the finish."),
            step("alchemy","Alchemist's Playground","Use the current table to find and alchemise the highest-value item, then deposit coins. Recheck after values change."),
            step("enchant","Enchanting Chamber","Collect shapes, cast your enchantment spell and deposit the resulting orbs. Follow the displayed bonus shape when useful."),
            step("graveyard","Creature Graveyard","Collect bones and convert them to fruit. Eat when needed and deposit the remainder while monitoring health."),
            step("reward","Redeem the selected reward","Recheck all four point balances and buy the reward once its requirements are met."));
        add(map,MinigameActivityCatalog.Activity.VOLCANIC_MINE,"Volcanic Mine","https://oldschool.runescape.wiki/w/Volcanic_Mine",
            step("prepare","Agree a team role","Bring the required pickaxe, numulite, heat protection and food. Agree who checks and maintains each vent before entering."),
            step("check","Read the assigned vent","Inspect your assigned vent and communicate its observed pressure. Use the team plan to decide whether to block or unblock its chamber."),
            step("chamber","Maintain the chamber","Keep the assigned pressure under control. Recheck after the game changes vent conditions instead of assuming the opening state lasts."),
            step("mine","Follow and mine the boulder","Mine the current boulder, maintain a route across platforms and watch your health and the stability display."),
            step("exit","Return to the exit","Stop the mining loop early enough to reach the exit before the native game timer expires."),
            step("reward","Review points and restock","Check your point target, replenish protection and food, and confirm the next team role."));
        add(map,MinigameActivityCatalog.Activity.BLAST_MINE,"Blast Mine","https://oldschool.runescape.wiki/w/Blast_Mine",
            step("prepare","Prepare a blast circuit","Bring a chisel, dynamite and a tinderbox. Choose a short circuit with a clear retreat for each blast."),
            step("chisel","Excavate hard rock","Chisel a hard-rock face to make room for a dynamite pot."),
            step("load","Load the dynamite","Place dynamite into the excavated rock before lighting it."),
            step("light","Light and move clear","Light the dynamite and immediately move away from the blast area."),
            step("collect","Collect blasted ore promptly","Take the blasted ore from destroyed rock before it disappears, then deposit it in the sack."),
            step("reward","Collect the processed ore","Return to the operator to collect the ore accumulated by your circuit and replenish dynamite."));
        add(map,MinigameActivityCatalog.Activity.FISHING_TRAWLER,"Fishing Trawler","https://oldschool.runescape.wiki/w/Fishing_Trawler",
            step("prepare","Choose a deck role","Bring bailing buckets and swamp paste for the hold, or rope and an axe for the appropriate upper-deck tasks."),
            step("hold","Maintain the hold","Bail rising water and patch leaks. Watch the water display rather than waiting for the hold to flood."),
            step("nets","Maintain the nets","Repair a damaged net with rope so the trawler can keep catching fish."),
            step("tentacle","Deal with the tentacle","Use the upper-deck encounter to contribute when the tentacle is present and actionable."),
            step("finish","Maintain contribution to the end","Keep contributing while watching the trip timer, water and activity display."),
            step("reward","Search the shore reward net","Search the reward net on shore and restock before boarding the next trip."));
        add(map,MinigameActivityCatalog.Activity.DRIFT_NET,"Drift net fishing","https://oldschool.runescape.wiki/w/Drift_net_fishing",
            step("prepare","Prepare the underwater kit","Bring drift nets and suitable underwater equipment, then store enough replacements with Annette for your session."),
            step("set","Set both net frames","Install nets on empty frames. Check the two native net states before beginning the herding loop."),
            step("herd","Herd nearby fish shoals","Prod shoals toward set nets. Keep both frames working rather than leaving a full frame unattended."),
            step("empty","Empty a full net","Empty a net when its native state is full, then replace it."),
            step("restock","Restock and continue","Collect replacement nets from Annette or your stores and repeat until your session goal is reached."));
        add(map,MinigameActivityCatalog.Activity.TROUBLE_BREWING,"Trouble Brewing","https://oldschool.runescape.wiki/w/Trouble_Brewing",
            step("prepare","Choose a crew contribution","Choose an ingredient or repair task and collect the tools from your crew supply area."),
            step("gather","Gather ingredients","Gather scrapey logs, grubs, bitternuts or the water ingredients needed by your crew."),
            step("process","Process the ingredients","Cut scrapey logs into bark with a knife and process coloured ingredients as required by your chosen task."),
            step("deposit","Use your crew hoppers","Deposit each ingredient in your own crew matching hopper. Check the native ingredient counters for the next shortage."),
            step("maintain","Maintain production","Continue useful ingredient or repair work and monitor the crew score until the round ends."),
            step("reward","Review your reward goal","Check your reward currency and restock tools before another round."));
        add(map,MinigameActivityCatalog.Activity.SOUL_WARS,"Soul Wars","https://oldschool.runescape.wiki/w/Soul_Wars",
            step("prepare","Collect team supplies","Collect bandages and the supplies for your role from your base."),
            step("capture","Capture the Soul Obelisk","Work with your team to control the obelisk and maintain useful activity."),
            step("fragments","Collect soul fragments","Collect fragments from defeated creatures and bring them to the obelisk."),
            step("offer","Offer while your team controls it","Sacrifice fragments only while your team controls the obelisk, weakening the opposing avatar."),
            step("avatar","Support the avatar push","Follow the team attack or defence plan while monitoring both avatar strength and health."),
            step("reward","Review zeal and prepare again","Finish contributing through the end of the match and check the next zeal reward goal."));
        add(map,MinigameActivityCatalog.Activity.CASTLE_WARS,"Castle Wars","https://oldschool.runescape.wiki/w/Castle_Wars",
            step("prepare","Collect bandages and tools","Collect the supplies for your attack or defence role from your own castle."),
            step("status","Read both flag states","Check which standards are at their stands, dropped or being carried, then choose a useful team role."),
            step("capture","Take the opposing standard","Reach the opposing stand and take the enemy standard when available."),
            step("return","Return to your own stand","Carry the enemy standard to your own stand while your team protects the return route."),
            step("defend","Protect your standard","Help recover your standard or defend your carrier. Recheck the flag status after every capture attempt."),
            step("reward","Finish the match","Continue the team role through the match timer and review the next reward goal."));
        return Collections.unmodifiableMap(map);
    }
    /** A reference position only: an observation is not proof that preceding checklist milestones were completed. */
    static int observedIndex(Lesson lesson,MinigameActivityCatalog.Snapshot snapshot)
    {
        if(lesson==null||snapshot==null||snapshot.activity!=lesson.activity||snapshot.steps.isEmpty())return -1;
        String text=snapshot.steps.get(0).title.toLowerCase(Locale.ROOT);
        String key="";
        switch(lesson.activity)
        {
            case WINTERTODT:key=contains(text,"repair","light")?"repair":contains(text,"kindling","feed")?"feed":contains(text,"pyromancer","potion")?"support":"gather";break;
            case GOTR:key=contains(text,"empower")?"empower":contains(text,"altar")?"altar":contains(text,"essence")?"craft":"gather";break;
            case TEMPOROSS:key=contains(text,"tether","fire","repair")?"hazard":contains(text,"spirit")?"spirit":contains(text,"load")?"load":contains(text,"cook")?"cook":"gather";break;
            case GIANTS_FOUNDRY:key=contains(text,"return")?"reward":contains(text,"mould")?"mould":contains(text,"collect")?"preform":contains(text,"heat","cool")?"temperature":contains(text,"work","tool")?"work":"commission";break;
            case MIXOLOGY:key=contains(text,"deliver")?"deliver":contains(text,"agitator","retort","alembic")?"process":contains(text,"mix ")?"recipe":"paste";break;
            case MAHOGANY_HOMES:key=contains(text,"remove")?"remove":"build";break;
            case PEST_CONTROL:key=contains(text,"spinner")?"spinner":contains(text,"portal")?"portal":"defend";break;
            case BLAST_FURNACE:key=contains(text,"collect")?"collect":"load";break;
            case TITHE_FARM:key=contains(text,"water dry")?"water":contains(text,"harvest")?"harvest":contains(text,"dead")?"recover":contains(text,"deposit")?"deposit":"plant";break;
            case BARBARIAN_ASSAULT:key=contains(text,"omega","queen")?"queen":contains(text,"supplies","dispenser")?"prepare":contains(text,"call")?"call":"role";break;
            case NIGHTMARE_ZONE:key=contains(text,"protection")?"protect":"train";break;
            case PYRAMID_PLUNDER:key=contains(text,"trap")?"trap":contains(text,"advance")?"advance":"loot";break;
            case MAGE_TRAINING_ARENA:key=contains(text,"alchem")?"alchemy":contains(text,"bones","health")?"graveyard":contains(text,"enchant")?"enchant":"telekinetic";break;
            case VOLCANIC_MINE:key=contains(text,"exit","leave")?"exit":contains(text,"vent","chamber")?"check":"mine";break;
            case BLAST_MINE:key=contains(text,"collect")?"collect":contains(text,"light")?"light":contains(text,"load")?"load":"chisel";break;
            case FISHING_TRAWLER:key=contains(text,"water","bail")?"hold":contains(text,"net")?"nets":"finish";break;
            case DRIFT_NET:key=contains(text,"empty")?"empty":contains(text,"replacement")?"restock":contains(text,"set net")?"set":"herd";break;
            case TROUBLE_BREWING:key=contains(text,"deposit")?"deposit":contains(text,"cut","knife")?"process":"gather";break;
            case SOUL_WARS:key=contains(text,"restock")?"prepare":contains(text,"fragments")?"offer":"capture";break;
            case CASTLE_WARS:key=contains(text,"bandages")?"prepare":contains(text,"return")?"return":contains(text,"protect")?"defend":"status";break;
            default:break;
        }
        for(int i=0;i<lesson.milestones.size();i++)if(lesson.milestones.get(i).id.equals(key))return i;
        return -1;
    }
    private static boolean contains(String value,String... words){for(String word:words)if(value.contains(word))return true;return false;}
}
