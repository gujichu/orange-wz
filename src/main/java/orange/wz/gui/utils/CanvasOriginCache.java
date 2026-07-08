package orange.wz.gui.utils;

import orange.wz.gui.component.panel.EditPane;
import orange.wz.provider.WzDirectory;
import orange.wz.provider.WzFile;
import orange.wz.provider.WzFolder;
import orange.wz.provider.WzImage;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.WzObject;
import orange.wz.provider.properties.WzVectorProperty;

import javax.swing.tree.DefaultMutableTreeNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将 _Canvas 图片路径映射到同级目录下的属性 WZ（如 UI_000.wz、Map_000.wz）节点，缓存 origin 描点。
 * 属性 WZ 由实际上级目录推断：{@code Data/Map/_Canvas/...} → {@code Map_000.wz}。
 * 索引在内存中构建（遍历已加载 WzObject，不展开 GUI 树）。
 */
public final class CanvasOriginCache {

    private static final Pattern CANVAS_REL_PATH =
            Pattern.compile("(?:^|/)_Canvas_\\d+\\.wz/(.+)$");
    private static final Pattern CANVAS_WZ_FILE_IN_PATH =
            Pattern.compile("((?:.+/)?_Canvas_\\d+\\.wz)");
    private static final Pattern METADATA_WZ_NAME =
            Pattern.compile("^([A-Za-z][A-Za-z0-9]*)_\\d+\\.wz$");

    public record OriginEntry(boolean nodeFound, boolean hasOrigin, int x, int y,
                              WzImageProperty metaNode, String metaPath) {
        public static OriginEntry notFound() {
            return new OriginEntry(false, false, 0, 0, null, null);
        }

        public static OriginEntry of(WzImageProperty metaNode, String metaPath, int x, int y, boolean hasOrigin) {
            return new OriginEntry(true, hasOrigin, x, y, metaNode, metaPath);
        }

        public boolean found() {
            return nodeFound && metaNode != null;
        }
    }

    private final EditPane editPane;
    private final Map<String, OriginEntry> cacheByCanvasPath = new HashMap<>();
    private Map<String, WzImageProperty> nodeIndex;
    private List<String> metadataWzPaths;
    private Map<String, String> canvasWzFilePathByTreePath;
    private boolean indexBuilt;

    public CanvasOriginCache(EditPane editPane) {
        this.editPane = editPane;
    }

    public void buildIndex() {
        if (indexBuilt) {
            return;
        }
        nodeIndex = new HashMap<>();
        metadataWzPaths = new ArrayList<>();
        canvasWzFilePathByTreePath = new HashMap<>();
        scanLoadedRoots(editPane.getTreeRoot());
        indexBuilt = true;
    }

    public void preload(Collection<String> canvasPaths) {
        buildIndex();
        for (String canvasPath : canvasPaths) {
            cacheByCanvasPath.computeIfAbsent(canvasPath, this::resolve);
        }
    }

    public OriginEntry get(String canvasPath) {
        buildIndex();
        return cacheByCanvasPath.computeIfAbsent(canvasPath, this::resolve);
    }

    public String resolveMetadataPath(String canvasPath) {
        OriginEntry entry = get(canvasPath);
        if (entry.metaPath() != null) {
            return entry.metaPath();
        }
        String relative = extractNodeRelativePath(canvasPath);
        if (relative == null) {
            return null;
        }
        for (String metaBase : sortMetadataWzPaths(metadataWzPaths, inferPreferredPrefix(canvasPath))) {
            String key = findIndexKey(metaBase, relative);
            if (key != null) {
                return key;
            }
        }
        return null;
    }

    public void put(String canvasPath, OriginEntry entry) {
        cacheByCanvasPath.put(canvasPath, entry);
    }

    public void clear() {
        cacheByCanvasPath.clear();
        if (nodeIndex != null) {
            nodeIndex.clear();
        }
        if (metadataWzPaths != null) {
            metadataWzPaths.clear();
        }
        if (canvasWzFilePathByTreePath != null) {
            canvasWzFilePathByTreePath.clear();
        }
        indexBuilt = false;
    }

    public int size() {
        return cacheByCanvasPath.size();
    }

    public static String extractNodeRelativePath(String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            return null;
        }
        String normalized = fullPath.replace('\\', '/');
        Matcher canvasMatcher = CANVAS_REL_PATH.matcher(normalized);
        if (canvasMatcher.find()) {
            return canvasMatcher.group(1);
        }
        int wzEnd = normalized.indexOf(".wz/");
        if (wzEnd > 0) {
            int start = normalized.lastIndexOf('/', wzEnd);
            String wzName = normalized.substring(start + 1, wzEnd + 3);
            if (METADATA_WZ_NAME.matcher(wzName).matches()) {
                return normalized.substring(wzEnd + 4);
            }
        }
        return null;
    }

    public static String extractCanvasWzTreePath(String canvasPath) {
        if (canvasPath == null) {
            return null;
        }
        Matcher m = CANVAS_WZ_FILE_IN_PATH.matcher(canvasPath.replace('\\', '/'));
        if (!m.find()) {
            return null;
        }
        return m.group(1);
    }

    public static String extractCanvasWzFileName(String canvasPath) {
        String treePath = extractCanvasWzTreePath(canvasPath);
        if (treePath == null) {
            return null;
        }
        int slash = treePath.lastIndexOf('/');
        return slash >= 0 ? treePath.substring(slash + 1) : treePath;
    }

    public static String inferMetadataPrefixFromCanvasFilePath(String canvasFilePath) {
        if (canvasFilePath == null || canvasFilePath.isBlank()) {
            return null;
        }
        Path canvasWz = Path.of(canvasFilePath);
        Path canvasDir = canvasWz.getParent();
        if (canvasDir == null || !"_Canvas".equalsIgnoreCase(canvasDir.getFileName().toString())) {
            return null;
        }
        Path typeDir = canvasDir.getParent();
        if (typeDir == null) {
            return null;
        }
        return typeDir.getFileName().toString();
    }

    public static List<String> buildMetaLookupPaths(String canvasPath, List<String> metadataWzPathsInTree,
                                                    String preferredPrefix) {
        String relative = extractNodeRelativePath(canvasPath);
        if (relative == null) {
            return List.of();
        }
        List<String> bases = sortMetadataWzPaths(metadataWzPathsInTree, preferredPrefix);
        if (bases.isEmpty() && preferredPrefix != null) {
            bases = List.of(preferredPrefix + "_000.wz");
        }
        List<String> lookupPaths = new ArrayList<>(bases.size());
        for (String base : bases) {
            lookupPaths.add(base + "/" + relative);
        }
        return lookupPaths;
    }

    static List<String> sortMetadataWzPaths(List<String> paths, String preferredPrefix) {
        if (paths == null || paths.isEmpty()) {
            return List.of();
        }
        String preferred000 = preferredPrefix != null ? preferredPrefix + "_000.wz" : null;
        List<String> sorted = new ArrayList<>(paths);
        sorted.sort(metadataWzPathComparator(preferred000));
        return sorted;
    }

    private OriginEntry resolve(String canvasPath) {
        String normalized = canvasPath.replace('\\', '/');
        if (isMetadataWzPath(normalized)) {
            return resolveFromIndexPath(normalized);
        }

        String relative = extractNodeRelativePath(normalized);
        if (relative == null) {
            return OriginEntry.notFound();
        }

        for (String metaPath : buildMetaLookupPaths(normalized, metadataWzPaths, inferPreferredPrefix(normalized))) {
            OriginEntry entry = resolveFromIndexPath(metaPath);
            if (entry.hasOrigin()) {
                return entry;
            }
        }
        OriginEntry nodeOnly = findNodeWithoutOrigin(normalized, relative);
        if (nodeOnly != null) {
            return nodeOnly;
        }
        return resolveOriginFromParentPaths(normalized, relative);
    }

    private OriginEntry findNodeWithoutOrigin(String canvasPath, String relative) {
        for (String metaPath : buildMetaLookupPaths(canvasPath, metadataWzPaths, inferPreferredPrefix(canvasPath))) {
            OriginEntry entry = resolveFromIndexPath(metaPath);
            if (entry.found()) {
                return entry;
            }
        }
        return null;
    }

    /** 帧节点无 origin 时，向父路径回退查找（如 .../backgrnd/0 → .../backgrnd）。 */
    private OriginEntry resolveOriginFromParentPaths(String canvasPath, String relative) {
        int slash = relative.lastIndexOf('/');
        while (slash > 0) {
            String parentRelative = relative.substring(0, slash);
            for (String metaBase : sortMetadataWzPaths(metadataWzPaths, inferPreferredPrefix(canvasPath))) {
                String key = findIndexKey(metaBase, parentRelative);
                if (key == null) {
                    continue;
                }
                OriginEntry entry = resolveFromIndexPath(key);
                if (entry.hasOrigin()) {
                    return entry;
                }
            }
            slash = parentRelative.lastIndexOf('/');
        }
        return OriginEntry.notFound();
    }

    private OriginEntry resolveFromIndexPath(String metaPath) {
        if (metaPath == null) {
            return OriginEntry.notFound();
        }
        WzImageProperty prop = nodeIndex.get(metaPath);
        String resolvedPath = metaPath;
        if (prop == null) {
            int wzEnd = metaPath.indexOf(".wz/");
            if (wzEnd > 0) {
                String base = metaPath.substring(0, wzEnd + 3);
                String relative = metaPath.substring(wzEnd + 4);
                resolvedPath = findIndexKey(base, relative);
                if (resolvedPath != null) {
                    prop = nodeIndex.get(resolvedPath);
                }
            }
        }
        if (prop == null) {
            return OriginEntry.notFound();
        }
        WzObject originObj = prop.getChild("origin");
        if (originObj instanceof WzVectorProperty vec) {
            return OriginEntry.of(prop, resolvedPath, vec.getX(), vec.getY(), true);
        }
        return OriginEntry.of(prop, resolvedPath, 0, 0, false);
    }

    private String findIndexKey(String metadataBase, String relative) {
        if (metadataBase == null || relative == null) {
            return null;
        }
        String exact = metadataBase + "/" + relative;
        if (nodeIndex.containsKey(exact)) {
            return exact;
        }
        String suffix = "/" + relative;
        String preferredWzName = metadataBase.substring(metadataBase.lastIndexOf('/') + 1);
        String preferredMatch = null;
        String anyMatch = null;
        for (String key : nodeIndex.keySet()) {
            if (!key.endsWith(suffix) && !key.equals(relative)) {
                continue;
            }
            if (key.contains(preferredWzName)) {
                preferredMatch = key;
                break;
            }
            if (anyMatch == null) {
                anyMatch = key;
            }
        }
        return preferredMatch != null ? preferredMatch : anyMatch;
    }

    private String inferPreferredPrefix(String canvasPath) {
        String canvasWzTreePath = extractCanvasWzTreePath(canvasPath);
        if (canvasWzTreePath == null) {
            return null;
        }
        String filePath = canvasWzFilePathByTreePath.get(canvasWzTreePath);
        if (filePath == null) {
            String wzName = extractCanvasWzFileName(canvasPath);
            if (wzName != null) {
                for (Map.Entry<String, String> entry : canvasWzFilePathByTreePath.entrySet()) {
                    if (entry.getKey().endsWith("/" + wzName) || entry.getKey().equals(wzName)) {
                        filePath = entry.getValue();
                        break;
                    }
                }
            }
        }
        return inferMetadataPrefixFromCanvasFilePath(filePath);
    }

    private static boolean isMetadataWzPath(String path) {
        int wzEnd = path.indexOf(".wz/");
        if (wzEnd <= 0) {
            return false;
        }
        int start = path.lastIndexOf('/', wzEnd);
        String wzName = path.substring(start + 1, wzEnd + 3);
        return METADATA_WZ_NAME.matcher(wzName).matches();
    }

    /** 遍历视图中已加载对象（含 WzFolder 内未展开到树的文件）。 */
    private void scanLoadedRoots(DefaultMutableTreeNode root) {
        if (root == null) {
            return;
        }
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
            if (child.getUserObject() instanceof WzObject wzObject) {
                scanWzObject(wzObject);
            }
        }
    }

    private void scanWzObject(WzObject obj) {
        if (obj instanceof WzFolder folder) {
            for (WzObject child : folder.getChildren()) {
                scanWzObject(child);
            }
            return;
        }
        if (obj instanceof WzDirectory dir && dir.isWzFile()) {
            if (METADATA_WZ_NAME.matcher(dir.getName()).matches()) {
                indexMetadataWz(dir);
            } else if (dir.getName().startsWith("_Canvas_")) {
                registerCanvasWzFile(dir);
            }
        }
    }

    private void registerCanvasWzFile(WzDirectory canvasWzDir) {
        WzFile wzFile = canvasWzDir.getWzFile();
        if (wzFile != null && wzFile.getFilePath() != null) {
            canvasWzFilePathByTreePath.put(canvasWzDir.getPath(), wzFile.getFilePath());
        }
    }

    private void indexMetadataWz(WzDirectory wzRoot) {
        String rootPath = wzRoot.getPath();
        metadataWzPaths.add(rootPath);
        if (wzRoot.isWzFile()) {
            WzFile wzFile = wzRoot.getWzFile();
            if (wzFile != null) {
                wzFile.parse();
            }
        }
        indexDirectoryContents(wzRoot, rootPath);
    }

    private void indexDirectoryContents(WzDirectory dir, String metadataRootPath) {
        for (WzImage img : dir.getImages()) {
            if (!img.parse()) {
                continue;
            }
            List<WzImageProperty> children = img.getChildren();
            if (children != null) {
                for (WzImageProperty child : children) {
                    indexPropertyTree(child, metadataRootPath);
                }
            }
        }
        for (WzDirectory sub : dir.getDirectories()) {
            indexDirectoryContents(sub, metadataRootPath);
        }
    }

    private void indexPropertyTree(WzImageProperty prop, String metadataRootPath) {
        String path = prop.getPath();
        nodeIndex.put(path, prop);
        if (prop.isListProperty()) {
            List<WzImageProperty> children = prop.getChildren();
            if (children != null) {
                for (WzImageProperty child : children) {
                    indexPropertyTree(child, metadataRootPath);
                }
            }
        }
    }

    private static Comparator<String> metadataWzPathComparator(String preferred000) {
        return (a, b) -> {
            String nameA = a.substring(a.lastIndexOf('/') + 1);
            String nameB = b.substring(b.lastIndexOf('/') + 1);
            if (preferred000 != null) {
                boolean aPreferred = nameA.equals(preferred000) || a.endsWith("/" + preferred000);
                boolean bPreferred = nameB.equals(preferred000) || b.endsWith("/" + preferred000);
                if (aPreferred && !bPreferred) {
                    return -1;
                }
                if (!aPreferred && bPreferred) {
                    return 1;
                }
            }
            boolean a000 = nameA.endsWith("_000.wz");
            boolean b000 = nameB.endsWith("_000.wz");
            if (a000 && !b000) {
                return -1;
            }
            if (!a000 && b000) {
                return 1;
            }
            return a.compareTo(b);
        };
    }

    public static boolean applyOrigin(WzImageProperty toNode, int x, int y) {
        if (toNode == null) {
            return false;
        }
        WzObject originChild = toNode.getChild("origin");
        if (originChild instanceof WzVectorProperty vec) {
            vec.setX(x);
            vec.setY(y);
        } else {
            WzImage wzImage = toNode.getWzImage();
            toNode.addChild(new WzVectorProperty("origin", x, y, toNode, wzImage));
        }
        toNode.setTempChanged(true);
        if (toNode.getWzImage() != null) {
            toNode.getWzImage().setChanged(true);
        }
        return true;
    }
}
