package rc.SecondWaveOptions.DoctrineScaling;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.rules.HasMemory;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import rc.SecondWaveOptions.SecondWaveOptionsModPlugin;

import static rc.SecondWaveOptions.SecondWaveOptionsModPlugin.*;

public class BookOfGrudgesListener extends BaseCampaignEventListener {

    static Logger log = Global.getLogger(BookOfGrudgesListener.class);
    public String nextGrudgeKey = String.format("$%s_%s", SecondWaveOptionsModPlugin.MOD_PREFIX, "nextGrudge"); //V is integer
    public String savedGrudgesKey = String.format("$%s_%s", SecondWaveOptionsModPlugin.MOD_PREFIX, "savedGrudges"); //V is float
    public String notificationText = "Due to previous transgressions, your reputation with %s has been reduced to %s/100 (%s)";
    public String notificationText2 = "%s/100 (%s)";

    public enum GrudgeMode {
        STANDARD,
        TRUE
    }

    public BookOfGrudgesListener() {
        super(false);
        log.setLevel(Level.INFO);
    }

    @Override
    public void reportPlayerReputationChange(final String faction, final float delta) {
        FactionAPI factionAPI = Global.getSector().getFaction(faction);
        if (delta < 0) {
            boolean noDuration = bogDuration == -1;
            addGrudge(factionAPI, delta, noDuration, bogDuration);
        }
        float totalDelta = addRepFromGrudges(factionAPI);
        float maxRel = getMaxRep(totalDelta);
        RepLevel repLevel = getMaxRepLevel(totalDelta);
        GrudgeMode grudgeMode = GrudgeMode.valueOf(bogGrudgeMode);
        switch (grudgeMode) {
            case STANDARD:
                boolean changedRep = factionAPI.getRelToPlayer().ensureAtBest(repLevel);
                if (changedRep) {
                    Global.getSector().getCampaignUI().addMessage(
                            String.format(notificationText, factionAPI.getDisplayNameWithArticle()
                                    , repLevel.getDisplayName().toLowerCase()), Misc.getNegativeHighlightColor(),
                            factionAPI.getDisplayNameWithArticle(),
                            repLevel.getDisplayName().toLowerCase(),
                            factionAPI.getColor(),
                            Misc.getRelColor(maxRel));
                }
                break;
            case TRUE:
                if (factionAPI.getRelToPlayer().getRel() > maxRel) {
                    factionAPI.getRelToPlayer().setRel(maxRel);
                    Global.getSector().getCampaignUI().addMessage(
                            String.format(notificationText, factionAPI.getDisplayNameWithArticle()
                                    , Misc.getRoundedValue(maxRel * 100), repLevel.getDisplayName().toLowerCase()),
                            Misc.getNegativeHighlightColor(),
                            factionAPI.getDisplayNameWithArticle(),
                            String.format(notificationText2, Misc.getRoundedValue(maxRel * 100), repLevel.getDisplayName().toLowerCase()),
                            factionAPI.getColor(),
                            Misc.getRelColor(maxRel));
                }
                break;
        }
    }

    @Override
    public void reportPlayerReputationChange(final PersonAPI person, final float delta) {
        if (!bogIncludePersons) return;
        if (delta < 0) {
            boolean noDuration = bogDuration == -1;
            addGrudge(person, delta, noDuration, bogDuration);
        }
        float totalDelta = addRepFromGrudges(person);
        float maxRel = getMaxRep(totalDelta);
        RepLevel repLevel = getMaxRepLevel(totalDelta);
        GrudgeMode grudgeMode = GrudgeMode.valueOf(bogGrudgeMode);
        switch (grudgeMode) {
            case STANDARD:
                boolean changedRep = person.getRelToPlayer().ensureAtBest(repLevel);
                if (changedRep) {
                    Global.getSector().getCampaignUI().addMessage(
                            String.format(notificationText, person.getNameString()
                                    , repLevel.getDisplayName().toLowerCase()), Misc.getNegativeHighlightColor(),
                            person.getNameString(),
                            repLevel.getDisplayName().toLowerCase(),
                            person.getFaction().getColor(),
                            Misc.getRelColor(maxRel));
                }
                break;
            case TRUE:
                if (person.getRelToPlayer().getRel() > maxRel) {
                    person.getRelToPlayer().setRel(maxRel);
                    Global.getSector().getCampaignUI().addMessage(
                            String.format(notificationText, person.getNameString()
                                    , Misc.getRoundedValue(maxRel * 100), repLevel.getDisplayName().toLowerCase()),
                            Misc.getNegativeHighlightColor(),
                            person.getNameString(),
                            String.format(notificationText2, Misc.getRoundedValue(maxRel * 100), repLevel.getDisplayName().toLowerCase()),
                            person.getFaction().getColor(),
                            Misc.getRelColor(maxRel));
                }
                break;
        }
    }

    public void addGrudge(Object object, float delta, boolean noDuration, float durationInDays) {
        if (object instanceof HasMemory) {
            HasMemory objectWithMemory = (HasMemory) object;
            MemoryAPI memory = objectWithMemory.getMemoryWithoutUpdate();
            int nextGrudge = (memory.contains(nextGrudgeKey)) ? memory.getInt(nextGrudgeKey) + 1 : 1;
            if (noDuration) {
                memory.set(String.format("%s_%s", savedGrudgesKey, nextGrudge), delta);
            } else {
                memory.set(String.format("%s_%s", savedGrudgesKey, nextGrudge), delta, durationInDays);
            }
            memory.set(nextGrudgeKey, nextGrudge);
            log.debug(String.format("Grudge %s: delta=%s", nextGrudge, delta));
        }
    }

    // this will be a non-positive number
    public float addRepFromGrudges(Object object) {
        float totalDelta = 0;
        if (object instanceof HasMemory) {
            HasMemory objectWithMemory = (HasMemory) object;
            MemoryAPI memory = objectWithMemory.getMemoryWithoutUpdate();
            if (memory.contains(nextGrudgeKey)) {
                int latestGrudge = memory.getInt(nextGrudgeKey);
                for (int i = latestGrudge; i > 0; i--) {
                    if (!memory.contains(String.format("%s_%s", savedGrudgesKey, i))) break;
                    totalDelta += memory.getFloat(String.format("%s_%s", savedGrudgesKey, i));
                }
            }
        }
        log.debug(String.format("Total delta from grudges: %s", totalDelta));
        return totalDelta;
    }

    public float getMaxRep(float delta) {
        float maxRepAsFloat = Misc.getRoundedValueFloat(1f + delta);
        log.debug(String.format("Max reputation: %s", maxRepAsFloat));
        return maxRepAsFloat;
    }

    public RepLevel getMaxRepLevel(float delta) {
        float maxRepAsFloat = 1f + delta;
        RepLevel maxRepLevel = RepLevel.getLevelFor(maxRepAsFloat);
        log.debug(String.format("Max reputation: %s, rep level %s", maxRepAsFloat, maxRepLevel));
        return RepLevel.getLevelFor(maxRepAsFloat);
    }
}