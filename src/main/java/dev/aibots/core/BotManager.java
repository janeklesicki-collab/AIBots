package dev.aibots.core;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/** Lista botów i ich uruchamianie (z odstępem, żeby serwer nie blokował połączeń). */
public final class BotManager {
    private BotManager() {}

    private static final List<McBot> BOTS = new CopyOnWriteArrayList<>();
    private static final Random RND = new Random();
    private static long nextStartAt = 0;

    public static List<McBot> all() {
        return BOTS;
    }

    public static synchronized McBot add(String address, String nick, String commands) {
        String host = address.trim();
        int port = 25565;
        boolean explicit = false;
        int c = host.lastIndexOf(':');
        if (c > 0 && host.indexOf(':') == c) {
            try {
                port = Integer.parseInt(host.substring(c + 1));
                host = host.substring(0, c);
                explicit = true;
            } catch (NumberFormatException ignored) {
            }
        }

        String name = nick == null ? "" : nick.replaceAll("[^A-Za-z0-9_]", "");
        if (name.length() > 16) name = name.substring(0, 16);
        if (name.isEmpty()) name = randomNick();
        for (McBot b : BOTS) {
            if (b.name().equalsIgnoreCase(name)) {
                name = (name.length() > 13 ? name.substring(0, 13) : name) + RND.nextInt(900) + 100;
                break;
            }
        }

        long now = System.currentTimeMillis();
        long start = Math.max(now, nextStartAt);
        nextStartAt = start + 4000;
        McBot bot = new McBot(name, host, port, explicit, commands, start - now);
        BOTS.add(bot);
        bot.start();
        return bot;
    }

    public static void remove(McBot bot) {
        bot.stop();
        BOTS.remove(bot);
    }

    public static void stopAll() {
        for (McBot b : BOTS) b.stop();
        BOTS.clear();
    }

    private static String randomNick() {
        String cons = "bdfgklmnprstwz";
        String vow = "aeiou";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            sb.append(cons.charAt(RND.nextInt(cons.length()))).append(vow.charAt(RND.nextInt(vow.length())));
        }
        sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));
        sb.append(10 + RND.nextInt(90));
        return sb.toString();
    }
}
