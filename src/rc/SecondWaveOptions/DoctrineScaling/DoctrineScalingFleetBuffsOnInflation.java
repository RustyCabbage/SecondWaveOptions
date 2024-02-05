package rc.SecondWaveOptions.DoctrineScaling;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionDoctrineAPI;
import com.fs.starfarer.api.campaign.FleetInflater;
import com.fs.starfarer.api.campaign.listeners.FleetInflationListener;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;
import rc.SecondWaveOptions.SecondWaveOptionsModPlugin;

import java.util.Random;

import static com.fs.starfarer.api.characters.SkillsChangeRemoveVentsCapsEffect.getMaxCaps;
import static com.fs.starfarer.api.characters.SkillsChangeRemoveVentsCapsEffect.getMaxVents;
import static com.fs.starfarer.api.util.Misc.DISSIPATION_PER_VENT;
import static com.fs.starfarer.api.util.Misc.FLUX_PER_CAPACITOR;
import static rc.SecondWaveOptions.DoctrineScaling.DoctrineScalingListener.isValidFaction;
import static rc.SecondWaveOptions.SecondWaveOptionsModPlugin.dsFleetBuffsChainSModProb;
import static rc.SecondWaveOptions.SecondWaveOptionsModPlugin.dsFleetBuffsSModProb;

public class DoctrineScalingFleetBuffsOnInflation implements FleetInflationListener {
    Logger log = Global.getLogger(DoctrineScalingFleetBuffsOnInflation.class);
    public String scaledByDSKey = String.format("$%s_%s", SecondWaveOptionsModPlugin.MOD_PREFIX, "scaledByDS"); //V is float

    public enum HullModType {
        ALL,
        BUILT_IN_MOD,
        PERMA_MOD,
        HULL_MOD
    }

    public enum OfficerUpgradeType {
        COMMANDER,
        ADD,
        LEVEL,
        ELITE,
    }

    @Override
    public void reportFleetInflated(final CampaignFleetAPI fleet, final FleetInflater inflater) {
        if (fleet.getMemoryWithoutUpdate().contains(scaledByDSKey)) {
            log.debug(String.format("Skipping fleet %s at %s because already scaled"
                    , fleet.getNameWithFactionKeepCase(), fleet.getLocation()));
            return;
        }
        if (isValidFaction(fleet.getFaction())) {
            FactionDoctrineAPI doctrine = fleet.getFaction().getDoctrine();
            if (doctrine.getOfficerQuality() > 5) {
                log.debug("----------------------------------------------------");
                log.debug(String.format("Upgrading officers in fleet %s at %s:"
                        , fleet.getNameWithFactionKeepCase(), fleet.getLocation()));
                upgradeOfficerQuality(doctrine, fleet, null, 0.2f, 0.5f, OfficerUpgradeType.ADD);
                for (FleetMemberAPI member : fleet.getFleetData().getMembersInPriorityOrder()) {
                    if (member.isFighterWing() || member.isStation() || member.getCaptain().isDefault()) continue;
                    upgradeOfficerQuality(doctrine, fleet, member, 0.1f, 0.5f, OfficerUpgradeType.LEVEL);
                    upgradeOfficerQuality(doctrine, fleet, member, 0.1f, 0.5f, OfficerUpgradeType.ELITE);
                }
                upgradeOfficerQuality(doctrine, fleet, null, 0.1f, 0.25f, OfficerUpgradeType.COMMANDER);
                log.debug("----------------------------------------------------");
            }
            if (doctrine.getShipQuality() > 5) {
                log.debug("----------------------------------------------------");
                log.debug(String.format("Adding s-mods to fleet %s at %s:"
                        , fleet.getNameWithFactionKeepCase(), fleet.getLocation()));
                for (FleetMemberAPI member : fleet.getFleetData().getMembersInPriorityOrder()) {
                    if (member.isFighterWing() || member.isStation()) continue;

                    ShipVariantAPI variant = member.getVariant().clone();
                    variant.setSource(VariantSource.REFIT);

                    float sModProbability = getSModProbability(doctrine, member, dsFleetBuffsSModProb);
                    WeightedRandomPicker<String> sModdableMods = buildSModPicker(member, HullModType.ALL);
                    Random random = new Random();
                    boolean stop = false, refit = false;
                    while (!sModdableMods.isEmpty()) {
                        if (random.nextFloat() > sModProbability) stop = true;
                        if (stop) break;
                        String toSMod = sModdableMods.pickAndRemove();
                        if (member.getHullSpec().getBuiltInMods().contains(toSMod)) {
                            variant.getSModdedBuiltIns().add(toSMod);
                        } else {
                            variant.addPermaMod(toSMod, true);
                        }
                        refit = true;
                        log.debug(String.format("\t Added %s as an s-mod for %s of class %s"
                                , toSMod, member.getShipName(), member.getHullId()));
                        sModProbability *= dsFleetBuffsChainSModProb;
                    }
                    if (refit) {
                        spendUnusedOP(member, variant);
                    }
                    member.setVariant(variant, false, true);
                }
                log.debug("----------------------------------------------------");
            }
        }
        fleet.getMemoryWithoutUpdate().set(scaledByDSKey, true);
    }

    public void upgradeOfficerQuality(FactionDoctrineAPI doctrine, CampaignFleetAPI fleet, FleetMemberAPI member
            , float baseProbability, float chainProbability, OfficerUpgradeType officerUpgradeType) {
        Random random = new Random();
        if (fleet == null && member != null) fleet = member.getFleetData().getFleet();
        switch (officerUpgradeType) {
            case COMMANDER:
                // Add commander skills
                if (fleet == null) return;
                float commanderSkillProbability = getCommanderSkillProbability(doctrine, fleet, baseProbability);
                if (fleet.getCommander() != null) {
                    PersonAPI commander = fleet.getCommander();
                    WeightedRandomPicker<String> commanderSkillPicker = new WeightedRandomPicker<>();
                    for (String skillId : Global.getSettings().getSkillIds()) {
                        if (!Global.getSettings().getSkillSpec(skillId).isAdmiralSkill()
                                || commander.getStats().hasSkill(skillId)) continue;
                        commanderSkillPicker.add(skillId);
                    }
                    while (random.nextFloat() < commanderSkillProbability && !commanderSkillPicker.isEmpty()) {
                        commander.getStats().setLevel(commander.getStats().getLevel() + 1);
                        String skillToAdd = commanderSkillPicker.pickAndRemove();
                        commander.getStats().setSkillLevel(skillToAdd, 1f);
                        log.debug(String.format("\t Added commander skill %s", skillToAdd));
                        commanderSkillProbability *= chainProbability;
                    }
                }
                break;
            case ADD:
                if (fleet == null) return;
                WeightedRandomPicker<FleetMemberAPI> shipPicker = new WeightedRandomPicker<>();
                for (FleetMemberAPI fleetMember : fleet.getFleetData().getMembersInPriorityOrder()) {
                    if (fleetMember.isFighterWing() || fleetMember.isStation()) continue;
                    if (fleetMember.getCaptain().isDefault()) {
                        shipPicker.add(fleetMember, fleetMember.getDeploymentPointsCost());
                    }
                }
                int chainNumber = 0;
                while (!shipPicker.isEmpty()) {
                    PersonAPI officer = OfficerManagerEvent.createOfficer(fleet.getFaction(), 1, false);
                    member = shipPicker.pickAndRemove();
                    float addOfficerProbability = getOfficerUpgradeProbability(doctrine, member, baseProbability)
                            * (float) Math.pow(chainProbability, chainNumber);
                    if (random.nextFloat() < addOfficerProbability) {
                        member.setCaptain(officer);
                        log.debug(String.format("\t Added officer to %s of class %s", member.getShipName(), member.getHullId()));
                        chainNumber++;
                    } else {
                        break;
                    }
                }
                break;
            case LEVEL:
                if (member == null) return;
                if (!member.getCaptain().isDefault()) {
                    PersonAPI officer = member.getCaptain();
                    // Add level and skill
                    float levelOfficerProbability = getOfficerUpgradeProbability(doctrine, member, baseProbability);
                    WeightedRandomPicker<String> skillPicker = new WeightedRandomPicker<>();
                    for (String skillId : Global.getSettings().getSkillIds()) {
                        if (!Global.getSettings().getSkillSpec(skillId).isCombatOfficerSkill()
                                || officer.getStats().hasSkill(skillId)) continue;
                        skillPicker.add(skillId);
                    }
                    while (random.nextFloat() < levelOfficerProbability && !skillPicker.isEmpty()) {
                        officer.getStats().setLevel(officer.getStats().getLevel() + 1);
                        String skillToAdd = skillPicker.pickAndRemove();
                        officer.getStats().setSkillLevel(skillToAdd, 1f);
                        log.debug(String.format("\t Added %s as a skill for %s of class %s"
                                , skillToAdd, member.getShipName(), member.getHullId()));
                        levelOfficerProbability *= chainProbability;
                    }
                }
                break;
            case ELITE:
                if (member == null) return;
                if (!member.getCaptain().isDefault()) {
                    PersonAPI officer = member.getCaptain();
                    // Elitify skills
                    float eliteSkillProbability = getOfficerUpgradeProbability(doctrine, member, baseProbability);
                    WeightedRandomPicker<String> elitePicker = new WeightedRandomPicker<>();
                    for (MutableCharacterStatsAPI.SkillLevelAPI skill : officer.getStats().getSkillsCopy()) {
                        if (skill.getSkill().isCombatOfficerSkill() && skill.getLevel() == 1) {
                            elitePicker.add(skill.getSkill().getId());
                        }
                    }
                    while (random.nextFloat() < eliteSkillProbability && !elitePicker.isEmpty()) {
                        String skillToElite = elitePicker.pickAndRemove();
                        officer.getStats().setSkillLevel(skillToElite, 2f);
                        log.debug(String.format("\t Made skill %s elite for %s of class %s"
                                , skillToElite, member.getShipName(), member.getHullId()));
                        eliteSkillProbability *= chainProbability;
                    }
                }
                break;
        }

    }

    public float getCommanderSkillProbability(FactionDoctrineAPI doctrine, CampaignFleetAPI fleet, float baseProbability) {
        float commanderSkillProbability = baseProbability
                * Math.max(0f, doctrine.getOfficerQuality() - 5)
                * (1f + 0.1f * fleet.getCommanderStats().getLevel() / 6f)
                * (1f + 0.1f * fleet.getFleetSizeCount() / Global.getSettings().getInt("maxShipsInAIFleet"));
        return Math.max(0f, commanderSkillProbability);
    }

    public float getOfficerUpgradeProbability(FactionDoctrineAPI doctrine, FleetMemberAPI member, float baseProbability) {
        float officerUpgradeProbability = baseProbability
                * Math.max(0f, doctrine.getOfficerQuality() - 5);
        if (member.getCaptain() != null) {
            officerUpgradeProbability *= 1f - 0.1f * member.getCaptain().getStats().getLevel();
        }
        if (member.isCivilian()) officerUpgradeProbability *= 0.3f;
        if (member.isFlagship()) officerUpgradeProbability *= 1.2f;
        switch (member.getVariant().getHullSize()) {
            case CRUISER:
                officerUpgradeProbability *= 0.8f;
                break;
            case DESTROYER:
                officerUpgradeProbability *= 0.6f;
                break;
            case FRIGATE:
                officerUpgradeProbability *= 0.4f;
                break;
            default:
                break;
        }
        return officerUpgradeProbability;
    }

    /**
     * Currently only adds vents and caps
     * todo Learn how to add hullmods
     *
     * @param member
     */
    public void spendUnusedOP(FleetMemberAPI member, ShipVariantAPI variant) {
        if (variant == null) variant = member.getVariant();
        MutableCharacterStatsAPI stats = (member.getFleetCommanderForStats() != null) ? member.getFleetCommanderForStats().getFleetCommanderStats() : null;
        int maxVents = getMaxVents(variant.getHullSize(), stats);
        int maxCaps = getMaxCaps(variant.getHullSize(), stats);

        float fluxCapacity = member.getStats().getFluxCapacity().getModifiedValue();
        float fluxDissipation = member.getStats().getFluxDissipation().getModifiedValue();
        float ventProbability = fluxDissipation / ((FLUX_PER_CAPACITOR / DISSIPATION_PER_VENT) * fluxDissipation + fluxCapacity);
        Random random = new Random();

        int oldVents = variant.getNumFluxVents();
        int oldCaps = variant.getNumFluxCapacitors();
        int unusedOP = variant.getUnusedOP(stats);
        while (unusedOP > 0) {
            if (variant.getNumFluxVents() < maxVents && variant.getNumFluxCapacitors() < maxCaps) {
                if (random.nextFloat() < ventProbability) {
                    variant.setNumFluxVents(variant.getNumFluxVents() + 1);
                } else {
                    variant.setNumFluxCapacitors(variant.getNumFluxCapacitors() + 1);
                }
            } else if (variant.getNumFluxVents() < maxVents) {
                variant.setNumFluxVents(variant.getNumFluxVents() + 1);
            } else if (variant.getNumFluxCapacitors() < maxCaps) {
                variant.setNumFluxCapacitors(variant.getNumFluxCapacitors() + 1);
            }
            int newUnusedOP = variant.getUnusedOP(stats);
            if (newUnusedOP == unusedOP) break;
            unusedOP = newUnusedOP;
        }
        int newVents = variant.getNumFluxVents();
        int newCaps = variant.getNumFluxCapacitors();
        log.debug(String.format("\t Added: V=%s, C=%s to fill unused OP", newVents - oldVents, newCaps - oldCaps));
    }

    public float getSModProbability(FactionDoctrineAPI doctrine, FleetMemberAPI member, float baseProbability) {
        float sModProbability = baseProbability * Math.max(0f, doctrine.getShipQuality() - 5);
        if (member.isCivilian()) sModProbability *= 0.5f;
        if (member.isFlagship()) sModProbability *= 1.4f;
        switch (member.getVariant().getHullSize()) {
            case CAPITAL_SHIP:
                sModProbability *= 1.4f;
                break;
            case DESTROYER:
                sModProbability *= 0.6f;
                break;
            case FRIGATE:
                sModProbability *= 0.2f;
                break;
            default:
                break;
        }
        return Math.max(0f, sModProbability);
    }

    public WeightedRandomPicker<String> buildSModPicker(FleetMemberAPI member, HullModType type) {
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
        ShipAPI.HullSize hullSize = member.getVariant().getHullSize();
        switch (type) {
            case ALL:
                for (String hullModId : member.getHullSpec().getBuiltInMods()) {
                    HullModSpecAPI hullModSpec = Global.getSettings().getHullModSpec(hullModId);
                    // no s-mod effect or already s-modded or suppressed or penalty mod means skip
                    if (hullModSpec.getSModDescription(hullSize).equals("")
                            || hullModSpec.getTags().contains(Tags.HULLMOD_NO_BUILD_IN)
                            || hullModSpec.getEffect().isSModEffectAPenalty()
                            || hullModSpec.isHidden()
                            || hullModSpec.isHiddenEverywhere()
                            || member.getVariant().getSModdedBuiltIns().contains(hullModId)
                            || member.getVariant().getSuppressedMods().contains(hullModId)) continue;
                    picker.add(hullModId);
                }
                for (String hullModId : member.getVariant().getPermaMods()) {
                    HullModSpecAPI hullModSpec = Global.getSettings().getHullModSpec(hullModId);
                    // no s-mod effect or already s-modded or suppressed or penalty mod means skip
                    if (hullModSpec.getSModDescription(hullSize).equals("")
                            || hullModSpec.getEffect().isSModEffectAPenalty()
                            || hullModSpec.isHidden()
                            || hullModSpec.isHiddenEverywhere()
                            || member.getVariant().getSMods().contains(hullModId)
                            || member.getVariant().getSuppressedMods().contains(hullModId)) continue;
                    picker.add(hullModId);
                }
                for (String hullModId : member.getVariant().getNonBuiltInHullmods()) {
                    HullModSpecAPI hullModSpec = Global.getSettings().getHullModSpec(hullModId);
                    // already s-modded or permamod or suppressed or penalty mod means skip
                    if (hullModSpec.getEffect().isSModEffectAPenalty()
                            || hullModSpec.isHidden()
                            || hullModSpec.isHiddenEverywhere()
                            || member.getVariant().getSMods().contains(hullModId)
                            || member.getVariant().getPermaMods().contains(hullModId)
                            || member.getVariant().getSuppressedMods().contains(hullModId)) continue;
                    picker.add(hullModId);
                }
                break;
            case BUILT_IN_MOD:
                for (String hullModId : member.getHullSpec().getBuiltInMods()) {
                    HullModSpecAPI hullModSpec = Global.getSettings().getHullModSpec(hullModId);
                    // no s-mod effect or already s-modded or suppressed or penalty mod means skip
                    if (hullModSpec.getSModDescription(hullSize).equals("")
                            || hullModSpec.getTags().contains(Tags.HULLMOD_NO_BUILD_IN)
                            || hullModSpec.getEffect().isSModEffectAPenalty()
                            || hullModSpec.isHidden()
                            || hullModSpec.isHiddenEverywhere()
                            || member.getVariant().getSModdedBuiltIns().contains(hullModId)
                            || member.getVariant().getSuppressedMods().contains(hullModId)) continue;
                    picker.add(hullModId);
                }
                break;
            case PERMA_MOD:
                for (String hullModId : member.getVariant().getPermaMods()) {
                    HullModSpecAPI hullModSpec = Global.getSettings().getHullModSpec(hullModId);
                    // no s-mod effect or already s-modded or suppressed or penalty mod means skip
                    if (hullModSpec.getSModDescription(hullSize).equals("")
                            || hullModSpec.getEffect().isSModEffectAPenalty()
                            || hullModSpec.isHidden()
                            || hullModSpec.isHiddenEverywhere()
                            || member.getVariant().getSMods().contains(hullModId)
                            || member.getVariant().getSuppressedMods().contains(hullModId)) continue;
                    picker.add(hullModId);
                }
                break;
            case HULL_MOD:
                for (String hullModId : member.getVariant().getNonBuiltInHullmods()) {
                    HullModSpecAPI hullModSpec = Global.getSettings().getHullModSpec(hullModId);
                    // already s-modded or permamod or suppressed or penalty mod means skip
                    if (hullModSpec.getEffect().isSModEffectAPenalty()
                            || hullModSpec.isHidden()
                            || hullModSpec.isHiddenEverywhere()
                            || member.getVariant().getSMods().contains(hullModId)
                            || member.getVariant().getPermaMods().contains(hullModId)
                            || member.getVariant().getSuppressedMods().contains(hullModId)) continue;
                    picker.add(hullModId);
                }
                break;
        }
        return picker;
    }
}