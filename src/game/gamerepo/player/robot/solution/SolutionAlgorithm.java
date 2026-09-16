package game.gamerepo.player.robot.solution;

/** BaseSolution.getSolutionCreatedOrder() degerinin okunakli karsiligi. */
public enum SolutionAlgorithm {
    FIRST, SECOND, THIRD, NONE;

    public static SolutionAlgorithm fromOrder(Integer order) {
        if (order == null) {
            return NONE;
        }
        return switch (order) {
            case 1 -> FIRST;
            case 2 -> SECOND;
            case 3 -> THIRD;
            default -> NONE;
        };
    }
}
