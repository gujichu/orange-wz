package orange.wz.gui.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 加载技能/能力等节点属性名的中文含义：先读 classpath {@code /tools/info.json}（随 jar/资源打包），
 * 再合并当前工作目录下 {@code tools/info.json}（同名键以磁盘文件为准，便于本地调试追加如 hyper）。
 * 文件不存在、解析失败或没有对应键时均静默跳过，不抛异常；仅保留一份不可变表，避免重复持有大对象。
 * {@link #lookup(String)} 先按原样匹配键，再按忽略大小写（{@link Locale#ROOT}）匹配。
 */
@Slf4j
public final class SkillPropertyInfoJson {
    private static final Map<String, String> ENTRIES;
    /** 小写键 → 释义；仅用于忽略大小写查找，同一小写多键时保留 JSON 中先出现的一条 */
    private static final Map<String, String> ENTRIES_BY_LOWER;

    static {
        Map<String, String> loaded = loadSafely();
        ENTRIES = loaded;
        ENTRIES_BY_LOWER = buildLowerCaseIndex(loaded);
    }

    private SkillPropertyInfoJson() {
    }

    private static Map<String, String> loadSafely() {
        try {
            return load();
        } catch (Throwable t) {
            log.debug("加载属性含义 JSON 已跳过: {}", t.toString());
            return Map.of();
        }
    }

    private static Map<String, String> buildLowerCaseIndex(Map<String, String> src) {
        if (src == null || src.isEmpty()) {
            return Map.of();
        }
        Map<String, String> byLower = new HashMap<>();
        for (Map.Entry<String, String> e : src.entrySet()) {
            String low = e.getKey().toLowerCase(Locale.ROOT);
            byLower.putIfAbsent(low, e.getValue());
        }
        return Collections.unmodifiableMap(byLower);
    }

    private static Map<String, String> load() {
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, String>>() {
        }.getType();

        Map<String, String> fromClasspath = readFromClasspath(gson, type);
        Map<String, String> fromFile = readFromWorkingDirFile(gson, type);

        if (fromFile.isEmpty()) {
            return fromClasspath;
        }
        if (fromClasspath.isEmpty()) {
            return fromFile;
        }
        Map<String, String> merged = new HashMap<>(fromClasspath);
        merged.putAll(fromFile);
        return Collections.unmodifiableMap(merged);
    }

    private static Map<String, String> readFromClasspath(Gson gson, Type type) {
        try (InputStream in = SkillPropertyInfoJson.class.getResourceAsStream("/tools/info.json")) {
            if (in == null) {
                return Map.of();
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return normalizeOrEmpty(gson.fromJson(reader, type));
            }
        } catch (Exception e) {
            log.debug("classpath /tools/info.json 不可用，跳过: {}", e.getMessage());
            return Map.of();
        }
    }

    private static Map<String, String> readFromWorkingDirFile(Gson gson, Type type) {
        try {
            Path p = Path.of(System.getProperty("user.dir", "."), "tools", "info.json");
            if (!Files.isRegularFile(p)) {
                return Map.of();
            }
            try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                return normalizeOrEmpty(gson.fromJson(br, type));
            }
        } catch (Exception e) {
            log.debug("工作目录 tools/info.json 不可用，跳过: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 丢弃 null 键值与空键，避免异常数据；空则返回不可变的空表。
     */
    private static Map<String, String> normalizeOrEmpty(Map<String, String> parsed) {
        if (parsed == null || parsed.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, String> e : parsed.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            if (k == null || v == null) {
                continue;
            }
            k = k.trim();
            v = v.trim();
            if (k.isEmpty() || v.isEmpty()) {
                continue;
            }
            out.put(k, v);
        }
        if (out.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * @return 有释义则返回文本；文件未加载、无键、空串均返回 {@code null}。先精确匹配键，再按忽略大小写匹配。
     */
    public static String lookup(String propertyName) {
        if (propertyName == null) {
            return null;
        }
        String key = propertyName.trim();
        if (key.isEmpty() || ENTRIES.isEmpty()) {
            return null;
        }
        String exact = ENTRIES.get(key);
        if (exact != null) {
            return exact;
        }
        return ENTRIES_BY_LOWER.get(key.toLowerCase(Locale.ROOT));
    }
}
