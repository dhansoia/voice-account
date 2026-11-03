package com.ndsg.voicehisaab;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Categories and payment modes, each with the spoken keywords (Roman Hinglish,
 * English and Devanagari) that select it. Order matters: on a tie the earlier
 * category wins.
 */
public final class Categories {
    private Categories() {}

    public static final String OTHER_INCOME = "Other Income";
    public static final String OTHER_EXPENSE = "Other Expense";

    public static final Map<String, String[]> INCOME = new LinkedHashMap<>();
    public static final Map<String, String[]> EXPENSE = new LinkedHashMap<>();
    public static final Map<String, String[]> MODES = new LinkedHashMap<>();

    static {
        INCOME.put("Salary", new String[]{"salary", "salry", "tankhwah", "tankha", "tankhah", "vetan",
                "सैलरी", "वेतन", "तनख्वाह", "तनखा", "पगार"});
        INCOME.put("Business / Sales", new String[]{"business", "sale", "sales", "bikri", "dhandha", "dhanda", "vyapar",
                "client", "customer", "order", "बिक्री", "बिजनेस", "धंधा", "व्यापार", "सेल", "ग्राहक"});
        INCOME.put("Fees / Professional", new String[]{"consultancy", "consulting", "professional", "project",
                "commission", "honorarium", "mandey", "lecture", "fees", "fee", "कमीशन", "कंसल्टेंसी", "मानदेय", "प्रोजेक्ट", "फीस"});
        INCOME.put("Rent Received", new String[]{"rent", "kiraya", "kiraaya", "किराया", "किराए", "किराये", "रेंट"});
        INCOME.put("Interest / Dividend", new String[]{"interest", "byaj", "byaaj", "dividend", "fd",
                "ब्याज", "डिविडेंड", "एफडी"});
        INCOME.put("Refund / Cashback", new String[]{"refund", "cashback", "wapas", "vapas", "return",
                "रिफंड", "कैशबैक", "वापस"});
        INCOME.put("Gift Received", new String[]{"gift", "shagun", "tohfa", "गिफ्ट", "शगुन", "तोहफा"});
        INCOME.put("Loan Received", new String[]{"loan", "udhar", "udhaar", "karz", "karza", "लोन", "उधार", "कर्ज", "कर्जा"});
        INCOME.put(OTHER_INCOME, new String[]{});

        EXPENSE.put("Food & Dining", new String[]{"food", "khana", "khaana", "lunch", "dinner", "breakfast", "nashta",
                "restaurant", "hotel", "chai", "tea", "coffee", "snacks", "snack", "samosa", "pizza", "burger", "zomato",
                "swiggy", "dhaba", "mithai", "sweets", "canteen", "juice", "icecream",
                "खाना", "नाश्ता", "चाय", "लंच", "डिनर", "होटल", "रेस्टोरेंट", "समोसा", "मिठाई", "जोमैटो", "स्विगी",
                "कॉफी", "ढाबा", "पिज़्ज़ा", "पिज्जा", "जूस"});
        EXPENSE.put("Groceries", new String[]{"grocery", "groceries", "ration", "sabzi", "sabji", "vegetable", "vegetables",
                "fruit", "fruits", "doodh", "milk", "atta", "aata", "dal", "daal", "rice", "chawal", "kirana", "blinkit",
                "zepto", "bigbasket", "aloo", "pyaz", "pyaaz", "tamatar", "oil", "tel", "cheeni", "sugar", "dairy",
                "राशन", "सब्जी", "सब्जियां", "किराना", "दूध", "आटा", "दाल", "चावल", "फल", "आलू", "प्याज", "टमाटर",
                "तेल", "चीनी", "ग्रोसरी"});
        EXPENSE.put("Fuel", new String[]{"petrol", "diesel", "fuel", "cng", "पेट्रोल", "डीजल", "सीएनजी", "फ्यूल"});
        EXPENSE.put("Travel & Transport", new String[]{"travel", "taxi", "cab", "uber", "ola", "auto", "rickshaw", "bus",
                "train", "metro", "ticket", "flight", "toll", "parking", "rapido", "fastag", "safar", "yatra",
                "टैक्सी", "ऑटो", "रिक्शा", "बस", "ट्रेन", "मेट्रो", "टिकट", "टोल", "पार्किंग", "फ्लाइट", "सफर", "यात्रा",
                "फास्टैग", "भाड़ा", "भाडा", "bhada", "bhaada"});
        EXPENSE.put("Mobile & Internet", new String[]{"mobile", "recharge", "internet", "wifi", "broadband", "jio",
                "airtel", "dth", "data", "मोबाइल", "रिचार्ज", "इंटरनेट", "वाईफाई", "जियो", "एयरटेल"});
        EXPENSE.put("Bills & Utilities", new String[]{"bill", "bills", "bijli", "electricity", "light", "water", "pani",
                "paani", "gas", "cylinder", "lpg", "maintenance", "society", "बिजली", "बिल", "पानी", "गैस", "सिलेंडर",
                "मेंटेनेंस", "सोसाइटी"});
        EXPENSE.put("Rent", new String[]{"rent", "kiraya", "kiraaya", "makan", "makaan", "flat", "room", "pg",
                "किराया", "किराए", "किराये", "रेंट", "मकान", "कमरा"});
        EXPENSE.put("Medical", new String[]{"medicine", "medicines", "dawai", "dawa", "davai", "dava", "doctor", "hospital",
                "medical", "chemist", "test", "lab", "pharmacy", "clinic", "checkup", "dentist",
                "दवाई", "दवा", "डॉक्टर", "डाक्टर", "अस्पताल", "हॉस्पिटल", "मेडिकल", "जांच", "टेस्ट", "क्लीनिक"});
        EXPENSE.put("Education", new String[]{"school", "college", "fees", "fee", "tuition", "book", "books", "kitab",
                "kitaab", "coaching", "course", "exam", "stationery", "copy", "university",
                "स्कूल", "कॉलेज", "फीस", "ट्यूशन", "किताब", "किताबें", "कोचिंग", "कोर्स", "परीक्षा"});
        EXPENSE.put("Shopping", new String[]{"shopping", "kapde", "kapda", "kapre", "clothes", "shoes", "joote", "joota",
                "amazon", "flipkart", "myntra", "meesho", "mall", "shirt", "saree", "suit", "jeans", "watch", "electronics",
                "कपड़े", "कपडे", "कपड़ा", "जूते", "शॉपिंग", "अमेज़न", "अमेजन", "फ्लिपकार्ट", "साड़ी", "शर्ट"});
        EXPENSE.put("Household", new String[]{"household", "furniture", "repair", "plumber", "electrician", "cleaning",
                "utensils", "bartan", "saman", "samaan", "appliance", "carpenter", "paint",
                "मरम्मत", "फर्नीचर", "बर्तन", "सामान", "रिपेयर", "प्लंबर"});
        EXPENSE.put("Entertainment", new String[]{"movie", "film", "cinema", "netflix", "hotstar", "prime", "party",
                "outing", "picnic", "game", "फिल्म", "मूवी", "पार्टी", "पिकनिक", "सिनेमा"});
        EXPENSE.put("Staff Salary", new String[]{"staff", "driver", "maid", "servant", "naukar", "kaamwali", "kamwali",
                "bai", "mali", "maali", "guard", "chowkidar", "labour", "mazdoor", "majdoor", "wages", "salary", "vetan",
                "tankhwah", "ड्राइवर", "नौकर", "कामवाली", "माली", "मजदूर", "मजदूरी", "स्टाफ", "चौकीदार", "सैलरी", "वेतन",
                "तनख्वाह"});
        EXPENSE.put("Donation", new String[]{"donation", "daan", "mandir", "temple", "gurudwara", "church", "charity",
                "chanda", "दान", "मंदिर", "चंदा", "गुरुद्वारा", "डोनेशन"});
        EXPENSE.put("Personal Care", new String[]{"salon", "haircut", "parlour", "parlor", "barber", "cutting", "spa",
                "cosmetics", "सैलून", "नाई", "पार्लर", "कटिंग"});
        EXPENSE.put("Loan / EMI", new String[]{"emi", "loan", "installment", "kisht", "kist", "insurance", "premium",
                "lic", "किस्त", "ईएमआई", "लोन", "बीमा", "प्रीमियम", "इंश्योरेंस"});
        EXPENSE.put("Gifts Given", new String[]{"gift", "shagun", "tohfa", "present", "गिफ्ट", "शगुन", "तोहफा"});
        EXPENSE.put(OTHER_EXPENSE, new String[]{});

        MODES.put("Cash", new String[]{"cash", "nakad", "naqad", "nagad", "नकद", "नगद", "कैश"});
        MODES.put("UPI", new String[]{"upi", "gpay", "googlepay", "google pay", "phonepe", "phone pe", "paytm", "bhim",
                "qr", "scan", "यूपीआई", "फोनपे", "फोन पे", "पेटीएम", "गूगल पे", "जीपे"});
        MODES.put("Card", new String[]{"card", "credit card", "debit card", "swipe", "कार्ड"});
        MODES.put("Bank", new String[]{"bank", "neft", "rtgs", "imps", "cheque", "check", "transfer", "account", "online",
                "बैंक", "चेक", "ट्रांसफर", "खाते", "खाता"});
    }

    public static List<String> names(boolean income) {
        return new ArrayList<>((income ? INCOME : EXPENSE).keySet());
    }

    public static List<String> modes() {
        return new ArrayList<>(MODES.keySet());
    }

    public static String other(boolean income) {
        return income ? OTHER_INCOME : OTHER_EXPENSE;
    }
}
