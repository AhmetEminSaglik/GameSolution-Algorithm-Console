package game.play;

import game.gamerepo.Model;
import printarray.PrintArray;

import java.util.ArrayList;

public class CopyModel {

    ArrayList<int[][]> modelList = new ArrayList<>();

    public int[][] copyModel(Model model) {
        int[][] values = new int[model.getGameSquares().length][model.getGameSquares().length];
        for (int i = 0; i < model.getGameSquares().length; i++) {
            for (int j = 0; j < model.getGameSquares().length; j++) {
                values[i][j] = model.getGameSquares()[i][j];
            }
        }
        return values;
    }

    void addToList(int[][] value) {
        modelList.add(value);
        System.out.println("Listeye eklendi boyutu : " + modelList.size());

    }

    public void sendModelToCompareAndAddToList(Model model) {
        int[][] values = copyModel(model);
        if (modelList.size() == 0) {
            addToList(values);
        } else {
             for (int[][] tmp : modelList) {
                if (comparedItemsAreSame(tmp, values)) {
                    System.out.println("Solution values are SAME so this solution WONT BE ADDED to list");

                    for (int i = 0; i < modelList.size(); i++) {

                        System.out.println("Same solution is in this  index : " + i);
                        new PrintArray().printMultipleArray(modelList.get(i));
                    }

                    return;
                }
            }
            addToList(values);
            System.out.println("Solution values are DIFFERENT so this solution WILL BE ADDED to list " + modelList.size());

        }


    }

    public boolean comparedItemsAreSame(int[][] v1, int[][] v2) {
        for (int i = 0; i < v1.length; i++) {
            for (int j = 0; j < v1.length; j++) {
                if (v1[i][j] != v2[i][j]) {
                    return false;
                }
            }
        }
        return true;
    }
}
