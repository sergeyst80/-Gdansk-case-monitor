package pl.sergeyst.gdanskmonitor;

import java.net.URI;

public final class PortalPolicy {
    private PortalPolicy() {}
    public static boolean trusted(String url) {
        try {
            URI uri = new URI(url);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "klient.gdansk.uw.gov.pl".equalsIgnoreCase(uri.getHost())
                    && uri.getRawUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443);
        } catch (Exception e) { return false; }
    }
}
