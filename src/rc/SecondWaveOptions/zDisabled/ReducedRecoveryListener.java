package rc.SecondWaveOptions.zDisabled;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BuffManagerAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import org.apache.log4j.Logger;

@Deprecated
public class ReducedRecoveryListener extends BaseCampaignEventListener {

    Logger log = Global.getLogger(this.getClass());

    public ReducedRecoveryListener() {
        super(false);
    }

    public static class ReducedRecoveryBuff implements BuffManagerAPI.Buff {
        private String id;
        private float mult;

        public ReducedRecoveryBuff(String id, float mult) {
            this.id = id;
            this.mult = mult;
        }

        @Override
        public void apply(final FleetMemberAPI member) {
            member.getStats().getDynamic().getStat(Stats.INDIVIDUAL_SHIP_RECOVERY_MOD).modifyMult(getId(), getMult());
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public boolean isExpired() {
            return false;
        }

        public float getMult() {
            return mult;
        }

        @Override
        public void advance(final float days) {
        }
    }

    @Override
    public void reportFleetSpawned(final CampaignFleetAPI fleet) {
        if (fleet.isPlayerFleet()) return;
        for (FleetMemberAPI member : fleet.getMembersWithFightersCopy()) {
            if (member.isFighterWing()) continue;
            member.getStats().getDynamic().getStat(Stats.INDIVIDUAL_SHIP_RECOVERY_MOD).modifyMult(this.getClass().getName(),0f);
            ReducedRecoveryBuff buff = new ReducedRecoveryBuff(this.getClass().getName(), 0.0000001f);
            member.getBuffManager().addBuff(buff);
            log.debug(String.format("Added buff %s to %s",buff.getId(), member.getShipName()));
        }
    }
}