package com.ndsg.voicehisaab;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a spoken sentence (Hindi, English or mixed; Roman or Devanagari script)
 * into an entry: type, amount, category, payment mode and date.
 *
 * Examples: "Petrol 500 UPI se", "salary 85000 mili", "kal dawai pe 1200 kharch kiye",
 * "बिजली का बिल 3450 भरा", "do hazar paanch sau ka ration", "5 tarikh ko kiraya 15000 diya".
 */
public final class VoiceParser {
    private VoiceParser() {}

    public static final class Result {
        public boolean income;
        public boolean typeDetected;
        public long paise;
        public String category;
        public String mode;      // null when not spoken
        public LocalDate date;
        public String note;
    }

    // ---------- vocabulary ----------
    private static final Map<String, Double> UNITS = new HashMap<>();
    private static final Map<String, Double> MULTS = new HashMap<>();
    private static final Map<String, Double> PREFIX = new HashMap<>();
    private static final Set<String> CONNECTORS = new HashSet<>();
    private static final Set<String> CURRENCY = new HashSet<>();
    private static final Map<String, Integer> MONTHS = new HashMap<>();
    private static final Set<String> TARIKH = new HashSet<>();
    private static final Map<String, Integer> INCOME_WORDS = new HashMap<>();
    private static final Map<String, Integer> EXPENSE_WORDS = new HashMap<>();
    private static final Set<String> FOR_MARKERS = new HashSet<>();
    private static final Map<String, List<String>> CAT_IN = new LinkedHashMap<>();
    private static final Map<String, List<String>> CAT_OUT = new LinkedHashMap<>();
    private static final Map<String, List<String>> MODE_KW = new LinkedHashMap<>();

    private static final Pattern DIGITS = Pattern.compile("^\\d+(\\.\\d+)?$");
    private static final Pattern K_SUFFIX = Pattern.compile("^(\\d+(?:\\.\\d+)?)k$");
    private static final Pattern ORDINAL = Pattern.compile("^(\\d{1,2})(st|nd|rd|th)$");

    private static void put(Map<String, Double> m, double v, String... words) {
        for (String w : words) m.put(norm(w), v);
    }

    private static void put(Set<String> s, String... words) {
        for (String w : words) s.add(norm(w));
    }

    private static void weight(Map<String, Integer> m, int w, String... words) {
        for (String x : words) m.put(norm(x), w);
    }

    static {
        String[] en = {"", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven",
                "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"};
        for (int i = 1; i < en.length; i++) UNITS.put(en[i], (double) i);
        String[] tens = {"twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"};
        for (int i = 0; i < tens.length; i++) UNITS.put(tens[i], (double) (20 + i * 10));

        put(UNITS, 1, "ek", "एक");
        put(UNITS, 2, "do", "दो");
        put(UNITS, 3, "teen", "तीन");
        put(UNITS, 4, "char", "chaar", "चार");
        put(UNITS, 5, "paanch", "panch", "paach", "पांच", "पाँच");
        put(UNITS, 6, "chhe", "chhah", "chah", "chhey", "छह", "छः", "छे");
        put(UNITS, 7, "saat", "सात");
        put(UNITS, 8, "aath", "आठ");
        put(UNITS, 9, "nau", "नौ");
        put(UNITS, 10, "das", "dus", "दस");
        put(UNITS, 11, "gyarah", "gyara", "ग्यारह");
        put(UNITS, 12, "barah", "baara", "बारह");
        put(UNITS, 13, "terah", "तेरह");
        put(UNITS, 14, "chaudah", "chauda", "चौदह");
        put(UNITS, 15, "pandrah", "pandra", "पंद्रह", "पन्द्रह");
        put(UNITS, 16, "solah", "sola", "सोलह");
        put(UNITS, 17, "satrah", "सत्रह");
        put(UNITS, 18, "atharah", "athara", "अठारह");
        put(UNITS, 19, "unnis", "उन्नीस");
        put(UNITS, 20, "bees", "बीस");
        put(UNITS, 25, "pachchis", "pachis", "पच्चीस");
        put(UNITS, 30, "tees", "तीस");
        put(UNITS, 40, "chalis", "chaalis", "चालीस");
        put(UNITS, 50, "pachas", "pachaas", "पचास");
        put(UNITS, 60, "साठ");
        put(UNITS, 70, "sattar", "सत्तर");
        put(UNITS, 75, "pachattar", "पचहत्तर");
        put(UNITS, 80, "assi", "अस्सी");
        put(UNITS, 90, "nabbe", "नब्बे");
        put(UNITS, 1.5, "dedh", "dedhh", "डेढ़", "डेढ");
        put(UNITS, 2.5, "dhai", "dhaai", "ढाई");

        put(MULTS, 100, "hundred", "sau", "सौ");
        put(MULTS, 1000, "thousand", "hazar", "hazaar", "hajar", "hajaar", "हजार", "हज़ार");
        put(MULTS, 100000, "lakh", "lakhs", "lac", "lacs", "laakh", "लाख");
        put(MULTS, 10000000, "crore", "crores", "karod", "karor", "करोड़", "करोड");

        put(PREFIX, 0.25, "sava", "savaa", "सवा");
        put(PREFIX, 0.5, "sadhe", "saadhe", "sade", "saade", "साढ़े", "साढे");
        put(PREFIX, -0.25, "paune", "पौने");

        put(CONNECTORS, "and", "aur", "और");
        put(CURRENCY, "rs", "rupees", "rupee", "rupay", "rupaye", "rupaiye", "rupaiya", "rupiya", "rupya", "rupye",
                "rupe", "inr", "रुपये", "रुपए", "रुपया", "रुपय", "रु", "रूपये", "रूपए", "रूपया", "रुपयों", "रुपैया");

        String[][] months = {
                {"january", "jan", "जनवरी"}, {"february", "feb", "फरवरी"}, {"march", "मार्च"},
                {"april", "apr", "अप्रैल"}, {"may", "मई"}, {"june", "जून"}, {"july", "जुलाई"},
                {"august", "aug", "अगस्त"}, {"september", "sept", "sep", "सितंबर", "सितम्बर"},
                {"october", "oct", "अक्टूबर", "अक्तूबर"}, {"november", "nov", "नवंबर", "नवम्बर"},
                {"december", "dec", "दिसंबर", "दिसम्बर"}};
        for (int i = 0; i < months.length; i++) for (String m : months[i]) MONTHS.put(norm(m), i + 1);
        put(TARIKH, "tarikh", "tareekh", "tarik", "tareek", "date", "तारीख", "तारीक", "तारिख");

        // Type words. Verbs weigh 2, nouns 1, so "salary di" = expense and "payment mili" = income.
        weight(INCOME_WORDS, 2, "received", "receive", "recieved", "got", "credited", "earned", "earn",
                "mila", "mile", "mili", "milaa", "aaya", "aaye", "aayi", "aya", "aye", "ayi", "aai", "prapt", "kamaya",
                "kamaye", "मिला", "मिले", "मिली", "आया", "आए", "आये", "आई", "आयी", "प्राप्त", "कमाए", "कमाये", "कमाया");
        weight(INCOME_WORDS, 1, "income", "aamdani", "amdani", "aay", "kamai", "kamaai", "credit",
                "आमदनी", "आय", "कमाई", "इनकम", "क्रेडिट");
        weight(EXPENSE_WORDS, 2, "spent", "spend", "paid", "pay", "payed", "bought", "buy", "purchased", "gave",
                "given", "kharch", "kharche", "kharchi", "diya", "diye", "di", "dia", "bhara", "bhare", "bhari",
                "kharida", "kharidi", "kharide", "liya", "liye", "li", "lia", "chukaya", "chukaye", "debited",
                "खर्च", "खर्चे", "खर्ची", "दिया", "दिए", "दिये", "दी", "भरा", "भरे", "भरी", "खरीदा", "खरीदी", "खरीदे",
                "लिया", "लिए", "लिये", "ली", "चुकाया", "चुकाए", "भुगतान");
        weight(EXPENSE_WORDS, 1, "expense", "expenses", "kharcha", "vyay", "payment", "cost", "debit",
                "व्यय", "पेमेंट", "खर्चा", "डेबिट");
        put(FOR_MARKERS, "ke", "ki", "के", "की");

        for (Map.Entry<String, String[]> e : Categories.INCOME.entrySet()) CAT_IN.put(e.getKey(), normAll(e.getValue()));
        for (Map.Entry<String, String[]> e : Categories.EXPENSE.entrySet()) CAT_OUT.put(e.getKey(), normAll(e.getValue()));
        for (Map.Entry<String, String[]> e : Categories.MODES.entrySet()) MODE_KW.put(e.getKey(), normAll(e.getValue()));
    }

    private static List<String> normAll(String[] a) {
        List<String> out = new ArrayList<>();
        for (String s : a) out.add(norm(s));
        return out;
    }

    /** Lower-case, unify Hindi spellings (nukta, chandrabindu), Devanagari digits, strip punctuation. */
    static String norm(String s) {
        if (s == null) return "";
        String t = Normalizer.normalize(s, Normalizer.Form.NFD);
        t = t.replace("़", "").replace('ँ', 'ं');
        t = Normalizer.normalize(t, Normalizer.Form.NFC);
        StringBuilder sb = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= '०' && c <= '९') c = (char) ('0' + (c - '०'));
            sb.append(c);
        }
        t = sb.toString().toLowerCase(Locale.ROOT);
        t = t.replace("₹", " rs ");
        t = t.replaceAll("(?<=\\d),(?=\\d)", "");
        t = t.replaceAll("[^\\p{L}\\p{M}\\p{N}.]", " ");
        t = t.replaceAll("(?<!\\d)\\.|\\.(?!\\d)", " ");
        // "500rs" / "rs500" → separate tokens
        t = t.replaceAll("(\\d)(rs|rupees|rupaye)\\b", "$1 $2");
        t = t.replaceAll("\\b(rs)(\\d)", "$1 $2");
        return t.trim().replaceAll("\\s+", " ");
    }

    private static final class Group {
        int start, end;
        double value;
        boolean currency;
        boolean date;
    }

    public static Result parse(String heard, LocalDate today) {
        Result r = new Result();
        String text = norm(heard);
        String[] tok = text.isEmpty() ? new String[0] : text.split(" ");
        String padded = " " + text + " ";

        // ----- type -----
        int in = 0, out = 0;
        for (int i = 0; i < tok.length; i++) {
            String t = tok[i];
            Integer wi = INCOME_WORDS.get(t);
            if (wi != null) in += wi;
            Integer wo = EXPENSE_WORDS.get(t);
            if (wo != null) {
                boolean isFor = (t.equals("liye") || t.equals(norm("लिए")) || t.equals(norm("लिये")))
                        && i > 0 && FOR_MARKERS.contains(tok[i - 1]);
                if (!isFor) out += wo;
            }
        }
        r.income = in > out;
        r.typeDetected = in != out;

        // ----- numbers & dates -----
        List<Group> groups = numberGroups(tok);
        LocalDate explicitDate = null;
        for (int i = 0; i < tok.length; i++) {
            Matcher om = ORDINAL.matcher(tok[i]);
            if (om.matches()) {
                Integer mon = i + 1 < tok.length ? MONTHS.get(tok[i + 1]) : null;
                explicitDate = makeDate(Integer.parseInt(om.group(1)), mon, today);
            }
        }
        for (Group g : groups) {
            String before = g.start > 0 ? tok[g.start - 1] : "";
            String after = g.end + 1 < tok.length ? tok[g.end + 1] : "";
            g.currency = CURRENCY.contains(before) || CURRENCY.contains(after);
            boolean whole = g.value == Math.rint(g.value);
            if (whole && g.value >= 1 && g.value <= 31 && (MONTHS.containsKey(after) || TARIKH.contains(after))) {
                g.date = true;
                LocalDate d = makeDate((int) g.value, MONTHS.get(after), today);
                if (d != null) explicitDate = d;
            } else if (whole && g.value >= 1 && g.value <= 31 && MONTHS.containsKey(before) && !g.currency) {
                g.date = true;
                LocalDate d = makeDate((int) g.value, MONTHS.get(before), today);
                if (d != null) explicitDate = d;
            } else if (whole && g.value >= 1990 && g.value <= 2100 && !g.currency && g.start > 0
                    && (MONTHS.containsKey(before) || isDateGroupEnd(groups, g.start - 2))) {
                g.date = true; // a year after "3 november"
            }
        }
        Group best = null;
        for (Group g : groups) {
            if (g.date) continue;
            if (best == null) { best = g; continue; }
            if (g.currency && !best.currency) best = g;
            else if (g.currency == best.currency && g.value > best.value) best = g;
        }
        if (best != null) r.paise = Math.round(best.value * 100);

        // ----- date -----
        LocalDate d = today;
        if (padded.contains(" day before yesterday ") || has(tok, "parso", "parson", "parsoon", "परसों", "परसो")) {
            d = today.minusDays(2);
        } else if (has(tok, "kal", "yesterday", "कल", "kall")) {
            d = today.minusDays(1);
        }
        if (explicitDate != null) d = explicitDate;
        r.date = d;

        // ----- category & mode -----
        r.category = bestMatch(r.income ? CAT_IN : CAT_OUT, tok, padded);
        if (r.category == null) r.category = Categories.other(r.income);
        r.mode = bestMatch(MODE_KW, tok, padded);

        String note = heard == null ? "" : heard.trim().replaceAll("\\s+", " ");
        if (!note.isEmpty()) note = note.substring(0, 1).toUpperCase(Locale.ROOT) + note.substring(1);
        r.note = note;
        return r;
    }

    private static boolean isDateGroupEnd(List<Group> groups, int idx) {
        for (Group g : groups) if (g.date && g.end == idx) return true;
        return false;
    }

    private static LocalDate makeDate(int day, Integer month, LocalDate today) {
        try {
            if (month == null) {
                LocalDate d = today.withDayOfMonth(Math.min(day, today.lengthOfMonth()));
                if (day > today.lengthOfMonth()) return null;
                if (d.isAfter(today)) {
                    LocalDate prev = today.minusMonths(1);
                    if (day > prev.lengthOfMonth()) return null;
                    d = prev.withDayOfMonth(day);
                }
                return d;
            }
            LocalDate d = LocalDate.of(today.getYear(), month, day);
            if (d.isAfter(today)) d = d.minusYears(1);
            return d;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean has(String[] tok, String... words) {
        for (String w : words) {
            String n = norm(w);
            for (String t : tok) if (t.equals(n)) return true;
        }
        return false;
    }

    private static boolean isDev(String s) {
        return !s.isEmpty() && s.charAt(0) >= 'ऀ' && s.charAt(0) <= 'ॿ';
    }

    private static boolean matches(String kw, String[] tok, String padded) {
        if (kw.isEmpty()) return false;
        if (kw.indexOf(' ') >= 0) return padded.contains(" " + kw + " ");
        boolean dev = isDev(kw);
        for (String t : tok) {
            if (t.equals(kw)) return true;
            if (dev) {
                if (kw.length() >= 3 && t.startsWith(kw)) return true;
            } else if (t.equals(kw + "s") || t.equals(kw + "es")) {
                return true;
            }
        }
        return false;
    }

    private static String bestMatch(Map<String, List<String>> map, String[] tok, String padded) {
        String best = null;
        int bestScore = 0;
        for (Map.Entry<String, List<String>> e : map.entrySet()) {
            int score = 0;
            for (String kw : e.getValue()) if (matches(kw, tok, padded)) score++;
            if (score > bestScore) { bestScore = score; best = e.getKey(); }
        }
        return best;
    }

    // ---------- number grouping ----------
    private static final int K_NONE = 0, K_NUM = 1, K_TENS_WORD = 2, K_MULT = 3;

    private static List<Group> numberGroups(String[] tok) {
        List<Group> groups = new ArrayList<>();
        boolean inGroup = false;
        double total = 0, cur = 0;
        int last = K_NONE, start = -1, end = -1;
        Double pending = null;

        for (int i = 0; i <= tok.length; i++) {
            String t = i < tok.length ? tok[i] : null;
            Double num = null, mult = null;
            boolean word = false;
            if (t != null) {
                if (DIGITS.matcher(t).matches()) {
                    num = Double.parseDouble(t);
                } else {
                    Matcher km = K_SUFFIX.matcher(t);
                    if (km.matches()) {
                        num = Double.parseDouble(km.group(1));
                        mult = 1000.0;
                    } else if (UNITS.containsKey(t)) {
                        num = UNITS.get(t);
                        word = true;
                    } else if (MULTS.containsKey(t)) {
                        mult = MULTS.get(t);
                    } else if (PREFIX.containsKey(t)) {
                        if (inGroup && last != K_MULT) {
                            addGroup(groups, start, end, total + cur);
                            inGroup = false; total = 0; cur = 0; last = K_NONE;
                        }
                        pending = PREFIX.get(t);
                        continue;
                    } else if (CONNECTORS.contains(t) && inGroup && last == K_MULT && i + 1 < tok.length
                            && (DIGITS.matcher(tok[i + 1]).matches() || UNITS.containsKey(tok[i + 1]))) {
                        continue;
                    }
                }
            }

            if (num != null) {
                double n = num;
                if (pending != null) { n += pending; pending = null; }
                boolean additive = inGroup && (last == K_MULT
                        || (last == K_TENS_WORD && word && n < 10));
                if (inGroup && !additive) {
                    addGroup(groups, start, end, total + cur);
                    inGroup = false;
                }
                if (!inGroup) {
                    inGroup = true; total = 0; cur = 0; start = i;
                }
                cur += n;
                end = i;
                last = (word && n >= 20 && n % 10 == 0 && n < 100) ? K_TENS_WORD : K_NUM;
                if (mult == null) continue;
            }

            if (mult != null) {
                if (!inGroup) {
                    inGroup = true; total = 0; cur = 0; start = i;
                }
                double base = cur;
                if (base == 0) base = pending != null ? 1 + pending : 1;
                pending = null;
                if (mult == 100) {
                    cur = base * 100;
                } else {
                    total += base * mult;
                    cur = 0;
                }
                end = i;
                last = K_MULT;
                continue;
            }

            // non-number token (or end of input)
            pending = null;
            if (inGroup) {
                addGroup(groups, start, end, total + cur);
                inGroup = false; total = 0; cur = 0; last = K_NONE;
            }
        }
        return groups;
    }

    private static void addGroup(List<Group> groups, int start, int end, double value) {
        if (value <= 0) return;
        Group g = new Group();
        g.start = start;
        g.end = end;
        g.value = value;
        groups.add(g);
    }
}
