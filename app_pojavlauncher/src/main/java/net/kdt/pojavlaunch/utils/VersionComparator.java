package net.kdt.pojavlaunch.utils;

/** Compares the numeric components of ordinary dotted launcher/game version strings. */
public final class VersionComparator {
    private VersionComparator() {
    }

    /** Returns a negative value, zero, or a positive value when {@code left} is older, equal, or newer. */
    public static int compare(String left, String right) {
        long[] leftParts = components(left);
        long[] rightParts = components(right);
        int count = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < count; i++) {
            long a = i < leftParts.length ? leftParts[i] : 0;
            long b = i < rightParts.length ? rightParts[i] : 0;
            if (a < b) return -1;
            if (a > b) return 1;
        }
        return 0;
    }

    private static long[] components(String input) {
        if (input == null) return new long[0];
        String normalized = input.trim().replaceFirst("^[^0-9]*", "");
        int suffixStart = normalized.indexOf('-');
        if (suffixStart >= 0) normalized = normalized.substring(0, suffixStart);
        suffixStart = normalized.indexOf('+');
        if (suffixStart >= 0) normalized = normalized.substring(0, suffixStart);
        if (normalized.isEmpty()) return new long[0];

        String[] rawParts = normalized.split("\\.");
        long[] result = new long[rawParts.length];
        for (int i = 0; i < rawParts.length; i++) {
            String digits = rawParts[i].replaceFirst("[^0-9].*$", "");
            if (digits.isEmpty()) {
                result[i] = 0;
            } else {
                try {
                    result[i] = Long.parseLong(digits);
                } catch (NumberFormatException ignored) {
                    result[i] = Long.MAX_VALUE;
                }
            }
        }
        return result;
    }
}
