package dev.aibots.core;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;

/** Rekord SRV (_minecraft._tcp), tak jak robi to zwykła gra. */
final class SrvLookup {
    private SrvLookup() {}

    record Target(String host, int port) {}

    static Target resolve(String host, int port, boolean explicitPort) {
        if (!explicitPort) {
            try {
                Hashtable<String, String> env = new Hashtable<>();
                env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
                env.put("java.naming.provider.url", "dns:");
                env.put("com.sun.jndi.dns.timeout.initial", "3000");
                env.put("com.sun.jndi.dns.timeout.retries", "1");
                Attributes attrs = new InitialDirContext(env)
                        .getAttributes("_minecraft._tcp." + host, new String[]{"SRV"});
                Attribute a = attrs.get("srv");
                if (a != null && a.size() > 0) {
                    String[] parts = a.get(0).toString().split(" ");
                    String h = parts[3];
                    if (h.endsWith(".")) h = h.substring(0, h.length() - 1);
                    return new Target(h, Integer.parseInt(parts[2]));
                }
            } catch (Exception ignored) {
                // brak SRV - zwykły adres
            }
        }
        return new Target(host, port);
    }
}
