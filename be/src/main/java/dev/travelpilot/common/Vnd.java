package dev.travelpilot.common;

import java.text.NumberFormat;
import java.util.Locale;

/** Vietnamese currency formatting: 1000000 -> "1.000.000đ". */
public final class Vnd {

    private static final Locale VI = Locale.forLanguageTag("vi-VN");

    private Vnd() {}

    public static String format(long amount) {
        return NumberFormat.getIntegerInstance(VI).format(amount) + "đ";
    }
}
