package dev.aibots.core;

/**
 * Numery pakietów protokołu 769 (Minecraft 1.21.4).
 * Wszystkie w jednym miejscu, żeby łatwo je poprawić, gdyby któryś się nie zgadzał.
 */
final class Ids {
    private Ids() {}

    static final int PROTOCOL = 769;

    // LOGIN: serwer -> bot
    static final int L_DISCONNECT = 0x00, L_ENCRYPTION = 0x01, L_SUCCESS = 0x02, L_COMPRESSION = 0x03, L_PLUGIN_REQ = 0x04;
    // LOGIN: bot -> serwer
    static final int LC_HELLO = 0x00, LC_PLUGIN_RESP = 0x02, LC_ACK = 0x03;

    // CONFIG: serwer -> bot
    static final int CS_COOKIE_REQ = 0x00, CS_DISCONNECT = 0x02, CS_FINISH = 0x03, CS_KEEPALIVE = 0x04,
            CS_PING = 0x05, CS_ADD_PACK = 0x09, CS_KNOWN_PACKS = 0x0E;
    // CONFIG: bot -> serwer
    static final int CC_INFO = 0x00, CC_COOKIE_RESP = 0x01, CC_FINISH_ACK = 0x03, CC_KEEPALIVE = 0x04,
            CC_PONG = 0x05, CC_PACK_RESP = 0x06, CC_KNOWN_PACKS = 0x07;

    // PLAY: serwer -> bot
    static final int PS_DISCONNECT = 0x1D, PS_KEEPALIVE = 0x27, PS_LOGIN = 0x2C, PS_SYNC_POS = 0x42;
    // PLAY: bot -> serwer
    static final int PC_CONFIRM_TP = 0x00, PC_CHAT_CMD = 0x05, PC_CHAT = 0x07, PC_KEEPALIVE = 0x1A,
            PC_POS_ROT = 0x1D;
}
