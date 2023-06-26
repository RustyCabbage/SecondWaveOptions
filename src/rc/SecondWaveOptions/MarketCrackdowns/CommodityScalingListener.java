package rc.SecondWaveOptions.MarketCrackdowns;

import rc.SecondWaveOptions.Utils.MathUtils;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import org.apache.log4j.Logger;
import rc.SecondWaveOptions.SecondWaveOptionsModPlugin;

import java.util.HashMap;

//todo figure out how economy resets work
//todo figure out how to adjust surplus and demand
public class CommodityScalingListener extends BaseCampaignEventListener {
    Logger log = Global.getLogger(this.getClass());
    String KEY_submarketCommodityScale = "$secondwaveoptions_CommodityScale_%s_%s"; // submarket level key
    float memoryDuration = 21f; // should be long enough that the market would naturally regenerate its usual maximum number of resources
    HashMap<String, HashMap<String, Integer>> submarketCommodityAmountsOnOpen = new HashMap<>();
    boolean marketCargoUpdated = false;

    public CommodityScalingListener() {
        super(false);
    }

    public enum SubmarketType {
        OPEN,
        BLACK_MARKET,
        OTHER,
        FREE_TRANSFER
    }

    @Override
    public void reportPlayerOpenedMarket(MarketAPI market) {
    }

    // can't use OpenedMarket because commodities aren't loaded until cargo is updated
    @Override
    public void reportPlayerOpenedMarketAndCargoUpdated(MarketAPI market) {
        log.debug("--------------------------------");
        log.debug("Opening market " + market.getId());
        log.debug("--------------------------------");
        if (marketCargoUpdated) return;
        for (SubmarketAPI submarket : market.getSubmarketsCopy()) {
            String submarketKey = String.format(KEY_submarketCommodityScale, market.getId(), submarket.getSpecId());
            log.debug("Submarket " + submarket.getSpecId() + " is type " + getSubmarketType(submarket));
            if (!market.getMemoryWithoutUpdate().contains(submarketKey)) {
                switch (getSubmarketType(submarket)) {
                    case OPEN:
                        initializeCommodityScale(submarket, SecondWaveOptionsModPlugin.openCommodityScale);
                        break;
                    case BLACK_MARKET:
                        initializeCommodityScale(submarket, SecondWaveOptionsModPlugin.blackCommodityScale);
                        break;
                    case OTHER:
                        initializeCommodityScale(submarket, SecondWaveOptionsModPlugin.otherCommodityScale);
                        break;
                    case FREE_TRANSFER:
                        break;
                }
            } else {
                updateCommodities(submarket, SecondWaveOptionsModPlugin.commodityVariationScale);
            }
        }
        market.updatePrices();
        market.updatePriceMult();
        submarketCommodityAmountsOnOpen = getSubmarketCommodityAmounts(market);
        marketCargoUpdated = true; //prevent refreshes when switching between market dialogue
    }

    /*
    Check if any market transactions occurred
     */
    @Override
    public void reportPlayerClosedMarket(MarketAPI market) {
        if (!marketCargoUpdated) return;
        log.debug("--------------------------------");
        log.debug("Closing market " + market.getId());
        log.debug("--------------------------------");
        HashMap<String, HashMap<String, Integer>> submarketCommodityAmountsOnClose = getSubmarketCommodityAmounts(market);
        for (String submarketSpecId : submarketCommodityAmountsOnOpen.keySet()) {
            String submarketKey = String.format(KEY_submarketCommodityScale, market.getId(), submarketSpecId);
            HashMap<String, Integer> commodityMap = (HashMap<String, Integer>) market.getMemoryWithoutUpdate().get(submarketKey);
            HashMap<String, Integer> commodityAmountsOnOpen = submarketCommodityAmountsOnOpen.get(submarketSpecId);
            HashMap<String, Integer> commodityAmountsOnClose = submarketCommodityAmountsOnClose.get(submarketSpecId);
            if (commodityAmountsOnOpen == null || commodityAmountsOnClose == null) {
                log.error("----------------------------------------------------");
                log.error(String.format("Null object: open=%s, closed=%s", commodityAmountsOnOpen, commodityAmountsOnClose));
                log.error("----------------------------------------------------");
                return;
            }
            for (String commodityId : commodityAmountsOnOpen.keySet()) {
                int amountOnOpen = commodityAmountsOnOpen.get(commodityId);
                int amountOnClose = commodityAmountsOnClose.get(commodityId);
                if (amountOnOpen != amountOnClose) {
                    log.debug(String.format("  Updating %s in %s from %s to %s",
                                            commodityId, submarketSpecId
                            , amountOnOpen
                            , amountOnClose));
                    commodityMap.put(commodityId, amountOnClose);
                }
            }
            market.getMemoryWithoutUpdate().set(submarketKey, commodityMap, memoryDuration);
        }
        marketCargoUpdated = false;
    }

    @Override
    public void reportEconomyTick(int iterIndex) {
    }

    public void initializeCommodityScale(SubmarketAPI submarket, float commodityScale) {
        log.debug(String.format("Initializing commodity scaling for %s at %swith commodityScale value %s"
                , submarket.getSpecId(), submarket.getMarket().getId(), commodityScale));
        String submarketKey = String.format(KEY_submarketCommodityScale, submarket.getMarket().getId(), submarket.getSpecId());
        HashMap<String, Integer> commodityMap = new HashMap<>();
        for (String commodityId : Global.getSector().getEconomy().getAllCommodityIds()) {
            if (submarket.getCargo().getCommodityQuantity(commodityId) > 0) {
                log.debug((String.format("  Removing %s %s from %s of %s (originally %s)",
                                         Math.round(submarket.getCargo().getCommodityQuantity(commodityId) * (1f - commodityScale))
                        , commodityId, submarket.getSpecId(), submarket.getMarket().getId()
                        , submarket.getCargo().getCommodityQuantity(commodityId))));
                submarket.getCargo().removeCommodity(commodityId
                        , Math.round(submarket.getCargo().getCommodityQuantity(commodityId) * (1f - commodityScale)));

            }
            commodityMap.put(commodityId, Math.round(submarket.getCargo().getCommodityQuantity(commodityId)));
        }
        submarket.getMarket().getMemoryWithoutUpdate().set(submarketKey, commodityMap, memoryDuration);
    }

    public void updateCommodities(SubmarketAPI submarket, float variationScale) {
        log.debug("Updating commodities for " + submarket.getSpecId() + " at " + submarket.getMarket().getId());
        String submarketKey = String.format(KEY_submarketCommodityScale, submarket.getMarket().getId(), submarket.getSpecId());
        HashMap<String, Integer> commodityMap = (HashMap<String, Integer>) submarket.getMarket().getMemoryWithoutUpdate().get(submarketKey);
        log.debug(commodityMap);
        float allowedVariation = variationScale * MathUtils.clampValue((memoryDuration - submarket.getMarket().getMemoryWithoutUpdate().getExpire(submarketKey)) / memoryDuration
                                                                               + MathUtils.getNormalRandom(0f, 0.1f), 0f, 1f);
        //log.debug(allowedVariation);
        for (String commodityId : Global.getSector().getEconomy().getAllCommodityIds()) {
            float savedCommodityAmount = commodityMap.get(commodityId);
            /*
            log.debug(String.format("%s - %s: %s vs [%s,%s]",
                    submarket.getSpecId(), commodityId, submarket.getCargo().getCommodityQuantity(commodityId)
                    , savedCommodityAmount * (1 - allowedVariation)
                    , savedCommodityAmount * (1 + allowedVariation)));
            /**/
            if (submarket.getCargo().getCommodityQuantity(commodityId) > savedCommodityAmount * (1 + allowedVariation)) {
                float toRemove = submarket.getCargo().getCommodityQuantity(commodityId) - savedCommodityAmount * (1 + allowedVariation);
                log.debug(String.format("  Removing %s %s in %s to meet limits", toRemove, commodityId, submarket.getSpecId()));
                submarket.getCargo().removeCommodity(commodityId, toRemove);
            } else if (submarket.getCargo().getCommodityQuantity(commodityId) < savedCommodityAmount * (1 - allowedVariation)) {
                float toAdd = savedCommodityAmount * (1 - allowedVariation) - submarket.getCargo().getCommodityQuantity(commodityId);
                log.debug(String.format("  Adding %s %s in %s to meet limits", toAdd, commodityId, submarket.getSpecId()));
                submarket.getCargo().addCommodity(commodityId, toAdd);
            }
        }
    }

    /*
     * HashMap<submarketSpecId, HashMap<commodityId,commodityAmount>
     */
    public HashMap<String, HashMap<String, Integer>> getSubmarketCommodityAmounts(MarketAPI market) {
        HashMap<String, HashMap<String, Integer>> submarketCommodityAmounts = new HashMap<>();
        if (market == null) return submarketCommodityAmounts;
        for (SubmarketAPI submarket : market.getSubmarketsCopy()) {
            if (getSubmarketType(submarket) == SubmarketType.FREE_TRANSFER) {
                continue;
            }
            HashMap<String, Integer> commodityAmounts = new HashMap<>();
            for (String commodityId : Global.getSector().getEconomy().getAllCommodityIds()) {
                commodityAmounts.put(commodityId, (int) Math.floor(submarket.getCargo().getCommodityQuantity(commodityId)));
            }
            submarketCommodityAmounts.put(submarket.getSpecId(), commodityAmounts);
        }
        return submarketCommodityAmounts;
    }

    public SubmarketType getSubmarketType(SubmarketAPI submarket) {
        if (submarket.getPlugin().isOpenMarket()) return SubmarketType.OPEN;
        if (submarket.getPlugin().isBlackMarket()) return SubmarketType.BLACK_MARKET;
        if (submarket.getPlugin().isFreeTransfer()) return SubmarketType.FREE_TRANSFER;
        return SubmarketType.OTHER;
    }
}