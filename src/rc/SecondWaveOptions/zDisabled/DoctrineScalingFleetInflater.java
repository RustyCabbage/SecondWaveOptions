package rc.SecondWaveOptions.zDisabled;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetInflater;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.plugins.AutofitPlugin;

import java.util.List;

@Deprecated
public class DoctrineScalingFleetInflater implements FleetInflater, AutofitPlugin.AutofitPluginDelegate {

    @Override
    public void fitFighterInSlot(final int index, final AutofitPlugin.AvailableFighter fighter, final ShipVariantAPI variant) {

    }

    @Override
    public void clearFighterSlot(final int index, final ShipVariantAPI variant) {

    }

    @Override
    public void fitWeaponInSlot(final WeaponSlotAPI slot, final AutofitPlugin.AvailableWeapon weapon, final ShipVariantAPI variant) {

    }

    @Override
    public void clearWeaponSlot(final WeaponSlotAPI slot, final ShipVariantAPI variant) {

    }

    @Override
    public List<AutofitPlugin.AvailableWeapon> getAvailableWeapons() {
        return null;
    }

    @Override
    public List<AutofitPlugin.AvailableFighter> getAvailableFighters() {
        return null;
    }

    @Override
    public boolean isPriority(final WeaponSpecAPI weapon) {
        return false;
    }

    @Override
    public boolean isPriority(final FighterWingSpecAPI wing) {
        return false;
    }

    @Override
    public List<String> getAvailableHullmods() {
        return null;
    }

    @Override
    public void syncUIWithVariant(final ShipVariantAPI variant) {

    }

    @Override
    public ShipAPI getShip() {
        return null;
    }

    @Override
    public FactionAPI getFaction() {
        return null;
    }

    @Override
    public boolean isAllowSlightRandomization() {
        return false;
    }

    @Override
    public boolean isPlayerCampaignRefit() {
        return false;
    }

    @Override
    public boolean canAddRemoveHullmodInPlayerCampaignRefit(final String modId) {
        return false;
    }

    @Override
    public void inflate(final CampaignFleetAPI fleet) {

    }

    @Override
    public boolean removeAfterInflating() {
        return false;
    }

    @Override
    public void setRemoveAfterInflating(final boolean removeAfterInflating) {

    }

    @Override
    public Object getParams() {
        return null;
    }

    @Override
    public float getQuality() {
        return 0;
    }

    @Override
    public void setQuality(final float quality) {

    }
}