package gg.grumble.core.utils;

public class StringUtils {
    public static String toOrdinal(int number) {
        if (number <= 0) return Integer.toString(number);

        int mod100 = number % 100;

        if (mod100 >= 11 && mod100 <= 13) {
            return number + "th";
        }

        return switch (number % 10) {
            case 1 -> number + "st";
            case 2 -> number + "nd";
            case 3 -> number + "rd";
            default -> number + "th";
        };
    }

}
