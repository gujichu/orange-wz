package orange.wz.provider.ms;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import orange.wz.provider.WzAESConstant;
import orange.wz.provider.WzImage;
import orange.wz.provider.tools.BinaryReader;
import orange.wz.provider.tools.CryptoConstants;
import orange.wz.provider.tools.TextImagePropertyReader;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Slf4j
public final class WzMsFile {
    @Getter
    public static final class EntryImage {
        private final String entryName;
        private final WzImage image;
        private final int flags;
        private final int unk1;
        private final int unk2;
        private final byte[] entryKey;

        public EntryImage(String entryName, WzImage image, int flags, int unk1, int unk2, byte[] entryKey) {
            this.entryName = entryName;
            this.image = image;
            this.flags = flags;
            this.unk1 = unk1;
            this.unk2 = unk2;
            this.entryKey = entryKey;
        }
    }

    public static List<EntryImage> load(Path msPath, byte[] iv, byte[] userKey) {
        byte[] all = java.nio.file.Files.exists(msPath) ? orange.wz.provider.tools.FileTool.readFile(msPath) : null;
        if (all == null || all.length < 32) {
            throw new RuntimeException("MS 文件读取失败或内容为空: " + msPath);
        }

        String originalFileName = msPath.getFileName().toString();
        List<String> errors = new ArrayList<>();
        try {
            return loadSnowV2(all, originalFileName, iv, userKey);
        } catch (Exception e) {
            errors.add("Snow2(v2): " + e.getMessage());
            log.debug("MS Snow2 解析未命中 {}: {}", msPath, e.getMessage());
        }
        try {
            return WzMsChaChaLoader.load(all, originalFileName, iv, userKey);
        } catch (Exception e) {
            errors.add("ChaCha20(v4): " + e.getMessage());
            log.debug("MS ChaCha20 解析未命中 {}: {}", msPath, e.getMessage());
        }
        throw new RuntimeException("MS 文件解析失败: " + msPath + " [" + String.join("; ", errors) + "]");
    }

    private static List<EntryImage> loadSnowV2(byte[] all, String originalFileName, byte[] iv, byte[] userKey) {
        originalFileName = originalFileName.toLowerCase(Locale.ROOT);
        ByteBuffer bb = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);

        int randByteCount = originalFileName.chars().sum() % WzMsConstants.RAND_BYTE_MOD + WzMsConstants.RAND_BYTE_OFFSET;
        byte[] randBytes = new byte[randByteCount];
        bb.get(randBytes);

        int hashedSaltLen = bb.getInt();
        int saltLen = (hashedSaltLen & 0xFF) ^ (randBytes[0] & 0xFF);
        byte[] saltBytes = new byte[saltLen * 2];
        bb.get(saltBytes);

        char[] saltChars = new char[saltLen];
        for (int i = 0; i < saltLen; i++) {
            saltChars[i] = (char) ((randBytes[i] & 0xFF) ^ (saltBytes[i * 2] & 0xFF));
        }
        String salt = new String(saltChars);
        String fileNameWithSalt = originalFileName + salt;

        int headerStart = bb.position();
        int padAmount = originalFileName.chars().map(v -> v * 3).sum() % WzMsConstants.HEADER_PAD_MOD + WzMsConstants.HEADER_PAD_OFFSET;
        int headerCipherLen = 9;
        byte[] headerCipher = new byte[headerCipherLen];
        System.arraycopy(all, headerStart, headerCipher, 0, headerCipherLen);
        byte[] headerPlain = new Snow2CryptoTransform(deriveSnowKey(fileNameWithSalt, false), null, false).transform(headerCipher);
        ByteBuffer hb = ByteBuffer.wrap(headerPlain).order(ByteOrder.LITTLE_ENDIAN);
        int hash = hb.getInt();
        int version = hb.get() & 0xFF;
        int entryCount = hb.getInt();
        if (version != WzMsConstants.SUPPORTED_VERSION_SNOW) {
            throw new RuntimeException("不支持的 MS Snow2 版本: " + version);
        }

        int checkHash = hashedSaltLen + version + entryCount;
        ByteBuffer sbuf = ByteBuffer.wrap(saltBytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < saltLen; i++) {
            checkHash += sbuf.getShort() & 0xFFFF;
        }
        if (checkHash != hash) {
            throw new RuntimeException("MS 文件头 hash 校验失败");
        }

        int entryStart = headerStart + headerCipherLen + padAmount;
        byte[] entryCipherTail = new byte[all.length - entryStart];
        System.arraycopy(all, entryStart, entryCipherTail, 0, entryCipherTail.length);
        byte[] entryPlain = new Snow2CryptoTransform(deriveSnowKey(fileNameWithSalt, true), null, false).transform(entryCipherTail);
        ByteBuffer eb = ByteBuffer.wrap(entryPlain).order(ByteOrder.LITTLE_ENDIAN);
        List<WzMsEntry> entries = new ArrayList<>(entryCount);
        for (int i = 0; i < entryCount; i++) {
            int nameLen = eb.getInt();
            char[] chars = new char[nameLen];
            for (int j = 0; j < nameLen; j++) {
                chars[j] = eb.getChar();
            }
            String entryName = new String(chars);
            int checksum = eb.getInt();
            int flags = eb.getInt();
            int startPos = eb.getInt();
            int size = eb.getInt();
            int sizeAligned = eb.getInt();
            int unk1 = eb.getInt();
            int unk2 = eb.getInt();
            byte[] entryKey = new byte[WzMsConstants.SNOW_KEY_LENGTH];
            eb.get(entryKey);
            entries.add(new WzMsEntry(entryName, checksum, flags, startPos, size, sizeAligned, unk1, unk2, entryKey));
        }

        int dataStart = alignToPage(entryStart + eb.position());
        List<EntryImage> result = new ArrayList<>();
        for (WzMsEntry entry : entries) {
            int start = dataStart + entry.getStartPos() * WzMsConstants.BLOCK_ALIGNMENT;
            byte[] encData = new byte[entry.getSizeAligned()];
            System.arraycopy(all, start, encData, 0, entry.getSizeAligned());
            byte[] imageBytes = decryptData(encData, entry, salt);

            int slash = entry.getName().lastIndexOf('/');
            String imgName = slash >= 0 ? entry.getName().substring(slash + 1) : entry.getName();
            List<String> attemptLogs = new ArrayList<>();
            WzImage image = tryParseImage(imgName, imageBytes, iv, userKey, attemptLogs, "double-pass");
            String decryptMode = "double-pass";
            if (image == null) {
                byte[] singlePassBytes = decryptDataSinglePass(encData, entry, salt);
                image = tryParseImage(imgName, singlePassBytes, iv, userKey, attemptLogs, "single-pass");
                if (image != null) {
                    imageBytes = singlePassBytes;
                    decryptMode = "single-pass";
                }
            }
            if (image == null) {
                log.warn("MS 内部 Img 解析失败，已跳过: {}", imgName);
                log.warn(
                        "MS Img 失败详情 name={} checksum={} flags={} startPos={} size={} sizeAligned={} unk1={} unk2={} decryptMode={} plainHead16={} attempts={}",
                        entry.getName(),
                        entry.getCheckSum(),
                        entry.getFlags(),
                        entry.getStartPos(),
                        entry.getSize(),
                        entry.getSizeAligned(),
                        entry.getUnk1(),
                        entry.getUnk2(),
                        decryptMode,
                        toHex(imageBytes, 16),
                        String.join(" | ", attemptLogs)
                );
                continue;
            }
            result.add(new EntryImage(entry.getName(), image, entry.getFlags(), entry.getUnk1(), entry.getUnk2(), entry.getEntryKey()));
        }
        return result;
    }

    static WzImage tryParseImage(String imgName, byte[] imageBytes, byte[] uiIv, byte[] uiUserKey, List<String> attemptLogs, String modeTag) {
        if (TextImagePropertyReader.isTextPropertyV1(imageBytes)) {
            WzImage image = new WzImage(imgName, new BinaryReader(imageBytes), null);
            image.setDataSize(imageBytes.length);
            image.setOffset(0);
            if (image.parse()) {
                attemptLogs.add(modeTag + ":text-property-v1:ok");
                return image;
            }
            attemptLogs.add(modeTag + ":text-property-v1:fail(status=" + image.getStatus() + ")");
        }

        List<NamedBytes> ivCandidates = new ArrayList<>();
        ivCandidates.add(new NamedBytes("WZ_CMS_IV", WzAESConstant.WZ_CMS_IV));
        ivCandidates.add(new NamedBytes("WZ_GMS_IV", WzAESConstant.WZ_GMS_IV));
        ivCandidates.add(new NamedBytes("WZ_LATEST_IV", WzAESConstant.WZ_LATEST_IV));
        ivCandidates.add(new NamedBytes("WZ_MSEA2IV", CryptoConstants.WZ_MSEA2IV));
        if (uiIv != null) {
            ivCandidates.add(new NamedBytes("UI_SELECTED_IV", uiIv));
        }

        List<NamedBytes> keyCandidates = new ArrayList<>();
        keyCandidates.add(new NamedBytes("DEFAULT_KEY", WzAESConstant.DEFAULT_KEY));
        if (uiUserKey != null) {
            keyCandidates.add(new NamedBytes("UI_SELECTED_KEY", uiUserKey));
        }

        for (NamedBytes ivCandidate : ivCandidates) {
            if (ivCandidate.bytes == null) {
                continue;
            }
            for (NamedBytes keyCandidate : keyCandidates) {
                if (keyCandidate.bytes == null) {
                    continue;
                }
                WzImage image = new WzImage(imgName, new BinaryReader(imageBytes, ivCandidate.bytes, keyCandidate.bytes), null);
                image.setDataSize(imageBytes.length);
                image.setOffset(0);
                if (image.parse()) {
                    attemptLogs.add(modeTag + ":" + ivCandidate.name + "+" + keyCandidate.name + ":ok");
                    return image;
                }
                attemptLogs.add(
                        modeTag + ":" + ivCandidate.name + "+" + keyCandidate.name +
                                ":fail(status=" + image.getStatus() + ", msg=" + image.getStatus().getMessage() +
                                ", header=" + toHex(imageBytes, 8) + ", firstByte=" + firstByteHex(imageBytes) + ")"
                );
            }
        }
        return null;
    }

    private record NamedBytes(String name, byte[] bytes) {}

    private static String toHex(byte[] data, int maxLen) {
        if (data == null || data.length == 0) {
            return "<empty>";
        }
        int len = Math.min(data.length, maxLen);
        StringBuilder sb = new StringBuilder(len * 3);
        for (int i = 0; i < len; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format("%02X", data[i] & 0xFF));
        }
        if (data.length > len) {
            sb.append(" ...");
        }
        return sb.toString();
    }

    private static String firstByteHex(byte[] data) {
        if (data == null || data.length == 0) {
            return "NA";
        }
        return String.format("%02X", data[0] & 0xFF);
    }

    public static byte[] save(String originalFileName, List<EntryImage> entryImages, byte[] iv, byte[] userKey) {
        try {
            // 与 Harepacker 保持一致：.ms 内部 img 按 BMS(全0 IV) 写入
            byte[] imageIv = WzAESConstant.WZ_LATEST_IV;
            byte[] imageUserKey = WzAESConstant.DEFAULT_KEY;

            String fileNameLower = originalFileName.toLowerCase(Locale.ROOT);
            String salt = generateSalt();
            String fileNameWithSalt = fileNameLower + salt;
            SecureRandom random = new SecureRandom();

            List<WzMsEntry> entries = new ArrayList<>();
            List<byte[]> encryptedEntryData = new ArrayList<>();
            long currentBlockIndex = 0;

            for (EntryImage entryImage : entryImages) {
                byte[] raw = imageToBytes(entryImage.getImage(), imageIv, imageUserKey);
                byte[] entryKey = entryImage.getEntryKey();
                if (entryKey == null || entryKey.length != WzMsConstants.ENTRY_KEY_SIZE) {
                    entryKey = new byte[WzMsConstants.ENTRY_KEY_SIZE];
                    random.nextBytes(entryKey);
                }
                WzMsEntry entry = new WzMsEntry(entryImage.getEntryName(), 0, entryImage.getFlags(), 0, 0, 0, entryImage.getUnk1(), entryImage.getUnk2(), entryKey);
                byte[] encrypted = encryptData(raw, entry, salt);
                int size = encrypted.length;
                int sizeAligned = ((size + (WzMsConstants.ENTRY_SIZE_ALIGNED - 1)) / WzMsConstants.ENTRY_SIZE_ALIGNED) * WzMsConstants.ENTRY_SIZE_ALIGNED;
                entry.setData(raw);
                entry.setSize(size);
                entry.setSizeAligned(sizeAligned);
                entry.setStartPos((int) currentBlockIndex);
                currentBlockIndex += sizeAligned / WzMsConstants.BLOCK_ALIGNMENT;
                int keySum = 0;
                for (byte b : entryKey) keySum += b & 0xFF;
                entry.setCheckSum(entry.getFlags() + entry.getStartPos() + entry.getSize() + entry.getSizeAligned() + entry.getUnk1() + keySum);
                entries.add(entry);
                encryptedEntryData.add(encrypted);
            }

            int randByteCount = fileNameLower.chars().sum() % WzMsConstants.RAND_BYTE_MOD + WzMsConstants.RAND_BYTE_OFFSET;
            byte[] randBytes = new byte[randByteCount];
            random.nextBytes(randBytes);

            byte xorVal = randBytes[0];
            int saltLen = salt.length();
            int hashedSaltLen = ((saltLen ^ xorVal) & 0xFF);
            byte[] saltBytes = new byte[saltLen * 2];
            for (int i = 0; i < saltLen; i++) {
                saltBytes[i * 2] = (byte) (randBytes[i] ^ (byte) salt.charAt(i));
                saltBytes[i * 2 + 1] = 0;
            }

            int sumSalt = 0;
            ByteBuffer saltBuffer = ByteBuffer.wrap(saltBytes).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < saltLen; i++) {
                sumSalt += saltBuffer.getShort() & 0xFFFF;
            }
            int version = WzMsConstants.SUPPORTED_VERSION_SNOW;
            int entryCount = entries.size();
            int hash = hashedSaltLen + version + entryCount + sumSalt;

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(randBytes);
            writeIntLE(out, hashedSaltLen);
            out.write(saltBytes);

            ByteBuffer headerPlain = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN);
            headerPlain.putInt(hash);
            headerPlain.put((byte) version);
            headerPlain.putInt(entryCount);
            out.write(new Snow2CryptoTransform(deriveSnowKey(fileNameWithSalt, false), null, true).transform(headerPlain.array()));

            int padAmount = fileNameLower.chars().map(v -> v * 3).sum() % WzMsConstants.HEADER_PAD_MOD + WzMsConstants.HEADER_PAD_OFFSET;
            byte[] headerPad = new byte[padAmount];
            random.nextBytes(headerPad);
            out.write(headerPad);

            ByteArrayOutputStream entryPlain = new ByteArrayOutputStream();
            for (WzMsEntry entry : entries) {
                writeIntLE(entryPlain, entry.getName().length());
                for (char c : entry.getName().toCharArray()) {
                    writeShortLE(entryPlain, (short) c);
                }
                writeIntLE(entryPlain, entry.getCheckSum());
                writeIntLE(entryPlain, entry.getFlags());
                writeIntLE(entryPlain, entry.getStartPos());
                writeIntLE(entryPlain, entry.getSize());
                writeIntLE(entryPlain, entry.getSizeAligned());
                writeIntLE(entryPlain, entry.getUnk1());
                writeIntLE(entryPlain, entry.getUnk2());
                entryPlain.write(entry.getEntryKey());
            }
            out.write(new Snow2CryptoTransform(deriveSnowKey(fileNameWithSalt, true), null, true).transform(entryPlain.toByteArray()));

            int dataStart = alignToPage(out.size());
            if (dataStart > out.size()) {
                out.write(new byte[dataStart - out.size()]);
            }

            int idx = 0;
            for (WzMsEntry entry : entries) {
                byte[] encrypted = encryptedEntryData.get(idx++);
                out.write(encrypted);
                int pad = entry.getSizeAligned() - entry.getSize();
                if (pad > 0) {
                    out.write(new byte[pad]);
                }
            }

            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("MS 保存失败: " + e.getMessage(), e);
        }
    }

    private static byte[] imageToBytes(WzImage image, byte[] iv, byte[] userKey) {
        orange.wz.provider.tools.BinaryWriter writer = new orange.wz.provider.tools.BinaryWriter();
        writer.setWzMutableKey(new orange.wz.provider.tools.WzMutableKey(iv, userKey));
        image.save(writer);
        return writer.output();
    }

    private static void writeIntLE(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 24) & 0xFF);
    }

    private static void writeShortLE(ByteArrayOutputStream out, short v) {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
    }

    private static int alignToPage(int pos) {
        return (pos + WzMsConstants.PAGE_ALIGNMENT_MASK) & ~WzMsConstants.PAGE_ALIGNMENT_MASK;
    }

    private static String generateSalt() {
        SecureRandom random = new SecureRandom();
        int saltLen = WzMsConstants.SALT_MIN_LENGTH + random.nextInt(WzMsConstants.SALT_MAX_LENGTH - WzMsConstants.SALT_MIN_LENGTH + 1);
        char[] chars = new char[saltLen];
        for (int i = 0; i < saltLen; i++) {
            chars[i] = (char) (WzMsConstants.ASCII_PRINTABLE_MIN +
                    random.nextInt(WzMsConstants.ASCII_PRINTABLE_MAX - WzMsConstants.ASCII_PRINTABLE_MIN + 1));
        }
        return new String(chars);
    }

    private static byte[] deriveSnowKey(String fileNameWithSalt, boolean isEntryKey) {
        byte[] key = new byte[WzMsConstants.SNOW_KEY_LENGTH];
        int len = fileNameWithSalt.length();
        if (!isEntryKey) {
            for (int i = 0; i < key.length; i++) {
                key[i] = (byte) (fileNameWithSalt.charAt(i % len) + i);
            }
        } else {
            for (int i = 0; i < key.length; i++) {
                key[i] = (byte) (i + (i % 3 + 2) * fileNameWithSalt.charAt(len - 1 - i % len));
            }
        }
        return key;
    }

    private static byte[] deriveImgKey(WzMsEntry entry, String salt) {
        long keyHash = WzMsConstants.INITIAL_KEY_HASH;
        for (char c : salt.toCharArray()) {
            keyHash = (keyHash ^ c) * WzMsConstants.KEY_HASH_MULTIPLIER;
            keyHash &= 0xFFFFFFFFL;
        }
        char[] digitsChars = Long.toUnsignedString(keyHash).toCharArray();
        byte[] digits = new byte[digitsChars.length];
        for (int i = 0; i < digits.length; i++) {
            digits[i] = (byte) (digitsChars[i] - '0');
        }
        byte[] imgKey = new byte[WzMsConstants.SNOW_KEY_LENGTH];
        String entryName = entry.getName();
        byte[] entryKey = entry.getEntryKey();
        for (int i = 0; i < imgKey.length; i++) {
            int digitIdx = i % digits.length;
            int entryKeyIdx = (digits[(i + 2) % digits.length] + i) % entryKey.length;
            int mixed = (digits[digitIdx] % 2) + (entryKey[entryKeyIdx] & 0xFF) + ((digits[(i + 1) % digits.length] + i) % 5);
            imgKey[i] = (byte) (i + entryName.charAt(i % entryName.length()) * mixed);
        }
        return imgKey;
    }

    private static byte[] decryptData(byte[] encryptedBlockAligned, WzMsEntry entry, String salt) {
        byte[] imgKey = deriveImgKey(entry, salt);
        int size = entry.getSize();
        byte[] firstPass = new Snow2CryptoTransform(imgKey, null, false).transform(encryptedBlockAligned);
        byte[] result = new byte[size];
        int firstLen = Math.min(size, WzMsConstants.DOUBLE_ENCRYPT_INITIAL_BYTES);
        // 对齐到4字节后再做第二次变换，输入来自真实后续密文字节（而不是零填充），
        // 以贴近 C# CryptoStream 在 size<1024 且非4对齐时的读取行为。
        int secondInputLen = Math.min(firstPass.length, align4(firstLen));
        byte[] firstPartAligned = new byte[secondInputLen];
        System.arraycopy(firstPass, 0, firstPartAligned, 0, secondInputLen);
        byte[] twice = new Snow2CryptoTransform(imgKey, null, false).transform(firstPartAligned);
        System.arraycopy(twice, 0, result, 0, firstLen);
        if (size > firstLen) {
            System.arraycopy(firstPass, firstLen, result, firstLen, size - firstLen);
        }
        return result;
    }

    private static byte[] decryptDataSinglePass(byte[] encryptedBlockAligned, WzMsEntry entry, String salt) {
        byte[] imgKey = deriveImgKey(entry, salt);
        int size = entry.getSize();
        byte[] firstPass = new Snow2CryptoTransform(imgKey, null, false).transform(encryptedBlockAligned);
        byte[] result = new byte[size];
        System.arraycopy(firstPass, 0, result, 0, size);
        return result;
    }

    private static byte[] encryptData(byte[] plainData, WzMsEntry entry, String salt) {
        byte[] imgKey = deriveImgKey(entry, salt);
        int dataLen = plainData.length;
        int firstLen = Math.min(dataLen, WzMsConstants.DOUBLE_ENCRYPT_INITIAL_BYTES);
        byte[] firstPart = new byte[firstLen];
        System.arraycopy(plainData, 0, firstPart, 0, firstLen);
        byte[] firstEncrypted = new Snow2CryptoTransform(imgKey, null, true).transform(firstPart);

        byte[] onePassInput = new byte[dataLen];
        System.arraycopy(firstEncrypted, 0, onePassInput, 0, firstLen);
        if (dataLen > firstLen) {
            System.arraycopy(plainData, firstLen, onePassInput, firstLen, dataLen - firstLen);
        }
        return new Snow2CryptoTransform(imgKey, null, true).transform(onePassInput);
    }

    private static int align4(int value) {
        return (value + 3) & ~3;
    }
}
