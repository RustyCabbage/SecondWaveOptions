package rc.SecondWaveOptions.zDisabled;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import org.apache.log4j.Logger;

import java.util.*;

@Deprecated
public class ShipClassifier {

    Logger log = Global.getLogger(ShipClassifier.class);

    public void test() {
        List<String> DISALLOWED_TAGS = Arrays.asList(
                Tags.NO_SELL, Tags.NO_DROP, Tags.NO_BP_DROP, Tags.HULLMOD_NO_DROP_SALVAGE
                , Tags.TAG_NO_AUTOFIT);
        List<ShipHullSpecAPI.ShipTypeHints> DISALLOWED_HINTS = Arrays.asList(ShipHullSpecAPI.ShipTypeHints.STATION);
        for (ShipHullSpecAPI spec : Global.getSettings().getAllShipHullSpecs()) {
            if (spec.isDefaultDHull()
                    || spec.getHullSize() == ShipAPI.HullSize.FIGHTER
                    || !spec.hasHullName()
                    || "shuttlepod".equals(spec.getHullId())
                    || !Collections.disjoint(spec.getTags(), DISALLOWED_TAGS)
                    || !Collections.disjoint(spec.getHints(), DISALLOWED_HINTS)) continue;
            log.info(String.format("%s: %s"
                    , spec.getHullId()
                    , getWeaponSlotFillOrder(spec, 8, 2.5f, false, true)));
        }
    }

    //Warship
    //Forward Facing
    //Broadside

    //Carrier
    //Combat Carrier
    //Not Combat Carrier
    //Phase Ship

    public enum Roles {
        Assault,
        Attack,
        Elite,
        Escort,
        Missile,
        Overdriven,
        Shielded,
        Standard,
        Strike,
        Support,
        Armored
    }

    public float[] getMinAndMaxAngle(WeaponSlotAPI weaponSlot) {
        float maxAngle = (weaponSlot.getAngle() + weaponSlot.getArc() / 2f);
        float minAngle = (weaponSlot.getAngle() - weaponSlot.getArc() / 2f);
        float over360 = 0;
        while (maxAngle > 360) {
            maxAngle -= 360;
            minAngle -= 360;
            over360++;
        }
        return new float[]{minAngle, maxAngle, over360};
    }

    public String getWeaponSlotCoverage(WeaponSlotAPI weaponSlot, int numDirections) {
        return getWeaponSlotCoverage(weaponSlot, numDirections, 0f);
    }

    public String getWeaponSlotCoverage(WeaponSlotAPI weaponSlot, int numDirections, float tolerance) {
        float[] minAndMaxAngle = getMinAndMaxAngle(weaponSlot);
        float[] angles = new float[numDirections];
        int anglesCovered = 0;
        for (int i = 0; i < numDirections; i++) {
            angles[i] = (i * 360f / numDirections);
            if (minAndMaxAngle[0] <= angles[i] - tolerance && minAndMaxAngle[1] >= angles[i] + tolerance
                    || minAndMaxAngle[0] <= angles[i] - 360 - tolerance && minAndMaxAngle[1] >= angles[i] - 360 + tolerance)
                anglesCovered += Math.pow(2, i);
        }
        String binaryString = Integer.toBinaryString(anglesCovered);
        binaryString = addLeadingZeros(binaryString, numDirections);
        StringBuilder reversed = new StringBuilder(binaryString).reverse();
        return reversed.toString();
    }

    public String addLeadingZeros(String binaryString, int desiredLength) {
        int currentLength = binaryString.length();
        int zerosToAdd = desiredLength - currentLength;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < zerosToAdd; i++) {
            builder.append("0");
        }
        builder.append(binaryString);
        return builder.toString();
    }

    public boolean isMissileSlot(WeaponSlotAPI weaponSlot, boolean strict) {
        List<WeaponAPI.WeaponType> missileTypes = Arrays.asList(WeaponAPI.WeaponType.MISSILE
                , WeaponAPI.WeaponType.COMPOSITE
                , WeaponAPI.WeaponType.SYNERGY
                , WeaponAPI.WeaponType.UNIVERSAL);
        WeaponAPI.WeaponType weaponType = weaponSlot.getWeaponType();
        if (strict) return weaponType == WeaponAPI.WeaponType.MISSILE;
        return (missileTypes.contains(weaponType));
    }

    public WeaponAPI.WeaponSize getLargestWeaponSlotSize(ShipHullSpecAPI hullSpec, boolean includeMissiles, boolean strictMissiles) {
        boolean large = false, medium = false, small = false;
        for (WeaponSlotAPI weaponSlot : hullSpec.getAllWeaponSlotsCopy()) {
            if (!weaponSlot.isWeaponSlot() && !weaponSlot.isBuiltIn()) continue;
            if (!includeMissiles && isMissileSlot(weaponSlot, strictMissiles)) continue;
            WeaponAPI.WeaponSize slotSize = weaponSlot.getSlotSize();
            switch (slotSize) {
                case LARGE:
                    large = true;
                    break;
                case MEDIUM:
                    medium = true;
                    break;
                case SMALL:
                    small = true;
                    break;
            }
        }
        if (large) return WeaponAPI.WeaponSize.LARGE;
        if (medium) return WeaponAPI.WeaponSize.MEDIUM;
        if (small) return WeaponAPI.WeaponSize.SMALL;
        return null;
    }

    public String getMostImportantDirection(ShipHullSpecAPI hullSpec, int numDirections, float tolerance, boolean includeMissiles, boolean strictMissiles) {
        if (hullSpec == null) return null;
        WeaponAPI.WeaponSize largestSize = getLargestWeaponSlotSize(hullSpec, includeMissiles, strictMissiles);
        int directions = 0;
        int maxDigit = 0;
        String directionString = String.valueOf(directions);
        for (WeaponSlotAPI weaponSlot : hullSpec.getAllWeaponSlotsCopy()) {
            if ((!weaponSlot.isWeaponSlot() && !weaponSlot.isBuiltIn()) || isMissileSlot(weaponSlot, strictMissiles)) continue;
            if (weaponSlot.getSlotSize() == largestSize) {
                String weaponSlotCoverage = getWeaponSlotCoverage(weaponSlot, numDirections, tolerance);
                directions += Integer.parseInt(weaponSlotCoverage);
                // do not exceed 9
                directionString = String.valueOf(directions);
                for (int i = 0; i < directionString.length(); i++) {
                    int digit = Character.getNumericValue(directionString.charAt(i));
                    if (digit > maxDigit) maxDigit = digit;
                    if (maxDigit == 9) break;
                }
                if (maxDigit == 9) break;
            }
        }
        if (maxDigit == 0) return null;
        directionString = addLeadingZeros(directionString, numDirections);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < directionString.length(); i++) {
            int digit = Character.getNumericValue(directionString.charAt(i));
            if (digit == maxDigit) {
                builder.append("1");
            } else {
                builder.append("0");
            }
        }
        /* Reducer doesn't really work due to Prometheus 2 (or any even grouping of optimal directions)
        while (true) {
            directionString = builder.toString();
            builder.setLength(0);
            for (int i = 0; i < directionString.length(); i++) {
                int digit = Character.getNumericValue(directionString.charAt(i));
                int priorDigit = (i - 1 >= 0) ? Character.getNumericValue(directionString.charAt(i - 1)) : Character.getNumericValue(directionString.charAt(directionString.length() - 1));
                int nextDigit = Character.getNumericValue(directionString.charAt((i + 1) % directionString.length()));
                if (digit == 1 && (priorDigit + nextDigit) % 2 == 0) {
                    builder.append("1");
                } else {
                    builder.append("0");
                }
            }
            if (builder.toString().equals(directionString)) break;
        }
        /**/
        directionString = builder.toString();
        return directionString;
    }

    public List<String> getWeaponSlotFillOrder(final ShipHullSpecAPI hullSpec, final int numDirections, final float tolerance, boolean includeMissiles, boolean strictMissiles) {
        List<String> weaponSlotIds = new ArrayList<>();
        final String mostImportantDirection = getMostImportantDirection(hullSpec, numDirections, tolerance, includeMissiles, strictMissiles);
        if (mostImportantDirection == null) return weaponSlotIds;
        final int mostImportantDirectionInt = Integer.parseInt(mostImportantDirection, 2);
        for (WeaponSlotAPI weaponSlot : hullSpec.getAllWeaponSlotsCopy()) {
            if (weaponSlot.isWeaponSlot()) weaponSlotIds.add(weaponSlot.getId());
        }
        Collections.sort(weaponSlotIds, new Comparator<String>() {
            @Override
            public int compare(final String slotId1, final String slotId2) {
                WeaponSlotAPI slot1 = hullSpec.getWeaponSlotAPI(slotId1);
                WeaponSlotAPI slot2 = hullSpec.getWeaponSlotAPI(slotId2);
                int size1 = getSlotSizeAsInt(slot1);
                int size2 = getSlotSizeAsInt(slot2);
                if (size1 != size2) return Integer.compare(size2, size1);
                boolean isMissile1 = isMissileSlot(slot1, false);
                boolean isMissile2 = isMissileSlot(slot2, false);
                if (isMissile1 && !isMissile2) return 1;
                if (!isMissile1 && isMissile2) return -1;
                int coverageSlot1 = Integer.parseInt(getWeaponSlotCoverage(slot1, numDirections, tolerance), 2);
                int coverageSlot2 = Integer.parseInt(getWeaponSlotCoverage(slot2, numDirections, tolerance), 2);
                //todo prioritize based on narrowness of coverage
                if ((mostImportantDirectionInt & coverageSlot1) > 0 && (mostImportantDirectionInt & coverageSlot2) <= 0) return -1;
                if ((mostImportantDirectionInt & coverageSlot2) > 0 && (mostImportantDirectionInt & coverageSlot1) <= 0) return 1;
                return slotId1.compareTo(slotId2);
            }
        });
        return weaponSlotIds;
    }

    public int getSlotSizeAsInt(WeaponSlotAPI weaponSlot) {
        WeaponAPI.WeaponSize slotSize = weaponSlot.getSlotSize();
        switch (slotSize) {
            case LARGE:
                return 3;
            case MEDIUM:
                return 2;
            case SMALL:
                return 1;
        }
        return -1;
    }

    public Set<Roles> getPotentialShipRoles(ShipHullSpecAPI hullSpec, FactionAPI faction) {
        Set<Roles> potentialShipRoles = new HashSet<>();
        return potentialShipRoles;
    }


}