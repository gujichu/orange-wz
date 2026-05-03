package orange.wz.provider.ms;

public final class WzMsConstants {
    private WzMsConstants() {
    }

    public static final int SUPPORTED_VERSION = 2;
    public static final int SNOW_KEY_LENGTH = 16;
    public static final int BLOCK_ALIGNMENT = 1024;
    public static final int PAGE_ALIGNMENT_MASK = 0x3FF;
    public static final int PAGE_ALIGNMENT_SIZE = 0x400;
    public static final int RAND_BYTE_MOD = 312;
    public static final int RAND_BYTE_OFFSET = 30;
    public static final int HEADER_PAD_MOD = 212;
    public static final int HEADER_PAD_OFFSET = 33;
    public static final long INITIAL_KEY_HASH = 0x811C9DC5L;
    public static final long KEY_HASH_MULTIPLIER = 0x1000193L;
    public static final int SALT_MIN_LENGTH = 4;
    public static final int SALT_MAX_LENGTH = 12;
    public static final int DOUBLE_ENCRYPT_INITIAL_BYTES = 1024;
    public static final int ASCII_PRINTABLE_MIN = 33;
    public static final int ASCII_PRINTABLE_MAX = 127;
    public static final int ENTRY_SIZE_ALIGNED = 1024;
    public static final int ENTRY_KEY_SIZE = 16;
}
