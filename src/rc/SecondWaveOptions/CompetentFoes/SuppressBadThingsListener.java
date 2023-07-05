package rc.SecondWaveOptions.CompetentFoes;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import org.apache.log4j.Logger;
import rc.SecondWaveOptions.SecondWaveOptionsModPlugin;

import java.util.Random;

import static rc.SecondWaveOptions.SecondWaveOptionsModPlugin.*;

//todo apply this to all dmods??
// todo s-mod Solar Shielding for LG?
public class SuppressBadThingsListener extends BaseCampaignEventListener {
    //boolean newInteraction = false;
    static Logger log = Global.getLogger(SuppressBadThingsListener.class);
    public static String blessedByLuddKey = String.format("$%s_%s", SecondWaveOptionsModPlugin.MOD_PREFIX, "blessedByLudd"); //V is boolean
    public static String escapedNoticeKey = String.format("$%s_%s", SecondWaveOptionsModPlugin.MOD_PREFIX, "escapedNoticeKey"); //V is boolean

    public SuppressBadThingsListener() {
        super(false);
    }

    @Override
    public void reportShownInteractionDialog(final InteractionDialogAPI dialog) {
        //log.debug(String.format("New dialog %s", dialog));
        if (dialog.getInteractionTarget() == null) return;
        if (dialog.getInteractionTarget() instanceof CampaignFleetAPI) {
            CampaignFleetAPI fleet = (CampaignFleetAPI) dialog.getInteractionTarget();
            if (enableBlessedByLudd) suppressBadThings(fleet, Factions.LUDDIC_PATH,
                                                       HullMods.ILL_ADVISED, bblChance, blessedByLuddKey, true, true);
            if (enableMakeSindriaGreatAgain) {
                suppressBadThings(fleet, Factions.DIKTAT,
                                  HullMods.ANDRADA_MODS, msgaChance, escapedNoticeKey, true, true);
                suppressBadThings(fleet, Factions.LIONS_GUARD,
                                  HullMods.ANDRADA_MODS, mlggaChance, escapedNoticeKey, true, true);
            }
        }
    }

    @Override
    public void reportPlayerEngagement(final EngagementResultAPI result) {
        BattleAPI battle = result.getBattle();
        battle.genCombined();
        for (CampaignFleetAPI fleet : battle.getBothSides()) {
            if (fleet.isPlayerFleet()) continue;
            if (enableBlessedByLudd) unsuppressBadThings(fleet, Factions.LUDDIC_PATH
                    , HullMods.ILL_ADVISED, blessedByLuddKey, true, true);
            if (enableMakeSindriaGreatAgain) {
                unsuppressBadThings(fleet, Factions.DIKTAT
                        , HullMods.ANDRADA_MODS, escapedNoticeKey, true, true);
                unsuppressBadThings(fleet, Factions.LIONS_GUARD
                        , HullMods.ANDRADA_MODS, escapedNoticeKey, true, true);

            }
        }
    }

    public static void suppressBadThings(CampaignFleetAPI fleet, String factionId, String badThing, float chance, String memKey, boolean checkForKey, boolean setKey) {
        if (!fleet.getFaction().getId().equals(factionId)) return;
        if (checkForKey && fleet.getMemoryWithoutUpdate().contains(memKey)) return;
        for (FleetMemberAPI m : fleet.getMembersWithFightersCopy()) {
            if (m.getVariant().hasHullMod(badThing)
                    && !m.getVariant().getSuppressedMods().contains(badThing)) {
                if ((new Random()).nextFloat() < chance) {
                    m.getVariant().addSuppressedMod(badThing);
                }
                log.debug(String.format("Added %s to suppressed mods on %s", badThing, m.getShipName()));
            }
        }
        if (setKey) fleet.getMemoryWithoutUpdate().set(memKey, true);
    }

    public static void unsuppressBadThings(CampaignFleetAPI fleet, String factionId, String badThing, String memKey, boolean checkForKey, boolean unsetKey) {
        if (fleet.isPlayerFleet() || !fleet.getFaction().getId().equals(factionId)) return;
        if (checkForKey && !fleet.getMemoryWithoutUpdate().contains(memKey)) return;
        for (FleetMemberAPI m : fleet.getMembersWithFightersCopy()) {
            if (m.getVariant().getSuppressedMods().contains(badThing)) {
                m.getVariant().removeSuppressedMod(badThing);
                log.debug(String.format("Removed %s from suppressed mods on %s", badThing, m.getShipName()));
            }
        }
        if (unsetKey) fleet.getMemoryWithoutUpdate().unset(memKey);
    }

    public static boolean hasMemKey(CampaignFleetAPI fleet, String memKey) {
        return fleet.getMemoryWithoutUpdate().contains(memKey);
    }
}