package rc.SecondWaveOptions.MarketCrackdowns;

import rc.SecondWaveOptions.Utils.MarketUtils;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;
import rc.SecondWaveOptions.SecondWaveOptionsModPlugin;

public class SecureMarkets {
    Logger log = Global.getLogger(this.getClass());

    public SecureMarkets() {
    }

    /*
    Only system owners can get patrols
    Hidden markets cannot get patrols or industry
    Respect max industries
     */
    public void upgradeAllMarkets() {
        for (StarSystemAPI starSystem : Global.getSector().getEconomy().getStarSystemsWithMarkets()) {
            log.debug("Star System: " + starSystem.getId());
            FactionAPI systemOwner = null;
            if (SecondWaveOptionsModPlugin.secureMarketsAddPatrols) {
                systemOwner = MarketUtils.getSystemOwner(starSystem, true);
                log.debug(starSystem + " is owned by " + systemOwner.getId());
                log.debug("----------------------------------------");
            }
            // probably don't want to remove unique versions
            for (MarketAPI market : Misc.getMarketsInLocation(starSystem)) {
                if (market.isPlayerOwned() || market.isInHyperspace() || market.getFaction().isNeutralFaction()) continue;
                if (SecondWaveOptionsModPlugin.secureMarketsAddPatrols && !market.isHidden() && market.getFaction().equals(systemOwner)) {
                    int patrolCode = addPatrolsToMarket(market, SecondWaveOptionsModPlugin.secureMarketsHighCommandSize, SecondWaveOptionsModPlugin.secureMarketsMilitaryBaseSize, SecondWaveOptionsModPlugin.secureMarketsPatrolHQSize);
                    log.debug("Added patrol to " + market.getId() + "/" + market.getName() + ", code " + patrolCode);
                }
                if (SecondWaveOptionsModPlugin.secureMarketsAddStations) {
                    int stationCode = addStationsToMarket(market, SecondWaveOptionsModPlugin.secureMarketsStarFortressSize, SecondWaveOptionsModPlugin.secureMarketsBattlestationSize, SecondWaveOptionsModPlugin.secureMarketsOrbitalStationSize);
                    log.debug("Added station to " + market.getId() + "/" + market.getName() + ", code " + stationCode);
                }
                if (SecondWaveOptionsModPlugin.secureMarketsAddDefenses) {
                    int defenseCode = addDefensesToMarket(market, SecondWaveOptionsModPlugin.secureMarketsHeavyBatteriesSize, SecondWaveOptionsModPlugin.secureMarketsGroundDefensesSize);
                    log.debug("Added defenses to " + market.getId() + "/" + market.getName() + ", code " + defenseCode);
                }
                if (SecondWaveOptionsModPlugin.secureMarketsAddHeavyInd && !market.isHidden()) {
                    int heavyIndCode = addHeavyIndustryToMarket(market, SecondWaveOptionsModPlugin.secureMarketsOrbitalWorksSize, SecondWaveOptionsModPlugin.secureMarketsHeavyIndustrySize);
                    log.debug("Added heavy industry to " + market.getId() + "/" + market.getName() + ", code " + heavyIndCode);
                }
                log.debug("----------------------------");
            }
        }

    }

    // first digit: size category; second digit: 0 if unchanged, 1 if patrol hq, 2 if military base, 3 if high command
    // negative if no industry slots but a patrol hq is present
    public int addPatrolsToMarket(MarketAPI market, int highCommandSize, int militaryBaseSize, int patrolHQSize) {
        if (market.getSize() >= highCommandSize) {
            // remove possible industries
            if (market.hasIndustry(Industries.HIGHCOMMAND)) return 30;
            if (market.hasIndustry(Industries.MILITARYBASE))
                market.removeIndustry(Industries.MILITARYBASE, null, false);
            boolean canBuildIndustry = Misc.getNumIndustries(market) < Misc.getMaxIndustries(market);
            // if industry slot is available, remove patrol HQ if present and add industry
            // otherwise add patrol HQ if not present
            if (canBuildIndustry) {
                if (market.hasIndustry(Industries.PATROLHQ)) market.removeIndustry(Industries.PATROLHQ, null, false);
                market.addIndustry(Industries.HIGHCOMMAND);
                return 33;
            } else {
                if (market.hasIndustry(Industries.PATROLHQ)) return -31;
                market.addIndustry(Industries.PATROLHQ);
                return 31;
            }
        } else if (market.getSize() >= militaryBaseSize) {
            if (market.hasIndustry(Industries.HIGHCOMMAND)) return 23;
            if (market.hasIndustry(Industries.MILITARYBASE)) return 20;
            // if industry slot is available, remove patrol HQ if present and add industry
            // otherwise add patrol HQ if not present
            boolean canBuildIndustry = Misc.getNumIndustries(market) < Misc.getMaxIndustries(market);
            if (canBuildIndustry) {
                if (market.hasIndustry(Industries.PATROLHQ)) market.removeIndustry(Industries.PATROLHQ, null, false);
                market.addIndustry(Industries.MILITARYBASE);
                return 22;
            } else {
                if (market.hasIndustry(Industries.PATROLHQ)) return -21;
                market.addIndustry(Industries.PATROLHQ);
                return 21;
            }
        } else if (market.getSize() >= patrolHQSize) {
            if (market.hasIndustry(Industries.HIGHCOMMAND)) return 13;
            if (market.hasIndustry(Industries.MILITARYBASE)) return 12;
            if (market.hasIndustry(Industries.PATROLHQ)) return 10;
            market.addIndustry(Industries.PATROLHQ);
            return 11;
        }
        return 0;
    }

    // first digit: size category; second digit: old station tier; third digit: 0 if unchanged, 1 if low-tech, 2 midline, 3 high-tech, 9 random
    public int addStationsToMarket(MarketAPI market, int starFortressSize, int battlestationSize, int orbitalStationSize) {
        Industry station = Misc.getStationIndustry(market);
        if (market.getSize() >= starFortressSize) {
            if (station != null) {
                if (station.getSpec().hasTag(Industries.TAG_STARFORTRESS)) return 330;
                if (station.getSpec().hasTag(Industries.TAG_BATTLESTATION)) {
                    // will not remove custom stations
                    switch (station.getSpec().getId()) {
                        case Industries.BATTLESTATION:
                            market.removeIndustry(station.getSpec().getId(), null, false);
                            market.addIndustry(Industries.STARFORTRESS);
                            return 321;
                        case Industries.BATTLESTATION_MID:
                            market.removeIndustry(station.getSpec().getId(), null, false);
                            market.addIndustry(Industries.STARFORTRESS_MID);
                            return 322;
                        case Industries.BATTLESTATION_HIGH:
                            market.removeIndustry(station.getSpec().getId(), null, false);
                            market.addIndustry(Industries.STARFORTRESS_HIGH);
                            return 323;
                        default:
                            return 320;
                    }
                }
                if (station.getSpec().hasTag(Industries.TAG_STATION)) {
                    switch (station.getSpec().getId()) {
                        case Industries.ORBITALSTATION:
                            market.removeIndustry(station.getSpec().getId(), null, false);
                            market.addIndustry(Industries.STARFORTRESS);
                            return 311;
                        case Industries.ORBITALSTATION_MID:
                            market.removeIndustry(station.getSpec().getId(), null, false);
                            market.addIndustry(Industries.STARFORTRESS_MID);
                            return 312;
                        case Industries.ORBITALSTATION_HIGH:
                            market.removeIndustry(station.getSpec().getId(), null, false);
                            market.addIndustry(Industries.STARFORTRESS_HIGH);
                            return 313;
                        default:
                            return 310;
                    }
                }
            } else {
                market.addIndustry(pickRandomStationId(true, false, false));
                return 309;
            }
        } else if (market.getSize() >= battlestationSize) {
            if (station != null) {
                if (station.getSpec().hasTag(Industries.TAG_STARFORTRESS)) return 230;
                if (station.getSpec().hasTag(Industries.TAG_BATTLESTATION)) return 220;
                if (station.getSpec().hasTag(Industries.TAG_STATION)) {
                    switch (station.getSpec().getId()) {
                        case Industries.ORBITALSTATION:
                            market.removeIndustry(station.getSpec().getId(), null, false);
                            market.addIndustry(Industries.BATTLESTATION);
                            return 211;
                        case Industries.ORBITALSTATION_MID:
                            market.removeIndustry(station.getSpec().getId(), null, false);
                            market.addIndustry(Industries.BATTLESTATION_MID);
                            return 212;
                        case Industries.ORBITALSTATION_HIGH:
                            market.removeIndustry(station.getSpec().getId(), null, false);
                            market.addIndustry(Industries.BATTLESTATION_HIGH);
                            return 213;
                        default:
                            return 210;
                    }
                }
            } else {
                market.addIndustry(pickRandomStationId(false, true, false));
                return 209;
            }
        } else if (market.getSize() >= orbitalStationSize) {
            if (station != null) {
                if (station.getSpec().hasTag(Industries.TAG_STARFORTRESS)) return 130;
                if (station.getSpec().hasTag(Industries.TAG_BATTLESTATION)) return 120;
                if (station.getSpec().hasTag(Industries.TAG_STATION)) return 110;
            } else {
                market.addIndustry(pickRandomStationId(false, false, true));
                return 109;
            }
        }
        return 0;
    }

    public String pickRandomStationId(boolean addStarFortress, boolean addBattleStation, boolean addOrbitalStation) {
        WeightedRandomPicker<String> randomStation = new WeightedRandomPicker<>();
        if (addStarFortress) {
            randomStation.add(Industries.STARFORTRESS);
            randomStation.add(Industries.STARFORTRESS_MID);
            randomStation.add(Industries.STARFORTRESS_HIGH);
        }
        if (addBattleStation) {
            randomStation.add(Industries.BATTLESTATION);
            randomStation.add(Industries.BATTLESTATION_MID);
            randomStation.add(Industries.BATTLESTATION_HIGH);
        }
        if (addOrbitalStation) {
            randomStation.add(Industries.ORBITALSTATION);
            randomStation.add(Industries.ORBITALSTATION_MID);
            randomStation.add(Industries.ORBITALSTATION_HIGH);
        }
        return randomStation.pick();
    }

    // first digit: size category; second digit: 0 if unchanged, 1 if ground defenses, 2 if heavy batteries
    public int addDefensesToMarket(MarketAPI market, int heavyBatteriesSize, int groundDefensesSize) {
        if (market.getSize() >= heavyBatteriesSize) {
            if (market.hasIndustry(Industries.HEAVYBATTERIES)) return 20;
            if (market.hasIndustry(Industries.GROUNDDEFENSES))
                market.removeIndustry(Industries.GROUNDDEFENSES, null, false);
            market.addIndustry(Industries.HEAVYBATTERIES);
            return 22;
        } else if (market.getSize() >= groundDefensesSize) {
            if (market.hasIndustry(Industries.HEAVYBATTERIES)) return 12;
            if (market.hasIndustry(Industries.GROUNDDEFENSES)) return 10;
            market.addIndustry(Industries.GROUNDDEFENSES);
            return 11;
        }
        return 0;
    }

    // first digit: size category; second digit: 0 if unchanged, 1 if heavy industry, 2 if orbital works
    // negative if no industry slots
    public int addHeavyIndustryToMarket(MarketAPI market, int orbitalWorksSize, int heavyIndustrySize) {
        if (market.getSize() >= orbitalWorksSize) {
            if (market.hasIndustry(Industries.ORBITALWORKS)) return 20;
            if (market.hasIndustry(Industries.HEAVYINDUSTRY))
                market.removeIndustry(Industries.HEAVYINDUSTRY, null, false);
            boolean canBuildIndustry = Misc.getNumIndustries(market) < Misc.getMaxIndustries(market);
            // if industry slot is available, add industry
            if (canBuildIndustry) {
                market.addIndustry(Industries.ORBITALWORKS);
                return 22;
            } else return -20;
        } else if (market.getSize() >= heavyIndustrySize) {
            if (market.hasIndustry(Industries.ORBITALWORKS)) return 12;
            if (market.hasIndustry(Industries.HEAVYINDUSTRY)) return 10;
            boolean canBuildIndustry = Misc.getNumIndustries(market) < Misc.getMaxIndustries(market);
            // if industry slot is available, add industry
            if (canBuildIndustry) {
                market.addIndustry(Industries.HEAVYINDUSTRY);
                return 11;
            } else return -10;
        }
        return 0;
    }
}