package orange.wz.provider.ms;

import lombok.Getter;
import lombok.Setter;

@Getter
public class WzMsHeader {
    private int hash;
    private final int entryCount;
    private final long headerStartPosition;
    private final long entryStartPosition;
    @Setter
    private long dataStartPosition;
    private final String fileName;
    private final String salt;
    private final String fileNameWithSalt;
    private final int version;

    public WzMsHeader(String fileName, String salt, String fileNameWithSalt, int hash, int version, int entryCount,
                      long headerStartPosition, long entryStartPosition) {
        this.fileName = fileName;
        this.salt = salt;
        this.fileNameWithSalt = fileNameWithSalt;
        this.hash = hash;
        this.version = version;
        this.entryCount = entryCount;
        this.headerStartPosition = headerStartPosition;
        this.entryStartPosition = entryStartPosition;
    }

    public void setHash(int hash) {
        this.hash = hash;
    }
}
