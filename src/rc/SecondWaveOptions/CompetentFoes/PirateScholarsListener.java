package rc.SecondWaveOptions.CompetentFoes;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;
import rc.SecondWaveOptions.SecondWaveOptionsModPlugin;

import java.util.*;

import static rc.SecondWaveOptions.SecondWaveOptionsModPlugin.*;

//todo should this apply to any other factions...? annoying because we don't want people to learn a ship then have no weapons to fit on
public class PirateScholarsListener extends BaseCampaignEventListener {
    static Logger log = Global.getLogger(PirateScholarsListener.class);
    public static String thingsLearnedKey = String.format("$%s_%s", SecondWaveOptionsModPlugin.MOD_PREFIX, "thingsLearned"); //V is Set<Pair<String, LearnableType>>

    public enum LearnableType {
        HULL,
        WEAPON,
        FIGHTER,
        HULLMOD
    }

    public PirateScholarsListener() {
        super(false);
    }

    @Override
    public void reportEconomyMonthEnd() {
        FactionAPI pirates = Global.getSector().getFaction(Factions.PIRATES);
        runAll(pirates);
    }

    public static void runAll(FactionAPI faction) {
        long seed = Math.abs(faction.getDisplayName().hashCode()
                                     + Global.getSector().getSeedString().hashCode()
                                     + Global.getSector().getPlayerPerson().getNameString().hashCode());
        int numLearningAttempts = genLearningAttempts(psLearningAttemptsPerMonth, psMonthsForBaseEffect);
        forget(faction);
        Set<Pair<String, LearnableType>> thingsToLearn = genThingsLearned(faction, seed, numLearningAttempts, psChance);
        actuallyLearnThings(faction, thingsToLearn);
    }

    // should be regenerated every time in case you sell stuff to the faction, and they learn something new
    public static Set<Pair<String, LearnableType>> genThingsLearned(FactionAPI faction, long seed, int numLearningAttempts, float learningChance) {
        Set<Pair<String, LearnableType>> thingsLearned = new HashSet<>();
        Random random = new Random(seed);
        WeightedRandomPicker<Pair<String, LearnableType>> picker = new WeightedRandomPicker<>(random);
        if (true) {
            addShipsToPicker(faction, picker);
        }
        if (true) {
            addWeaponsToPicker(faction, picker);
        }
        if (true) {
            addFightersToPicker(faction, picker);
        }
        if (false) {
            addHullModsToPicker(faction, picker);
        }
        if (picker.isEmpty()) return thingsLearned;
        /*
        HashMap<String, Float> pickProbabilityPercent = new HashMap<>();
        for (Pair<String, LearnableType> pick : picker.getItems()) {
            pickProbabilityPercent.put(pick.one, picker.getWeight(pick)/picker.getTotal()*100);
        }
        log.debug(pickProbabilityPercent);
        /**/
        for (int i = 0; i < numLearningAttempts; i++) {
            if (random.nextFloat() < learningChance) {
                Pair<String, LearnableType> pick = picker.pickAndRemove();
                thingsLearned.add(pick);
            }
        }
        faction.getMemoryWithoutUpdate().set(thingsLearnedKey, thingsLearned);
        return thingsLearned;
    }

    public static void actuallyLearnThings(FactionAPI faction, Set<Pair<String, LearnableType>> thingsToLearn) {
        for (Pair<String, LearnableType> thingToLearn : thingsToLearn) {
            switch (thingToLearn.two) {
                case HULL:
                    faction.addKnownShip(thingToLearn.one, true);
                    break;
                case WEAPON:
                    faction.addKnownWeapon(thingToLearn.one, true);
                    break;
                case FIGHTER:
                    faction.addKnownFighter(thingToLearn.one, true);
                    break;
                case HULLMOD:
                    faction.addKnownHullMod(thingToLearn.one);
                    break;
            }
            log.debug(String.format("Learned %s of type %s",thingToLearn.one, thingToLearn.two));
        }
    }

    public static void forget(FactionAPI faction) {
        if (!faction.getMemoryWithoutUpdate().contains(thingsLearnedKey)) return;
        Set<Pair<String, LearnableType>> thingsLearned = (Set<Pair<String, LearnableType>>) faction.getMemoryWithoutUpdate().get(thingsLearnedKey);
        for (Pair<String, LearnableType> thingToLearn : thingsLearned) {
            switch (thingToLearn.two) {
                case HULL:
                    faction.removeKnownShip(thingToLearn.one);
                    break;
                case WEAPON:
                    faction.removeKnownWeapon(thingToLearn.one);
                    break;
                case FIGHTER:
                    faction.removeKnownFighter(thingToLearn.one);
                    break;
                case HULLMOD:
                    faction.removeKnownHullMod(thingToLearn.one);
                    break;
            }
        }
        faction.getMemoryWithoutUpdate().unset(thingsLearnedKey);
    }

    public static void addShipsToPicker(FactionAPI faction, WeightedRandomPicker<Pair<String, LearnableType>> picker) {
        List<String> DISALLOWED_TAGS = Arrays.asList(
                Tags.NO_SELL, Tags.NO_DROP, Tags.NO_BP_DROP, Tags.HULLMOD_NO_DROP_SALVAGE
                , Tags.TAG_NO_AUTOFIT, Tags.AUTOMATED_RECOVERABLE, Tags.OMEGA, Tags.RESTRICTED);
        List<ShipHullSpecAPI.ShipTypeHints> DISALLOWED_HINTS = Arrays.asList(
                ShipHullSpecAPI.ShipTypeHints.UNBOARDABLE, ShipHullSpecAPI.ShipTypeHints.HIDE_IN_CODEX, ShipHullSpecAPI.ShipTypeHints.STATION);
        for (ShipHullSpecAPI spec : Global.getSettings().getAllShipHullSpecs()) {
            if (spec.isDefaultDHull()
                    || spec.getHullSize() == ShipAPI.HullSize.FIGHTER
                    || !spec.hasHullName()
                    || "shuttlepod".equals(spec.getHullId())
                    || !Collections.disjoint(spec.getTags(), DISALLOWED_TAGS)
                    || !Collections.disjoint(spec.getHints(), DISALLOWED_HINTS)) continue;
            String id = spec.getHullId();
            if (!faction.getKnownShips().contains(id)) {
                float weight = 1f / (spec.getRarity());
                if (spec.hasTag(Items.TAG_RARE_BP)) weight *= 0.25f;
                switch (spec.getHullSize()) {
                    case CAPITAL_SHIP:
                        weight *= 0.1f;
                        break;
                    case CRUISER:
                        weight *= 0.5f;
                        break;
                    case DESTROYER:
                    case FRIGATE:
                        weight *= 1f;
                        break;
                    default:
                        break;
                }
                picker.add(new Pair<>(id, LearnableType.HULL), (float) Math.pow(weight, 2));
            }
        }
    }

    public static void addWeaponsToPicker(FactionAPI faction, WeightedRandomPicker<Pair<String, LearnableType>> picker) {
        List<String> DISALLOWED_TAGS = Arrays.asList(
                Tags.NO_SELL, Tags.NO_DROP, Tags.NO_BP_DROP, Tags.HULLMOD_NO_DROP_SALVAGE, Tags.OMEGA, Tags.RESTRICTED);
        for (WeaponSpecAPI spec : Global.getSettings().getAllWeaponSpecs()) {
            if (!Collections.disjoint(spec.getTags(), DISALLOWED_TAGS)
                    || spec.getAIHints().contains(WeaponAPI.AIHints.SYSTEM)) continue;
            boolean autofit = false;
            for (String tag : spec.getTags()) {
                if (Character.isDigit(tag.charAt(tag.length() - 1))) {
                    autofit = true;
                    break;
                }
            }
            String id = spec.getWeaponId();
            if (!faction.getKnownWeapons().contains(id) && autofit) {
                float weight = 1f / ((spec.getTier() + 1) * spec.getRarity());
                switch (spec.getSize()) {
                    case LARGE:
                        weight *= 0.5f;
                        break;
                    case MEDIUM:
                        weight *= 0.75f;
                        break;
                    case SMALL:
                        weight *= 1f;
                        break;
                }
                picker.add(new Pair<>(id, LearnableType.WEAPON), (float) Math.pow(weight, 2));
            }
        }
    }

    public static void addFightersToPicker(FactionAPI faction, WeightedRandomPicker<Pair<String, LearnableType>> picker) {
        List<String> DISALLOWED_TAGS = Arrays.asList(
                Tags.NO_SELL, Tags.NO_DROP, Tags.NO_BP_DROP, Tags.HULLMOD_NO_DROP_SALVAGE, Tags.OMEGA, Tags.RESTRICTED);
        for (FighterWingSpecAPI spec : Global.getSettings().getAllFighterWingSpecs()) {
            if (!Collections.disjoint(spec.getTags(), DISALLOWED_TAGS)) continue;
            boolean autofit = false;
            for (String tag : spec.getTags()) {
                if (Character.isDigit(tag.charAt(tag.length() - 1))) {
                    autofit = true;
                    break;
                }
            }
            String id = spec.getId();
            if (!faction.getKnownFighters().contains(id) && autofit) {
                float weight = 1f / ((spec.getTier() + 1) * spec.getRarity());
                if (spec.getOpCost(null) < 8 + 1) {
                    weight *= 1f;
                } else if (spec.getOpCost(null) < 8 * 2 + 1)
                    weight *= 0.75f;
                else {
                    weight *= 0.5f;
                }
                picker.add(new Pair<>(id, LearnableType.FIGHTER), (float) Math.pow(weight, 2));
            }
        }
    }

    public static void addHullModsToPicker(FactionAPI faction, WeightedRandomPicker<Pair<String, LearnableType>> picker) {
        List<String> DISALLOWED_TAGS = Arrays.asList(
                Tags.NO_DROP, Tags.NO_SELL, Tags.NO_BP_DROP, Tags.HULLMOD_NO_DROP_SALVAGE, Tags.HULLMOD_DMOD);
        List<String> REQUIRED_TAGS = Arrays.asList(Tags.HULLMOD_OFFENSIVE, Tags.HULLMOD_DEFENSIVE, Tags.HULLMOD_ENGINES);
        for (HullModSpecAPI spec : Global.getSettings().getAllHullModSpecs()) {
            if (spec.isHidden()
                    || spec.isHiddenEverywhere()
                    || spec.isAlwaysUnlocked()
                    || !Collections.disjoint(spec.getTags(), DISALLOWED_TAGS)
                    || Collections.disjoint(spec.getTags(), REQUIRED_TAGS)) continue;
            String id = spec.getId();
            if (!faction.getKnownHullMods().contains(id)) {
                float weight = 1f / ((spec.getTier() + 1) * spec.getRarity());
                picker.add(new Pair<>(id, LearnableType.HULLMOD), (float) Math.pow(weight, 2));
            }
        }
    }

    public static int genLearningAttempts(int learningAttemptsPerMonth, int monthsForBaseEffect) {
        int cyclesPassed = Global.getSector().getClock().getCycle() - 206;
        int monthsPassed = 12 * cyclesPassed + Global.getSector().getClock().getMonth() - 3;
        return Math.max(0, learningAttemptsPerMonth * (monthsPassed - monthsForBaseEffect));
    }
}