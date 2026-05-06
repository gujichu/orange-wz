package orange.wz.gui.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 图片预览缓存（LRU 策略）
 */
@Slf4j
public class ImagePreviewCache {

    private final int maxCacheSize;
    private final LinkedHashMap<String, ImagePreviewData> cache;

    public ImagePreviewCache(int maxCacheSize) {
        this.maxCacheSize = Math.max(1, maxCacheSize);
        this.cache = new LinkedHashMap<String, ImagePreviewData>(this.maxCacheSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, ImagePreviewData> eldest) {
                boolean shouldRemove = size() > ImagePreviewCache.this.maxCacheSize;
                if (shouldRemove) {
                    ImagePreviewData v = eldest.getValue();
                    if (v != null) {
                        v.discardPixelData();
                    }
                    log.debug("Cache full, removing oldest entry: {}", eldest.getKey());
                }
                return shouldRemove;
            }
        };
    }

    /**
     * 获取缓存
     */
    public ImagePreviewData get(String key) {
        return cache.get(key);
    }

    /**
     * 存入缓存
     */
    public void put(String key, ImagePreviewData data) {
        ImagePreviewData previous = cache.put(key, data);
        if (previous != null && previous != data) {
            previous.discardPixelData();
        }
        log.debug("Cached: {} (cache size: {})", key, cache.size());
    }

    /**
     * 是否包含
     */
    public boolean contains(String key) {
        return cache.containsKey(key);
    }

    /**
     * 清空缓存
     */
    public void clear() {
        for (ImagePreviewData v : new ArrayList<>(cache.values())) {
            if (v != null) {
                v.discardPixelData();
            }
        }
        cache.clear();
    }

    /**
     * 移除指定键的缓存
     */
    public void remove(String key) {
        ImagePreviewData removed = cache.remove(key);
        if (removed != null) {
            removed.discardPixelData();
        }
    }

    /**
     * 读取节点预览：先精确 path+过滤后缀，再尝试 path#p0+过滤后缀
     */
    public ImagePreviewData getPreviewForNodePath(String path, String filterSuffix) {
        String fs = filterSuffix != null ? filterSuffix : "";
        ImagePreviewData d = get(path + fs);
        if (d != null) {
            return d;
        }
        return get(path + "#p0" + fs);
    }

    /**
     * 按 {@link ImagePreviewData#buildCacheKey()} 写入（关闭预览缓存时不写入）
     */
    public void putPreview(ImagePreviewData data) {
        if (data == null || data.getRootNode() == null) {
            return;
        }
        if (!AnimationPreviewConfigIni.getOptions().isPreviewCacheEnabled()) {
            return;
        }
        put(data.buildCacheKey(), data);
    }

    /**
     * 移除某节点预览（含 path#p*、path#pf* 等后缀条目）
     */
    public void removePreviewGroup(String basePath) {
        List<String> keysToRemove = new ArrayList<>();
        for (String key : cache.keySet()) {
            if (key.equals(basePath) || key.startsWith(basePath + "#")) {
                keysToRemove.add(key);
            }
        }
        for (String key : keysToRemove) {
            ImagePreviewData removed = cache.remove(key);
            if (removed != null) {
                removed.discardPixelData();
            }
        }
    }

    /**
     * 分页预览专用：只保留当前页的预览数据，移除同节点其它页及非分页键，释放动画/图片像素占用。
     */
    public void retainOnlyPagedPreview(String basePath, int pageIndex, String filterSuffix) {
        String fs = filterSuffix != null ? filterSuffix : "";
        String exactKeep = basePath + "#p" + pageIndex + fs;
        String exactKeepLegacy = basePath + "#p" + pageIndex;
        List<String> keysToRemove = new ArrayList<>();
        for (String key : cache.keySet()) {
            if (key.equals(exactKeep) || key.equals(exactKeepLegacy)) {
                continue;
            }
            if (key.equals(basePath)) {
                keysToRemove.add(key);
                continue;
            }
            if (key.startsWith(basePath + "#")) {
                keysToRemove.add(key);
            }
        }
        for (String key : keysToRemove) {
            ImagePreviewData removed = cache.remove(key);
            if (removed != null) {
                removed.discardPixelData();
            }
            log.debug("分页预览释放其它页缓存: {}", key);
        }
    }

    /**
     * 移除指定路径前缀的所有缓存
     * 例如传入 "Skill.wz"，会移除所有 "Skill.wz/..." 开头的键
     */
    public void removeByPrefix(String prefix) {
        List<String> keysToRemove = new ArrayList<>();
        for (String key : cache.keySet()) {
            if (key.startsWith(prefix)) {
                keysToRemove.add(key);
            }
        }
        for (String key : keysToRemove) {
            ImagePreviewData removed = cache.remove(key);
            if (removed != null) {
                removed.discardPixelData();
            }
            log.debug("Removed cached entry: {}", key);
        }
    }
}
