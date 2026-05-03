package orange.wz.gui.utils;

import com.madgag.gif.fmsware.AnimatedGifEncoder;
import lombok.extern.slf4j.Slf4j;
import orange.wz.gui.component.canvas.AnimationFrame;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * GIF 生成器
 */
@Slf4j
public class GifGenerator {
    
    private static final int FFmpeg_THRESHOLD = 50; // 提高阈值，减少使用 FFmpeg 的情况
    
    /**
     * 生成 GIF
     */
    public static void generate(ImagePreviewData.AnimationData animation, File outputFile) throws Exception {
        List<AnimationFrame> frames = animation.getFrames();
        
        if (frames.size() >= FFmpeg_THRESHOLD) {
            log.info("Using FFmpeg for {} frames", frames.size());
            generateWithFfmpeg(frames, outputFile);
        } else {
            log.info("Using Java library for {} frames", frames.size());
            generateWithJava(frames, outputFile);
        }
    }
    
    /**
     * 使用 Java 库生成（高质量，但更快）
     */
    private static void generateWithJava(List<AnimationFrame> frames, File outputFile) throws IOException {
        // 首先计算画布尺寸（基于 origin 对齐）
        int[] canvasSize = calculateCanvasSize(frames);
        int canvasWidth = canvasSize[0];
        int canvasHeight = canvasSize[1];
        int originOffsetX = canvasSize[2];
        int originOffsetY = canvasSize[3];
        
        AnimatedGifEncoder encoder = new AnimatedGifEncoder();
        encoder.start(outputFile.getAbsolutePath());
        encoder.setRepeat(0); // 无限循环
        encoder.setQuality(5); // 平衡质量和速度（1=最高,20=最低,5是很好的平衡点）
        
        for (AnimationFrame frame : frames) {
            // 按照 origin 对齐绘制
            BufferedImage alignedImg = alignWithOrigin(frame, originOffsetX, originOffsetY, canvasWidth, canvasHeight);
            encoder.setDelay(frame.getDelayMs());
            encoder.addFrame(alignedImg);
        }
        
        encoder.finish();
        log.info("GIF generated successfully with {} frames, size={}x{}", frames.size(), canvasWidth, canvasHeight);
    }
    
    /**
     * 计算画布尺寸，基于所有帧的 origin 锚点
     * 返回 [canvasWidth, canvasHeight, originOffsetX, originOffsetY]
     * 
     * Origin 属性含义：
     * - originX, originY 表示图片中的锚点相对于图片左上角的偏移
     * - 播放动画时，所有图片的锚点应该对齐到同一个位置
     */
    private static int[] calculateCanvasSize(List<AnimationFrame> frames) {
        // 首先收集所有帧的 origin 信息用于调试
        if (log.isDebugEnabled()) {
            for (int i = 0; i < frames.size(); i++) {
                AnimationFrame frame = frames.get(i);
                BufferedImage img = frame.getImage();
                log.debug("Frame {}: origin=({},{}) size={}x{}", i, frame.getOriginX(), frame.getOriginY(), img.getWidth(), img.getHeight());
            }
        }
        
        // 计算所有图片相对于 origin 锚点的边界
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        
        for (AnimationFrame frame : frames) {
            BufferedImage img = frame.getImage();
            int originX = frame.getOriginX();
            int originY = frame.getOriginY();
            
            int imgWidth = img.getWidth();
            int imgHeight = img.getHeight();
            
            // 计算图片四个角相对于 origin 锚点的位置
            int x1 = -originX; // 图片左上角相对于 origin 的 X
            int y1 = -originY; // 图片左上角相对于 origin 的 Y
            int x2 = x1 + imgWidth; // 图片右下角相对于 origin 的 X
            int y2 = y1 + imgHeight; // 图片右下角相对于 origin 的 Y
            
            minX = Math.min(minX, x1);
            minY = Math.min(minY, y1);
            maxX = Math.max(maxX, x2);
            maxY = Math.max(maxY, y2);
        }
        
        // 计算画布大小
        int canvasWidth = maxX - minX;
        int canvasHeight = maxY - minY;
        
        // 计算 origin 点在画布上的位置
        int originOnCanvasX = -minX;
        int originOnCanvasY = -minY;
        
        log.debug("Canvas size: {}x{}, originOnCanvas=({},{})", canvasWidth, canvasHeight, originOnCanvasX, originOnCanvasY);
        
        return new int[]{canvasWidth, canvasHeight, originOnCanvasX, originOnCanvasY};
    }
    
    /**
     * 按照 origin 锚点对齐图片
     */
    private static BufferedImage alignWithOrigin(AnimationFrame frame, int originOnCanvasX, int originOnCanvasY, int canvasWidth, int canvasHeight) {
        BufferedImage img = frame.getImage();
        
        // 计算图片在画布上的绘制位置
        // 图片的 origin 点应该对齐到画布上的 originOnCanvas 位置
        int drawX = originOnCanvasX - frame.getOriginX();
        int drawY = originOnCanvasY - frame.getOriginY();
        
        BufferedImage newImg = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = newImg.createGraphics();
        
        try {
            // 禁用抗锯齿，保持原始直角边缘
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            
            // 按照 origin 锚点对齐绘制原图
            g2d.drawImage(img, drawX, drawY, null);
        } finally {
            g2d.dispose();
        }
        
        return newImg;
    }
    
    /**
     * 使用 FFmpeg 生成（高质量且快速）
     */
    private static void generateWithFfmpeg(List<AnimationFrame> frames, File outputFile) throws Exception {
        Path ffmpegPath = FfmpegHelper.getFfmpegPath();
        Path tempDir = Files.createTempDirectory("gif_frames");
        
        try {
            // 首先计算画布尺寸
            int[] canvasSize = calculateCanvasSize(frames);
            int canvasWidth = canvasSize[0];
            int canvasHeight = canvasSize[1];
            int originOffsetX = canvasSize[2];
            int originOffsetY = canvasSize[3];
            
            // 保存帧图片，使用 BMP 格式比 PNG 快得多
            List<Path> frameFiles = new ArrayList<>();
            for (int i = 0; i < frames.size(); i++) {
                Path frameFile = tempDir.resolve(String.format("frame_%04d.bmp", i));
                AnimationFrame frame = frames.get(i);
                // 按照 origin 对齐绘制
                BufferedImage alignedImg = alignWithOrigin(frame, originOffsetX, originOffsetY, canvasWidth, canvasHeight);
                // BMP 格式写入比 PNG 快很多
                ImageIO.write(alignedImg, "BMP", frameFile.toFile());
                frameFiles.add(frameFile);
            }
            
            // 计算帧率
            int totalDelay = frames.stream().mapToInt(AnimationFrame::getDelayMs).sum();
            double frameRate = (double) frames.size() * 1000 / totalDelay;
            
            // 构建 FFmpeg 命令（高质量但更快的 GIF）
            // 使用简化的调色板生成方式
            List<String> cmd = new ArrayList<>();
            cmd.add(ffmpegPath.toAbsolutePath().toString());
            cmd.add("-y");
            cmd.add("-framerate");
            cmd.add(String.valueOf(frameRate));
            cmd.add("-i");
            cmd.add(tempDir.resolve("frame_%04d.bmp").toString());
            
            // 使用快速且质量良好的方式
            cmd.add("-filter_complex");
            cmd.add("fps=" + frameRate + ",split[s0][s1];[s0]palettegen=stats_mode=diff[p];[s1][p]paletteuse=dither=floyd_steinberg");
            
            cmd.add("-loop");
            cmd.add("0");
            cmd.add(outputFile.getAbsolutePath());
            
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // 消耗输出流（防止死锁）
            process.getInputStream().transferTo(System.out);
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("FFmpeg exited with code " + exitCode);
            }
            
            log.info("High-quality GIF generated successfully with FFmpeg, size={}x{}", canvasWidth, canvasHeight);
            
        } finally {
            // 清理临时文件
            try {
                Files.list(tempDir).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        log.warn("Failed to delete temp file: {}", path, e);
                    }
                });
                Files.delete(tempDir);
            } catch (IOException e) {
                log.warn("Failed to clean up temp directory", e);
            }
        }
    }
}
