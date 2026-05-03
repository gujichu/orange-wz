package orange.wz.provider.ms;

import lombok.Getter;
import lombok.Setter;

@Getter
public class WzMsEntry {
    private final String name;
    @Setter
    private int checkSum;
    @Setter
    private int flags;
    @Setter
    private int startPos;
    @Setter
    private int size;
    @Setter
    private int sizeAligned;
    @Setter
    private int unk1;
    private final int unk2;
    private byte[] entryKey;
    @Setter
    private byte[] data;

    public WzMsEntry(String name, int checkSum, int flags, int startPos, int size, int sizeAligned, int unk1, int unk2, byte[] entryKey) {
        this.name = name;
        this.checkSum = checkSum;
        this.flags = flags;
        this.startPos = startPos;
        this.size = size;
        this.sizeAligned = sizeAligned;
        this.unk1 = unk1;
        this.unk2 = unk2;
        this.entryKey = entryKey;
    }

    public void setEntryKey(byte[] entryKey) {
        this.entryKey = entryKey;
    }
}
