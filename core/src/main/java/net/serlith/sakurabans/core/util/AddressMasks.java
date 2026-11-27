package net.serlith.sakurabans.core.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AddressMasks {

    private static final String IPV4_PART = "(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)";
    private static final String GROUP = "[0-9a-fA-F]{1,4}";
    private static final Pattern ADDRESS = Pattern.compile(
            "(?<![\\w.:])" + IPV4_PART + "(?:\\." + IPV4_PART + "){3}(?![\\w:]|\\.\\d)"
                    + "|(?<![\\w:])(?:" + GROUP + ":){7}" + GROUP + "(?:%\\w+)?(?![\\w:])"
                    + "|(?<![\\w:])(?:" + GROUP + "(?::" + GROUP + "){0,6})?::(?:" + GROUP
                    + "(?::" + GROUP + "){0,6})?(?:%\\w+)?(?![\\w:])");

    private AddressMasks() {
    }

    public static String censor(final String text) {
        final Matcher matcher = ADDRESS.matcher(text);
        final StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            final String found = matcher.group();
            // "::" alone is not an address anyone wrote.
            final String replacement = found.equals("::") ? found
                    : InetAddresses.parseLiteral(found.replaceFirst("%.*$", ""))
                    .map(InetAddresses::censor).orElse("*");
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
