package orange.wz.provider;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import orange.wz.exception.BizException;
import orange.wz.exception.ExceptionEnum;
import orange.wz.model.Pair;
import orange.wz.provider.ms.WzMsFile;
import orange.wz.provider.properties.WzListProperty;
import orange.wz.provider.tools.BinaryReader;
import orange.wz.provider.tools.FileTool;
import orange.wz.provider.tools.JsonExport;
import orange.wz.provider.tools.WzFileStatus;
import orange.wz.provider.tools.WzMutableKey;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Getter
@Setter
@Slf4j
public class WzMsImageFile extends WzImageFile {
    private List<WzMsFile.EntryImage> sourceEntries = new ArrayList<>();

    public WzMsImageFile(String name, String filePath, String keyBoxName, byte[] iv, byte[] key) {
        super(name, filePath, keyBoxName, iv, key);
    }

    @Override
    public synchronized boolean parse(boolean realParse) {
        if (getStatus() == WzFileStatus.PARSE_SUCCESS) {
            return true;
        }
        if (!realParse) {
            return true;
        }
        try {
            List<WzMsFile.EntryImage> loaded = WzMsFile.load(Path.of(getFilePath()), getIv(), getKey());
            sourceEntries = loaded;
            // .ms 没有直接复用 WzImageFile.parse()，需要手动补上 reader，
            // 否则后续 Canvas setPng/compressImage 取 WzMutableKey 会 NPE。
            super.setReader(new orange.wz.provider.tools.BinaryReader(getIv(), getKey()));
            // 注意：getChildren() 返回的是副本，不能直接 clear；这里使用 unparse 清空真实 children 容器
            unparse();
            for (WzMsFile.EntryImage entry : loaded) {
                String nodeName = entry.getEntryName();
                int slash = nodeName.lastIndexOf('/');
                if (slash >= 0) {
                    nodeName = nodeName.substring(slash + 1);
                }
                WzListProperty wrapper = new WzListProperty(nodeName, this, this);
                for (WzImageProperty child : entry.getImage().getChildren()) {
                    wrapper.addChild(child.deepClone(wrapper), true);
                }
                wrapper.setChildrenWzImage(this);
                addChild(wrapper, true);
            }
            setStatus(WzFileStatus.PARSE_SUCCESS);
            setChanged(false);
            return true;
        } catch (Exception e) {
            log.error("MS 文件解析失败 {}: {}", getName(), e.getMessage(), e);
            setStatus(WzFileStatus.ERROR_SPECIAL_ENCODE);
            return false;
        }
    }

    @Override
    public boolean save() {
        try {
            if (getStatus() != WzFileStatus.PARSE_SUCCESS && !parse()) {
                return false;
            }
            String originalFileName = Path.of(getFilePath()).getFileName().toString();
            byte[] out = WzMsFile.save(originalFileName, buildEntriesForSave(), getIv(), getKey());
            Path savePath = Path.of(getFilePath() + ".bak");
            if (!FileTool.saveFile(savePath, out)) {
                return false;
            }
            for (int i = 0; i < 10; i++) {
                try {
                    FileTool.moveAndReplace(savePath, Path.of(getFilePath()));
                    setNewFile(false);
                    return true;
                } catch (IOException e) {
                    if (i == 0) {
                        System.gc();
                    } else if (i == 9) {
                        log.error("{} 替换 {} 失败: {}", savePath, Path.of(getFilePath()), e.getMessage());
                    }
                    Thread.sleep(500);
                }
            }
            return true;
        } catch (Exception e) {
            log.error("MS 保存失败 {}: {}", getName(), e.getMessage(), e);
            return false;
        }
    }

    public void exportFileToImg(Path basePath, List<Pair<WzImage, Path>> collector) {
        if (!parse()) {
            throw new RuntimeException("MS 文件解析失败: " + getName());
        }
        String name = getName().replaceAll("(?i)\\.ms$", "");
        Path folder = basePath.resolve(name);
        try {
            FileTool.createDirectory(folder);
        } catch (IOException e) {
            throw new BizException(ExceptionEnum.INTERNAL_SERVER_ERROR, "目录操作失败: " + folder + ", " + e.getMessage());
        }
        for (WzImageProperty wrapper : getChildren()) {
            collector.add(new Pair<>(toExportImage(wrapper), folder.resolve(resolveImgFileName(wrapper.getName()))));
        }
    }

    public void exportFileToXml(Path basePath, List<Pair<WzImage, Path>> collector) {
        if (!parse()) {
            throw new RuntimeException("MS 文件解析失败: " + getName());
        }
        Path folder = basePath.resolve(getName());
        try {
            FileTool.createDirectory(folder);
        } catch (IOException e) {
            throw new BizException(ExceptionEnum.INTERNAL_SERVER_ERROR, "目录操作失败: " + folder + ", " + e.getMessage());
        }
        for (WzImageProperty wrapper : getChildren()) {
            String filename = resolveImgFileName(wrapper.getName()) + ".xml";
            collector.add(new Pair<>(toExportImage(wrapper), folder.resolve(filename)));
        }
    }

    public void exportFileToJson(Path basePath, List<Pair<WzImage, Path>> collector) {
        exportFileToJson(basePath, collector, false);
    }

    public void exportFileToJson(Path basePath, List<Pair<WzImage, Path>> collector, boolean mergeIntoParent) {
        if (!parse()) {
            throw new RuntimeException("MS 文件解析失败: " + getName());
        }
        Path folder = mergeIntoParent
                ? basePath
                : basePath.resolve(JsonExport.resolveExportRootFolderName(getName()));
        try {
            FileTool.createDirectory(folder);
        } catch (IOException e) {
            throw new BizException(ExceptionEnum.INTERNAL_SERVER_ERROR, "目录操作失败: " + folder + ", " + e.getMessage());
        }
        for (WzImageProperty wrapper : getChildren()) {
            String filename = JsonExport.resolveJsonFileName(resolveImgFileName(wrapper.getName()));
            collector.add(new Pair<>(toExportImage(wrapper), folder.resolve(filename)));
        }
    }

    public WzImage toExportImage(WzImageProperty wrapper) {
        WzImage image = new WzImage(wrapper.getName(), null, new BinaryReader(getIv(), getKey()));
        for (WzImageProperty child : wrapper.getChildren()) {
            image.addChild(child.deepClone(image), true);
        }
        image.setChildrenWzImage();
        image.setStatus(WzFileStatus.PARSE_SUCCESS);
        return image;
    }

    private static String resolveImgFileName(String name) {
        return name.endsWith(".img") ? name : name + ".img";
    }

    public boolean saveAsWz(Path outPath) {
        try {
            WzFile wzFile = WzFile.createNewFile(outPath.toString(), (short) 0, getKeyBoxName(), getIv(), getKey());
            WzDirectory root = wzFile.getWzDirectory();
            for (WzMsFile.EntryImage entry : buildEntriesForSave()) {
                WzImage image = entry.getImage().deepClone(root);
                image.setReader(new orange.wz.provider.tools.BinaryReader(new WzMutableKey(getIv(), getKey())));
                image.setChildrenWzImage();
                root.addChild(image);
            }
            return wzFile.save();
        } catch (Exception e) {
            log.error("MS 另存为 WZ 失败: {}", e.getMessage(), e);
            return false;
        }
    }

    private List<WzMsFile.EntryImage> buildEntriesForSave() {
        List<WzMsFile.EntryImage> result = new ArrayList<>();
        List<WzImageProperty> wrappers = getChildren();
        for (int i = 0; i < wrappers.size(); i++) {
            WzImageProperty wrapper = wrappers.get(i);
            String entryName;
            byte[] entryKey = null;
            int flags = 0;
            int unk1 = 0;
            int unk2 = 0;
            if (i < sourceEntries.size()) {
                WzMsFile.EntryImage old = sourceEntries.get(i);
                entryName = old.getEntryName();
                entryKey = old.getEntryKey() == null ? null : Arrays.copyOf(old.getEntryKey(), old.getEntryKey().length);
                flags = old.getFlags();
                unk1 = old.getUnk1();
                unk2 = old.getUnk2();
            } else {
                String category = getName().replaceAll("(?i)\\.ms$", "");
                entryName = category + "/" + wrapper.getName();
                if (!entryName.endsWith(".img")) {
                    entryName += ".img";
                }
            }

            WzImage image = new WzImage(wrapper.getName(), null, new orange.wz.provider.tools.BinaryReader(getIv(), getKey()));
            for (WzImageProperty child : wrapper.getChildren()) {
                image.addChild(child.deepClone(image), true);
            }
            image.setChildrenWzImage();
            image.setStatus(WzFileStatus.PARSE_SUCCESS);
            image.setChanged(true);
            result.add(new WzMsFile.EntryImage(entryName, image, flags, unk1, unk2, entryKey));
        }
        return result;
    }
}
