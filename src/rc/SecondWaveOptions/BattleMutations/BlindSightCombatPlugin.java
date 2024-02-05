package rc.SecondWaveOptions.BattleMutations;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.List;

/**
 * Ships have vastly reduced vision range
 * todo adjust based on ecm?
 */
public class BlindSightCombatPlugin extends BaseEveryFrameCombatPlugin {

    public String sourceId = "secondwaveoptions_BlindSight";
    public Logger log = Global.getLogger(this.getClass());
    private CombatEngineAPI engine;
    private final Color TRANSPARENT_COLOR = new Color(0, 0, 0, 0);

    public void init(CombatEngineAPI engine) {
        // log.debug("Enabling BlindSight");
        if (true) this.engine = engine;
        else this.engine = null;
    }

    @Override
    public void advance(final float amount, final List<InputEventAPI> events) {
        if (engine == null) return;
        if (engine.getFleetManager(FleetSide.PLAYER).getGoal().equals(FleetGoal.ESCAPE)
                || engine.getFleetManager(FleetSide.ENEMY).getGoal().equals(FleetGoal.ESCAPE)) {
            return;
        }

        /*
        if (engine.isEnemyInFullRetreat()) {
            for (ShipAPI ship : engine.getShips()) {
                ship.getMutableStats().getSightRadiusMod().unmodify(sourceId);
            }
            return;
        }
        /* */

        // reduce all ship sensor strength
        for (ShipAPI ship : engine.getShips()) {
            float sensorStrength = ship.getMutableStats().getSensorStrength().getModifiedValue();
            ship.getMutableStats().getSightRadiusMod().modifyMult(sourceId
                    , Math.max(0.25f, 0.25f + 0.075f * (sensorStrength - 30f) / 30f)); //todo configurable?
        }

        // reveal firing ships and projectiles
        for (DamagingProjectileAPI proj : engine.getProjectiles()) {
            if (proj.isFading()
                    || proj.isExpired()
                    || proj.getSource() == null
                    || proj.isFromMissile()
                    || proj instanceof MissileAPI) continue;
            revealAroundSource(proj, 50f);
            if (proj.getElapsed() < 1f) {
                CombatEntityAPI dummy = spawnDummyFromSource(proj);
                revealAroundSource(dummy, 100f);
            }
        }

        // missiles are revealed to both sides, unless they are LRMs
        for (MissileAPI missile : engine.getMissiles()) {
            if (missile.getWeaponSpec().getMaxRange() >= 3500f
                    || missile.isFading()
                    || missile.isExpired()) continue;
            revealAroundSource(missile, 100f); //todo change depending on missile parameters?
        }

        // damaging beams reveal
        for (BeamAPI beam : engine.getBeams()) {
            if (!beam.didDamageThisFrame()
                    || beam.getSource() == null) continue;
            CombatEntityAPI dummyFrom = spawnDummyAtLocation(beam.getFrom());
            CombatEntityAPI dummyTo = spawnDummyAtLocation(beam.getTo());
            revealAroundSource(dummyFrom, 50f);
            revealAroundSource(dummyTo, 50f);
        }
    }

    public void revealAroundSource(CombatEntityAPI source, float radius) {
        Vector2f location = source.getLocation();
        float trueRadius = (engine.getNebula().locationHasNebula(location.x, location.y)) ? radius / 2f : radius;
        for (int i = 0; i < 2; i++) {
            engine.getFogOfWar(i).revealAroundPoint(source
                    , location.x, location.y, trueRadius);
        }
    }

    public CombatEntityAPI spawnDummyFromSource(CombatEntityAPI source) {
        CombatEntityAPI dummy = null;
        if (source instanceof DamagingProjectileAPI) {
            DamagingProjectileAPI proj = (DamagingProjectileAPI) source;
            dummy = engine.spawnEmpArcVisual(proj.getSource().getLocation(), proj.getSource()
                    , proj.getSource().getLocation(), proj.getSource(), 0f
                    , TRANSPARENT_COLOR, TRANSPARENT_COLOR);
        }
        return dummy;
    }

    public CombatEntityAPI spawnDummyAtLocation(Vector2f location) {
        CombatEntityAPI dummy = engine.spawnEmpArcVisual(location, null, location, null
                , 0, TRANSPARENT_COLOR, TRANSPARENT_COLOR);
        return dummy;
    }
}