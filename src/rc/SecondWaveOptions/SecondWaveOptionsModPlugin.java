package rc.SecondWaveOptions;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import lunalib.lunaSettings.LunaSettings;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import rc.SecondWaveOptions.CompetentFoes.AutofitTweaks;
import rc.SecondWaveOptions.CompetentFoes.PirateScholarsListener;
import rc.SecondWaveOptions.CompetentFoes.SuppressBadThingsListener;
import rc.SecondWaveOptions.DoctrineScaling.DoctrineScalingListener;
import rc.SecondWaveOptions.MarketCrackdowns.CommodityScalingListener;
import rc.SecondWaveOptions.MarketCrackdowns.SecureMarkets;

import static rc.SecondWaveOptions.Utils.MarketUtils.isInCore;

//todo other plans: ai tweaks, combat ghost ship??,
public class SecondWaveOptionsModPlugin extends BaseModPlugin {
    public static String MOD_ID = "RustyCabbage_SecondWaveOptions";
    public static String MOD_PREFIX = "secondwaveoptions";
    Logger log = Global.getLogger(SecondWaveOptionsModPlugin.class);
    public static boolean
            enableAutofitTweaks, enableBlessedByLudd, enableMakeSindriaGreatAgain, enablePirateScholars,
            enableDoctrineScaling,
            enableCommodityScaling, enableNoFreeStorage, enableSecureMarkets;
    public static float
            atAutofitRandomizeProbability,
            bblChance, msgaChance, mlggaChance,
            psChance;
    public static int
            psLearningAttemptsPerMonth, psMonthsForBaseEffect;
    public static float
            openCommodityScale, blackCommodityScale, otherCommodityScale, commodityVariationScale;
    public static boolean
            secureMarketsAddPatrols, secureMarketsAddStations, secureMarketsAddDefenses, secureMarketsAddHeavyInd;
    public static int
            secureMarketsHighCommandSize, secureMarketsMilitaryBaseSize, secureMarketsPatrolHQSize,
            secureMarketsStarFortressSize, secureMarketsBattlestationSize, secureMarketsOrbitalStationSize,
            secureMarketsHeavyBatteriesSize, secureMarketsGroundDefensesSize,
            secureMarketsOrbitalWorksSize, secureMarketsHeavyIndustrySize;
    public static float
            dsFlatBasePoints, dsFlatGrowthPoints, dsNoMarketBaseMultiplier, dsNoMarketGrowthMultiplier,
            dsBaseScalingPerMonth, dsGrowthScalingPerMonth,
            dsBaseScalingPerLevel, dsGrowthScalingPerLevel,
            dsBaseScalingPerColony, dsGrowthScalingPerColony,
            dsBaseCostPerPoint, dsCostPerTotalPoints, dsCostPerAdditionalPoint, dsCostAboveVanilla,
            dsBaseMarketSizePower, dsBaseSprawlPenaltyPower, dsGrowthMarketSizePower, dsGrowthSprawlPenaltyPower;
    public static int
            dsMonthForBaseEffect, dsLevelForBaseEffect, dsColonyForBaseEffect;
    public String noFreeMarketsKey = String.format("$%s_%s", MOD_PREFIX, "noFreeStorage");//V is boolean

    @Override
    public void onApplicationLoad() {
        Logger.getRootLogger().setLevel(Level.DEBUG);
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
        log.info("--------------------------------------------------");
        log.info("Reloading LunaLib settings");
        loadLunaLibSettings();

        if (enableAutofitTweaks) {
            for (FactionAPI faction : Global.getSector().getAllFactions()) {
                AutofitTweaks.saveAutofitRandomizeProbability(faction);
                AutofitTweaks.updateAutofitRandomizeProbability(faction, atAutofitRandomizeProbability);
            }
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
        }
        if (enableCommodityScaling) {
            CommodityScalingListener commodityScalingListener = new CommodityScalingListener();
            log.info("Adding " + commodityScalingListener.getClass().getName());
            Global.getSector().addTransientListener(commodityScalingListener);
        }
        if (enableNoFreeStorage) {
            for (SectorEntityToken token : Global.getSector().getEntitiesWithTag(Tags.STATION)) {
                if (isInCore(token)) {
                    token.addTag(noFreeMarketsKey);
                    token.getMemoryWithoutUpdate().set(noFreeMarketsKey, true);
                }
            }
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

    @Override
    public void beforeGameSave() {
        for (FactionAPI faction : Global.getSector().getAllFactions()) {
            DoctrineScalingListener.restoreDoctrineStats(faction);
            AutofitTweaks.restoreAutofitRandomizeProbability(faction);
        }
        PirateScholarsListener.forget(Global.getSector().getFaction(Factions.PIRATES));
        for (SectorEntityToken token : Global.getSector().getEntitiesWithTag(noFreeMarketsKey)) {
            token.removeTag(noFreeMarketsKey);
            token.getMemoryWithoutUpdate().unset(noFreeMarketsKey);
        }
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
        if (enableNoFreeStorage) {
            for (SectorEntityToken token : Global.getSector().getEntitiesWithTag(Tags.STATION)) {
                if (isInCore(token)) {
                    token.addTag(noFreeMarketsKey);
                    token.getMemoryWithoutUpdate().set(noFreeMarketsKey, true);
                }
            }
        }
        if (enableDoctrineScaling) {
            for (FactionAPI faction : Global.getSector().getAllFactions()) {
                if (DoctrineScalingListener.isValidFaction(faction)) DoctrineScalingListener.runAll(faction, false);
            }
        }
    }

    public void loadLunaLibSettings() {
        // Competent Foes
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
        psChance = LunaSettings.getFloat(MOD_ID, String.format("%s_psChance", MOD_PREFIX));
        psLearningAttemptsPerMonth = LunaSettings.getInt(MOD_ID, String.format("%s_psLearningAttemptsPerMonth", MOD_PREFIX));
        psMonthsForBaseEffect = LunaSettings.getInt(MOD_ID, String.format("%s_psMonthsForBaseEffect", MOD_PREFIX));

        //Doctrine Scaling
        enableDoctrineScaling = LunaSettings.getBoolean(MOD_ID, String.format("%s_doctrineScalingEnable", MOD_PREFIX));
        dsFlatBasePoints = LunaSettings.getFloat(MOD_ID, String.format("%s_dsFlatBasePoints", MOD_PREFIX));
        dsFlatGrowthPoints = LunaSettings.getFloat(MOD_ID, String.format("%s_dsFlatGrowthPoints", MOD_PREFIX));
        dsNoMarketBaseMultiplier = LunaSettings.getFloat(MOD_ID, String.format("%s_dsNoMarketBaseMultiplier", MOD_PREFIX));
        dsNoMarketGrowthMultiplier = LunaSettings.getFloat(MOD_ID, String.format("%s_dsNoMarketGrowthMultiplier", MOD_PREFIX));
        dsMonthForBaseEffect = LunaSettings.getInt(MOD_ID, String.format("%s_dsMonthForBaseEffect", MOD_PREFIX));
        dsBaseScalingPerMonth = LunaSettings.getFloat(MOD_ID, String.format("%s_dsBaseScalingPerMonth", MOD_PREFIX));
        dsGrowthScalingPerMonth = LunaSettings.getFloat(MOD_ID, String.format("%s_dsGrowthScalingPerMonth", MOD_PREFIX));
        dsLevelForBaseEffect = LunaSettings.getInt(MOD_ID, String.format("%s_dsLevelForBaseEffect", MOD_PREFIX));
        dsBaseScalingPerLevel = LunaSettings.getFloat(MOD_ID, String.format("%s_dsBaseScalingPerLevel", MOD_PREFIX));
        dsGrowthScalingPerLevel = LunaSettings.getFloat(MOD_ID, String.format("%s_dsGrowthScalingPerLevel", MOD_PREFIX));
        dsColonyForBaseEffect = LunaSettings.getInt(MOD_ID, String.format("%s_dsColonyForBaseEffect", MOD_PREFIX));
        dsBaseScalingPerColony = LunaSettings.getFloat(MOD_ID, String.format("%s_dsBaseScalingPerColony", MOD_PREFIX));
        dsGrowthScalingPerColony = LunaSettings.getFloat(MOD_ID, String.format("%s_dsGrowthScalingPerColony", MOD_PREFIX));
        dsBaseCostPerPoint = LunaSettings.getFloat(MOD_ID, String.format("%s_dsBaseCostPerPoint", MOD_PREFIX));
        dsCostPerTotalPoints = LunaSettings.getFloat(MOD_ID, String.format("%s_dsCostPerTotalPoints", MOD_PREFIX));
        dsCostPerAdditionalPoint = LunaSettings.getFloat(MOD_ID, String.format("%s_dsCostPerAdditionalPoint", MOD_PREFIX));
        dsCostAboveVanilla = LunaSettings.getFloat(MOD_ID, String.format("%s_dsCostAboveVanilla", MOD_PREFIX));
        dsBaseMarketSizePower = LunaSettings.getFloat(MOD_ID, String.format("%s_dsBaseMarketSizePower", MOD_PREFIX));
        dsBaseSprawlPenaltyPower = LunaSettings.getFloat(MOD_ID, String.format("%s_dsBaseSprawlPenaltyPower", MOD_PREFIX));
        dsGrowthMarketSizePower = LunaSettings.getFloat(MOD_ID, String.format("%s_dsGrowthMarketSizePower", MOD_PREFIX));
        dsGrowthSprawlPenaltyPower = LunaSettings.getFloat(MOD_ID, String.format("%s_dsGrowthSprawlPenaltyPower", MOD_PREFIX));

        // Market Crackdowns
        //commodity scaling
        enableCommodityScaling = LunaSettings.getBoolean(MOD_ID, String.format("%s_commodityScalingEnable", MOD_PREFIX));
        openCommodityScale = LunaSettings.getFloat(MOD_ID, String.format("%s_openCommodityScale", MOD_PREFIX));
        blackCommodityScale = LunaSettings.getFloat(MOD_ID, String.format("%s_blackCommodityScale", MOD_PREFIX));
        otherCommodityScale = LunaSettings.getFloat(MOD_ID, String.format("%s_otherCommodityScale", MOD_PREFIX));
        commodityVariationScale = LunaSettings.getFloat(MOD_ID, String.format("%s_commodityVariationScale", MOD_PREFIX));
        //no free storage
        enableNoFreeStorage = LunaSettings.getBoolean(MOD_ID, String.format("%s_noFreeStorageEnable", MOD_PREFIX));
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