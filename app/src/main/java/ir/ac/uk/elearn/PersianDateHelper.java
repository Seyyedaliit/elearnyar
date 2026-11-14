package ir.ac.uk.elearn;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class PersianDateHelper {

    private static final String[] PERSIAN_MONTHS = {
            "فروردین", "اردیبهشت", "خرداد",
            "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر",
            "دی", "بهمن", "اسفند"
    };

    private static final SimpleDateFormat API_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    public static Date parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        try {
            return API_FORMAT.parse(dateStr.trim());
        } catch (ParseException e) {
            try {
                SimpleDateFormat alt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                return alt.parse(dateStr.trim());
            } catch (Exception ex) {
                return null;
            }
        }
    }

    public static String toPersianDate(String dateStr) {
        Date date = parseDate(dateStr);
        if (date == null) {
            return dateStr != null ? dateStr : "";
        }
        return toPersianDate(date);
    }

    public static String toPersianDate(Date date) {
        if (date == null) return "";
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);

        int gYear = cal.get(Calendar.YEAR);
        int gMonth = cal.get(Calendar.MONTH) + 1;
        int gDay = cal.get(Calendar.DAY_OF_MONTH);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);

        int[] jalali = gregorianToJalali(gYear, gMonth, gDay);
        int jYear = jalali[0];
        int jMonth = jalali[1];
        int jDay = jalali[2];

        String monthName = (jMonth >= 1 && jMonth <= 12) ? PERSIAN_MONTHS[jMonth - 1] : "";
        String timeStr = String.format(Locale.US, "%02d:%02d", hour, minute);

        return toPersianDigits(jDay + " " + monthName + " " + jYear + " ساعت " + timeStr);
    }

    public static String getRemainingTime(String dateStr) {
        Date date = parseDate(dateStr);
        if (date == null) return "";
        long now = System.currentTimeMillis();
        long diff = date.getTime() - now;

        if (diff <= 0) {
            return "مهلت پایان یافته";
        }

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            long remHours = hours % 24;
            if (remHours > 0) {
                return toPersianDigits(days + " روز و " + remHours + " ساعت مانده");
            } else {
                return toPersianDigits(days + " روز مانده");
            }
        } else if (hours > 0) {
            long remMin = minutes % 60;
            return toPersianDigits(hours + " ساعت و " + remMin + " دقیقه مانده");
        } else if (minutes > 0) {
            return toPersianDigits(minutes + " دقیقه مانده!");
        } else {
            return "کمتر از ۱ دقیقه مانده!";
        }
    }

    public static boolean isExpired(String dateStr) {
        Date date = parseDate(dateStr);
        if (date == null) return false;
        return date.getTime() <= System.currentTimeMillis();
    }

    /**
     * Accurate JDF Gregorian to Jalali converter algorithm
     */
    public static int[] gregorianToJalali(int gy, int gm, int gd) {
        int[] g_d_m = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
        int gy2 = (gm > 2) ? (gy + 1) : gy;
        int days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + g_d_m[gm - 1];
        int jy = -1595 + (33 * (days / 12053));
        days %= 12053;
        jy += 4 * (days / 1461);
        days %= 1461;
        if (days > 365) {
            jy += (days - 1) / 365;
            days = (days - 1) % 365;
        }
        int jm, jd;
        if (days < 186) {
            jm = 1 + (days / 31);
            jd = 1 + (days % 31);
        } else {
            jm = 7 + ((days - 186) / 30);
            jd = 1 + ((days - 186) % 30);
        }
        return new int[]{jy, jm, jd};
    }

    public static String toPersianDigits(String text) {
        if (text == null) return "";
        char[] persianDigits = {'۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹'};
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c >= '0' && c <= '9') {
                sb.append(persianDigits[c - '0']);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
