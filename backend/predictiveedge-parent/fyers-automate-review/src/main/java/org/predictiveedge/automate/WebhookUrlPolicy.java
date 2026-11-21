package org.predictiveedge.automate;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Accepts only HTTPS webhook URLs on FYERS hosts, so the bridge cannot be pointed at arbitrary servers. */
public final class WebhookUrlPolicy {
    private final List<String> allowedHostSuffixes;

    public WebhookUrlPolicy(List<String> allowedHostSuffixes) {
        this.allowedHostSuffixes = allowedHostSuffixes.stream().map(s -> s.strip().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty()).toList();
        if (this.allowedHostSuffixes.isEmpty()) throw new IllegalArgumentException("At least one allowed host is required");
    }

    public URI validate(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Webhook URL is required");
        URI uri;
        try {
            uri = new URI(raw.strip());
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Webhook URL is not a valid URL");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("Webhook URL must use https");
        if (uri.getUserInfo() != null) throw new IllegalArgumentException("Webhook URL must not contain credentials");
        if (uri.getPort() != -1 && uri.getPort() != 443) throw new IllegalArgumentException("Webhook URL must use the default https port");
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean allowed = allowedHostSuffixes.stream().anyMatch(s -> host.equals(s) || host.endsWith("." + s));
        if (!allowed) {
            throw new IllegalArgumentException("Webhook URL must be a FYERS Automate URL (" + String.join(", ", allowedHostSuffixes) + ")");
        }
        return uri;
    }

    public static String mask(URI uri) {
        String text = uri.toString();
        String tail = text.length() > 6 ? text.substring(text.length() - 6) : text;
        return "https://" + uri.getHost() + "/…" + tail;
    }

    public static String fingerprint(URI uri) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(uri.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
