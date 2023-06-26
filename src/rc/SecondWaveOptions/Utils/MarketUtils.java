package rc.SecondWaveOptions.Utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

public class MarketUtils {
    static Logger log = Global.getLogger(MarketUtils.class);

    public enum MarketScoreType {
        CONTROL,
        GROWTH
    }

    public static boolean isInCore(SectorEntityToken token) {
        Vector2f locationInHyperspace = token.getLocationInHyperspace();
        Vector2f coreMin = Misc.getCoreMin();
        Vector2f coreMax = Misc.getCoreMax();
        return (coreMin.x < locationInHyperspace.x && locationInHyperspace.x < coreMax.x
                && coreMin.y < locationInHyperspace.y && locationInHyperspace.y < coreMax.y);
    }

    public static FactionAPI getSystemOwner(StarSystemAPI starSystem, boolean excludePlayerFaction) {
        FactionAPI systemOwner = Misc.getClaimingFaction(starSystem.getStar());
        if (systemOwner == null) {
            float maxScore = 0;
            for (FactionAPI faction : Global.getSector().getAllFactions()) {
                if (excludePlayerFaction && faction.isPlayerFaction()) continue;
                float factionScore = 0;
                for (MarketAPI market : Misc.getMarketsInLocation(starSystem, faction.getId())) {
                    factionScore += getMarketScoreForControl(market, 3
                            , true, true, true, false, true, true);
                }
                log.debug(String.format("%s faction score: %s", faction.getId(), factionScore));
                if (factionScore > maxScore) {
                    maxScore = factionScore;
                    systemOwner = faction; // not dealing with ties if ur last, ur last;
                }
            }
        }
        return systemOwner;
    }

    public static List<MarketAPI> getSortedFactionMarkets(FactionAPI faction, final MarketScoreType marketScoreType, final float marketSizePower) {
        TreeSet<MarketAPI> sortedFactionMarkets = new TreeSet<>(new Comparator<MarketAPI>() {
            @Override
            public int compare(final MarketAPI market1, final MarketAPI market2) {
                if (market1.getSize() != market2.getSize()) return Integer.compare(market2.getSize(), market1.getSize());
                switch (marketScoreType) {
                    case CONTROL:
                        float controlScore1 = getMarketScoreForControl(market1, marketSizePower
                                , true, false, true, true, true, true);
                        float controlScore2 = getMarketScoreForControl(market2, marketSizePower
                                , true, false, true, true, true, true);
                        if (controlScore1 != controlScore2) return Float.compare(controlScore2, controlScore1);
                        break;
                    case GROWTH:
                        float growthScore1 = getMarketScoreForGrowth(market1, marketSizePower
                                , true, false, true, true, true, true);
                        float growthScore2 = getMarketScoreForGrowth(market2, marketSizePower
                                , true, false, true, true, true, true);
                        if (growthScore1 != growthScore2) return Float.compare(growthScore2, growthScore1);
                        break;
                }
                if (market1.getNetIncome() != market2.getNetIncome()) return Float.compare(market2.getNetIncome(), market1.getNetIncome());
                return market2.getName().compareToIgnoreCase(market1.getName());
            }
        });
        sortedFactionMarkets.addAll(Misc.getFactionMarkets(faction));
        /*
        StringBuilder debug = new StringBuilder();
        for (MarketAPI market : sortedFactionMarkets) {
            debug.append(market.getId()).append(", ");
        }
        log.debug(debug.toString());
        /**/
        return new ArrayList<>(sortedFactionMarkets);
    }

    // todo systemOwnerMult, contestedSystemMult
    public static float getMarketScoreForControl(MarketAPI market, float sizePower
            , boolean useMilitaryMult, boolean useStationMult, boolean useHeavyIndustryMult
            , boolean useStabilityMult, boolean useFactionMult, boolean useHiddenMult) {
        float marketScore = (float) Math.pow(market.getSize(), sizePower);
        float militaryMult = 1f;
        float stationMult = 1f;
        float industryMult = 1f;
        float stabilityMult = 1f;
        float factionMult = 1f;
        float hiddenMult = 1f;
        if (useStabilityMult) {
            stabilityMult = 1f + (market.getStabilityValue() - 8f) / 20f; // between 0.6 (0) and 1.1 (10)
        }
        if (useFactionMult) {
            //todo maybe check if they have hidden markets
            if (Misc.isPirateFaction(market.getFaction())) factionMult *= 0.8f;
            else if (Misc.isDecentralized(market.getFaction())) factionMult *= 0.6f;
        }
        if (useHiddenMult && market.isHidden()) {
            hiddenMult *= 0.6f;
        }
        for (Industry industry : market.getIndustries()) {
            if (useMilitaryMult) {
                militaryMult = Math.max(militaryMult
                        , getMilitaryMult(industry, 1.5f, 1.3f, 1.1f
                                , 1.1f, 0.75f, new float[]{1.3f, 1.2f, 1.1f}));
            }
            if (useStationMult) {
                stationMult = Math.max(stationMult
                        , getStationMult(industry, 1.3f, 1.2f, 1.1f
                                , 1.1f, 0.75f, new float[]{1.3f, 1.2f, 1.1f}));
            }
            if (useHeavyIndustryMult) {
                industryMult = Math.max(industryMult
                        , getHeavyIndustryMult(industry, 1.2f, 1.1f
                                , 1.1f, 0.75f, new float[]{1.3f, 1.1f}, new float[]{1.5f, 1.3f, 1.1f}));
            }
        }
        float totalMarketScore = marketScore * militaryMult * stationMult * industryMult * stabilityMult * factionMult * hiddenMult;
        /*
        log.debug(String.format("%s market score for control: %s * %s * %s * %s * %s * %s * %s = %s for faction %s",
                                market.getId(), marketScore, militaryMult, stationMult, industryMult, stabilityMult, factionMult, hiddenMult,
                                totalMarketScore, market.getFactionId()));
        /**/
        return totalMarketScore;
    }

    public static float getMarketScoreForGrowth(MarketAPI market, float marketSizePower
            , boolean useMilitaryMult, boolean useStationMult, boolean useHeavyIndustryMult
            , boolean useStabilityMult, boolean useFactionMult, boolean useHiddenMult) {
        float marketScore = (float) Math.pow(market.getSize(), marketSizePower);
        float militaryMult = 1f;
        float stationMult = 1f;
        float industryMult = 1f;
        float stabilityMult = 1f;
        float factionMult = 1f;
        float hiddenMult = 1f;
        if (useStabilityMult) {
            stabilityMult = 1f + (market.getStabilityValue() - 8f) / 20f; // between 0.6 (0) and 1.1 (10)
        }
        if (useFactionMult) {
            //todo maybe check if they have hidden markets
            if (Misc.isPirateFaction(market.getFaction())) factionMult *= 0.8f;
            else if (Misc.isDecentralized(market.getFaction())) factionMult *= 0.6f;
        }
        if (useHiddenMult && market.isHidden()) {
            hiddenMult *= 0.8f;
        }
        for (Industry industry : market.getIndustries()) {
            if (useMilitaryMult) {
                militaryMult = Math.max(militaryMult
                        , getMilitaryMult(industry, 1.25f, 1.125f, 1.0f
                                , 1.1f, 0.75f, new float[]{1.3f, 1.2f, 1.1f}));
            }
            if (useStationMult) {
                stationMult = Math.max(stationMult
                        , getStationMult(industry, 1.1f, 1.05f, 1.0f
                                , 1.1f, 0.75f, new float[]{1.3f, 1.2f, 1.1f}));
            }
            if (useHeavyIndustryMult) {
                industryMult = Math.max(industryMult
                        , getHeavyIndustryMult(industry, 1.5f, 1.25f
                                , 1.1f, 0.75f, new float[]{1.3f, 1.1f}, new float[]{1.5f, 1.3f, 1.1f}));
            }
        }
        float totalMarketScore = marketScore * militaryMult * stationMult * industryMult * stabilityMult * factionMult * hiddenMult;
        /*
        log.debug(String.format("%s market score for growth: %s * %s * %s * %s * %s * %s * %s = %s for faction %s",
                                market.getId(), marketScore, militaryMult, stationMult, industryMult, stabilityMult, factionMult, hiddenMult,
                                totalMarketScore, market.getFactionId()));
        */
        return totalMarketScore;
    }

    //todo getIndustryMultByTag(Industry industry, HashMap<String, Float>, float improvedMult)?
    public static float getMilitaryMult(Industry industry, float highCommandMult, float militaryBaseMult, float patrolHQMult
            , float improvedMult, float disruptedMult, float[] aiCoreMult) {
        float militaryMult = 1f;
        if (industry.getSpec().hasTag(Industries.TAG_COMMAND)) militaryMult = Math.max(militaryMult, highCommandMult);
        else if (industry.getSpec().hasTag(Industries.TAG_MILITARY)) militaryMult = Math.max(militaryMult, militaryBaseMult);
        else if (industry.getSpec().hasTag(Industries.TAG_PATROL)) militaryMult = Math.max(militaryMult, patrolHQMult);
        if (militaryMult > 1f && industry.isImproved()) militaryMult *= improvedMult;
        if (industry.isDisrupted()) militaryMult *= disruptedMult;
        if (industry.getAICoreId() != null) {
            switch (industry.getAICoreId()) {
                case Commodities.ALPHA_CORE:
                    militaryMult *= aiCoreMult[0];
                    break;
                case Commodities.BETA_CORE:
                    militaryMult *= aiCoreMult[1];
                    break;
                case Commodities.GAMMA_CORE:
                    militaryMult *= aiCoreMult[2];
                    break;
                default:
                    break;
            }
        }
        return militaryMult;
    }

    public static float getStationMult(Industry industry, float starFortressMult, float battlestationMult, float orbitalStationMult
            , float improvedMult, float disruptedMult, float[] aiCoreMult) {
        float stationMult = 1f;
        if (industry.getSpec().hasTag(Industries.TAG_STARFORTRESS)) stationMult = Math.max(stationMult, starFortressMult);
        else if (industry.getSpec().hasTag(Industries.TAG_BATTLESTATION)) stationMult = Math.max(stationMult, battlestationMult);
        else if (industry.getSpec().hasTag(Industries.TAG_STATION)) stationMult = Math.max(stationMult, orbitalStationMult);
        if (stationMult > 1f && industry.isImproved()) stationMult *= improvedMult;
        if (industry.isDisrupted()) stationMult *= disruptedMult;
        if (industry.getAICoreId() != null) {
            switch (industry.getAICoreId()) {
                case Commodities.ALPHA_CORE:
                    stationMult *= aiCoreMult[0];
                    break;
                case Commodities.BETA_CORE:
                    stationMult *= aiCoreMult[1];
                    break;
                case Commodities.GAMMA_CORE:
                    stationMult *= aiCoreMult[2];
                    break;
                default:
                    break;
            }
        }
        return stationMult;
    }

    // todo bit too hardcoded for my tastes
    public static float getHeavyIndustryMult(Industry industry, float orbitalWorksMult, float heavyIndustryMult
            , float improvedMult, float disruptedMult, float[] nanoForgeMult, float[] aiCoreMult) {
        float industryMult = 1f;
        if (industry.getSpec().getId().equals(Industries.ORBITALWORKS)) industryMult = Math.max(industryMult, orbitalWorksMult);
        else if (industry.getSpec().hasTag(Industries.TAG_HEAVYINDUSTRY)) industryMult = Math.max(industryMult, heavyIndustryMult);
        if (industryMult > 1f && industry.isImproved()) industryMult *= improvedMult;
        if (industry.isDisrupted()) industryMult *= disruptedMult;
        if (industry.getSpecialItem() != null) {
            switch (industry.getSpecialItem().getId()) {
                case Items.PRISTINE_NANOFORGE:
                    industryMult *= nanoForgeMult[0];
                    break;
                case Items.CORRUPTED_NANOFORGE:
                    industryMult *= nanoForgeMult[1];
                    break;
                default:
                    break;
            }
        }
        if (industry.getAICoreId() != null) {
            switch (industry.getAICoreId()) {
                case Commodities.ALPHA_CORE:
                    industryMult *= aiCoreMult[0];
                    break;
                case Commodities.BETA_CORE:
                    industryMult *= aiCoreMult[1];
                    break;
                case Commodities.GAMMA_CORE:
                    industryMult *= aiCoreMult[2];
                    break;
                default:
                    break;
            }
        }
        return industryMult;
    }
}