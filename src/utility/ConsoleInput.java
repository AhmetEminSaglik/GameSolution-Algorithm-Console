package utility;

import java.util.Scanner;

/**
 * System.in icin TEK, paylasilan Scanner. Her satir okumada {@code new Scanner(System.in)}
 * yaratmak riskli: bir onceki Scanner akistan fazladan byte tamponlamis olabilir, o Scanner
 * atilinca o veri kaybolur (ozellikle piped/redirected girdide). Ozellikle bu proje bastan
 * tekrar tekrar calisip (bkz. Main dongusu) birden fazla soruyu art arda sordugu icin
 * TEK Scanner sart.
 */
public final class ConsoleInput {

    private static final Scanner SCANNER = new Scanner(System.in);

    private ConsoleInput() {
    }

    public static String readLine() {
        return SCANNER.nextLine();
    }
}
