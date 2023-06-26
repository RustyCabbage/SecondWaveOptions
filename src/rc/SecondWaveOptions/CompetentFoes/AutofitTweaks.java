package rc.SecondWaveOptions.CompetentFoes;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import org.apache.log4j.Logger;
import rc.SecondWaveOptions.SecondWaveOptionsModPlugin;
import rc.SecondWaveOptions.Utils.MathUtils;

//todo DynaSector
public class AutofitTweaks {
    static Logger log = Global.getLogger(AutofitTweaks.class);
    public static String savedARPKey = String.format("$%s_%s", SecondWaveOptionsModPlugin.MOD_PREFIX, "savedARP"); //V is float

    public static void saveAutofitRandomizeProbability(FactionAPI faction) {
        faction.getMemoryWithoutUpdate().set(savedARPKey, faction.getDoctrine().getAutofitRandomizeProbability());
    }

    public static void updateAutofitRandomizeProbability(FactionAPI faction, float probability) {
        probability = MathUtils.clampValue(probability, 0, 1);
        faction.getDoctrine().setAutofitRandomizeProbability(probability);
    }

    public static void restoreAutofitRandomizeProbability(FactionAPI faction) {
        if (!faction.getMemoryWithoutUpdate().contains(savedARPKey)) {
            log.debug(String.format("No saved doctrine stats found for %s", faction));
            return;
        }
        float probability = faction.getMemoryWithoutUpdate().getFloat(savedARPKey);
        faction.getDoctrine().setAutofitRandomizeProbability(probability);
    }

}