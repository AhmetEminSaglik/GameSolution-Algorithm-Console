package printarray;

public class PrintArray {
    StringFormat stringFormat = new StringFormat();

    public void printArray(String array[]) {
        for (int i = 0; i < array.length; i++) {

            System.out.print(array[i]);
        }
    }

    public void printMultipleArray(int array[][]) {
        System.out.println(stringFormat.getStringFormatArray(array));

    }

    public void printMultipleArrayBoolean(boolean array[][]) {

        for (int i = array.length - 1; i >= 0; i--) {
            for (int j = 0; j < array[i].length; j++) {
                if (array[i][j]) {

                    System.out.print("[" + i + " ][" + j + "]  : ");
                    System.out.print(array[i][j]);
                }
            }
        }
    }

}
