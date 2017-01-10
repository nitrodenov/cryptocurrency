import java.util.ArrayList;
import java.util.Iterator;

/**
 * Created by nitrodenov on 28.11.16.
 */
public class R {
    public static void main(String[] args) {
        ArrayList<Integer> rr = new ArrayList<>();
        rr.add(1);
        rr.add(2);
        rr.add(3);
        rr.add(4);

        for (Iterator<Integer> iterator = rr.listIterator(); iterator.hasNext();) {
            Integer v = iterator.next();
            if (v < 4) {
                iterator.remove();
            }
        }

        for (Integer u : rr) {
            System.out.println(u);
        }
    }
}
