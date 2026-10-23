package eu.amadeos.apps.books.domain.comic;

import java.util.Comparator;
import java.util.Locale;

/** Orders names as people count: {@code page2.jpg} before {@code page10.jpg}. */
public final class NaturalOrder implements Comparator<String> {
    public static final NaturalOrder INSTANCE = new NaturalOrder();

    @Override
    public int compare(String a, String b) {
        String x = a.toLowerCase(Locale.ROOT);
        String y = b.toLowerCase(Locale.ROOT);
        int i = 0;
        int j = 0;
        while (i < x.length() && j < y.length()) {
            char c = x.charAt(i);
            char d = y.charAt(j);
            if (Character.isDigit(c) && Character.isDigit(d)) {
                int si = i;
                int sj = j;
                while (i < x.length() && Character.isDigit(x.charAt(i))) i++;
                while (j < y.length() && Character.isDigit(y.charAt(j))) j++;
                String n = x.substring(si, i).replaceFirst("^0+(?=.)", "");
                String m = y.substring(sj, j).replaceFirst("^0+(?=.)", "");
                if (n.length() != m.length()) return Integer.compare(n.length(), m.length());
                int cmp = n.compareTo(m);
                if (cmp != 0) return cmp;
            } else {
                if (c != d) return Character.compare(c, d);
                i++;
                j++;
            }
        }
        int rest = Integer.compare(x.length() - i, y.length() - j);
        return rest != 0 ? rest : a.compareTo(b);
    }
}
