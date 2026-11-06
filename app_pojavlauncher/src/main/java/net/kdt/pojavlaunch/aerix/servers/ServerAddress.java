package net.kdt.pojavlaunch.aerix.servers;

import java.net.IDN;
import java.util.Locale;
import java.util.regex.Pattern;

/** A Minecraft Java server address; URLs and unbracketed IPv6 literals are not accepted. */
public final class ServerAddress {
    public final String host;
    public final int port;
    public final boolean explicitPort;
    public final boolean ipv6;
    private ServerAddress(String host, int port, boolean explicitPort, boolean ipv6) {
        this.host = host; this.port = port; this.explicitPort = explicitPort; this.ipv6 = ipv6;
    }
    public static ServerAddress parse(String input) {
        if (input == null || input.length() > 300 || !input.equals(input.trim()) || input.isEmpty()) return null;
        String host, portText = null;
        boolean ipv6 = input.startsWith("[");
        if (ipv6) {
            int end = input.indexOf(']');
            if (end < 2) return null;
            host = input.substring(1, end);
            if (!host.contains(":") || !host.matches("[0-9a-fA-F:.]+")) return null;
            try { if (!(java.net.InetAddress.getByName(host) instanceof java.net.Inet6Address)) return null; }
            catch (java.net.UnknownHostException e) { return null; }
            if (end + 1 < input.length()) {
                if (input.charAt(end + 1) != ':') return null;
                portText = input.substring(end + 2);
            }
        } else {
            int colon = input.indexOf(':');
            if (colon >= 0) {
                if (colon != input.lastIndexOf(':')) return null;
                host = input.substring(0, colon); portText = input.substring(colon + 1);
            } else host = input;
            try { host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT); }
            catch (IllegalArgumentException e) { return null; }
            if (host.length() > 253 || host.isEmpty()) return null;
            String[] labels = host.split("\\.", -1);
            for (String label : labels) {
                if (label.isEmpty() || label.length() > 63 ||
                    !Pattern.matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?", label)) return null;
            }
        }
        int port = 25565;
        if (portText != null) {
            if (!portText.matches("[0-9]{1,5}")) return null;
            port = Integer.parseInt(portText);
            if (port < 1 || port > 65535) return null;
        }
        return new ServerAddress(host, port, portText != null, ipv6);
    }
    public boolean shouldResolveSrv() {
        return !explicitPort && !ipv6 && !host.equals("localhost") && !host.matches("[0-9.]+");
    }
    public String canonical() {
        return (ipv6 ? "[" + host + "]" : host) + (explicitPort ? ":" + port : "");
    }
}
