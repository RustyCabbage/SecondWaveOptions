package rc.SecondWaveOptions;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import lunalib.lunaSettings.LunaSettings;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import rc.SecondWaveOptions.CompetentFoes.AutofitTweaks;
import rc.SecondWaveOptions.CompetentFoes.BookOfGrudgesListener;
import rc.SecondWaveOptions.CompetentFoes.PirateScholarsListener;
import rc.SecondWaveOptions.CompetentFoes.SuppressBadThingsListener;
import rc.SecondWaveOptions.DoctrineScaling.DoctrineScalingFleetBuffsOnInflation;
import rc.SecondWaveOptions.DoctrineScaling.DoctrineScalingListener;
import rc.SecondWaveOptions.MarketCrackdowns.CommodityScalingListener;
import rc.SecondWaveOptions.MarketCrackdowns.NoFreeStorage;
import rc.SecondWaveOptions.MarketCrackdowns.ProtectionismListener;
import rc.SecondWaveOptions.MarketCrackdowns.SecureMarkets;

import static rc.SecondWaveOptions.CompetentFoes.SuppressBadThingsListener.blessedByLuddKey;
import static rc.SecondWaveOptions.CompetentFoes.SuppressBadThingsListener.escapedNoticeKey;

//todo other plans: ai tweaks
public class SecondWaveOptionsModPlugin extends BaseModPlugin {
    public static String MOD_ID = "RustyCabbage_SecondWaveOptions";
    public static String MOD_PREFIX = "secondwaveoptions";
    Logger log = Global.getLogger(SecondWaveOptionsModPlugin.class);
    public static boolean
            enableAutofitTweaks, enableBookOfGrudges, enableBlessedByLudd, enableMakeSindriaGreatAgain, enablePirateScholars,
            enableDoctrineScaling,
            enableCommodityScaling, enableNoFreeStorage, enableProtectionism, enableSecureMarkets;
    public static String bogGrudgeMode, psLearningMode;
    public static boolean bogIncludePersons, psLearnHullMods;
    public static float
            atAutofitRandomizeProbability, bogGrudgeFraction,
            bblChance, msgaChance, mlggaChance,
            psChance, psWeightPower;
    public static int bogDuration, psLearningAttemptsPerMonth, psMonthsForBaseEffect;

    public static float
            dsFlatBasePoints, dsFlatGrowthPoints, dsNoMarketBaseMultiplier, dsNoMarketGrowthMultiplier, dsEliteFactionBonus,
            dsBaseScalingPerMonth, dsGrowthScalingPerMonth,
            dsBaseScalingPerLevel, dsGrowthScalingPerLevel,
            dsBaseScalingPerColony, dsGrowthScalingPerColony, dsColonyMultiplierForPirates,
            dsBaseCostPerPoint, dsCostPerTotalPoints, dsCostPerAdditionalPoint, dsCostAboveVanilla,
            dsFleetBuffsOfficerProb, dsFleetBuffsChainOfficerProb,
            dsFleetBuffsLevelProb, dsFleetBuffsChainLevelProb,
            dsFleetBuffsEliteProb, dsFleetBuffsChainEliteProb,
            dsFleetBuffsCommanderProb, dsFleetBuffsChainCommanderProb,
            dsFleetBuffsSModProb, dsFleetBuffsChainSModProb,
            dsBaseMarketSizePower, dsBaseSprawlPenaltyPower, dsGrowthMarketSizePower, dsGrowthSprawlPenaltyPower;
    public static boolean dsUseFactionBlacklist;
    public static int dsMonthForBaseEffect, dsLevelForBaseEffect, dsColonyForBaseEffect;

    public static float
            openCommodityScale, blackCommodityScale, militaryCommodityScale, lrCommodityScale, otherCommodityScale, //commodityVariationScale
            protectionismMaxTariff, protectionismTariffImpact, protectionismNumColoniesPower,
            protectionismCommissionFactionMult, protectionismPirateFactionMult, protectionismDecentralizedFactionMult;
    public static boolean secureMarketsAddPatrols, secureMarketsAddStations, secureMarketsAddDefenses, secureMarketsAddHeavyInd;
    public static int
            secureMarketsHighCommandSize, secureMarketsMilitaryBaseSize, secureMarketsPatrolHQSize,
            secureMarketsStarFortressSize, secureMarketsBattlestationSize, secureMarketsOrbitalStationSize,
            secureMarketsHeavyBatteriesSize, secureMarketsGroundDefensesSize,
            secureMarketsOrbitalWorksSize, secureMarketsHeavyIndustrySize;


    @Override
    public void onApplicationLoad() {
        log.info("-----------------------------");
        log.info("Initializing LunaLib settings");
        log.info("-----------------------------");
        loadLunaLibSettings();
        // Test that the .jar is loaded and working, using the most obnoxious way possible.
        // throw new RuntimeException("Template mod loaded! Remove this crash in TemplateModPlugin.");

    }

    @Override
    public void onNewGame() {
        // The code below requires that Nexerelin is added as a library (not a dependency, it's only needed to compile the mod).
//        boolean isNexerelinEnabled = Global.getSettings().getModManager().isModEnabled("nexerelin");

//        if (!isNexerelinEnabled || SectorManager.getManager().isCorvusMode()) {
//                    new MySectorGen().generate(Global.getSector());
        // Add code that creates a new star system (will only run if Nexerelin's Random (corvus) mode is disabled).
//        }
    }

    @Override
    public void onNewGameAfterEconomyLoad() {
        if (enableSecureMarkets) {
            log.info("-----------------------------------------");
            SecureMarkets secureMarkets = new SecureMarkets();
            log.info("Applying " + secureMarkets.getClass().getName());
            log.info("-----------------------------------------");
            secureMarkets.upgradeAllMarkets();
        }
        if (enableDoctrineScaling) {
            for (FactionAPI faction : Global.getSector().getAllFactions()) {
                if (DoctrineScalingListener.isValidFaction(faction)) DoctrineScalingListener.runAll(faction, false);
            }
        }
    }

    @Override
    public void onGameLoad(boolean newGame) {
        Logger.getRootLogger().setLevel(Level.INFO);
        log.info("--------------------------------------------------");
        log.info("Reloading LunaLib settings");
        loadLunaLibSettings();

        if (enableAutofitTweaks) {
            for (FactionAPI faction : Global.getSector().getAllFactions()) {
                AutofitTweaks.saveAutofitRandomizeProbability(faction);
                AutofitTweaks.updateAutofitRandomizeProbability(faction, atAutofitRandomizeProbability);
            }
        }
        if (enableBookOfGrudges) {
            BookOfGrudgesListener bookOfGrudgesListener = new BookOfGrudgesListener();
            log.info("Adding " + bookOfGrudgesListener.getClass().getName());
            Global.getSector().addTransientListener(bookOfGrudgesListener);
        }
        if (enableBlessedByLudd || enableMakeSindriaGreatAgain) {
            SuppressBadThingsListener suppressBadThingsListener = new SuppressBadThingsListener();
            log.info("Adding " + suppressBadThingsListener.getClass().getName());
            Global.getSector().addTransientListener(suppressBadThingsListener);
        }
        if (enablePirateScholars) {
            PirateScholarsListener pirateScholarsListener = new PirateScholarsListener();
            log.info("Adding " + pirateScholarsListener.getClass().getName());
            Global.getSector().addTransientListener(pirateScholarsListener);
            PirateScholarsListener.runAll(Global.getSector().getFaction(Factions.PIRATES));
        }
        if (enableDoctrineScaling) {
            DoctrineScalingListener doctrineScalingListener = new DoctrineScalingListener();
            log.info("Adding " + doctrineScalingListener.getClass().getName());
            Global.getSector().addTransientListener(doctrineScalingListener);
            for (FactionAPI faction : Global.getSector().getAllFactions()) {
                if (DoctrineScalingListener.isValidFaction(faction)) DoctrineScalingListener.runAll(faction, false);
            }

            DoctrineScalingFleetBuffsOnInflation doctrineScalingFleetBuffsV2 = new DoctrineScalingFleetBuffsOnInflation();
            log.info("Adding " + doctrineScalingFleetBuffsV2.getClass().getName());
            Global.getSector().getListenerManager().addListener(doctrineScalingFleetBuffsV2, true);
        }
        if (enableCommodityScaling) {
            CommodityScalingListener commodityScalingListener = new CommodityScalingListener();
            log.info("Adding " + commodityScalingListener.getClass().getName());
            Global.getSector().addTransientListener(commodityScalingListener);
        }
        if (enableNoFreeStorage) {
            NoFreeStorage.disableFreeStorage();
        }
        if (enableProtectionism) {
            ProtectionismListener protectionismListener = new ProtectionismListener();
            log.info("Adding " + protectionismListener.getClass().getName());
            Global.getSector().addTransientListener(protectionismListener);
        }
        /* it won't work aaaaaaaaaaaaaa
        if (false) {
            ReducedRecoveryListener reducedRecoveryListener = new ReducedRecoveryListener();
            log.info("Adding " + reducedRecoveryListener.getClass().getName());
            Global.getSector().addTransientListener(reducedRecoveryListener);
        }
        /**/

        log.info("--------------------------------------------------");
    }

    //todo restore suppressed mods
    @Override
    public void beforeGameSave() {
        // pirate scholars
        PirateScholarsListener.forget(Global.getSector().getFaction(Factions.PIRATES));
        // suppress bad things
        for (LocationAPI loc : Global.getSector().getAllLocations()) {
            for (CampaignFleetAPI f : loc.getFleets()) {
                SuppressBadThingsListener.unsuppressBadThings(f, Factions.LUDDIC_PATH,
                                                              HullMods.ILL_ADVISED, blessedByLuddKey, true, false);
                SuppressBadThingsListener.unsuppressBadThings(f, Factions.DIKTAT,
                                                              HullMods.ANDRADA_MODS, escapedNoticeKey, true, false);
                SuppressBadThingsListener.unsuppressBadThings(f, Factions.LIONS_GUARD,
                                                              HullMods.ANDRADA_MODS, escapedNoticeKey, true, false);
            }
        }
        // doctrine scaling
        for (FactionAPI faction : Global.getSector().getAllFactions()) {
            DoctrineScalingListener.restoreDoctrineStats(faction);
            AutofitTweaks.restoreAutofitRandomizeProbability(faction);
        }
        // no free storage
        NoFreeStorage.restoreFreeStorage();
    }

    @Override
    public void afterGameSave() {
        if (enableAutofitTweaks) {
            for (FactionAPI faction : Global.getSector().getAllFactions()) {
                AutofitTweaks.saveAutofitRandomizeProbability(faction);
                AutofitTweaks.updateAutofitRandomizeProbability(faction, atAutofitRandomizeProbability);
            }
        }
        if (enablePirateScholars) {
            PirateScholarsListener.runAll(Global.getSector().getFaction(Factions.PIRATES));
        }
        if (enableBlessedByLudd || enableMakeSindriaGreatAgain) {
            for (LocationAPI loc : Global.getSector().getAllLocations()) {
                for (CampaignFleetAPI f : loc.getFleets()) {
                    if (enableBlessedByLudd && SuppressBadThingsListener.hasMemKey(f, blessedByLuddKey)) {
                        SuppressBadThingsListener.suppressBadThings(f, Factions.LUDDIC_PATH,
                                                                    HullMods.ILL_ADVISED, 1, blessedByLuddKey, false, false);
                    }
                    if (enableMakeSindriaGreatAgain && SuppressBadThingsListener.hasMemKey(f, escapedNoticeKey)) {
                        SuppressBadThingsListener.suppressBadThings(f, Factions.DIKTAT,
                                                                    HullMods.ANDRADA_MODS, 1, escapedNoticeKey, false, false);
                        SuppressBadThingsListener.suppressBadThings(f, Factions.LIONS_GUARD,
                                                                    HullMods.ANDRADA_MODS, 1, escapedNoticeKey, false, false);
                    }
                }
            }
        }
        if (enableDoctrineScaling) {
            for (FactionAPI faction : Global.getSector().getAllFactions()) {
                if (DoctrineScalingListener.isValidFaction(faction)) DoctrineScalingListener.runAll(faction, false);
            }
        }
        if (enableNoFreeStorage) {
            NoFreeStorage.disableFreeStorage();
        }
    }

    public void loadLunaLibSettings() {
        // // Book of Grudges
        enableBookOfGrudges = LunaSettings.getBoolean(MOD_ID, String.format("%s_bookOfGrudgesEnable", MOD_PREFIX));
        bogGrudgeMode = LunaSettings.getString(MOD_ID, String.format("%s_bogGrudgeMode", MOD_PREFIX));
        bogIncludePersons = LunaSettings.getBoolean(MOD_ID, String.format("%s_bogIncludePersons", MOD_PREFIX));
        bogGrudgeFraction = LunaSettings.getFloat(MOD_ID, String.format("%s_bogGrudgeFraction", MOD_PREFIX));
        bogDuration = LunaSettings.getInt(MOD_ID, String.format("%s_bogDuration", MOD_PREFIX));
        // // Competent Foes
        //autofit tweaks
        enableAutofitTweaks = LunaSettings.getBoolean(MOD_ID, String.format("%s_autofitTweaksEnable", MOD_PREFIX));
        atAutofitRandomizeProbability = LunaSettings.getFloat(MOD_ID, String.format("%s_atAutofitRandomizeProbability", MOD_PREFIX));
        //blessed by ludd
        enableBlessedByLudd = LunaSettings.getBoolean(MOD_ID, String.format("%s_blessedByLuddEnable", MOD_PREFIX));
        bblChance = LunaSettings.getFloat(MOD_ID, String.format("%s_bblChance", MOD_PREFIX));
        //make sindria great again
        enableMakeSindriaGreatAgain = LunaSettings.getBoolean(MOD_ID, String.format("%s_makeSindriaGreatAgainEnable", MOD_PREFIX));
        msgaChance = LunaSettings.getFloat(MOD_ID, String.format("%s_msgaChance", MOD_PREFIX));
        mlggaChance = LunaSettings.getFloat(MOD_ID, String.format("%s_mlggaChance", MOD_PREFIX));
        //pirate scholars
        enablePirateScholars = LunaSettings.getBoolean(MOD_ID, String.format("%s_pirateScholarsEnable", MOD_PREFIX));
        psLearningMode = LunaSettings.getString(MOD_ID, String.format("%s_psLearningMode", MOD_PREFIX));
        psChance = LunaSettings.getFloat(MOD_ID, String.format("%s_psChance", MOD_PREFIX));
        psLearningAttemptsPerMonth = LunaSettings.getInt(MOD_ID, String.format("%s_psLearningAttemptsPerMonth", MOD_PREFIX));
        psMonthsForBaseEffect = LunaSettings.getInt(MOD_ID, String.format("%s_psMonthsForBaseEffect", MOD_PREFIX));
        psWeightPower = LunaSettings.getFloat(MOD_ID, String.format("%s_psWeightPower", MOD_PREFIX));
        psLearnHullMods = LunaSettings.getBoolean(MOD_ID, String.format("%s_psLearnHullMods", MOD_PREFIX));
        // // Doctrine Scaling
        enableDoctrineScaling = LunaSettings.getBoolean(MOD_ID, String.format("%s_doctrineScalingEnable", MOD_PREFIX));
        dsFlatBasePoints = LunaSettings.getFloat(MOD_ID, String.format("%s_dsFlatBasePoints", MOD_PREFIX));
        dsFlatGrowthPoints = LunaSettings.getFloat(MOD_ID, String.format("%s_dsFlatGrowthPoints", MOD_PREFIX));
        dsNoMarketBaseMultiplier = LunaSettings.getFloat(MOD_ID, String.format("%s_dsNoMarketBaseMultiplier", MOD_PREFIX));
        dsNoMarketGrowthMultiplier = LunaSettings.getFloat(MOD_ID, String.format("%s_dsNoMarketGrowthMultiplier", MOD_PREFIX));
        dsEliteFactionBonus = LunaSettings.getFloat(MOD_ID, String.format("%s_dsEliteFactionBonus", MOD_PREFIX));
        dsUseFactionBlacklist = LunaSettings.getBoolean(MOD_ID, String.format("%s_dsUseFactionBlacklist", MOD_PREFIX));
        dsMonthForBaseEffect = LunaSettings.getInt(MOD_ID, String.format("%s_dsMonthForBaseEffect", MOD_PREFIX));
        dsBaseScalingPerMonth = LunaSettings.getFloat(MOD_ID, String.format("%s_dsBaseScalingPerMonth", MOD_PREFIX));
        dsGrowthScalingPerMonth = LunaSettings.getFloat(MOD_ID, String.format("%s_dsGrowthScalingPerMonth", MOD_PREFIX));
        dsLevelForBaseEffect = LunaSettings.getInt(MOD_ID, String.format("%s_dsLevelForBaseEffect", MOD_PREFIX));
        dsBaseScalingPerLevel = LunaSettings.getFloat(MOD_ID, String.format("%s_dsBaseScalingPerLevel", MOD_PREFIX));
        dsGrowthScalingPerLevel = LunaSettings.getFloat(MOD_ID, String.format("%s_dsGrowthScalingPerLevel", MOD_PREFIX));
        dsColonyForBaseEffect = LunaSettings.getInt(MOD_ID, String.format("%s_dsColonyForBaseEffect", MOD_PREFIX));
        dsBaseScalingPerColony = LunaSettings.getFloat(MOD_ID, String.format("%s_dsBaseScalingPerColony", MOD_PREFIX));
        dsGrowthScalingPerColony = LunaSettings.getFloat(MOD_ID, String.format("%s_dsGrowthScalingPerColony", MOD_PREFIX));
        dsColonyMultiplierForPirates = LunaSettings.getFloat(MOD_ID, String.format("%s_dsColonyMultiplierForPirates", MOD_PREFIX));
        dsBaseCostPerPoint = LunaSettings.getFloat(MOD_ID, String.format("%s_dsBaseCostPerPoint", MOD_PREFIX));
        dsCostPerTotalPoints = LunaSettings.getFloat(MOD_ID, String.format("%s_dsCostPerTotalPoints", MOD_PREFIX));
        dsCostPerAdditionalPoint = LunaSettings.getFloat(MOD_ID, String.format("%s_dsCostPerAdditionalPoint", MOD_PREFIX));
        dsCostAboveVanilla = LunaSettings.getFloat(MOD_ID, String.format("%s_dsCostAboveVanilla", MOD_PREFIX));
        dsFleetBuffsOfficerProb = LunaSettings.getFloat(MOD_ID, String.format("%s_dsFleetBuffsOfficerProb", MOD_PREFIX));
        dsFleetBuffsChainOfficerProb = LunaSettings.getFloat(MOD_ID, String.format("%s_dsFleetBuffsChainOfficerProb", MOD_PREFIX));
        dsFleetBuffsLevelProb = LunaSettings.getFloat(MOD_ID, String.format("%s_dsFleetBuffsLevelProb", MOD_PREFIX));
        dsFleetBuffsChainLevelProb = LunaSettings.getFloat(MOD_ID, String.format("%s_dsFleetBuffsChainLevelProb", MOD_PREFIX));
        dsFleetBuffsEliteProb = LunaSettings.getFloat(MOD_ID, String.format("%s_dsFleetBuffsEliteProb", MOD_PREFIX));
        dsFleetBuffsChainEliteProb = LunaSettings.getFloat(MOD_ID, String.format("%s_dsFleetBuffsChainEliteProb", MOD_PREFIX));
        dsFleetBuffsCommanderProb = LunaSettings.getFloat(MOD_ID, String.format("%s_dsFleetBuffsCommanderProb", MOD_PREFIX));
        dsFleetBuffsChainCommanderProb = LunaSettings.getFloat(MOD_ID, String.format("%s_dsFleetBuffsChainCommanderProb", MOD_PREFIX));
        dsFleetBuffsSModProb = LunaSettings.getFloat(MOD_ID, String.format("%s_dsFleetBuffsSModProb", MOD_PREFIX));
        dsFleetBuffsChainSModProb = LunaSettings.getFloat(MOD_ID, String.format("%s_dsFleetBuffsChainSModProb", MOD_PREFIX));
        dsBaseMarketSizePower = LunaSettings.getFloat(MOD_ID, String.format("%s_dsBaseMarketSizePower", MOD_PREFIX));
        dsBaseSprawlPenaltyPower = LunaSettings.getFloat(MOD_ID, String.format("%s_dsBaseSprawlPenaltyPower", MOD_PREFIX));
        dsGrowthMarketSizePower = LunaSettings.getFloat(MOD_ID, String.format("%s_dsGrowthMarketSizePower", MOD_PREFIX));
        dsGrowthSprawlPenaltyPower = LunaSettings.getFloat(MOD_ID, String.format("%s_dsGrowthSprawlPenaltyPower", MOD_PREFIX));

        // // Market Crackdowns
        //commodity scaling
        enableCommodityScaling = LunaSettings.getBoolean(MOD_ID, String.format("%s_commodityScalingEnable", MOD_PREFIX));
        openCommodityScale = LunaSettings.getFloat(MOD_ID, String.format("%s_openCommodityScale", MOD_PREFIX));
        blackCommodityScale = LunaSettings.getFloat(MOD_ID, String.format("%s_blackCommodityScale", MOD_PREFIX));
        militaryCommodityScale = LunaSettings.getFloat(MOD_ID, String.format("%s_militaryCommodityScale", MOD_PREFIX));
        lrCommodityScale = LunaSettings.getFloat(MOD_ID, String.format("%s_lrCommodityScale", MOD_PREFIX));
        otherCommodityScale = LunaSettings.getFloat(MOD_ID, String.format("%s_otherCommodityScale", MOD_PREFIX));
        //commodityVariationScale = LunaSettings.getFloat(MOD_ID, String.format("%s_commodityVariationScale", MOD_PREFIX));
        //no free storage
        enableNoFreeStorage = LunaSettings.getBoolean(MOD_ID, String.format("%s_noFreeStorageEnable", MOD_PREFIX));
        //protectionism
        enableProtectionism = LunaSettings.getBoolean(MOD_ID, String.format("%s_protectionismEnable", MOD_PREFIX));
        protectionismMaxTariff = LunaSettings.getFloat(MOD_ID, String.format("%s_protectionismMaxTariff", MOD_PREFIX));
        protectionismTariffImpact = LunaSettings.getFloat(MOD_ID, String.format("%s_protectionismTariffImpact", MOD_PREFIX));
        protectionismNumColoniesPower = LunaSettings.getFloat(MOD_ID, String.format("%s_protectionismNumColoniesPower", MOD_PREFIX));
        protectionismCommissionFactionMult = LunaSettings.getFloat(MOD_ID, String.format("%s_protectionismCommissionFactionMult", MOD_PREFIX));
        protectionismPirateFactionMult = LunaSettings.getFloat(MOD_ID, String.format("%s_protectionismPirateFactionMult", MOD_PREFIX));
        protectionismDecentralizedFactionMult = LunaSettings.getFloat(MOD_ID, String.format("%s_protectionismDecentralizedFactionMult", MOD_PREFIX));
        //secure markets
        enableSecureMarkets = LunaSettings.getBoolean(MOD_ID, String.format("%s_secureMarketsEnable", MOD_PREFIX));
        secureMarketsAddPatrols = LunaSettings.getBoolean(MOD_ID, String.format("%s_secureMarketsAddPatrols", MOD_PREFIX));
        secureMarketsAddStations = LunaSettings.getBoolean(MOD_ID, String.format("%s_secureMarketsAddStations", MOD_PREFIX));
        secureMarketsAddDefenses = LunaSettings.getBoolean(MOD_ID, String.format("%s_secureMarketsAddDefenses", MOD_PREFIX));
        secureMarketsAddHeavyInd = LunaSettings.getBoolean(MOD_ID, String.format("%s_secureMarketsAddHeavyInd", MOD_PREFIX));
        secureMarketsHighCommandSize = LunaSettings.getInt(MOD_ID, String.format("%s_smHighCommandSize", MOD_PREFIX));
        secureMarketsMilitaryBaseSize = LunaSettings.getInt(MOD_ID, String.format("%s_smMilitaryBaseSize", MOD_PREFIX));
        secureMarketsPatrolHQSize = LunaSettings.getInt(MOD_ID, String.format("%s_smPatrolHQSize", MOD_PREFIX));
        secureMarketsStarFortressSize = LunaSettings.getInt(MOD_ID, String.format("%s_smStarFortressSize", MOD_PREFIX));
        secureMarketsBattlestationSize = LunaSettings.getInt(MOD_ID, String.format("%s_smBattlestationSize", MOD_PREFIX));
        secureMarketsOrbitalStationSize = LunaSettings.getInt(MOD_ID, String.format("%s_smOrbitalStationSize", MOD_PREFIX));
        secureMarketsHeavyBatteriesSize = LunaSettings.getInt(MOD_ID, String.format("%s_smHeavyBatteriesSize", MOD_PREFIX));
        secureMarketsGroundDefensesSize = LunaSettings.getInt(MOD_ID, String.format("%s_smGroundDefensesSize", MOD_PREFIX));
        secureMarketsOrbitalWorksSize = LunaSettings.getInt(MOD_ID, String.format("%s_smOrbitalWorksSize", MOD_PREFIX));
        secureMarketsHeavyIndustrySize = LunaSettings.getInt(MOD_ID, String.format("%s_smHeavyIndustrySize", MOD_PREFIX));
    }
}