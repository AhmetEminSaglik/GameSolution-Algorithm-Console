package game.gamerepo;

public class Model {

    private String gameMapAreaName;
    private int gameSquares[][];
    private boolean visitedAreas[][];

    public int[][] getGameSquares() {
        return gameSquares;
    }

    public void setGameSquares(int[][] gameSquares) {
        this.gameSquares = gameSquares;
    }

    public boolean[][] getVisitedAreas() {
        return visitedAreas;
    }

    public void setVisitedAreas(boolean[][] visitedAreas) {
        this.visitedAreas = visitedAreas;
    }

    public String getGameMapAreaName() {
        return gameSquares.length + "x" + gameSquares[0].length;
    }

    /** Satir (dikey) sayisi. gameSquares[x][y] icin x'in ust siniri. */
    public int getRowCount() {
        return gameSquares.length;
    }

    /** Sutun (yatay) sayisi. gameSquares[x][y] icin y'nin ust siniri. */
    public int getColCount() {
        return gameSquares[0].length;
    }

    /** Toplam kare sayisi (satir * sutun). Oyunun bitis adimi bu degere esittir. */
    public int getTotalSquareCount() {
        return getRowCount() * getColCount();
    }
}
