package rc.SecondWaveOptions.MarketCrackdowns;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import org.apache.log4j.Logger;
import rc.SecondWaveOptions.SecondWaveOptionsModPlugin;

import static rc.SecondWaveOptions.Utils.MarketUtils.isCoreTheme;

public class NoFreeStorage {

    static Logger log = Global.getLogger(NoFreeStorage.class);
    public static String noFreeMarketsKey = String.format("$%s_%s", SecondWaveOptionsModPlugin.MOD_PREFIX, "noFreeStorage");//V is boolean

    public static boolean isFreeStorage(MarketAPI market) {
        if (market == null) return false;
        return (market.getSize() == 0
                && market.hasCondition(Conditions.ABANDONED_STATION)
                && !market.hasSpaceport()
                && !market.isInEconomy());
    }

    public static void disableFreeStorage() {
        for (SectorEntityToken token : Global.getSector().getEntitiesWithTag(Tags.STATION)) {
            if (isCoreTheme(token) && isFreeStorage(token.getMarket())) {
                token.addTag(noFreeMarketsKey);
                token.getMemoryWithoutUpdate().set(noFreeMarketsKey, true);
                if (token.getId() != null) log.debug("Removed storage options from " + token.getId());
            }
        }
    }

    public static void restoreFreeStorage() {
        for (SectorEntityToken token : Global.getSector().getEntitiesWithTag(noFreeMarketsKey)) {
            token.removeTag(noFreeMarketsKey);
            token.getMemoryWithoutUpdate().unset(noFreeMarketsKey);
            if (token.getId() != null) log.debug("Restored storage options for " + token.getId());
        }
    }
}