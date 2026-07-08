package orange.wz.gui.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import orange.wz.provider.tools.ExportXmlConfigIni;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
public final class LoadHistoryStorage {

    public static final int MAX_ENTRIES = 100;

    private static final String RELATIVE_HISTORY = "tools/history.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type ENTRY_LIST_TYPE = new TypeToken<List<LoadHistoryEntry>>() {
    }.getType();

    private static final LoadHistoryStorage INSTANCE = new LoadHistoryStorage();

    public static LoadHistoryStorage getInstance() {
        return INSTANCE;
    }

    private LoadHistoryStorage() {
    }

    /**
     * 与 {@code tools/config.ini} 相同规则：优先工作目录，其次 jar 同级 tools。
     */
    public static Path resolveHistoryPath() {
        Path workDir = Paths.get(System.getProperty("user.dir", ".")).resolve(RELATIVE_HISTORY).normalize();
        if (Files.isRegularFile(workDir)) {
            return workDir.toAbsolutePath();
        }
        try {
            Path configPath = ExportXmlConfigIni.resolveConfigPath();
            Path toolsDir = configPath.getParent();
            if (toolsDir != null) {
                Path bundle = toolsDir.resolve("history.json").normalize();
                if (Files.isRegularFile(bundle)) {
                    return bundle.toAbsolutePath();
                }
            }
            Path jarParent = Paths.get(LoadHistoryStorage.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getParent();
            if (jarParent != null) {
                Path bundle = jarParent.resolve(RELATIVE_HISTORY).normalize();
                if (Files.isRegularFile(bundle)) {
                    return bundle.toAbsolutePath();
                }
            }
        } catch (Exception ignored) {
        }
        return workDir.toAbsolutePath();
    }

    public synchronized void addEntries(List<File> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        List<LoadHistoryEntry> entries = loadAllMutable();
        long now = System.currentTimeMillis();
        for (File file : files) {
            if (file == null) {
                continue;
            }
            String path = file.getAbsolutePath();
            entries.removeIf(entry -> path.equals(entry.path()));
            entries.add(0, new LoadHistoryEntry(file.getName(), path, now));
        }
        trimToMax(entries);
        save(entries);
    }

    public synchronized List<LoadHistoryEntry> listByTimeDesc() {
        List<LoadHistoryEntry> entries = loadAllMutable();
        entries.sort(Comparator.comparingLong(LoadHistoryEntry::loadTime).reversed());
        return List.copyOf(entries);
    }

    private List<LoadHistoryEntry> loadAllMutable() {
        Path path = resolveHistoryPath();
        migrateLegacyIfNeeded(path);
        if (!Files.isRegularFile(path)) {
            return new ArrayList<>();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<LoadHistoryEntry> entries = GSON.fromJson(reader, ENTRY_LIST_TYPE);
            return entries != null ? new ArrayList<>(entries) : new ArrayList<>();
        } catch (IOException e) {
            log.warn("读取加载历史失败: {}", path, e);
            return new ArrayList<>();
        }
    }

    private void save(List<LoadHistoryEntry> entries) {
        Path path = resolveHistoryPath();
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(entries, ENTRY_LIST_TYPE, writer);
            }
        } catch (IOException e) {
            log.warn("保存加载历史失败: {}", path, e);
        }
    }

    private static void migrateLegacyIfNeeded(Path target) {
        if (Files.isRegularFile(target)) {
            return;
        }
        Path[] legacyPaths = {
                Path.of("load-history.json"),
                Path.of("tools/load-history.json")
        };
        for (Path legacy : legacyPaths) {
            if (!Files.isRegularFile(legacy)) {
                continue;
            }
            try {
                if (target.getParent() != null) {
                    Files.createDirectories(target.getParent());
                }
                Files.move(legacy, target, StandardCopyOption.REPLACE_EXISTING);
                log.info("已迁移加载历史: {} -> {}", legacy, target);
                return;
            } catch (IOException e) {
                log.warn("迁移加载历史失败: {} -> {}", legacy, target, e);
            }
        }
    }

    private static void trimToMax(List<LoadHistoryEntry> entries) {
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(entries.size() - 1);
        }
    }

    public record LoadHistoryEntry(String fileName, String path, long loadTime) {
    }
}
