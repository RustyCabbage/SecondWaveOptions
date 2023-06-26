package rc.SecondWaveOptions.DoctrineScaling;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;
import rc.SecondWaveOptions.SecondWaveOptionsModPlugin;
import rc.SecondWaveOptions.Utils.MarketUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static rc.SecondWaveOptions.SecondWaveOptionsModPlugin.*;
import static rc.SecondWaveOptions.Utils.MarketUtils.getMarketScoreForControl;
import static rc.SecondWaveOptions.Utils.MarketUtils.getMarketScoreForGrowth;

/* DESIGN GOALS
- base, growth, researched
- total points = base + researched
- researched increases monthly

- base and growth should be able to be calculated at any time
- researched should be persistent

- base and growth affected by markets
- base and growth start with a flat amount to make things more even
- flat base should not be too high or losing markets won't mean anything later
- base and growth increase over time to provide game scaling
- base and growth increase over player level to provide game scaling
- base and growth increase over player colonization to provide game scaling

- losing markets should hurt base and growth; big markets should have more impact
- anti-sprawl penalty to limit snowball effects and keep factions competitive
- anti-sprawl should affect the marginal market, not every market (that way losing big markets is more painful)

- growth should scale more slowly than base

- expect results over a playtime of around 10 cycles
- doctrine improvements should take progressively more time
- improvements over the vanilla limits should cost _much_ more

QUESTIONS
- should fleet doctrine ever be weakened due to e.g. low stability?

 */


// todo add features for officerquality and shipquality going past vanilla limits
public class DoctrineScalingListener extends BaseCampaignEventListener {
    static Logger log = Global.getLogger(DoctrineScalingListener.class);
    public static String savedResearchKey = String.format("$%s_%s", SecondWaveOptionsModPlugin.MOD_PREFIX, "savedResearch"); //V is float
    public static String savedDoctrineKey = String.format("$%s_%s", SecondWaveOptionsModPlugin.MOD_PREFIX, "savedDoctrine"); //V is int[]

    public enum DoctrineStat {
        OFFICER_QUALITY,
        SHIP_QUALITY,
        NUM_SHIPS
    }

    public DoctrineScalingListener() {
        super(false);
    }

    @Override
    public void reportEconomyMonthEnd() {
        log.debug("-----------------");
        log.debug("Economy Month End");
        log.debug("-----------------");
        for (FactionAPI faction : Global.getSector().getAllFactions()) {
            if (isValidFaction(faction)) runAll(faction, true);
        }
    }

    public static boolean isValidFaction(FactionAPI faction) {
        return !(faction.isPlayerFaction());
    }

    public static void runAll(FactionAPI faction, boolean doResearch) {
        log.debug("----------------------------------------------------");
        log.debug(String.format("Running Doctrine Scaling for: %s", faction));
        log.debug("----------------------------------------------------");
        if (!faction.getMemoryWithoutUpdate().contains(savedDoctrineKey)) saveDoctrineStats(faction);
        // todo should i make all of these return a copy? idk seems like a lot of work
        try {
            FactionScoreData factionScoreData = getFactionScoreData(faction, 600, 5);
            restoreDoctrineStats(factionScoreData.faction);
            if (Misc.getFactionMarkets(faction).isEmpty()) {
                scaleFactionScoreNoMarket(factionScoreData, dsNoMarketBaseMultiplier, dsNoMarketGrowthMultiplier);
            } else {
                addFactionScoreFromMarkets(factionScoreData
                        , dsBaseMarketSizePower, dsBaseSprawlPenaltyPower, dsGrowthMarketSizePower, dsGrowthSprawlPenaltyPower); // lower penalty means more points
            }
            scaleFactionScoreByTime(factionScoreData, dsMonthForBaseEffect, dsBaseScalingPerMonth, dsGrowthScalingPerMonth);
            scaleFactionScoreByLevel(factionScoreData, dsLevelForBaseEffect, dsBaseScalingPerLevel, dsGrowthScalingPerLevel);
            scaleFactionScoreByPlayerColonies(factionScoreData, dsColonyForBaseEffect, dsBaseScalingPerColony, dsGrowthScalingPerColony);
            if (doResearch) addResearchToFactionData(factionScoreData);
            saveResearchFromFactionData(factionScoreData, false);
            allocateFactionScore(factionScoreData, null, dsBaseCostPerPoint, dsCostPerTotalPoints, dsCostPerAdditionalPoint, dsCostAboveVanilla);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static FactionScoreData getFactionScoreData(FactionAPI faction, float base, float growth) {
        if (faction == null) return null;
        FactionScoreData factionScoreData;
        if (faction.getMemoryWithoutUpdate().contains(savedResearchKey)) {
            factionScoreData = new FactionScoreData(faction, base, growth, faction.getMemoryWithoutUpdate().getFloat(savedResearchKey));
        } else {
            factionScoreData = new FactionScoreData(faction, base, growth, 0f);
        }
        //log.debug(String.format("Base faction score: %s", factionScoreData));
        return factionScoreData;
    }

    public static float getFactionScoreFromMarkets(FactionAPI faction, MarketUtils.MarketScoreType marketScoreType
            , float marketSizePower, float sprawlPenaltyPower) {
        List<MarketAPI> sortedFactionMarkets = MarketUtils.getSortedFactionMarkets(faction, marketScoreType, marketSizePower);
        float factionScore = 0;
        float marketScore = 0;
        for (int i = 0; i < sortedFactionMarkets.size(); i++) {
            switch (marketScoreType) {
                case CONTROL:
                    marketScore = getMarketScoreForControl(sortedFactionMarkets.get(i), marketSizePower
                            , true, true, true, true, true, true)
                            / (float) Math.pow(i + 1, sprawlPenaltyPower);

                    break;
                case GROWTH:
                    marketScore = getMarketScoreForGrowth(sortedFactionMarkets.get(i), marketSizePower
                            , true, false, true, true, true, true)
                            / (float) Math.pow(i + 1, sprawlPenaltyPower);
                    break;
            }
            //log.debug(String.format("Market score %s for %s: %s", marketScoreType, sortedFactionMarkets.get(i).getId(), marketScore));
            factionScore += marketScore;
        }
        return factionScore;
    }

    public static FactionScoreData addFactionScoreFromMarkets(FactionScoreData factionScoreData
            , float baseMarketSizePower, float baseSprawlPenaltyPower
            , float growthMarketSizePower, float growthSprawlPenaltyPower) {
        factionScoreData.base += getFactionScoreFromMarkets(factionScoreData.faction, MarketUtils.MarketScoreType.CONTROL
                , baseMarketSizePower, baseSprawlPenaltyPower);
        factionScoreData.growth += getFactionScoreFromMarkets(factionScoreData.faction, MarketUtils.MarketScoreType.GROWTH
                , growthMarketSizePower, growthSprawlPenaltyPower);
        log.debug(String.format("Faction score after markets: %s", factionScoreData));
        return factionScoreData;
    }

    public static FactionScoreData scaleFactionScoreNoMarket(FactionScoreData factionScoreData, float baseMultiplier, float growthMultiplier) {
        factionScoreData.base *= baseMultiplier;
        factionScoreData.growth *= growthMultiplier;
        return factionScoreData;
    }

    public static FactionScoreData scaleFactionScoreByTime(FactionScoreData factionScoreData, int monthsForBaseEffect
            , float baseScalingPerMonth, float growthScalingPerMonth) {
        int cyclesPassed = Global.getSector().getClock().getCycle() - 206;
        int monthsPassed = 12 * cyclesPassed + Global.getSector().getClock().getMonth() - 3;
        int effectiveMonthsPassed = monthsPassed - monthsForBaseEffect;
        factionScoreData.base *= 1 + (effectiveMonthsPassed * baseScalingPerMonth);
        factionScoreData.growth *= 1 + (effectiveMonthsPassed * growthScalingPerMonth);
        log.debug(String.format("Faction score after time: %s", factionScoreData));
        return factionScoreData;
    }

    public static FactionScoreData scaleFactionScoreByLevel(FactionScoreData factionScoreData, int levelForBaseEffect
            , float baseScalingPerLevel, float growthScalingPerLevel) {
        int playerLevel = Global.getSector().getPlayerStats().getLevel();
        int effectivePlayerLevel = playerLevel - levelForBaseEffect;
        factionScoreData.base *= 1 + (effectivePlayerLevel * baseScalingPerLevel);
        factionScoreData.growth *= 1 + (effectivePlayerLevel * growthScalingPerLevel);
        log.debug(String.format("Faction score after level: %s", factionScoreData));
        return factionScoreData;
    }

    public static FactionScoreData scaleFactionScoreByPlayerColonies(FactionScoreData factionScoreData, int coloniesForBaseEffect
            , float baseScalingPerColony, float growthScalingPerColony) {
        int numColonies = Misc.getPlayerMarkets(false).size();
        int effectiveNumColonies = numColonies - coloniesForBaseEffect;
        factionScoreData.base *= 1 + (effectiveNumColonies * baseScalingPerColony);
        factionScoreData.growth *= 1 + (effectiveNumColonies * growthScalingPerColony);
        log.debug(String.format("Faction score after colonies: %s", factionScoreData));
        return factionScoreData;
    }

    public static FactionScoreData addResearchToFactionData(FactionScoreData factionScoreData) {
        factionScoreData.researched += factionScoreData.growth;
        return factionScoreData;
    }

    public static void saveResearchFromFactionData(FactionScoreData factionScoreData, boolean writeToCommon) throws IOException {
        factionScoreData.faction.getMemoryWithoutUpdate().set(savedResearchKey, factionScoreData.researched);
        log.info(factionScoreData.toString());
        if (writeToCommon) {
            String fileName = String.format("%s/%s_%s_%s", SecondWaveOptionsModPlugin.MOD_PREFIX
                    , Global.getSector().getPlayerPerson().getNameString(), Global.getSector().getSeedString().hashCode(), "FactionScoreHistory");
            String data = "";
            int counter = 0;
            if (Global.getSettings().fileExistsInCommon(fileName)) {
                data = Global.getSettings().readTextFileFromCommon(fileName);
                while (data.length() > 1000000) {
                    counter++;
                    fileName = String.format("%s_%s", fileName, counter);
                    data = Global.getSettings().readTextFileFromCommon(fileName);
                }
            }
            String newLine = System.getProperty("line.separator");
            String newData = data
                    + String.format("%s: %s%s", Global.getSector().getClock().getDateString(), factionScoreData, newLine);
            Global.getSettings().writeTextFileToCommon(fileName, newData);
        }
    }

    public static float getTotalFactionScore(FactionScoreData factionScoreData) {
        return factionScoreData.base + factionScoreData.researched;
    }

    public static int[] getFactionDoctrineStats(FactionAPI faction) {
        int[] doctrineStats = new int[DoctrineStat.values().length];
        for (DoctrineStat doctrineStat : DoctrineStat.values()) {
            switch (doctrineStat) {
                case OFFICER_QUALITY:
                    doctrineStats[doctrineStat.ordinal()] = faction.getDoctrine().getOfficerQuality();
                    break;
                case SHIP_QUALITY:
                    doctrineStats[doctrineStat.ordinal()] = faction.getDoctrine().getShipQuality();
                    break;
                case NUM_SHIPS:
                    doctrineStats[doctrineStat.ordinal()] = faction.getDoctrine().getNumShips();
                    break;
            }
        }
        return doctrineStats;
    }

    public int[] getFactionDoctrineStats(FactionScoreData factionScoreData) {
        int[] doctrineStats = new int[DoctrineStat.values().length];
        for (DoctrineStat doctrineStat : DoctrineStat.values()) {
            switch (doctrineStat) {
                case OFFICER_QUALITY:
                    doctrineStats[doctrineStat.ordinal()] = factionScoreData.faction.getDoctrine().getOfficerQuality();
                    break;
                case SHIP_QUALITY:
                    doctrineStats[doctrineStat.ordinal()] = factionScoreData.faction.getDoctrine().getShipQuality();
                    break;
                case NUM_SHIPS:
                    doctrineStats[doctrineStat.ordinal()] = factionScoreData.faction.getDoctrine().getNumShips();
                    break;
            }
        }
        return doctrineStats;
    }

    public static void saveDoctrineStats(FactionAPI faction) {
        int[] doctrineStats = getFactionDoctrineStats(faction);
        faction.getMemoryWithoutUpdate().set(savedDoctrineKey, doctrineStats);
        log.debug(String.format("Saved doctrine stats %s", Arrays.toString(doctrineStats)));
    }

    public void saveDoctrineStats(FactionScoreData factionScoreData) {
        int[] doctrineStats = getFactionDoctrineStats(factionScoreData);
        factionScoreData.faction.getMemoryWithoutUpdate().set(savedDoctrineKey, doctrineStats);
        log.debug(String.format("Saved doctrine stats %s", Arrays.toString(doctrineStats)));
    }

    public void restoreDoctrineStats(FactionScoreData factionScoreData) {
        int[] doctrineStats = (int[]) factionScoreData.faction.getMemoryWithoutUpdate().get(savedDoctrineKey);
        if (doctrineStats == null) {
            log.debug(String.format("No saved doctrine stats found for %s", factionScoreData.faction));
            return;
        }
        for (DoctrineStat doctrineStat : DoctrineStat.values()) {
            switch (doctrineStat) {
                case OFFICER_QUALITY:
                    factionScoreData.faction.getDoctrine().setOfficerQuality(doctrineStats[doctrineStat.ordinal()]);
                    break;
                case SHIP_QUALITY:
                    factionScoreData.faction.getDoctrine().setShipQuality(doctrineStats[doctrineStat.ordinal()]);
                    break;
                case NUM_SHIPS:
                    factionScoreData.faction.getDoctrine().setNumShips(doctrineStats[doctrineStat.ordinal()]);
                    break;
            }
        }
        log.debug(String.format("Restored doctrine stats %s", Arrays.toString(doctrineStats)));
    }

    public static void restoreDoctrineStats(FactionAPI faction) {
        int[] doctrineStats = (int[]) faction.getMemoryWithoutUpdate().get(savedDoctrineKey);
        if (doctrineStats == null) {
            log.debug(String.format("No saved doctrine stats found for %s", faction));
            return;
        }
        for (DoctrineStat doctrineStat : DoctrineStat.values()) {
            switch (doctrineStat) {
                case OFFICER_QUALITY:
                    faction.getDoctrine().setOfficerQuality(doctrineStats[doctrineStat.ordinal()]);
                    break;
                case SHIP_QUALITY:
                    faction.getDoctrine().setShipQuality(doctrineStats[doctrineStat.ordinal()]);
                    break;
                case NUM_SHIPS:
                    faction.getDoctrine().setNumShips(doctrineStats[doctrineStat.ordinal()]);
                    break;
            }
        }
        log.debug(String.format("Restored doctrine stats %s", Arrays.toString(doctrineStats)));
    }

    public static float getDoctrinePointCost(FactionScoreData factionScoreData, DoctrineStat doctrineStat
            , float baseCost, float costPerTotalPoints, float costPerAdditionalPoint, float costAboveVanilla) {
        int nextStat = 0;
        switch (doctrineStat) {
            case OFFICER_QUALITY:
                nextStat = factionScoreData.faction.getDoctrine().getOfficerQuality() + 1;
                break;
            case SHIP_QUALITY:
                nextStat = factionScoreData.faction.getDoctrine().getShipQuality() + 1;
                break;
            case NUM_SHIPS:
                nextStat = factionScoreData.faction.getDoctrine().getNumShips() + 1;
                break;
        }
        float marginalCost = costPerAdditionalPoint * nextStat;
        float marginalCostAboveVanilla = costAboveVanilla * Math.max(0, nextStat - 5);
        return baseCost + factionScoreData.faction.getDoctrine().getTotalStrengthPoints() * costPerTotalPoints
                + marginalCost + marginalCostAboveVanilla;
    }

    public static int[] allocateFactionScore(FactionScoreData factionScoreData, Random random
            , float baseCost, float costPerTotalPoints, float costPerAdditionalPoint, float costAboveVanilla) {
        int[] allocated = new int[DoctrineStat.values().length];
        long seed = Math.abs(factionScoreData.faction.hashCode()
                                     + Global.getSector().getSeedString().hashCode()
                                     + Global.getSector().getPlayerPerson().getNameString().hashCode());
        if (random == null) {
            random = new Random(seed);
        }
        float allocatablePoints = getTotalFactionScore(factionScoreData);
        while (allocatablePoints > 0) {
            WeightedRandomPicker<Pair<DoctrineStat, Float>> picker = new WeightedRandomPicker<>(random);
            log.debug(String.format("---Allocatable points: %s", allocatablePoints));
            for (DoctrineStat doctrineStat : DoctrineStat.values()) {
                float cost = getDoctrinePointCost(factionScoreData, doctrineStat, baseCost, costPerTotalPoints, costPerAdditionalPoint, costAboveVanilla);
                log.debug(String.format("Cost for %s: %s", doctrineStat, cost));
                Pair<DoctrineStat, Float> item = new Pair<>(doctrineStat, cost);
                if (allocatablePoints < cost) continue;
                int stat = 1;
                switch (doctrineStat) {
                    case OFFICER_QUALITY:
                        stat = factionScoreData.faction.getDoctrine().getOfficerQuality();
                        break;
                    case SHIP_QUALITY:
                        stat = factionScoreData.faction.getDoctrine().getShipQuality();
                        break;
                    case NUM_SHIPS:
                        stat = factionScoreData.faction.getDoctrine().getNumShips();
                        break;
                }
                float weight = 1f / (float) Math.sqrt(stat);
                picker.add(item, weight);
                log.debug(String.format("Added <%s,%s> to picker with weight %s", item.one, item.two, weight));
            }
            if (picker.isEmpty()) break;
            Pair<DoctrineStat, Float> pick = picker.pick();
            switch (pick.one) {
                case OFFICER_QUALITY:
                    factionScoreData.faction.getDoctrine().setOfficerQuality(factionScoreData.faction.getDoctrine().getOfficerQuality() + 1);
                    break;
                case SHIP_QUALITY:
                    factionScoreData.faction.getDoctrine().setShipQuality(factionScoreData.faction.getDoctrine().getShipQuality() + 1);
                    break;
                case NUM_SHIPS:
                    factionScoreData.faction.getDoctrine().setNumShips(factionScoreData.faction.getDoctrine().getNumShips() + 1);
                    break;
            }
            allocated[pick.one.ordinal()]++;
            allocatablePoints -= pick.two;
            log.debug(String.format("[Picked %s for %s points with probability %s]", pick.one, pick.two, picker.getWeight(pick) / picker.getTotal()));
        }
        log.debug(String.format("Allocation: %s", Arrays.toString(allocated)));
        log.debug(String.format("New doctrine: [%s, %s, %s]"
                , factionScoreData.faction.getDoctrine().getOfficerQuality()
                , factionScoreData.faction.getDoctrine().getShipQuality()
                , factionScoreData.faction.getDoctrine().getNumShips()));
        return allocated;
    }

    public static class FactionScoreData {
        private final FactionAPI faction;
        private float base;
        private float growth;
        private float researched;

        public FactionScoreData(FactionAPI faction) {
            this.faction = faction;
            this.base = 0f;
            this.growth = 0f;
            this.researched = 0f;
        }

        public FactionScoreData(FactionAPI faction, float base, float growth, float researched) {
            this.faction = faction;
            this.base = base;
            this.growth = growth;
            this.researched = researched;
        }

        @Override
        public String toString() {
            return "FactionScoreData{" +
                    "faction=" + faction +
                    ", base=" + base +
                    ", growth=" + growth +
                    ", researched=" + researched +
                    '}';
        }
    }
}