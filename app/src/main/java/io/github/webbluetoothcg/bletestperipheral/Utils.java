package io.github.webbluetoothcg.bletestperipheral;

import java.util.Random;

public class Utils {

    // 生成[min, max]范围内的随机数
    public static int getRandomRange(int min, int max) {
        Random random = new Random();
        return random.nextInt(max) % (max - min + 1) + min;
    }

    // int 转 byte数组，大端
    public static byte[] int2bytesBE(int n) {
        byte[] b = new byte[4];
        b[3] = (byte) (n & 0xff);
        b[2] = (byte) (n >> 8 & 0xff);
        b[1] = (byte) (n >> 16 & 0xff);
        b[0] = (byte) (n >> 24 & 0xff);
        return b;
    }

    // byte[2]转short，大端
    public static short bytes2shortBE(byte[] bytes) {
        return (short) ((bytes[0] & 0xff) << 8 | (bytes[1] & 0xff));
    }

    // byte[2]转short，小端
    public static short bytes2shortLE(byte[] bytes) {
        return (short) ((bytes[1] & 0xff) << 8 | (bytes[0] & 0xff));
    }
}
