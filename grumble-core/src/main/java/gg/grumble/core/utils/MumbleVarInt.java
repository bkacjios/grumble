package gg.grumble.core.utils;

import java.nio.ByteBuffer;

@SuppressWarnings({"unused", "UnusedReturnValue"})
public class MumbleVarInt {
    public static int writeVarInt(ByteBuffer buffer, long value) {
        int flag = 0;

        if ((value & 0x8000000000000000L) != 0 && (~value < 0x100000000L)) {
            value = ~value;
            if (value <= 0x3) {
                buffer.put((byte)(0xFC | value));
                return 1;
            } else {
                buffer.put((byte) 0xF8);
                flag = 1;
            }
        }

        if (value < 0x80) {
            buffer.put((byte)(value));
            return 1 + flag;
        } else if (value < 0x4000) {
            buffer.put((byte)((value >> 8) | 0x80));
            buffer.put((byte)(value & 0xFF));
            return 2 + flag;
        } else if (value < 0x200000) {
            buffer.put((byte)((value >> 16) | 0xC0));
            buffer.put((byte)((value >> 8) & 0xFF));
            buffer.put((byte)(value & 0xFF));
            return 3 + flag;
        } else if (value < 0x10000000) {
            buffer.put((byte)((value >> 24) | 0xE0));
            buffer.put((byte)((value >> 16) & 0xFF));
            buffer.put((byte)((value >> 8) & 0xFF));
            buffer.put((byte)(value & 0xFF));
            return 4 + flag;
        } else if (value < 0x100000000L) {
            buffer.put((byte) 0xF0);
            buffer.put((byte)((value >> 24) & 0xFF));
            buffer.put((byte)((value >> 16) & 0xFF));
            buffer.put((byte)((value >> 8) & 0xFF));
            buffer.put((byte)(value & 0xFF));
            return 5 + flag;
        } else {
            buffer.put((byte) 0xF4);
            buffer.put((byte)((value >> 56) & 0xFF));
            buffer.put((byte)((value >> 48) & 0xFF));
            buffer.put((byte)((value >> 40) & 0xFF));
            buffer.put((byte)((value >> 32) & 0xFF));
            buffer.put((byte)((value >> 24) & 0xFF));
            buffer.put((byte)((value >> 16) & 0xFF));
            buffer.put((byte)((value >> 8) & 0xFF));
            buffer.put((byte)(value & 0xFF));
            return 9 + flag;
        }
    }

    private static long readVarInt(ByteBuffer buffer) {
        byte v = buffer.get();

        if ((v & 0x80) == 0x00) {
            // 1-byte
            return v & 0x7F;
        } else if ((v & 0xC0) == 0x80) {
            // 2-byte
            byte b1 = buffer.get();
            return ((v & 0x3F) << 8) | (b1 & 0xFF);
        } else if ((v & 0xF0) == 0xF0) {
            switch (v & 0xFC) {
                case 0xF0 -> {
                    byte b1 = buffer.get(), b2 = buffer.get(), b3 = buffer.get(), b4 = buffer.get();
                    return ((long)(b1 & 0xFF) << 24) | ((long)(b2 & 0xFF) << 16)
                            | ((long)(b3 & 0xFF) << 8) | (b4 & 0xFF);
                }
                case 0xF4 -> {
                    byte b1 = buffer.get(), b2 = buffer.get(), b3 = buffer.get(), b4 = buffer.get();
                    byte b5 = buffer.get(), b6 = buffer.get(), b7 = buffer.get(), b8 = buffer.get();
                    return ((long)(b1 & 0xFF) << 56) | ((long)(b2 & 0xFF) << 48)
                            | ((long)(b3 & 0xFF) << 40) | ((long)(b4 & 0xFF) << 32)
                            | ((long)(b5 & 0xFF) << 24) | ((long)(b6 & 0xFF) << 16)
                            | ((long)(b7 & 0xFF) << 8) | (b8 & 0xFF);
                }
                case 0xF8 -> {
                    long innerValue = readVarInt(buffer);
                    return ~innerValue;
                }
                case 0xFC -> {
                    return ~(v & 0x03);
                }
                default -> throw new IllegalArgumentException("Invalid varint prefix: " + (v & 0xFF));
            }
        } else if ((v & 0xF0) == 0xE0) {
            byte b1 = buffer.get(), b2 = buffer.get(), b3 = buffer.get();
            return ((v & 0x0F) << 24) | ((b1 & 0xFF) << 16) | ((b2 & 0xFF) << 8) | (b3 & 0xFF);
        } else if ((v & 0xE0) == 0xC0) {
            byte b1 = buffer.get(), b2 = buffer.get();
            return ((v & 0x1F) << 16) | ((b1 & 0xFF) << 8) | (b2 & 0xFF);
        } else {
            throw new IllegalArgumentException("Invalid varint prefix: " + (v & 0xFF));
        }
    }

    public static byte readVarIntByte(ByteBuffer buffer) {
        return (byte) readVarInt(buffer);
    }

    public static short readVarIntShort(ByteBuffer buffer) {
        return (short) readVarInt(buffer);
    }

    public static char readVarIntChar(ByteBuffer buffer) {
        return (char) readVarInt(buffer);
    }

    public static int readVarIntInt(ByteBuffer buffer) {
        return (int) readVarInt(buffer);
    }

    public static long readVarIntLong(ByteBuffer buffer) {
        return readVarInt(buffer);
    }
}
