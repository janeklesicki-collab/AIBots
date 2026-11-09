package dev.aibots.core;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Bot bez okna gry: własne połączenie z serwerem (protokół 1.21.4), bez dodatkowych programów.
 * Krok 1: wchodzi na serwer (offline-mode), odpowiada na keep-alive, wykonuje komendy
 * po wejściu i opcjonalnie chodzi.
 */
public final class McBot {
    public enum State { WAITING, CONNECTING, ONLINE, FAILED, STOPPED }

    private enum Phase { LOGIN, CONFIG, PLAY }

    private static final class Fail extends RuntimeException {
        Fail(String m) { super(m); }
    }

    private final String name;
    private final String host;
    private final int port;
    private final boolean explicitPort;
    private final long startDelayMs;

    private volatile State state = State.WAITING;
    private volatile String status = "W kolejce";
    private volatile boolean running = true;
    private volatile boolean walking = false;
    private volatile Phase phase = Phase.LOGIN;
    private volatile int compression = -1;
    private volatile boolean joined = false;

    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private final Object outLock = new Object();

    private final Object posLock = new Object();
    private boolean havePos;
    private double x, y, z;
    private float yaw, pitch;

    private final ConcurrentLinkedQueue<String> pending = new ConcurrentLinkedQueue<>();
    private volatile long nextCommandAt;
    private final Random rnd = new Random();
    private int ticks, walkTicks, pauseTicks;
    private float walkYaw;

    public McBot(String name, String host, int port, boolean explicitPort, String commands, long startDelayMs) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.explicitPort = explicitPort;
        this.startDelayMs = startDelayMs;
        if (commands != null) {
            for (String c : commands.split(";")) {
                c = c.trim();
                if (!c.isEmpty()) pending.add(c);
            }
        }
    }

    public String name() { return name; }
    public State state() { return state; }
    public String status() { return status; }
    public boolean isWalking() { return walking; }
    public void setWalking(boolean w) { walking = w; }

    public void start() {
        Thread t = new Thread(this::runLoop, "aibot-" + name);
        t.setDaemon(true);
        t.start();
        Thread k = new Thread(this::tickLoop, "aibot-tick-" + name);
        k.setDaemon(true);
        k.start();
    }

    public void stop() {
        running = false;
        if (state != State.FAILED) {
            state = State.STOPPED;
            status = "Zatrzymany";
        }
        closeQuietly();
    }

    // ---------------------------------------------------------------- połączenie

    private void runLoop() {
        try {
            long until = System.currentTimeMillis() + startDelayMs;
            while (running && System.currentTimeMillis() < until) {
                status = "W kolejce (" + ((until - System.currentTimeMillis()) / 1000 + 1) + " s)";
                Thread.sleep(250);
            }
            if (!running) return;

            state = State.CONNECTING;
            status = "Łączenie…";
            SrvLookup.Target t = SrvLookup.resolve(host, port, explicitPort);
            socket = new Socket();
            socket.connect(new InetSocketAddress(t.host(), t.port()), 8000);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(30000);
            in = new BufferedInputStream(socket.getInputStream());
            out = new BufferedOutputStream(socket.getOutputStream());

            send(new PacketOut(0x00).varInt(Ids.PROTOCOL).string(t.host()).u16(t.port()).varInt(2));
            send(new PacketOut(Ids.LC_HELLO).string(name).uuid(offlineUuid(name)));
            status = "Logowanie…";

            while (running) {
                PacketIn p = readPacket();
                int id = p.varInt();
                switch (phase) {
                    case LOGIN -> handleLogin(id, p);
                    case CONFIG -> handleConfig(id, p);
                    case PLAY -> handlePlay(id, p);
                }
            }
        } catch (Fail f) {
            fail(f.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (SocketTimeoutException e) {
            if (running) fail("Przekroczono czas (timeout)");
        } catch (IOException e) {
            if (running) fail("Rozłączono: " + e.getMessage());
        } catch (RuntimeException e) {
            if (running) fail("Błąd protokołu: " + e.getMessage());
        } finally {
            closeQuietly();
            if (state != State.FAILED) {
                state = State.STOPPED;
                if (!"Zatrzymany".equals(status)) status = "Rozłączony";
            }
        }
    }

    private void fail(String msg) {
        state = State.FAILED;
        status = msg;
        running = false;
        closeQuietly();
    }

    private void closeQuietly() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
    }

    // ---------------------------------------------------------------- fazy

    private void handleLogin(int id, PacketIn p) throws IOException {
        switch (id) {
            case Ids.L_DISCONNECT -> throw new Fail("Wyrzucony: " + Disconnects.fromJson(p.string()));
            case Ids.L_ENCRYPTION -> throw new Fail("Serwer premium (online-mode) – bot bez konta nie wejdzie");
            case Ids.L_COMPRESSION -> compression = p.varInt();
            case Ids.L_PLUGIN_REQ -> {
                int mid = p.varInt();
                send(new PacketOut(Ids.LC_PLUGIN_RESP).varInt(mid).bool(false));
            }
            case Ids.L_SUCCESS -> {
                send(new PacketOut(Ids.LC_ACK));
                phase = Phase.CONFIG;
                status = "Konfiguracja…";
                send(new PacketOut(Ids.CC_INFO)
                        .string("pl_PL").u8(2).varInt(0).bool(true).u8(0x7F)
                        .varInt(1).bool(false).bool(true).varInt(2));
            }
            default -> { }
        }
    }

    private void handleConfig(int id, PacketIn p) throws IOException {
        switch (id) {
            case Ids.CS_COOKIE_REQ -> send(new PacketOut(Ids.CC_COOKIE_RESP).string(p.string()).bool(false));
            case Ids.CS_DISCONNECT -> throw new Fail("Wyrzucony: " + Disconnects.fromNbt(p.rest()));
            case Ids.CS_KEEPALIVE -> send(new PacketOut(Ids.CC_KEEPALIVE).i64(p.i64()));
            case Ids.CS_PING -> send(new PacketOut(Ids.CC_PONG).i32(p.i32()));
            case Ids.CS_KNOWN_PACKS -> send(new PacketOut(Ids.CC_KNOWN_PACKS).varInt(0));
            case Ids.CS_ADD_PACK -> {
                long most = p.i64(), least = p.i64();
                UUID uuid = new UUID(most, least);
                for (int result : new int[]{3, 4, 0}) { // przyjęty, pobrany, załadowany
                    send(new PacketOut(Ids.CC_PACK_RESP).uuid(uuid).varInt(result));
                }
            }
            case Ids.CS_FINISH -> {
                send(new PacketOut(Ids.CC_FINISH_ACK));
                phase = Phase.PLAY;
                status = "Wchodzenie…";
            }
            default -> { }
        }
    }

    private void handlePlay(int id, PacketIn p) throws IOException {
        switch (id) {
            case Ids.PS_DISCONNECT -> throw new Fail("Wyrzucony: " + Disconnects.fromNbt(p.rest()));
            case Ids.PS_KEEPALIVE -> send(new PacketOut(Ids.PC_KEEPALIVE).i64(p.i64()));
            case Ids.PS_LOGIN -> {
                if (!joined) {
                    joined = true;
                    state = State.ONLINE;
                    status = "Na serwerze";
                    nextCommandAt = System.currentTimeMillis() + 2500;
                }
            }
            case Ids.PS_SYNC_POS -> {
                int tp = p.varInt();
                double nx = p.f64(), ny = p.f64(), nz = p.f64();
                p.f64(); p.f64(); p.f64(); // prędkość
                float nyaw = p.f32(), npitch = p.f32();
                int fl = p.i32();
                synchronized (posLock) {
                    x = (fl & 0x01) != 0 ? x + nx : nx;
                    y = (fl & 0x02) != 0 ? y + ny : ny;
                    z = (fl & 0x04) != 0 ? z + nz : nz;
                    yaw = (fl & 0x08) != 0 ? yaw + nyaw : nyaw;
                    pitch = (fl & 0x10) != 0 ? pitch + npitch : npitch;
                    havePos = true;
                }
                send(new PacketOut(Ids.PC_CONFIRM_TP).varInt(tp));
                sendPosRot();
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- tick (co 50 ms)

    private void tickLoop() {
        try {
            while (running) {
                Thread.sleep(50);
                if (!joined || phase != Phase.PLAY) continue;
                try {
                    tick();
                } catch (IOException e) {
                    return;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void tick() throws IOException {
        long now = System.currentTimeMillis();
        if (!pending.isEmpty() && now >= nextCommandAt) {
            sendChat(pending.poll());
            nextCommandAt = now + 1500;
        }

        boolean have;
        synchronized (posLock) {
            have = havePos;
        }
        if (!have) return;
        ticks++;

        if (walking) {
            if (pauseTicks > 0) {
                pauseTicks--;
            } else {
                if (walkTicks <= 0) {
                    walkYaw = rnd.nextFloat() * 360f;
                    walkTicks = 40 + rnd.nextInt(80);
                    if (rnd.nextInt(3) == 0) pauseTicks = 20 + rnd.nextInt(40);
                }
                walkTicks--;
                double r = Math.toRadians(walkYaw);
                synchronized (posLock) {
                    x += -Math.sin(r) * 0.2;
                    z += Math.cos(r) * 0.2;
                    yaw = walkYaw;
                }
            }
            sendPosRot();
        } else if (ticks % 20 == 0) {
            sendPosRot();
        }
    }

    private void sendPosRot() throws IOException {
        double px, py, pz;
        float pyaw, ppitch;
        synchronized (posLock) {
            px = x; py = y; pz = z; pyaw = yaw; ppitch = pitch;
        }
        send(new PacketOut(Ids.PC_POS_ROT).f64(px).f64(py).f64(pz).f32(pyaw).f32(ppitch).u8(1));
    }

    private void sendChat(String c) throws IOException {
        if (c.startsWith("/")) {
            send(new PacketOut(Ids.PC_CHAT_CMD).string(c.substring(1)));
        } else {
            send(new PacketOut(Ids.PC_CHAT).string(c)
                    .i64(System.currentTimeMillis()).i64(rnd.nextLong())
                    .bool(false).varInt(0).u8(0).u8(0).u8(0));
        }
    }

    // ---------------------------------------------------------------- ramki sieciowe

    private void send(PacketOut p) throws IOException {
        byte[] body = p.toBytes();
        synchronized (outLock) {
            int th = compression;
            if (th < 0) {
                writeVarInt(out, body.length);
                out.write(body);
            } else if (body.length < th) {
                writeVarInt(out, body.length + 1);
                out.write(0);
                out.write(body);
            } else {
                Deflater d = new Deflater();
                d.setInput(body);
                d.finish();
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                while (!d.finished()) {
                    int n = d.deflate(buf);
                    bo.write(buf, 0, n);
                }
                d.end();
                byte[] comp = bo.toByteArray();
                writeVarInt(out, varIntSize(body.length) + comp.length);
                writeVarInt(out, body.length);
                out.write(comp);
            }
            out.flush();
        }
    }

    private PacketIn readPacket() throws IOException {
        int len = readVarInt(in);
        if (len < 0 || len > 8_000_000) throw new IOException("zła długość pakietu");
        byte[] raw = in.readNBytes(len);
        if (raw.length != len) throw new EOFException("koniec połączenia");
        if (compression < 0) return new PacketIn(raw);

        PacketIn h = new PacketIn(raw);
        int dataLen = h.varInt();
        byte[] rest = h.rest();
        if (dataLen == 0) return new PacketIn(rest);
        if (dataLen < 0 || dataLen > 8_000_000) throw new IOException("zła długość po dekompresji");
        Inflater inf = new Inflater();
        try {
            inf.setInput(rest);
            byte[] buf = new byte[dataLen];
            int n = 0;
            while (n < dataLen) {
                int c = inf.inflate(buf, n, dataLen - n);
                if (c == 0 && (inf.finished() || inf.needsInput() || inf.needsDictionary())) break;
                n += c;
            }
            if (n != dataLen) throw new IOException("błąd dekompresji");
            return new PacketIn(buf);
        } catch (DataFormatException e) {
            throw new IOException("błąd dekompresji", e);
        } finally {
            inf.end();
        }
    }

    private static int readVarInt(InputStream s) throws IOException {
        int v = 0, shift = 0;
        while (true) {
            int b = s.read();
            if (b < 0) throw new EOFException("koniec połączenia");
            v |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return v;
            shift += 7;
            if (shift >= 35) throw new IOException("zły VarInt");
        }
    }

    private static void writeVarInt(OutputStream s, int v) throws IOException {
        while ((v & ~0x7F) != 0) {
            s.write((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        s.write(v);
    }

    private static int varIntSize(int v) {
        int n = 1;
        while ((v & ~0x7F) != 0) {
            n++;
            v >>>= 7;
        }
        return n;
    }

    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }
}
