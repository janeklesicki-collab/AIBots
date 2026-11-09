package dev.aibots.core;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Budowanie pakietu Minecrafta: najpierw ID, potem dane. */
public final class PacketOut {
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream(32);

    public PacketOut(int id) {
        varInt(id);
    }

    private void b(int v) {
        bytes.write(v & 0xFF);
    }

    public PacketOut varInt(int v) {
        while ((v & ~0x7F) != 0) {
            b((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        b(v);
        return this;
    }

    public PacketOut u8(int v) { b(v); return this; }
    public PacketOut bool(boolean v) { b(v ? 1 : 0); return this; }
    public PacketOut u16(int v) { b(v >>> 8); b(v); return this; }
    public PacketOut i32(int v) { b(v >>> 24); b(v >>> 16); b(v >>> 8); b(v); return this; }

    public PacketOut i64(long v) {
        for (int s = 56; s >= 0; s -= 8) b((int) (v >>> s));
        return this;
    }

    public PacketOut f32(float f) { return i32(Float.floatToIntBits(f)); }
    public PacketOut f64(double d) { return i64(Double.doubleToLongBits(d)); }
    public PacketOut uuid(UUID u) { return i64(u.getMostSignificantBits()).i64(u.getLeastSignificantBits()); }

    public PacketOut string(String s) {
        byte[] d = s.getBytes(StandardCharsets.UTF_8);
        varInt(d.length);
        bytes.write(d, 0, d.length);
        return this;
    }

    public byte[] toBytes() {
        return bytes.toByteArray();
    }
}
