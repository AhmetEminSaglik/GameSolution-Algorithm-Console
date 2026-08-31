package game.gamerepo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelTest {

    @Test
    void totalSquareCount_isEdgeSquared() {
        Model model = new Model();
        model.setGameSquares(new int[5][5]);
        assertEquals(25, model.getTotalSquareCount());

        model.setGameSquares(new int[6][6]);
        assertEquals(36, model.getTotalSquareCount());
    }

    @Test
    void gameMapAreaName_isEdgeXEdge() {
        Model model = new Model();
        model.setGameSquares(new int[7][7]);
        assertEquals("7x7", model.getGameMapAreaName());
    }
}
