package orange.wz.gui.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * FFmpeg 帮助类
 */
@Slf4j
public class FfmpegHelper {
    
    private static Path ffmpegPath;
    private static final Object LOCK = new Object();
    
    /**
     * 获取 FFmpeg 可执行文件路径
     */
    public static Path getFfmpegPath() throws IOException {
        if (ffmpegPath == null) {
            synchronized (LOCK) {
                if (ffmpegPath == null) {
                    ffmpegPath = findFfmpeg();
                }
            }
        }
        return ffmpegPath;
    }
    
    /**
     * 查找 FFmpeg 可执行文件
     */
    private static Path findFfmpeg() throws IOException {
        // 1. 首先检查工作目录下的 tools/ffmpeg.exe
        Path workDirFfmpeg = Paths.get("tools", "ffmpeg.exe");
        if (Files.exists(workDirFfmpeg)) {
            log.info("Found FFmpeg in working directory: {}", workDirFfmpeg.toAbsolutePath());
            return workDirFfmpeg.toAbsolutePath();
        }
        
        // 2. 检查程序所在目录下的 tools/ffmpeg.exe（发布后的结构）
        try {
            Path programDir = new File(FfmpegHelper.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile().toPath();
            Path bundleFfmpeg = programDir.resolve("tools").resolve("ffmpeg.exe");
            if (Files.exists(bundleFfmpeg)) {
                log.info("Found FFmpeg in bundle directory: {}", bundleFfmpeg);
                return bundleFfmpeg;
            }
        } catch (Exception e) {
            log.debug("Could not find program directory, trying other locations", e);
        }
        
        throw new IOException("FFmpeg not found in tools/ffmpeg.exe. Please ensure the file exists.");
    }
}
