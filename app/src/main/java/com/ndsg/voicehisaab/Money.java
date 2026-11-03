package com.ndsg.voicehisaab;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Indian-style money formatting and parsing (₹1,25,000.50). */
public final class Money {
    private Money() {}

    public static String fmt(long paise) {
        boolean neg = paise < 0;
        long abs = Math.abs(paise);
        long rupees = abs / 100;
        long p = abs % 100;
        String r = Long.toString(rupees);
        StringBuilder sb = new StringBuilder();
        if (r.length() > 3) {
            String head = r.substring(0, r.length() - 3);
            String tail = r.substring(r.length() - 3);
            StringBuilder h = new StringBuilder();
            int count = 0;
            for (int i = head.length() - 1; i >= 0; i--) {
                h.insert(0, head.charAt(i));
                count++;
                if (count % 2 == 0 && i > 0) h.insert(0, ',');
            }
            sb.append(h).append(',').append(tail);
        } else {
            sb.append(r);
        }
        if (p != 0) sb.append('.').append(p < 10 ? "0" + p : Long.toString(p));
        return (neg ? "-₹" : "₹") + sb;
    }

    /** Parses user-typed amount like "1,250.50"; returns -1 if invalid. */
    public static long parse(String s) {
        if (s == null) return -1;
        String t = s.replace(",", "").replace("₹", "").trim();
        if (t.isEmpty()) return -1;
        try {
            BigDecimal bd = new BigDecimal(t).movePointRight(2).setScale(0, RoundingMode.HALF_UP);
            if (bd.signum() <= 0) return -1;
            return bd.longValueExact();
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /** Plain amount for an edit box, e.g. 125000.5 → "125000.50". */
    public static String plain(long paise) {
        if (paise % 100 == 0) return Long.toString(paise / 100);
        return BigDecimal.valueOf(paise).movePointLeft(2).toPlainString();
    }
}
