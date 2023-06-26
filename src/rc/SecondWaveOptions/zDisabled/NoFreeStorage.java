package rc.SecondWaveOptions.zDisabled;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import org.apache.log4j.Logger;

import static rc.SecondWaveOptions.Utils.MarketUtils.isInCore;

@Deprecated
public class NoFreeStorage {

    static Logger log = Global.getLogger(NoFreeStorage.class);

    public static void disableFreeStorage() {
        log.debug("----------------------");
        log.debug("Disabling free storage");

        for (SectorEntityToken token : Global.getSector().getEntitiesWithTag(Tags.STATION)) {
            log.debug(String.format("Checking token %s", token.getId()));
            disableFreeStorage(token);
        }
        log.debug("----------------------");
    }

    public static void disableFreeStorage(SectorEntityToken token) {
        if (token.getMarket() == null) {
            log.debug(String.format("Token %s has no market", token.getId()));
            return;
        }
        MarketAPI market = token.getMarket();

        if (market.getSize() == 0
                && market.hasCondition(Conditions.ABANDONED_STATION)
                && !market.hasSpaceport()
                && !market.isInEconomy()
                && market.hasSubmarket(Submarkets.SUBMARKET_STORAGE)
                && !market.isPlanetConditionMarketOnly()
                && token.hasTag(Tags.STATION)
                && token.getMemoryWithoutUpdate().getBoolean("$abandonedStation")
                && token.getMemoryWithoutUpdate().contains("$tradeMode")
                && isInCore(token)) {
            log.debug(String.format("Removing market %s from ", market.getId(), token.getId()));
            //token.setMarket(null);
            token.getMemoryWithoutUpdate().set("$tradeMode", "NONE");
        }

    }
}