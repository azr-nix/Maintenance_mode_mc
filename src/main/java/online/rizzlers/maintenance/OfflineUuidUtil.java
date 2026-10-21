package online.rizzlers.maintenance;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public final class OfflineUuidUtil {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{1,16}$");

    private OfflineUuidUtil() {}

    /**
     * Computes the standard Minecraft offline-mode UUID for a given username.
     * In offline/cracked environments (including Velocity + LimboAuth), offline UUIDs
     * are deterministically generated using MD5: UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)).
     */
    public static UUID getOfflineUuid(String username) {
        Objects.requireNonNull(username, "username cannot be null");
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Parses an input string as either a standard UUID (with or without dashes)
     * or a username. If a username, generates the offline UUID.
     */
    public static UUID parseOrResolve(String input) {
        Objects.requireNonNull(input, "input cannot be null");
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Input cannot be empty.");
        }

        // Check standard UUID format (36 chars with dashes)
        if (trimmed.length() == 36 && trimmed.contains("-")) {
            return UUID.fromString(trimmed);
        }

        // Check 32-char undashed UUID format
        if (trimmed.length() == 32 && trimmed.matches("^[0-9a-fA-F]{32}$")) {
            String formatted = trimmed.replaceFirst(
                    "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                    "$1-$2-$3-$4-$5");
            return UUID.fromString(formatted);
        }

        // Check username format
        if (USERNAME_PATTERN.matcher(trimmed).matches()) {
            return getOfflineUuid(trimmed);
        }

        // Try standard UUID parsing as fallback
        return UUID.fromString(trimmed);
    }

    public static boolean isValidUsername(String username) {
        return username != null && USERNAME_PATTERN.matcher(username.trim()).matches();
    }
}
