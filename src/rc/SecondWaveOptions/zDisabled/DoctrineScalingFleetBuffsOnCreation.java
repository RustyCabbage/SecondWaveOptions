package rc.SecondWaveOptions.zDisabled;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionDoctrineAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;

import java.util.Random;

import static rc.SecondWaveOptions.DoctrineScaling.DoctrineScalingListener.isValidFaction;

@Deprecated
public class DoctrineScalingFleetBuffsOnCreation extends BaseCampaignEventListener {
    Logger log = Global.getLogger(DoctrineScalingFleetBuffsOnCreation.class);
    float sModChainProbability = 0.5f; //todo setting sModChainProbability

    public DoctrineScalingFleetBuffsOnCreation() {
        super(false);
    }

    public enum HullModType {
        BUILT_IN_MOD,
        PERMA_MOD,
        HULL_MOD
    }

    @Override
    public void reportFleetSpawned(final CampaignFleetAPI fleet) {
        if (isValidFaction(fleet.getFaction())) {
            FactionDoctrineAPI doctrine = fleet.getFaction().getDoctrine();
            if (doctrine.getOfficerQuality() > 5) {

            }
            if (doctrine.getShipQuality() > 5) {
                log.debug("----------------------------------------------------");
                log.debug(String.format("Adding s-mods to fleet %s in %s,"
                        , fleet.getNameWithFaction(), fleet.getContainingLocation().getName()));
                log.debug("----------------------------------------------------");
                for (FleetMemberAPI member : fleet.getMembersWithFightersCopy()) {
                    if (member.isFighterWing() || member.isStation()) continue;
                    Random random = new Random();
                    float sModProbability = getSModProbability(doctrine, member);
                    boolean stop = false;
                    WeightedRandomPicker<String> builtInMods = buildSModPicker(member, HullModType.BUILT_IN_MOD);
                    while (!builtInMods.isEmpty()) {
                        if (random.nextFloat() > sModProbability) stop = true;
                        if (stop) break;
                        String toSMod = builtInMods.pickAndRemove();
                        member.getVariant().addPermaMod(toSMod, true);
                        log.debug(String.format("Added %s as an s-mod for %s of class %s"
                                , toSMod, member.getShipName(), member.getHullId()));
                        sModProbability *= sModChainProbability;
                    }
                    if (stop) continue;
                    WeightedRandomPicker<String> permaMods = buildSModPicker(member, HullModType.PERMA_MOD);
                    while (!permaMods.isEmpty()) {
                        if (random.nextFloat() > sModProbability) stop = true;
                        if (stop) break;
                        String toSMod = permaMods.pickAndRemove();
                        member.getVariant().addPermaMod(toSMod, true);
                        log.debug(String.format("Added %s as an s-mod for %s of class %s"
                                , toSMod, member.getShipName(), member.getHullId()));
                        sModProbability *= sModChainProbability;
                    }
                    if (stop) continue;
                    WeightedRandomPicker<String> hullMods = buildSModPicker(member, HullModType.HULL_MOD);
                    while (!hullMods.isEmpty()) {
                        if (random.nextFloat() > sModProbability) stop = true;
                        if (stop) break;
                        String toSMod = hullMods.pickAndRemove();
                        member.getVariant().addPermaMod(toSMod, true);
                        log.debug(String.format("Added %s as an s-mod for %s of class %s"
                                , toSMod, member.getShipName(), member.getHullId()));
                        sModProbability *= sModChainProbability;
                    }
                }
            }
        }
    }

    public float getSModProbability(FactionDoctrineAPI doctrine, FleetMemberAPI member) {
        float sModProbability = 0.5f * (doctrine.getShipQuality() - 5); //todo setting sModProbability
        if (member.isCivilian()) sModProbability *= 0.5f;
        if (member.isFlagship()) sModProbability *= 1.4f;
        switch (member.getVariant().getHullSize()) {
            case CAPITAL_SHIP:
                sModProbability *= 1.3f;
                break;
            case DESTROYER:
                sModProbability *= 0.7f;
                break;
            case FRIGATE:
                sModProbability *= 0.4f;
                break;
            default:
                break;
        }
        return sModProbability;
    }

    public WeightedRandomPicker<String> buildSModPicker(FleetMemberAPI member, HullModType type) {
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
        switch (type) {
            case BUILT_IN_MOD:
                for (String hullModId : member.getHullSpec().getBuiltInMods()) {
                    // no s-mod effect or already s-modded or suppressed mod means skip
                    if (Global.getSettings().getHullModSpec(hullModId).getSModDescription(member.getVariant().getHullSize()).equals("")
                            || member.getVariant().getSModdedBuiltIns().contains(hullModId)
                            || member.getVariant().getSuppressedMods().contains(hullModId)) continue;
                    picker.add(hullModId);
                }
                break;
            case PERMA_MOD:
                for (String hullModId : member.getVariant().getPermaMods()) {
                    // no s-mod effect or already s-modded means skip  or suppressed mod
                    if (Global.getSettings().getHullModSpec(hullModId).getSModDescription(member.getVariant().getHullSize()).equals("")
                            || member.getVariant().getSMods().contains(hullModId)
                            || member.getVariant().getSuppressedMods().contains(hullModId)) continue;
                    picker.add(hullModId);
                }
                break;
            case HULL_MOD:
                for (String hullModId : member.getVariant().getNonBuiltInHullmods()) {
                    // already s-modded or permamod means skip  or suppressed mod
                    if (member.getVariant().getSMods().contains(hullModId)
                            || member.getVariant().getPermaMods().contains(hullModId)
                            || member.getVariant().getSuppressedMods().contains(hullModId)) continue;
                    picker.add(hullModId);
                }
                break;
        }
        return picker;
    }
}