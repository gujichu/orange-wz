package orange.wz.provider.ms;

import lombok.extern.slf4j.Slf4j;
import orange.wz.provider.WzImage;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
final class WzMsChaChaLoader {
    static final int VERSION = 4;
    static final int HEADER_CIPHER_LEN = 8;
    static final int HEADER_PAD_OFFSET = 64;
    static final int CHACHA_KEY_LENGTH = 32;

    private static final byte[] CHACHA20_KEY_OBSCURE = {
            0x7B, 0x2F, 0x35, 0x48, 0x43, (byte) 0x95, 0x02, (byte) 0xB9,
            (byte) 0xAE, (byte) 0x91, (byte) 0xA6, (byte) 0xE1, (byte) 0xD8, (byte) 0xD6, 0x24, (byte) 0xB4,
            0x33, 0x10, 0x1D, 0x3D, (byte) 0xC1, (byte) 0xBB, (byte) 0xC6, (byte) 0xF4,
            (byte) 0xA5, (byte) 0xFE, (byte) 0xB3, 0x69, 0x6B, 0x56, (byte) 0xE4, 0x75
    };

    private WzMsChaChaLoader() {
    }

    static List<WzMsFile.EntryImage> load(byte[] all, String originalFileName, byte[] iv, byte[] userKey) {
        String fileName = originalFileName.toLowerCase(Locale.ROOT);
        int randByteCount = fileName.chars().sum() % WzMsConstants.RAND_BYTE_MOD + WzMsConstants.RAND_BYTE_OFFSET;
        if (all.length < randByteCount + 5) {
            throw new RuntimeException("MS v4 文件过短");
        }

        ByteBuffer bb = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
        byte[] randBytes = new byte[randByteCount];
        bb.get(randBytes);
        for (int i = 0; i < randBytes.length; i++) {
            randBytes[i] = (byte) (randBytes[i] >> 1);
        }

        int version = (bb.get() & 0xFF) ^ (randBytes[0] & 0xFF);
        if (version != VERSION) {
            throw new RuntimeException("不支持的 MS ChaCha 版本: " + version);
        }

        int hashedSaltLen = bb.getInt();
        int saltLen = (hashedSaltLen & 0xFF) ^ (randBytes[0] & 0xFF);
        if (saltLen <= 0 || bb.remaining() < saltLen * 2) {
            throw new RuntimeException("MS v4 salt 长度非法: " + saltLen);
        }
        byte[] saltBytes = new byte[saltLen * 2];
        bb.get(saltBytes);

        char[] saltChars = new char[saltLen];
        for (int i = 0; i < saltLen; i++) {
            int a = (randBytes[i] & 0xFF) ^ (saltBytes[i * 2] & 0xFF);
            saltChars[i] = (char) (((a | 0x4B) << 1) - a - 75);
        }
        String salt = new String(saltChars);
        String fileNameWithSalt = fileName + salt;

        int headerStart = bb.position();
        byte[] headerKey = deriveHeaderKey(fileNameWithSalt);
        byte[] emptyNonce = new byte[ChaCha20CryptoTransform.ALLOWED_NONCE_LENGTH];
        byte[] headerCipher = new byte[ChaCha20CryptoTransform.PROCESS_BYTES_AT_TIME];
        System.arraycopy(all, headerStart, headerCipher, 0, headerCipher.length);
        try (ChaCha20CryptoTransform headerCipherTransform = new ChaCha20CryptoTransform(headerKey, emptyNonce, 0)) {
            headerCipherTransform.transformBlock(headerCipher, 0, headerCipher, 0);
        }
        ByteBuffer hb = ByteBuffer.wrap(headerCipher).order(ByteOrder.LITTLE_ENDIAN);
        hb.getInt(); // hash — 暂不校验
        int entryCount = hb.getInt();

        int padAmount = fileName.chars().map(v -> v * 3).sum() % WzMsConstants.HEADER_PAD_MOD + HEADER_PAD_OFFSET;
        int entryStart = headerStart + HEADER_CIPHER_LEN + padAmount;
        if (entryCount < 0 || entryStart >= all.length) {
            throw new RuntimeException("MS v4 entry 区偏移非法");
        }

        byte[] entryTableKey = deriveEntryKey(fileNameWithSalt);
        List<WzMsEntry> entries = new ArrayList<>(entryCount);
        int physicalEnd;
        try (ChaCha20Reader entryReader = new ChaCha20Reader(all, entryStart, entryTableKey, emptyNonce)) {
            for (int i = 0; i < entryCount; i++) {
                String entryName = entryReader.readString();
                int checksum = entryReader.readInt32();
                int flags = entryReader.readInt32();
                int startPos = entryReader.readInt32();
                int size = entryReader.readInt32();
                int sizeAligned = entryReader.readInt32();
                int unk1 = entryReader.readInt32();
                int unk2 = entryReader.readInt32();
                byte[] entryKeyBytes = entryReader.readBytes(WzMsConstants.ENTRY_KEY_SIZE);
                int unk3 = entryReader.readInt32();
                int unk4 = entryReader.readInt32();
                entries.add(new WzMsEntry(entryName, checksum, flags, startPos, size, sizeAligned, unk1, unk2, entryKeyBytes, unk3, unk4));
            }
            physicalEnd = entryReader.physicalPosition();
        }

        int dataStart = physicalEnd;
        if ((dataStart & WzMsConstants.PAGE_ALIGNMENT_MASK) != 0) {
            dataStart = (dataStart & ~WzMsConstants.PAGE_ALIGNMENT_MASK) + WzMsConstants.PAGE_ALIGNMENT_SIZE;
        }

        List<WzMsFile.EntryImage> result = new ArrayList<>();
        for (WzMsEntry entry : entries) {
            int start = dataStart + entry.getStartPos() * WzMsConstants.BLOCK_ALIGNMENT;
            byte[] imageBytes = decryptImage(all, start, entry, salt);
            int slash = entry.getName().lastIndexOf('/');
            String imgName = slash >= 0 ? entry.getName().substring(slash + 1) : entry.getName();
            List<String> attemptLogs = new ArrayList<>();
            WzImage image = WzMsFile.tryParseImage(imgName, imageBytes, iv, userKey, attemptLogs, "chacha-v4");
            if (image == null) {
                log.warn("MS v4 内部 Img 解析失败，已跳过: {}", imgName);
                log.warn("MS v4 Img 失败详情 name={} attempts={}", entry.getName(), String.join(" | ", attemptLogs));
                continue;
            }
            result.add(new WzMsFile.EntryImage(entry.getName(), image, entry.getFlags(), entry.getUnk1(), entry.getUnk2(), entry.getEntryKey()));
        }
        return result;
    }

    private static byte[] deriveHeaderKey(String fileNameWithSalt) {
        byte[] key = new byte[CHACHA_KEY_LENGTH];
        int len = fileNameWithSalt.length();
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) (fileNameWithSalt.charAt(i % len) + i);
            key[i] ^= CHACHA20_KEY_OBSCURE[i];
        }
        return key;
    }

    private static byte[] deriveEntryKey(String fileNameWithSalt) {
        byte[] key = new byte[CHACHA_KEY_LENGTH];
        int len = fileNameWithSalt.length();
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) (i + (i % 3 + 2) * fileNameWithSalt.charAt(len - 1 - i % len));
            key[i] ^= CHACHA20_KEY_OBSCURE[i];
        }
        return key;
    }

    private static byte[] decryptImage(byte[] all, int start, WzMsEntry entry, String salt) {
        int size = entry.getSize();
        int sizeAligned = entry.getSizeAligned();
        byte[] enc = new byte[sizeAligned];
        System.arraycopy(all, start, enc, 0, sizeAligned);

        byte[] imgKey = deriveImgKey(entry, salt);
        byte[] nonce = new byte[ChaCha20CryptoTransform.ALLOWED_NONCE_LENGTH];
        int counter = deriveImgNonceAndCounter(salt, nonce);

        byte[] result = new byte[size];
        int cryptedSize = Math.min(size, WzMsConstants.DOUBLE_ENCRYPT_INITIAL_BYTES);
        if (cryptedSize > 0) {
            int processLen = (cryptedSize + 63) & ~63;
            byte[] chunk = new byte[processLen];
            System.arraycopy(enc, 0, chunk, 0, processLen);
            try (ChaCha20CryptoTransform cipher = new ChaCha20CryptoTransform(imgKey, nonce, counter)) {
                byte[] plain = new byte[processLen];
                cipher.transformBlock(chunk, 0, plain, 0);
                System.arraycopy(plain, 0, result, 0, cryptedSize);
            }
        }
        if (size > WzMsConstants.DOUBLE_ENCRYPT_INITIAL_BYTES) {
            System.arraycopy(enc, WzMsConstants.DOUBLE_ENCRYPT_INITIAL_BYTES, result,
                    WzMsConstants.DOUBLE_ENCRYPT_INITIAL_BYTES,
                    size - WzMsConstants.DOUBLE_ENCRYPT_INITIAL_BYTES);
        }
        return result;
    }

    private static long fnvHashSalt(String salt) {
        long keyHash = WzMsConstants.INITIAL_KEY_HASH;
        for (int i = 0; i < salt.length(); i++) {
            keyHash = ((keyHash ^ salt.charAt(i)) * WzMsConstants.KEY_HASH_MULTIPLIER) & 0xFFFFFFFFL;
        }
        return keyHash;
    }

    private static byte[] deriveImgKey(WzMsEntry entry, String salt) {
        String keyHashStr = Long.toString(fnvHashSalt(salt));
        byte[] keyHashDigits = new byte[keyHashStr.length()];
        for (int i = 0; i < keyHashStr.length(); i++) {
            keyHashDigits[i] = (byte) (keyHashStr.charAt(i) - '0');
        }

        byte[] imgKey = new byte[CHACHA_KEY_LENGTH];
        String entryName = entry.getName();
        byte[] entryKey = entry.getEntryKey();
        for (int i = 0; i < imgKey.length; i++) {
            imgKey[i] = (byte) (i + entryName.charAt(i % entryName.length()) * (
                    (keyHashDigits[i % keyHashDigits.length] & 0xFF) % 2
                            + (entryKey[(keyHashDigits[(i + 2) % keyHashDigits.length] + i) % entryKey.length] & 0xFF)
                            + ((keyHashDigits[(i + 1) % keyHashDigits.length] + i) % 5)
            ));
            imgKey[i] ^= CHACHA20_KEY_OBSCURE[i];
        }
        return imgKey;
    }

    private static int deriveImgNonceAndCounter(String salt, byte[] nonce) {
        int keyHash = (int) fnvHashSalt(salt);
        int keyHash2 = keyHash >>> 1;
        int keyHash3 = keyHash2 ^ 0x6C;

        byte[] keyHashData = new byte[12];
        ByteBuffer.wrap(keyHashData).order(ByteOrder.LITTLE_ENDIAN).putInt(keyHash).putInt(keyHash2).putInt(keyHash3);

        int a = 0;
        int b = 0;
        int c = 90;
        int d = 0;
        for (int i = 0; i < 12; i++) {
            keyHashData[i] ^= (byte) (d + 11 * (i / 11) + (c ^ (i >> 2)) + (a ^ b));
            d--;
            a += 8;
            b += 17;
            c += 43;
        }
        System.arraycopy(keyHashData, 0, nonce, 4, 8);
        return ByteBuffer.wrap(keyHashData, 8, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

}
