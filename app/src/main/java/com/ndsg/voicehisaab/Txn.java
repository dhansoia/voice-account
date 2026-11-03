package com.ndsg.voicehisaab;

import java.time.LocalDate;

/** One income or expense entry. Amounts are stored in paise to avoid rounding errors. */
public class Txn {
    public long id;
    public LocalDate date;
    public boolean income;
    public long paise;
    public String category;
    public String mode;
    public String note;
    public String heard;
    public long created;

    public long signedPaise() {
        return income ? paise : -paise;
    }
}
