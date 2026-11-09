package dev.aibots.core;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Czytanie pakietu Minecrafta z tablicy bajtów. */
public final class PacketIn {
    private final byte[] d;
    private int p;

    public PacketIn(byte[] d) {
        this.d = d;
    }

    public int u8() {
        if (p >= d.length) throw new IllegalStateException("za krótki pakiet");
        return d[p++] & 0xFF;
    }

    public int varInt() {
        int v = 0, s = 0;
        while (true) {
            int b = u8();
            v |= (b & 0x7F) << s;
            if ((b & 0x80) == 0) return v;
            s += 7;
            if (s >= 35) throw new IllegalStateException("zły VarInt");
        }
    }

    public int i32() { return (u8() << 24) | (u8() << 16) | (u8() << 8) | u8(); }

    public long i64() {
        long v = 0;
        for (int i = 0; i < 8; i++) v = (v << 8) | u8();
        return v;
    }

    public float f32() { return Float.intBitsToFloat(i32()); }
    public double f64() { return Double.longBitsToDouble(i64()); }

    public String string() {
        int n = varInt();
        if (n < 0 || p + n > d.length) throw new IllegalStateException("zły string");
        String s = new String(d, p, n, StandardCharsets.UTF_8);
        p += n;
        return s;
    }

    public byte[] rest() {
        return Arrays.copyOfRange(d, p, d.length);
    }
}
