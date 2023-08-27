package rc.SecondWaveOptions.zDisabled;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.plugins.impl.BaseAutofitPlugin;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static com.fs.starfarer.api.characters.SkillsChangeRemoveVentsCapsEffect.getMaxCaps;
import static com.fs.starfarer.api.characters.SkillsChangeRemoveVentsCapsEffect.getMaxVents;

@Deprecated
public class DoctrineScalingAutofitPlugin extends BaseAutofitPlugin {
    Logger log = Global.getLogger(DoctrineScalingAutofitPlugin.class);

    @Override
    public void doFit(final ShipVariantAPI current, final ShipVariantAPI target, final int maxSMods, final AutofitPluginDelegate delegate) {
        log.debug(String.format("Spending unused OP on variant %s for member %s", current.getHullVariantId(), delegate.getShip().getFleetMember()));
        spendUnusedOP(current, delegate);
    }

    public void spendUnusedOP(ShipVariantAPI variant, AutofitPluginDelegate delegate) {
        FleetMemberAPI member = delegate.getShip().getFleetMember();
        if (member == null) return;
        MutableCharacterStatsAPI stats = (member.getFleetCommanderForStats() != null) ? member.getFleetCommanderForStats().getFleetCommanderStats() : null;
        int unusedOP = variant.getUnusedOP(stats);
        int maxVents = getMaxVents(variant.getHullSize(), stats);
        int maxCaps = getMaxCaps(variant.getHullSize(), stats);
        int oldVents = variant.getNumFluxVents();
        int oldCaps = variant.getNumFluxCapacitors();
        List<String> newHullMods = new ArrayList<>();
        while (unusedOP > 0) {
            WeightedRandomPicker<String> availableHullMods = getAvailableHullMods(member, delegate);
            if (!availableHullMods.isEmpty()) {
                String toAdd = availableHullMods.pickAndRemove();
                variant.addMod(toAdd);
                newHullMods.add(toAdd);
                unusedOP = variant.getUnusedOP(stats);
            } else {
                break;
            }
        }
        while (unusedOP > 0) {
            if (variant.getNumFluxVents() < maxVents) {
                variant.setNumFluxVents(variant.getNumFluxVents() + 1);
                unusedOP = variant.getUnusedOP(stats);
            } else {
                break;
            }
        }
        while (unusedOP > 0) {
            if (variant.getNumFluxCapacitors() < maxCaps) {
                variant.setNumFluxCapacitors(variant.getNumFluxCapacitors() + 1);
                unusedOP = variant.getUnusedOP(stats);
            } else {
                break;
            }
        }
        int newVents = variant.getNumFluxVents();
        int newCaps = variant.getNumFluxCapacitors();
        log.debug(String.format("Added: V=%s, C=%s, H=%s",newVents-oldVents, newCaps-oldCaps, newHullMods));
    }

    public WeightedRandomPicker<String> getAvailableHullMods(FleetMemberAPI member, AutofitPluginDelegate delegate) {
        WeightedRandomPicker<String> availableHullMods = new WeightedRandomPicker<>();
        ShipVariantAPI variant = member.getVariant();
        MutableCharacterStatsAPI stats = (member.getFleetCommanderForStats() != null) ? member.getFleetCommanderForStats().getFleetCommanderStats() : null;
        int unusedOP = variant.getUnusedOP(stats);
        ShipAPI.HullSize hullSize = variant.getHullSize();
        for (String hullModId : member.getFleetData().getFleet().getFaction().getKnownHullMods()) {
            HullModSpecAPI hullModSpec = Global.getSettings().getHullModSpec(hullModId);
            if (variant.hasHullMod(hullModId)
                    || hullModSpec.isHidden()
                    || hullModSpec.isHiddenEverywhere()
                    || !hullModSpec.getEffect().isApplicableToShip(delegate.getShip())) continue;
            boolean tooExpensive = false;
            switch (hullSize) {
                case CAPITAL_SHIP:
                    if (hullModSpec.getCapitalCost() > unusedOP) tooExpensive = true;
                    break;
                case CRUISER:
                    if (hullModSpec.getCruiserCost() > unusedOP) tooExpensive = true;
                    break;
                case DESTROYER:
                    if (hullModSpec.getDestroyerCost() > unusedOP) tooExpensive = true;
                    break;
                case FRIGATE:
                    if (hullModSpec.getFrigateCost() > unusedOP) tooExpensive = true;
                    break;
                default:
                    tooExpensive = true;
                    break;
            }
            if (tooExpensive) continue;
            if (delegate.getAvailableHullmods().contains(hullModId)) availableHullMods.add(hullModId);
        }
        log.debug(String.format("Available Hullmods from delegate: %s",delegate.getAvailableHullmods()));
        log.debug(String.format("Available hullmods from method: %s", availableHullMods));
        return availableHullMods;
    }
}