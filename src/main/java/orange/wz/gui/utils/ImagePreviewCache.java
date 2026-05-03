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

    private static final int MAX_CACHE_SIZE = 7;

    private final LinkedHashMap<String, ImagePreviewData> cache;

    public ImagePreviewCache() {
        this.cache = new LinkedHashMap<String, ImagePreviewData>(MAX_CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, ImagePreviewData> eldest) {
                boolean shouldRemove = size() > MAX_CACHE_SIZE;
                if (shouldRemove) {
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
        cache.put(key, data);
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
        cache.clear();
    }

    /**
     * 移除指定键的缓存
     */
    public void remove(String key) {
        cache.remove(key);
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
            cache.remove(key);
            log.debug("Removed cached entry: {}", key);
        }
    }
}
