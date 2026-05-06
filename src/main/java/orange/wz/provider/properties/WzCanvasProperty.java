package orange.wz.provider.properties;

import orange.wz.provider.WzImage;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.WzObject;
import orange.wz.provider.tools.*;

import java.awt.image.BufferedImage;
import java.util.List;

public class WzCanvasProperty extends WzExtended {
    private WzPngProperty png;

    public WzCanvasProperty(String name, WzObject parent, WzImage wzImage) {
        super(name, WzType.CANVAS_PROPERTY, parent, wzImage);
    }

    public WzCanvasProperty(String name, int format, int scale, byte[] imageBytes, WzObject parent, WzImage wzImage) {
        this(name, parent, wzImage);
        png = new WzPngProperty(name, format, scale, imageBytes, this, wzImage);
    }

    public WzCanvasProperty(String name, int width, int height, int format, int scale, byte[] imageBytes, WzObject parent, WzImage wzImage) {
        this(name, parent, wzImage);
        png = new WzPngProperty(name, width, height, format, scale, imageBytes, this, wzImage);
    }
    // Png -------------------------------------------------------------------------------------------------------------
    public byte[] getImageBytes(boolean saveInMem) {
        return png.getImageBytes(saveInMem);
    }

    public int getWidth() {
        return png.getWidth();
    }

    public int getHeight() {
        return png.getHeight();
    }

    public WzPngFormat getFormat() {
        return png.getFormat();
    }

    public int getScale() {
        return png.getScale();
    }

    public BufferedImage getPngImage(boolean saveInMem) {
        return png.getImage(saveInMem);
    }

    public void initPngProperty(String name, WzObject parent, WzImage wzImage) {
        png = new WzPngProperty(name, parent, wzImage);
    }

    public void initPngProperty(String name, WzObject parent, WzImage wzImage, BinaryReader reader) {
        png = new WzPngProperty(name, parent, wzImage);
        png.setData(reader);
    }

    public void setPng(BufferedImage pngImage, WzPngFormat format, int scale) {
        png.setImage(pngImage, format, scale);
        wzImage.setChanged(true);
        setTempChanged(true);
    }

    public void setPng(BufferedImage pngImage, WzPngFormat format, int scale, int zlibCompressionLevel) {
        png.setImage(pngImage, format, scale, zlibCompressionLevel);
        wzImage.setChanged(true);
        setTempChanged(true);
    }

    public void setPng(BufferedImage pngImage, WzPngFormat format, int scale, int zlibCompressionLevel,
                       WzPngZlibCompressMode zlibMode) {
        png.setImage(pngImage, format, scale, zlibCompressionLevel, zlibMode);
        wzImage.setChanged(true);
        setTempChanged(true);
    }

    /** WZ 中当前存储的压缩图片字节长度（与写入体积相关） */
    public int getCompressedPngStorageLength() {
        byte[] b = png.getCompressedBytes(false);
        return b != null ? b.length : 0;
    }

    public WzPngProperty.CompressedPngData exportCompressedPngData() {
        return png.exportCompressedData();
    }

    public void copyPngFrom(WzCanvasProperty src, boolean keepImageInMem) {
        if (src == null) {
            throw new IllegalArgumentException("来源Canvas不能为空");
        }
        WzPngProperty.CompressedPngData data = src.exportCompressedPngData();
        if (data == null) {
            throw new IllegalStateException("来源Canvas压缩数据为空");
        }
        png.copyCompressedFrom(data, keepImageInMem);
        wzImage.setChanged(true);
        setTempChanged(true);
    }

    public void copyPngFromCompressedData(WzPngProperty.CompressedPngData data, boolean keepImageInMem) {
        if (data == null) {
            throw new IllegalArgumentException("来源压缩数据不能为空");
        }
        png.copyCompressedFrom(data, keepImageInMem);
        wzImage.setChanged(true);
        setTempChanged(true);
    }

    public void rebuildCompressedBytesUseNewWzKey(WzMutableKey wzMutableKey) {
        png.rebuildCompressedBytesUseNewWzKey(wzMutableKey);
    }

    public void clearImage() {
        png.clearImage();
    }

    public void clearPngProperty() {
        png.setParent(null);
        png = null;
    }

    public void scale(double scale) {
        BufferedImage originImage = getPngImage(false);
        BufferedImage newImage = scale >= 1.0 ? ImgTool.scaleAndSharpen(originImage, scale) : ImgTool.scale(originImage, scale);

        setPng(newImage, getFormat(), getScale());
    }

    // Override --------------------------------------------------------------------------------------------------------
    @Override
    public void setWzImage(WzImage wzImage) {
        this.wzImage = wzImage;
        png.setWzImage(wzImage);
    }

    @Override
    public void writeValue(BinaryWriter writer) {
        writer.writeStringBlock(WzExtendedType.CANVAS.getString(), WzImage.withoutOffsetFlag, WzImage.withOffsetFlag);
        writer.putByte((byte) 0);
        List<WzImageProperty> properties = children.get();
        if (!properties.isEmpty()) {
            writer.putByte((byte) 1);
            WzImage.writeListValue(writer, properties);
        } else {
            writer.putByte((byte) 0);
        }

        // Image Info
        writer.writeCompressedInt(png.getWidth());
        writer.writeCompressedInt(png.getHeight());
        writer.writeCompressedInt(png.getFormat().getValue());
        writer.putByte((byte) png.getScale());
        writer.putInt(0);

        // Write image
        byte[] bytes = png.getCompressedBytes(false);
        writer.putInt(bytes.length + 1);
        writer.putByte((byte) 0);
        writer.putBytes(bytes);
    }

    @Override
    public WzCanvasProperty deepClone(WzObject parent) {
        WzCanvasProperty clone = new WzCanvasProperty(name, parent, null);
        clone.png = png.deepClone(clone);
        for (WzImageProperty property : children.get()) {
            clone.addChild(property.deepClone(clone));
        }
        return clone;
    }
}
