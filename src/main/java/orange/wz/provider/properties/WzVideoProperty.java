package orange.wz.provider.properties;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import orange.wz.provider.WzImage;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.WzObject;
import orange.wz.provider.tools.BinaryReader;
import orange.wz.provider.tools.BinaryWriter;
import orange.wz.provider.tools.WzType;

import java.util.List;

/**
 * 对应 Harepacker 的 Canvas#Video（KMST v1181+）。
 *
 * 只读打通阶段：解析并跳过二进制数据，保留 offset/length，避免按 PNG Canvas 解析导致失败。
 */
@Setter
@Getter
public class WzVideoProperty extends WzExtended {
    private byte videoType;
    private int length;
    private int offset;
    @Getter(AccessLevel.NONE)
    private byte[] bytes;

    public WzVideoProperty(String name, WzObject parent, WzImage wzImage) {
        // 复用 RAW_DATA_PROPERTY 的类型与 UI 展示（当前项目暂无 Video 专用类型/表单）
        super(name, WzType.RAW_DATA_PROPERTY, parent, wzImage);
    }

    public void parse(BinaryReader reader, boolean parseNow) {
        videoType = reader.getByte();
        length = reader.readCompressedInt();
        offset = reader.getPosition();
        if (parseNow) {
            getBytes(true);
        } else {
            reader.skip(length);
        }
    }

    public byte[] getBytes(boolean saveInMem) {
        if (bytes == null) {
            BinaryReader reader = wzImage.getReader();
            int curOffset = reader.getPosition();
            reader.setPosition(offset);
            byte[] returnBytes = reader.getBytes(length);
            reader.setPosition(curOffset);
            if (saveInMem) {
                bytes = returnBytes;
            }
            return returnBytes;
        }
        return bytes;
    }

    @Override
    public void writeValue(BinaryWriter writer) {
        writer.writeStringBlock(WzExtendedType.CANVAS_VIDEO.getString(), WzImage.withoutOffsetFlag, WzImage.withOffsetFlag);
        writer.putByte((byte) 0);
        List<WzImageProperty> properties = children.get();
        if (!properties.isEmpty()) {
            writer.putByte((byte) 1);
            WzImage.writeListValue(writer, properties);
        } else {
            writer.putByte((byte) 0);
        }
        writer.putByte(videoType);
        byte[] bytes = getBytes(false);
        writer.writeCompressedInt(bytes.length);
        writer.putBytes(bytes);
    }

    @Override
    public WzVideoProperty deepClone(WzObject parent) {
        WzVideoProperty clone = new WzVideoProperty(name, parent, null);
        clone.videoType = videoType;
        clone.length = length;
        for (WzImageProperty property : children.get()) {
            clone.addChild(property.deepClone(clone));
        }
        byte[] bytes = getBytes(false);
        clone.bytes = new byte[bytes.length];
        System.arraycopy(bytes, 0, clone.bytes, 0, bytes.length);
        return clone;
    }
}

