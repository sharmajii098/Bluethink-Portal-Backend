package EmployeeManagementSystem.service;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/** Validate the portal OAuth callback before presenting a Connect button as configured. */
public final class CalendarRedirectPolicy {
    private CalendarRedirectPolicy() {}

    /** OAuth code must return to the portal origin, not a different host/port. */
    public static boolean samePortalOrigin(String callback, String portalUrl) {
        try {
            URI redirect = URI.create(callback);
            URI portal = URI.create(portalUrl);
            if (redirect.getHost() == null || portal.getHost() == null ||
                    redirect.getUserInfo() != null || portal.getUserInfo() != null ||
                    portal.getRawQuery() != null || portal.getRawFragment() != null ||
                    (!"".equals(portal.getRawPath()) && !"/".equals(portal.getRawPath())) ||
                    !redirect.getScheme().equalsIgnoreCase(portal.getScheme())) return false;
            return redirect.getHost().equalsIgnoreCase(portal.getHost()) && redirect.getPort() == portal.getPort();
        } catch (IllegalArgumentException | NullPointerException ex) {
            return false;
        }
    }

    public static boolean valid(String raw, String provider) {
        if (!Set.of("google", "microsoft").contains(provider) || raw == null || raw.isBlank()) return false;
        try {
            URI uri = URI.create(raw);
            String host = uri.getHost();
            if (host == null || uri.getUserInfo() != null || uri.getRawQuery() != null ||
                    uri.getRawFragment() != null || uri.getRawPath() == null ||
                    !uri.getRawPath().equals("/calendar/oauth/callback/" + provider)) return false;
            boolean local = Set.of("localhost", "127.0.0.1").contains(host.toLowerCase(Locale.ROOT));
            if (!("https".equalsIgnoreCase(uri.getScheme()) ||
                    (local && "http".equalsIgnoreCase(uri.getScheme())))) return false;
            return !host.contains("%") && (uri.getPort() == -1 || uri.getPort() > 0);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
