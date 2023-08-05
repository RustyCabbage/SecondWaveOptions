package rc.SecondWaveOptions.MarketCrackdowns;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.util.HashMap;

import static rc.SecondWaveOptions.SecondWaveOptionsModPlugin.*;

public class CommodityScalingListener extends BaseCampaignEventListener {
    Logger log = Global.getLogger(this.getClass());
    String KEY_submarketCommodityScale = "$secondwaveoptions_CommodityScale_%s_%s"; // submarket level key
    float memoryDuration = 30f; // should be long enough that the market would naturally regenerate its usual maximum number of resources
    HashMap<String, HashMap<String, Integer>> submarketCommodityAmountsOnOpen = new HashMap<>();
    boolean marketCargoUpdated = false;

    public CommodityScalingListener() {
        super(false);
        log.setLevel(Level.INFO);
    }

    public enum SubmarketType {
        OPEN,
        BLACK_MARKET,
        MILITARY,
        LOCAL_RESOURCES,
        STORAGE,
        OTHER_FREE_TRANSFER,
        OTHER
    }

    @Override
    public void reportPlayerOpenedMarket(MarketAPI market) {
    }

    @Override
    public void reportEconomyTick(int iterIndex) {
    }

    /**
     * can't use OpenedMarket because commodities aren't loaded until cargo is updated
     * initializes commodity scaling if no memory is present, else updates the submarket based on any changes in the number of commodities detected
     */
    @Override
    public void reportPlayerOpenedMarketAndCargoUpdated(MarketAPI market) {
        log.debug("--------------------------------");
        log.debug("Opening market " + market.getId());
        log.debug("--------------------------------");
        if (marketCargoUpdated) return; //prevent refreshes when switching between market dialogue
        float maxScale = 0f;
        for (SubmarketAPI submarket : market.getSubmarketsCopy()) {
            String submarketKey = String.format(KEY_submarketCommodityScale, market.getId(), submarket.getSpecId());
            log.debug("Submarket " + submarket.getSpecId() + " is type " + getSubmarketType(submarket));
            if (!market.getMemoryWithoutUpdate().contains(submarketKey)) {
                switch (getSubmarketType(submarket)) {
                    case OPEN:
                        initializeCommodityScale(submarket, openCommodityScale);
                        maxScale = Math.max(maxScale, openCommodityScale);
                        break;
                    case BLACK_MARKET:
                        initializeCommodityScale(submarket, blackCommodityScale);
                        maxScale = Math.max(maxScale, blackCommodityScale);
                        break;
                    case MILITARY:
                        initializeCommodityScale(submarket, militaryCommodityScale);
                        maxScale = Math.max(maxScale, militaryCommodityScale);
                        break;
                    case LOCAL_RESOURCES:
                        initializeCommodityScale(submarket, lrCommodityScale);
                        maxScale = Math.max(maxScale, lrCommodityScale);
                        break;
                    case STORAGE:
                    case OTHER_FREE_TRANSFER:
                        break;
                    case OTHER:
                        initializeCommodityScale(submarket, otherCommodityScale);
                        maxScale = Math.max(maxScale, otherCommodityScale);
                        break;
                }
            } else {
                updateCommodities(submarket);
            }
        }
        /* This messes up the F1 pricing data
        for (CommodityOnMarketAPI commodity : market.getCommoditiesCopy()) {
            commodity.setStockpile(commodity.getStockpile() * maxScale);
        }
        market.updatePrices();
        market.updatePriceMult();
        /**/
        submarketCommodityAmountsOnOpen = getSubmarketCommodityAmounts(market); // this amount will refresh every time the market is updated
        marketCargoUpdated = true; //prevent refreshes when switching between market dialogue
    }

    /**
     * Checks if any net changes in commodity amounts have occurred, which are saved into memory.
     */
    @Override
    public void reportPlayerClosedMarket(MarketAPI market) {
        if (!marketCargoUpdated) return; //prevent this from firing if no cargo has been updated
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
                log.error(String.format("Null object: open=%s, closed=%s", commodityAmountsOnOpen == null, commodityAmountsOnClose == null));
                log.error("----------------------------------------------------");
                continue;
            }
            for (String commodityId : commodityAmountsOnOpen.keySet()) {
                int amountOnOpen = (commodityAmountsOnOpen.get(commodityId) != null) ? commodityAmountsOnOpen.get(commodityId) : 0;
                int amountOnClose = (commodityAmountsOnClose.get(commodityId) != null) ? commodityAmountsOnClose.get(commodityId) : 0;
                if (amountOnOpen != amountOnClose) {
                    log.debug(String.format("  Updating %s in %s from %s to %s", commodityId, submarketSpecId, amountOnOpen, amountOnClose));
                    commodityMap.put(commodityId, amountOnClose);
                }
            }
            float memDur = memoryDuration;
            if (market.hasSubmarket(submarketSpecId) && market.getSubmarket(submarketSpecId).getPlugin() instanceof BaseSubmarketPlugin) {
                BaseSubmarketPlugin plugin = (BaseSubmarketPlugin) market.getSubmarket(submarketSpecId).getPlugin();
                memDur = plugin.getMinSWUpdateInterval() - plugin.getSinceSWUpdate();
            }
            market.getMemoryWithoutUpdate().set(submarketKey, commodityMap, memDur);
            log.debug(String.format("Memory key %s created with duration %s", submarketKey, memDur));
        }
        marketCargoUpdated = false;
    }

    /**
     * Removes commodities according to the commodityScale. Saves the info into memory depending on how much time is left for the next cargo update.
     *
     * @param submarket
     * @param commodityScale
     */
    public void initializeCommodityScale(SubmarketAPI submarket, float commodityScale) {
        if (submarket == null || submarket.getMarket() == null) {
            log.error("No market or submarket found");
            return;
        }
        log.debug(String.format("Initializing commodity scaling for %s at %s with commodityScale value %s"
                , submarket.getSpecId(), submarket.getMarket().getId(), commodityScale));
        String submarketKey = String.format(KEY_submarketCommodityScale, submarket.getMarket().getId(), submarket.getSpecId());
        HashMap<String, Integer> commodityMap = new HashMap<>();
        for (String commodityId : Global.getSector().getEconomy().getAllCommodityIds()) {
            if (submarket.getCargo().getCommodityQuantity(commodityId) > 0) {
                int toRemove = Math.round(submarket.getCargo().getCommodityQuantity(commodityId) * (1f - commodityScale));
                log.debug((String.format("  Removing %s %s from %s of %s (originally %s)", toRemove, commodityId
                        , submarket.getSpecId(), submarket.getMarket().getId(), submarket.getCargo().getCommodityQuantity(commodityId))));
                submarket.getCargo().removeCommodity(commodityId, toRemove);
            }
            commodityMap.put(commodityId, Math.round(submarket.getCargo().getCommodityQuantity(commodityId)));
        }
        float memDur = memoryDuration;
        if (submarket.getPlugin() instanceof BaseSubmarketPlugin) {
            BaseSubmarketPlugin plugin = (BaseSubmarketPlugin) submarket.getPlugin();
            memDur = plugin.getMinSWUpdateInterval() - plugin.getSinceLastCargoUpdate();
        }
        submarket.getMarket().getMemoryWithoutUpdate().set(submarketKey, commodityMap, memDur);
        log.debug(String.format("Memory key %s created with duration %s", submarketKey, memDur));
    }

    /**
     * Updates commodities by checking for the difference between the saved number of commodities (determined onMarketClose and the current amount
     * which is reduced according to a function that is slightly more punishing than f(x)=x : toKeep = diff * scale/(2-scale)
     *  f(x) = x/(2-x) satisfies
     * f(x) is monotonic increasing from x=0 to x=1
     * f(0) = 0, f(1) = 1
     * concave up
     * less than f(x) = x except at the end points
     * greater than f(x) = x^2 except at the end points
     *
     * @param submarket
     */
    public void updateCommodities(SubmarketAPI submarket) {
        log.debug("Updating commodities for " + submarket.getSpecId() + " at " + submarket.getMarket().getId());
        String submarketKey = String.format(KEY_submarketCommodityScale, submarket.getMarket().getId(), submarket.getSpecId());
        HashMap<String, Integer> commodityMap = (HashMap<String, Integer>) submarket.getMarket().getMemoryWithoutUpdate().get(submarketKey);
        log.debug(commodityMap);
        for (String commodityId : Global.getSector().getEconomy().getAllCommodityIds()) {
            int savedCommodityAmount = commodityMap.get(commodityId);
            float commodityAmountDiff = submarket.getCargo().getCommodityQuantity(commodityId) - savedCommodityAmount;
            if (commodityAmountDiff > 0) {
                float commodityScale = getCommodityScaleForSubmarket(submarket);
                float toKeep = commodityAmountDiff * commodityScale / (2 - commodityScale);
                float toRemove = commodityAmountDiff - toKeep;
                log.debug(String.format("  Removing %s %s in %s to meet limits", toRemove, commodityId, submarket.getSpecId()));
                submarket.getCargo().removeCommodity(commodityId, toRemove);
            }
        }
    }

    /**
     * Returns a HashMap<submarketSpecId, HashMap<commodityId,commodityAmount>>
     * Ignores Storage and Other Free Transfer type markets (Local Resources are still captured) //todo maybe make this its own method
     * Currently saves all commodities, not just ones that are present in the submarket
     *
     * @param market
     * @return
     */
    public HashMap<String, HashMap<String, Integer>> getSubmarketCommodityAmounts(MarketAPI market) {
        HashMap<String, HashMap<String, Integer>> submarketCommodityAmounts = new HashMap<>();
        if (market == null) return submarketCommodityAmounts;
        for (SubmarketAPI submarket : market.getSubmarketsCopy()) {
            if (getSubmarketType(submarket) == SubmarketType.OTHER_FREE_TRANSFER || getSubmarketType(submarket) == SubmarketType.STORAGE) {
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

    /**
     * Get the SubmarketType enum based on plugin and spec information.
     *
     * @param submarket
     * @return
     */
    public SubmarketType getSubmarketType(SubmarketAPI submarket) {
        if (submarket.getPlugin().isOpenMarket() || submarket.getSpecId().equals(Submarkets.SUBMARKET_OPEN)) return SubmarketType.OPEN;
        if (submarket.getPlugin().isBlackMarket() || submarket.getSpecId().equals(Submarkets.SUBMARKET_BLACK)) return SubmarketType.BLACK_MARKET;
        if (submarket.getSpecId().equals(Submarkets.GENERIC_MILITARY)) return SubmarketType.MILITARY;
        if (submarket.getSpecId().equals(Submarkets.LOCAL_RESOURCES)) return SubmarketType.LOCAL_RESOURCES;
        if (submarket.getSpecId().equals(Submarkets.SUBMARKET_STORAGE)) return SubmarketType.STORAGE;
        if (submarket.getPlugin().isFreeTransfer()) return SubmarketType.OTHER_FREE_TRANSFER;
        return SubmarketType.OTHER;
    }

    /**
     * Get commodity scale for the given submarket. Derived from LunaLib settings.
     *
     * @param submarket
     * @return
     */
    public float getCommodityScaleForSubmarket(SubmarketAPI submarket) {
        SubmarketType type = getSubmarketType(submarket);
        switch (type) {
            case OPEN:
                return openCommodityScale;
            case BLACK_MARKET:
                return blackCommodityScale;
            case MILITARY:
                return militaryCommodityScale;
            case LOCAL_RESOURCES:
                return lrCommodityScale;
            case OTHER:
                return otherCommodityScale;
            default:
                return 1;
        }
    }
}