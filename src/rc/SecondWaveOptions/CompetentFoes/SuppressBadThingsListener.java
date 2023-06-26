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
    Logger log = Global.getLogger(this.getClass());
    String blessedByLuddKey = String.format("$%s_%s", SecondWaveOptionsModPlugin.MOD_PREFIX, "blessedByLudd"); //V is boolean
    String escapedNoticeKey = String.format("$%s_%s", SecondWaveOptionsModPlugin.MOD_PREFIX, "escapedNoticeKey"); //V is boolean

    public SuppressBadThingsListener() {
        super(false);
    }

    @Override
    public void reportShownInteractionDialog(final InteractionDialogAPI dialog) {
        //log.debug(String.format("New dialog %s", dialog));
        if (dialog.getInteractionTarget() == null) return;
        if (dialog.getInteractionTarget() instanceof CampaignFleetAPI) {
            CampaignFleetAPI fleet = (CampaignFleetAPI) dialog.getInteractionTarget();
            if (enableBlessedByLudd) suppressBadThings(fleet, Factions.LUDDIC_PATH, HullMods.ILL_ADVISED, bblChance, blessedByLuddKey);
            if (enableMakeSindriaGreatAgain) {
                suppressBadThings(fleet, Factions.DIKTAT, HullMods.ANDRADA_MODS, msgaChance, escapedNoticeKey);
                suppressBadThings(fleet, Factions.LIONS_GUARD, HullMods.ANDRADA_MODS, mlggaChance, escapedNoticeKey);
            }
        }
    }

    @Override
    public void reportPlayerEngagement(final EngagementResultAPI result) {
        BattleAPI battle = result.getBattle();
        battle.genCombined();
        for (CampaignFleetAPI fleet : battle.getBothSides()) {
            if (fleet.isPlayerFleet()) continue;
            if (enableBlessedByLudd) unsuppressBadThings(fleet, Factions.LUDDIC_PATH, HullMods.ILL_ADVISED, blessedByLuddKey);
            if (enableMakeSindriaGreatAgain) {
                unsuppressBadThings(fleet, Factions.DIKTAT, HullMods.ANDRADA_MODS, escapedNoticeKey);
                unsuppressBadThings(fleet, Factions.LIONS_GUARD, HullMods.ANDRADA_MODS, escapedNoticeKey);

            }
        }
    }

    public void suppressBadThings(CampaignFleetAPI fleet, String factionId, String badThing, float chance, String memKey) {
        if (!fleet.getFaction().getId().equals(factionId)) return;
        if (fleet.getMemoryWithoutUpdate().contains(memKey)) return;
        for (FleetMemberAPI m : fleet.getMembersWithFightersCopy()) {
            if (m.getVariant().hasHullMod(badThing)
                    && !m.getVariant().getSuppressedMods().contains(badThing)) {
                if ((new Random()).nextFloat() < chance) {
                    m.getVariant().addSuppressedMod(badThing);
                }
                log.debug(String.format("Added %s to suppressed mods on %s", badThing, m.getShipName()));
            }
        }
        fleet.getMemoryWithoutUpdate().set(memKey, true);
    }

    public void unsuppressBadThings(CampaignFleetAPI fleet, String factionId, String badThing, String memKey) {
        if (fleet.isPlayerFleet() || !fleet.getFaction().getId().equals(factionId)) return;
        if (!fleet.getMemoryWithoutUpdate().contains(memKey)) return;
        for (FleetMemberAPI m : fleet.getMembersWithFightersCopy()) {
            if (m.getVariant().getSuppressedMods().contains(badThing)) {
                m.getVariant().removeSuppressedMod(badThing);
                log.debug(String.format("Removed %s from suppressed mods on %s", badThing, m.getShipName()));
            }
        }
        fleet.getMemoryWithoutUpdate().unset(memKey);
    }
}