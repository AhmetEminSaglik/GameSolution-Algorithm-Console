package game.gamerepo.player.robot.solution.second.navigation;

import game.location.DirectionLocation;

public class Navigation {
    int step, oneWayNumbersValue;
    DirectionLocation compulsoryLocation = null;
    boolean isExitSituationWasLocatedInThisStep;

    public int getStep() {
        return step;
    }

    public void setStep(int step) {
        this.step = step;
    }

    public int getOneWayNumbersValue() {
        return oneWayNumbersValue;
    }

    public void setOneWayNumbersValue(int oneWayNumbersValue) {
        this.oneWayNumbersValue = oneWayNumbersValue;
    }

    public DirectionLocation getCompulsoryLocation() {
        return compulsoryLocation;
    }

    public void setCompulsoryLocation(DirectionLocation compulsoryLocation) {
        this.compulsoryLocation = compulsoryLocation;
    }

    public boolean isExitSituationWasLocatedInThisStep() {
        return isExitSituationWasLocatedInThisStep;
    }

    public void setExitSituationWasLocatedInThisStep(boolean exitSituationWasLocatedInThisStep) {
        isExitSituationWasLocatedInThisStep = exitSituationWasLocatedInThisStep;
    }

    @Override
    public String toString() {
        return "Navigation{" +
                "step=" + step +
                ", oneWayNumbersValue=" + oneWayNumbersValue +
                ", compulsoryLocation=" + compulsoryLocation +
                ", isExitSituationWasLocatedInThisStep=" + isExitSituationWasLocatedInThisStep +
                '}';
    }
}
