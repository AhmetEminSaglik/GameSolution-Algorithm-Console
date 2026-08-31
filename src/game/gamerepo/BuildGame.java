package game.gamerepo;

import errormessage.InvalidGameConfigException;
import game.Game;
import validation.SquareValidationGame;

import java.util.Scanner;

public class BuildGame {

    private int rowCount;
    private int colCount;
    private Game game;

    /** Konsoldan satir x sutun sorar. */
    public BuildGame() {
        int[] size = determineGridSize();
        buildGame(size[0], size[1]);
    }

    /** Kare kisayol: rowCount = colCount = edge. */
    public BuildGame(int edge) {
        buildGame(edge, edge);
    }

    public BuildGame(int rowCount, int colCount) {
        buildGame(rowCount, colCount);
    }

    private void buildGame(int rowCount, int colCount) {
        try {
            new SquareValidationGame(rowCount, colCount);
            this.rowCount = rowCount;
            this.colCount = colCount;
            game = new Game();
        } catch (InvalidGameConfigException e) {
            System.out.println(e.getMessage());
            int[] size = determineGridSize();
            buildGame(size[0], size[1]);
        }
    }

    public Game createGame() {
        game.setModel(new Model());
        game.getModel().setGameSquares(createMultipleArrayFromIntegers(rowCount, colCount));
        return game;
    }

    public Game createVisitedArea() {
        game.getModel().setVisitedAreas(buildVisitedArea(game));
        clearVisitedAreas(game);
        return game;
    }

    public int[][] createMultipleArrayFromIntegers(int verticalSquare, int horizontalSquare) {
        return new int[verticalSquare][horizontalSquare];
    }

    boolean[][] buildVisitedArea(Game game) {
        return new boolean[rowCount][colCount];
    }

    void clearVisitedAreas(Game game) {
        boolean[][] visited = game.getModel().getVisitedAreas();
        for (int i = 0; i < visited.length; i++) {
            for (int j = 0; j < visited[i].length; j++) {
                visited[i][j] = false;
            }
        }
    }

    public Game getGame() {
        return game;
    }

    public void setGame(Game game) {
        this.game = game;
    }

    /**
     * Konsoldan grid boyutu okur. Kabul edilen bicimler:
     *   "5"      -> 5x5 (kare)
     *   "5 6"    -> 5 satir 6 sutun
     *   "5x6"    -> 5 satir 6 sutun
     */
    public int[] determineGridSize() {
        System.out.print("Grid boyutu (kare icin tek sayi, dikdortgen icin \"satir sutun\" ya da \"5x6\"): ");
        String line = new Scanner(System.in).nextLine().trim().toLowerCase();
        String[] parts = line.split("[\\sx]+");
        int rows = Integer.parseInt(parts[0]);
        int cols = (parts.length > 1) ? Integer.parseInt(parts[1]) : rows;
        return new int[]{rows, cols};
    }
}
