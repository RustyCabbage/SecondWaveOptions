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
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import rc.SecondWaveOptions.SecondWaveOptionsModPlugin;
import rc.SecondWaveOptions.Utils.MathUtils;

import java.io.IOException;
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

    public enum LearningMode {
        MANUAL,
        DYNAMIC
    }

    public PirateScholarsListener() {
        super(false);
        log.setLevel(Level.INFO);
    }

    @Override
    public void reportEconomyMonthEnd() {
        FactionAPI pirates = Global.getSector().getFaction(Factions.PIRATES);
        runAll(pirates);
    }

    public static void runAll(FactionAPI faction) {
        long learningSeed = Math.abs(faction.getId().hashCode()
                                             + Global.getSettings().getVersionString().hashCode()
                                             + Global.getSector().getSeedString().hashCode());
        long pickerSeed = Math.abs(faction.getDisplayName().hashCode()
                                           + Global.getSettings().getGameVersion().hashCode()
                                           + Global.getSector().getPlayerPerson().getNameString().hashCode()
                                           + Global.getSector().getSeedString().hashCode());
        //log.debug(learningSeed);
        //log.debug(pickerSeed);
        forget(faction);
        int numLearningAttempts = 0;
        Set<Pair<String, LearnableType>> thingsToLearn = new HashSet<>();
        LearningMode learningMode = LearningMode.valueOf(psLearningMode);
        switch (learningMode) {
            case MANUAL:
                numLearningAttempts = genLearningAttempts(psLearningAttemptsPerMonth, psMonthsForBaseEffect);
                thingsToLearn = genThingsLearned(faction, pickerSeed, learningSeed, numLearningAttempts, psChance);
                break;
            case DYNAMIC:
                Pair<Integer, Float> dynamicLearningParameters = getDynamicLearningParameters();
                numLearningAttempts = genLearningAttempts(dynamicLearningParameters.one, psMonthsForBaseEffect);
                thingsToLearn = genThingsLearned(faction, pickerSeed, learningSeed, numLearningAttempts, dynamicLearningParameters.two);
                break;
        }
        if (!thingsToLearn.isEmpty()) actuallyLearnThings(faction, thingsToLearn);
    }

    // should be regenerated every time in case you sell stuff to the faction, and they learn something new
    public static Set<Pair<String, LearnableType>> genThingsLearned(FactionAPI faction, long pickerSeed, long learningSeed, int numLearningAttempts, float learningChance) {
        Set<Pair<String, LearnableType>> thingsLearned = new HashSet<>();
        Random pickerRandom = new Random(pickerSeed);
        Random learningRandom = new Random(learningSeed);
        for (int i = 0; i < 10; i++) {
            float a = pickerRandom.nextFloat();
            float b = learningRandom.nextFloat();
            //log.debug(a + " " + b);
        }
        WeightedRandomPicker<Pair<String, LearnableType>> picker = new WeightedRandomPicker<>(pickerRandom);
        addShipsToPicker(faction, picker, psWeightPower);
        addWeaponsToPicker(faction, picker, psWeightPower);
        addFightersToPicker(faction, picker, psWeightPower);

        if (psLearnHullMods) {
            addHullModsToPicker(faction, picker, psWeightPower);
        }
        /*
        List<String> pickItem = new ArrayList<>();
        HashMap<String, Float> pickWeight = new HashMap<>();
        HashMap<String, Float> pickProbabilityPercent = new HashMap<>();
        for (Pair<String, LearnableType> pick : picker.getItems()) {
            pickItem.add(pick.one);
            pickWeight.put(pick.one, picker.getWeight(pick));
            pickProbabilityPercent.put(pick.one, picker.getWeight(pick) / picker.getTotal() * 100);
        }
        log.debug(pickItem);
        log.debug(pickWeight);
        log.debug(pickProbabilityPercent);
        log.debug(picker.getTotal());
        /**/
        int numLearnableTechs = picker.getItems().size();
        log.info(String.format("Learnable Techs: %s",numLearnableTechs));
        for (int i = 0; i < numLearningAttempts && !picker.isEmpty(); i++) {
            float nextFloat = learningRandom.nextFloat();
            if (nextFloat < learningChance) {
                //picker.print(String.format("Learning attempt %s",i+1));
                Pair<String, LearnableType> pick = picker.pickAndRemove();
                log.debug(String.format("Learning attempt %s: Picked [%s,%s]", i + 1, pick.one, pick.two));
                thingsLearned.add(pick);
                if (picker.isEmpty()) log.info("Learned All Techs.");
            } else {
                //log.debug(String.format("Learning attempt %s: Failed to pick with %s > %s", i + 1, nextFloat, learningChance));
            }
        }
        if (!picker.isEmpty()) log.info(String.format("Learned %s Techs.",numLearnableTechs-picker.getItems().size()));
        faction.getMemoryWithoutUpdate().set(thingsLearnedKey, thingsLearned);
        //log.debug(thingsLearned);
        return thingsLearned;
    }

    public static void actuallyLearnThings(FactionAPI faction, Set<Pair<String, LearnableType>> thingsToLearn) {
        if (thingsToLearn.isEmpty()) return;
        for (Pair<String, LearnableType> thingToLearn : thingsToLearn) {
            if (thingToLearn.one == null || thingToLearn.two == null) continue;
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
            //log.debug(String.format("Learned %s of type %s", thingToLearn.one, thingToLearn.two));
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

    public static void addShipsToPicker(FactionAPI faction, WeightedRandomPicker<Pair<String, LearnableType>> picker, float weightPower) {
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
                float weight = 1f / Math.min(1, spec.getRarity());
                if (spec.hasTag(Items.TAG_RARE_BP)) weight *= 0.25f;
                switch (spec.getHullSize()) {
                    case CAPITAL_SHIP:
                        weight *= 0.2f;
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
                picker.add(new Pair<>(id, LearnableType.HULL), (float) Math.pow(weight, weightPower));
            }
        }
    }


    public static void addWeaponsToPicker(FactionAPI faction, WeightedRandomPicker<Pair<String, LearnableType>> picker, float weightPower) {
        List<String> DISALLOWED_TAGS = Arrays.asList(
                Tags.NO_SELL, Tags.NO_DROP, Tags.NO_BP_DROP, Tags.HULLMOD_NO_DROP_SALVAGE, Tags.OMEGA, Tags.RESTRICTED);
        for (WeaponSpecAPI spec : Global.getSettings().getAllWeaponSpecs()) {
            if (spec.getRarity() == 0f
                    || !Collections.disjoint(spec.getTags(), DISALLOWED_TAGS)
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
                float weight = 1f / Math.max(1, (spec.getTier() + 1)) * MathUtils.clampValue(spec.getRarity(), 0.000001f, 1f);
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
                picker.add(new Pair<>(id, LearnableType.WEAPON), (float) Math.pow(weight, weightPower));
            }
        }
    }

    public static void addFightersToPicker(FactionAPI faction, WeightedRandomPicker<Pair<String, LearnableType>> picker, float weightPower) {
        List<String> DISALLOWED_TAGS = Arrays.asList(
                Tags.NO_SELL, Tags.NO_DROP, Tags.NO_BP_DROP, Tags.HULLMOD_NO_DROP_SALVAGE, Tags.OMEGA, Tags.RESTRICTED);
        for (FighterWingSpecAPI spec : Global.getSettings().getAllFighterWingSpecs()) {
            if (spec.getRarity() == 0f
                    || !Collections.disjoint(spec.getTags(), DISALLOWED_TAGS)) continue;
            boolean autofit = false;
            for (String tag : spec.getTags()) {
                if (Character.isDigit(tag.charAt(tag.length() - 1))) {
                    autofit = true;
                    break;
                }
            }
            String id = spec.getId();
            if (!faction.getKnownFighters().contains(id) && autofit) {
                float weight = 1f / Math.max(1, (spec.getTier() + 1)) * MathUtils.clampValue(spec.getRarity(), 0.000001f, 1f);
                if (spec.getOpCost(null) < 8 + 1) {
                    weight *= 1f;
                } else if (spec.getOpCost(null) < 8 * 2 + 1)
                    weight *= 0.75f;
                else {
                    weight *= 0.5f;
                }
                picker.add(new Pair<>(id, LearnableType.FIGHTER), (float) Math.pow(weight, weightPower));
            }
        }
    }

    public static void addHullModsToPicker(FactionAPI faction, WeightedRandomPicker<Pair<String, LearnableType>> picker, float weightPower) {
        List<String> DISALLOWED_TAGS = Arrays.asList(
                Tags.NO_DROP, Tags.NO_SELL, Tags.NO_BP_DROP, Tags.HULLMOD_NO_DROP_SALVAGE, Tags.HULLMOD_DMOD);
        List<String> REQUIRED_TAGS = Arrays.asList(Tags.HULLMOD_OFFENSIVE, Tags.HULLMOD_DEFENSIVE, Tags.HULLMOD_ENGINES);
        for (HullModSpecAPI spec : Global.getSettings().getAllHullModSpecs()) {
            if (spec.isHidden()
                    || spec.isHiddenEverywhere()
                    || spec.isAlwaysUnlocked()
                    || spec.getRarity() == 0f
                    || !Collections.disjoint(spec.getTags(), DISALLOWED_TAGS)
                    || Collections.disjoint(spec.getTags(), REQUIRED_TAGS)) continue;
            String id = spec.getId();
            if (!faction.getKnownHullMods().contains(id)) {
                float weight = 1f / Math.max(1, (spec.getTier() + 1)) * MathUtils.clampValue(spec.getRarity(), 0.000001f, 1f);
                picker.add(new Pair<>(id, LearnableType.HULLMOD), (float) Math.pow(weight, weightPower));
            }
        }
    }

    public static int genLearningAttempts(int learningAttemptsPerMonth, int monthsForBaseEffect) {
        int cyclesPassed = Global.getSector().getClock().getCycle() - 206;
        int monthsPassed = 12 * cyclesPassed + Global.getSector().getClock().getMonth() - 3;
        int numLearningAttempts = Math.max(0, learningAttemptsPerMonth * (monthsPassed - monthsForBaseEffect));
        log.debug(String.format("Current date: %s", Global.getSector().getClock().getDateString()));
        log.debug(String.format("Num Learning Attempts: %s", numLearningAttempts));
        return numLearningAttempts;
    }

    public static Pair<Integer, Float> getDynamicLearningParameters() {
        Set<String> sources = new HashSet<>();
        try {
            JSONArray shipData = Global.getSettings().getMergedSpreadsheetDataForMod("fs_rowSource", "data/hulls/ship_data.csv", "starsector-core");
            for (int i = 0; i < shipData.length(); i++) {
                JSONObject row = shipData.getJSONObject(i);
                String source = row.getString("fs_rowSource");
                String toAdd = source.substring(Math.max(0, source.indexOf("..")), source.indexOf("data"));
                sources.add(toAdd);
            }
            JSONArray weaponData = Global.getSettings().getMergedSpreadsheetDataForMod("fs_rowSource", "data/weapons/weapon_data.csv", "starsector-core");
            for (int i = 0; i < weaponData.length(); i++) {
                JSONObject row = weaponData.getJSONObject(i);
                String source = row.getString("fs_rowSource");
                String toAdd = source.substring(Math.max(0, source.indexOf("..")), source.indexOf("data"));
                sources.add(toAdd);
            }
            JSONArray wingData = Global.getSettings().getMergedSpreadsheetDataForMod("fs_rowSource", "data/hulls/wing_data.csv", "starsector-core");
            for (int i = 0; i < wingData.length(); i++) {
                JSONObject row = wingData.getJSONObject(i);
                String source = row.getString("fs_rowSource");
                String toAdd = source.substring(Math.max(0, source.indexOf("..")), source.indexOf("data"));
                sources.add(toAdd);
            }
            if (psLearnHullMods) {
                JSONArray hullModData = Global.getSettings().getMergedSpreadsheetDataForMod("fs_rowSource", "data/hullmods/hull_mods.csv", "starsector-core");
                for (int i = 0; i < hullModData.length(); i++) {
                    JSONObject row = hullModData.getJSONObject(i);
                    String source = row.getString("fs_rowSource");
                    String toAdd = source.substring(Math.max(0, source.indexOf("..")), source.indexOf("data"));
                    sources.add(toAdd);
                }
            }
        } catch (JSONException | IOException e) {
            log.error("Failed to parse csv data", e);
        }
        int n = sources.size() + 2;
        float C0 = 1f / (7 + n * n);
        float q = (float) Math.pow(C0, (float) 1 / (2 * n));
        float p = 1 - q;
        /*
        float E = n*p;
        float V = n*p*q;
        log.debug(String.format("%s %s %s %s %s %s",n,C0,p,q,E,V));
        /**/
        log.info(String.format("Learning Attempts Per Month: %s", n));
        log.info(String.format("Learning Chance: %s", p));
        return new Pair<>(n, p);
    }
}