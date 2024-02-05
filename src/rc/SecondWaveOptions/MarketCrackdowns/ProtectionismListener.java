package rc.SecondWaveOptions.MarketCrackdowns;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import rc.SecondWaveOptions.Utils.MathUtils;

import static rc.SecondWaveOptions.SecondWaveOptionsModPlugin.*;

/**
 * Factions will increase tariffs as player colonies grow
 * only affects open markets :(
 * todo... affect the submarket where the transaction is occurring?
 */
public class ProtectionismListener extends BaseCampaignEventListener {
    Logger log = Global.getLogger(ProtectionismListener.class);
    float MAX_TARIFF_RATE = protectionismMaxTariff * 0.01f; // 65% is a bit higher than the highest historical US tariff rate
    /*
    String economyPlayerSellIllegalImpactMult = "economyPlayerSellIllegalImpactMult";
    String oldSellIllegalImpactMultKey = String.format("$%s_%s", SecondWaveOptionsModPlugin.MOD_PREFIX, "oldSellIllegalImpactMult"); //V is float
    /**/

    public ProtectionismListener() {
        super(false);
        //log.setLevel(Level.INFO);
    }

    /**
     * No PERCENT mode because the base value is 0.
     */
    public enum TariffMode {
        FLAT,
        MULT
    }

    @Override
    public void reportPlayerOpenedMarket(final MarketAPI market) {
        if (market.isPlayerOwned()) return;
        /*
        Global.getSector().getMemoryWithoutUpdate().set(oldSellIllegalImpactMultKey, Global.getSettings().getFloat(economyPlayerSellIllegalImpactMult));
        Global.getSettings().setFloat(economyPlayerSellIllegalImpactMult, 10f);
        /**/
        modifyTariff(market, TariffMode.FLAT);
    }

    @Override
    public void reportPlayerClosedMarket(final MarketAPI market) {
        market.getTariff().unmodify(this.toString());
        log.debug("Returning tariff to " + market.getTariff().getModifiedValue());
        /*
        if (Global.getSector().getMemoryWithoutUpdate().contains(oldSellIllegalImpactMultKey)) {
            Global.getSettings().setFloat(economyPlayerSellIllegalImpactMult, Global.getSector().getMemoryWithoutUpdate().getFloat(oldSellIllegalImpactMultKey));
            Global.getSector().getMemoryWithoutUpdate().unset(oldSellIllegalImpactMultKey);
        }
        /**/
    }

    /* Doesn't have the intended effect
    @Override
    public void reportPlayerMarketTransaction(final PlayerMarketTransaction transaction) {
        if (transaction.getSubmarket().getPlugin().isBlackMarket()) {
            if (!Global.getSector().getMemoryWithoutUpdate().contains(oldSellIllegalImpactMultKey)) {
                Global.getSector().getMemoryWithoutUpdate().set(oldSellIllegalImpactMultKey, Global.getSettings().getFloat(economyPlayerSellIllegalImpactMult));
                Global.getSettings().setFloat(economyPlayerSellIllegalImpactMult, 100000f);
            }
            log.debug("Black market: Trade impact mult set to " + Global.getSettings().getFloat(economyPlayerSellIllegalImpactMult));
            log.debug(transaction.getBaseTradeValueImpact());
        } else {
            if (Global.getSector().getMemoryWithoutUpdate().contains(oldSellIllegalImpactMultKey)) {
                Global.getSettings().setFloat(economyPlayerSellIllegalImpactMult,Global.getSector().getMemoryWithoutUpdate().getFloat(oldSellIllegalImpactMultKey));
                Global.getSector().getMemoryWithoutUpdate().unset(oldSellIllegalImpactMultKey);
            }
            log.debug("Not black market: Trade impact mult set to " + Global.getSettings().getFloat(economyPlayerSellIllegalImpactMult));
            log.debug(transaction.getBaseTradeValueImpact());
        }
    }
    /**/

    /**
     * We expect the max market score to be from a size 6 colony with 4 upgraded and improved industries = 6 * 4 * 3 = 72
     * You might expect up to 5 such markets, disregarding the usage of Alpha Cores
     * Non-linear function of the number of colonies
     *
     * @return
     */
    public float getPlayerColonyScore() {
        float totalScore = 0;
        float numColonies = 0;
        for (MarketAPI market : Misc.getPlayerMarkets(false)) {
            if (!market.hasSpaceport() // don't count them for a small grace period
                    || market.isHidden()
                    || !market.isInEconomy()) continue;
            numColonies++;
            float size = market.getSize();
            float numIndustries = 0;
            for (Industry industry : market.getIndustries()) {
                if (industry.isHidden()) continue; // who would know?
                if (industry.isIndustry()) numIndustries++;
                if (industry.isIndustry() && industry.getSpec().getDowngrade() != null) numIndustries++;
                if (industry.isIndustry() && industry.isImproved()) numIndustries++;
            }
            float marketScore = size * Math.max(0.5f, numIndustries);
            log.debug(String.format("Market score for %s: %s", market.getId(), marketScore));
            totalScore += marketScore;
        }
        totalScore *= Math.pow(numColonies, protectionismNumColoniesPower);
        log.debug("Total player colony score: " + totalScore);
        return totalScore;
    }

    /**
     * 4% per maxed out colony before scaling by numColonies. Given 30% base for vanilla and 18% base for nex:
     * 1: 34% || 22%
     * 2: 41% || 29%
     * 3: 51% || 39%
     * 4: 62% || 50%
     * 5: 75% || 63%
     * 6: 89% || 77%
     *
     * @param colonyScore
     * @return
     */
    public float getFlatTariff(float colonyScore) {
        float flatTariff = colonyScore / 72f * protectionismTariffImpact * 0.01f;
        log.debug("Flat tariff: " + flatTariff);
        return flatTariff;
    }

    /**
     * Unused atm since there's no clear reason to use over flat tariff. Slightly more lenient with nex enabled
     * 1: 34% || 20%
     * 2: 41% || 25%
     * 3: 51% || 30%
     * 4: 62% || 37%
     * 5: 75% || 45%
     * 6: 89% || 53%
     *
     * @param colonyScore
     * @return
     */
    public float getMultTariff(float colonyScore) {
        return (colonyScore / 72f * 0.2f * 0.01f); //todo configurable
    }

    public float updateTariffDueToFaction(MarketAPI market, float originalTariff, float commissionFactionMult, float pirateFactionMult, float decentralizedFactionMult) {
        float newTariff = originalTariff;
        if (Misc.getCommissionFactionId().equals(market.getFactionId())) newTariff *= commissionFactionMult;
        if (Misc.isPirateFaction(market.getFaction())) newTariff *= pirateFactionMult;
        if (Misc.isDecentralized(market.getFaction())) newTariff *= decentralizedFactionMult;
        return newTariff;
    }

    /**
     * Returns final tariff after applying the effect
     * @param market
     * @param mode
     * @return
     */
    public float modifyTariff(MarketAPI market, TariffMode mode) {
        float playerColonyScore = getPlayerColonyScore();
        float currTariff = market.getTariff().getModifiedValue();
        float tariff = 0f;
        switch (mode) {
            case FLAT:
                tariff = getFlatTariff(playerColonyScore);
                log.debug("Final tariff: " + tariff);
                tariff = updateTariffDueToFaction(market, tariff, protectionismCommissionFactionMult, protectionismPirateFactionMult, protectionismDecentralizedFactionMult);
                log.debug("Final tariff: " + tariff);
                tariff = Math.min(MAX_TARIFF_RATE - currTariff, tariff);
                log.debug("Final tariff: " + tariff);
                tariff = MathUtils.roundToTwoDecimals(tariff);
                log.debug("Final tariff: " + tariff);
                market.getTariff().modifyFlat(this.toString(), tariff);
                break;
            case MULT:
                tariff = getMultTariff(playerColonyScore);
                tariff = updateTariffDueToFaction(market, tariff, 0.5f, 0.7f, 0.7f);
                tariff = MathUtils.roundToTwoDecimals(Math.min(MAX_TARIFF_RATE / currTariff, 1 + tariff));
                log.debug("Final tariff: " + tariff);
                market.getTariff().modifyMult(this.toString(), tariff);
                break;
        }
        return tariff;
    }
}