package orange.wz.gui.video;

import lombok.extern.slf4j.Slf4j;
import orange.wz.gui.utils.FfmpegHelper;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 通过 FFmpeg 将 MCV0（IVF 封装的 VP8/VP9）解码为 {@link BufferedImage} 序列。
 */
@Slf4j
public final class CanvasVideoFfmpegDecoder {

    private static final int VP80 = 0x30385056;
    private static final int VP90 = 0x30395056;

    private CanvasVideoFfmpegDecoder() {
    }

    public record DecodedVideo(List<BufferedImage> frames, int[] delayMillis) {
    }

    public static DecodedVideo decode(byte[] mcvPayload) throws IOException, InterruptedException {
        McvHeader header = McvFileParser.parse(mcvPayload);
        int fourCc = header.getFourCc();
        if (fourCc != VP80 && fourCc != VP90) {
            throw new IOException("不支持的 VPX FourCC: 0x" + Integer.toHexString(fourCc));
        }
        Path ffmpeg = FfmpegHelper.getFfmpegPath();
        McvFrameInfo[] frameInfos = header.getFrames();
        List<byte[]> colorChunks = new ArrayList<>();
        for (McvFrameInfo fi : frameInfos) {
            colorChunks.add(slice(mcvPayload, fi.getDataOffset(), fi.getDataCount()));
        }
        byte[] colorIvf = IvfMuxer.buildIvf(fourCc, header.getWidth(), header.getHeight(), colorChunks);
        List<BufferedImage> colors = decodeIvfToBgraFrames(ffmpeg, colorIvf, header.getWidth(), header.getHeight(),
                frameInfos.length);

        boolean anyAlpha = (header.getDataFlags() & McvDataFlags.ALPHA_MAP) != 0
                && Arrays.stream(frameInfos).anyMatch(CanvasVideoFfmpegDecoder::hasAlphaChunk);
        if (anyAlpha) {
            boolean allAlpha = Arrays.stream(frameInfos).allMatch(CanvasVideoFfmpegDecoder::hasAlphaChunk);
            if (allAlpha) {
                List<byte[]> alphaChunks = new ArrayList<>();
                for (McvFrameInfo fi : frameInfos) {
                    alphaChunks.add(slice(mcvPayload, fi.getAlphaDataOffset(), fi.getAlphaDataCount()));
                }
                byte[] alphaIvf = IvfMuxer.buildIvf(fourCc, header.getWidth(), header.getHeight(), alphaChunks);
                List<BufferedImage> alphas = decodeIvfToBgraFrames(ffmpeg, alphaIvf, header.getWidth(), header.getHeight(),
                        frameInfos.length);
                for (int i = 0; i < colors.size(); i++) {
                    mergeAlphaMap(colors.get(i), alphas.get(i));
                }
            } else {
                for (int i = 0; i < frameInfos.length; i++) {
                    McvFrameInfo fi = frameInfos[i];
                    if (hasAlphaChunk(fi)) {
                        byte[] one = IvfMuxer.buildIvf(fourCc, header.getWidth(), header.getHeight(),
                                List.of(slice(mcvPayload, fi.getAlphaDataOffset(), fi.getAlphaDataCount())));
                        List<BufferedImage> a = decodeIvfToBgraFrames(ffmpeg, one, header.getWidth(), header.getHeight(), 1);
                        mergeAlphaMap(colors.get(i), a.get(0));
                    }
                }
            }
        }

        int[] delays = new int[frameInfos.length];
        for (int i = 0; i < frameInfos.length; i++) {
            long ns = frameInfos[i].getDelayNanoseconds();
            long msLong = ns / 1_000_000L;
            if (msLong < 0) {
                msLong = 0;
            }
            int ms = (int) Math.min(Integer.MAX_VALUE, msLong);
            delays[i] = Math.max(1, ms);
        }
        return new DecodedVideo(colors, delays);
    }

    private static boolean hasAlphaChunk(McvFrameInfo fi) {
        return fi.getAlphaDataCount() > 0 && fi.getAlphaDataOffset() >= 0;
    }

    private static byte[] slice(byte[] data, long offset, int len) {
        if (len <= 0) {
            return new byte[0];
        }
        if (offset < 0 || offset > data.length) {
            throw new IllegalArgumentException("帧数据偏移越界");
        }
        int o = (int) offset;
        if (o + len > data.length) {
            throw new IllegalArgumentException("帧数据长度越界");
        }
        byte[] r = new byte[len];
        System.arraycopy(data, o, r, 0, len);
        return r;
    }

    /**
     * 与 WzComparerR2 {@code MergeAlphaMap} 一致：用 alpha 贴图的 BGRA 第 3 字节（R）写入颜色贴图 alpha。
     */
    public static void mergeAlphaMap(BufferedImage color, BufferedImage alphaMap) {
        int w = color.getWidth();
        int h = color.getHeight();
        int[] c = new int[w * h];
        int[] a = new int[w * h];
        color.getRGB(0, 0, w, h, c, 0, w);
        alphaMap.getRGB(0, 0, w, h, a, 0, w);
        for (int i = 0; i < c.length; i++) {
            int argb = c[i];
            int aa = (a[i] >> 16) & 0xff;
            c[i] = (aa << 24) | (argb & 0xffffff);
        }
        color.setRGB(0, 0, w, h, c, 0, w);
    }

    private static List<BufferedImage> decodeIvfToBgraFrames(Path ffmpeg, byte[] ivfBytes, int width, int height,
                                                             int expectedFrames)
            throws IOException, InterruptedException {
        Path tmp = Files.createTempFile("wzvideo", ".ivf");
        try {
            Files.write(tmp, ivfBytes);
            return decodeIvfFileToBgraFrames(ffmpeg, tmp, width, height, expectedFrames);
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
        }
    }

    private static List<BufferedImage> decodeIvfFileToBgraFrames(Path ffmpeg, Path ivfFile, int width, int height,
                                                                 int expectedFrames)
            throws IOException, InterruptedException {
        List<String> cmd = List.of(
                ffmpeg.toAbsolutePath().toString(),
                "-loglevel", "error",
                "-y",
                "-f", "ivf",
                "-i", ivfFile.toAbsolutePath().toString(),
                "-f", "rawvideo",
                "-pix_fmt", "bgra",
                "-"
        );
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectError(ProcessBuilder.Redirect.PIPE);
        Process p = pb.start();
        Thread drainErr = new Thread(() -> drainQuietly(p.getErrorStream()), "ffmpeg-stderr");
        drainErr.setDaemon(true);
        drainErr.start();
        List<BufferedImage> out = new ArrayList<>();
        try (InputStream in = p.getInputStream()) {
            int stride = width * 4;
            byte[] row = new byte[stride];
            for (int f = 0; f < expectedFrames; f++) {
                BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < height; y++) {
                    readFully(in, row);
                    for (int x = 0; x < width; x++) {
                        int bi = x * 4;
                        int b = row[bi] & 0xff;
                        int g = row[bi + 1] & 0xff;
                        int r = row[bi + 2] & 0xff;
                        int a = row[bi + 3] & 0xff;
                        img.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                    }
                }
                out.add(img);
            }
        }
        int code = p.waitFor();
        if (code != 0) {
            throw new IOException("FFmpeg 解码失败，退出码: " + code);
        }
        if (out.size() != expectedFrames) {
            throw new IOException("解码帧数不匹配: " + out.size() + " / " + expectedFrames);
        }
        return out;
    }

    private static void readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        int need = buf.length;
        while (need > 0) {
            int n = in.read(buf, off, need);
            if (n < 0) {
                throw new IOException("视频流意外结束");
            }
            off += n;
            need -= n;
        }
    }

    private static void drainQuietly(InputStream err) {
        try {
            err.transferTo(java.io.OutputStream.nullOutputStream());
        } catch (IOException ignored) {
        }
    }

    /**
     * 将 IVF 中的 VP 流无损封装为 WebM（失败时退回重新编码）。
     */
    public static void exportWebmCopy(byte[] mcvPayload, Path outputWebm) throws IOException, InterruptedException {
        McvHeader header = McvFileParser.parse(mcvPayload);
        int fourCc = header.getFourCc();
        if (fourCc != VP80 && fourCc != VP90) {
            throw new IOException("不支持的 VPX FourCC: 0x" + Integer.toHexString(fourCc));
        }
        Path ffmpeg = FfmpegHelper.getFfmpegPath();
        McvFrameInfo[] frameInfos = header.getFrames();
        List<byte[]> colorChunks = new ArrayList<>();
        for (McvFrameInfo fi : frameInfos) {
            colorChunks.add(slice(mcvPayload, fi.getDataOffset(), fi.getDataCount()));
        }
        byte[] colorIvf = IvfMuxer.buildIvf(fourCc, header.getWidth(), header.getHeight(), colorChunks);
        Path tmpIvf = Files.createTempFile("wzvideo-export", ".ivf");
        try {
            Files.write(tmpIvf, colorIvf);
            List<String> copyCmd = new ArrayList<>();
            copyCmd.add(ffmpeg.toAbsolutePath().toString());
            copyCmd.add("-y");
            copyCmd.add("-f");
            copyCmd.add("ivf");
            copyCmd.add("-i");
            copyCmd.add(tmpIvf.toAbsolutePath().toString());
            copyCmd.add("-c:v");
            copyCmd.add("copy");
            copyCmd.add(outputWebm.toAbsolutePath().toString());
            if (runFfmpeg(copyCmd) != 0) {
                String vcodec = fourCc == VP90 ? "libvpx-vp9" : "libvpx";
                List<String> encCmd = List.of(
                        ffmpeg.toAbsolutePath().toString(),
                        "-y",
                        "-f", "ivf",
                        "-i", tmpIvf.toAbsolutePath().toString(),
                        "-c:v", vcodec,
                        "-b:v", "0",
                        "-crf", "32",
                        "-an",
                        outputWebm.toAbsolutePath().toString()
                );
                if (runFfmpeg(encCmd) != 0) {
                    throw new IOException("导出 WebM 失败");
                }
            }
        } finally {
            Files.deleteIfExists(tmpIvf);
        }
    }

    private static int runFfmpeg(List<String> cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process p = pb.start();
        p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
        return p.waitFor();
    }

    /**
     * 将已解码帧按固定帧率 {@link VideoPlaybackConstants#EXPORT_FRAMERATE_EXPR}（每帧 60ms）导出为 WebM 或 MP4。
     * WebM 使用 yuva420p 保留透明；MP4 使用 yuv420p（透明铺白底）。
     */
    public static void exportVideoFromFrames(List<BufferedImage> frames, Path outputFile, boolean asMp4)
            throws IOException, InterruptedException {
        if (frames == null || frames.isEmpty()) {
            throw new IOException("没有可导出的帧");
        }
        Path ffmpeg = FfmpegHelper.getFfmpegPath();
        Path tmpDir = Files.createTempDirectory("wzvideo-export-seq");
        try {
            for (int i = 0; i < frames.size(); i++) {
                BufferedImage src = frames.get(i);
                BufferedImage out = asMp4 ? flattenOnWhite(src) : src;
                Path f = tmpDir.resolve(String.format("frame_%04d.png", i));
                ImageIO.write(out, "png", f.toFile());
            }
            String pattern = tmpDir.resolve("frame_%04d.png").toAbsolutePath().toString().replace("\\", "/");
            List<String> cmd = new ArrayList<>();
            cmd.add(ffmpeg.toAbsolutePath().toString());
            cmd.add("-y");
            cmd.add("-framerate");
            cmd.add(VideoPlaybackConstants.EXPORT_FRAMERATE_EXPR);
            cmd.add("-i");
            cmd.add(pattern);
            if (asMp4) {
                cmd.add("-c:v");
                cmd.add("libx264");
                cmd.add("-pix_fmt");
                cmd.add("yuv420p");
                cmd.add("-movflags");
                cmd.add("+faststart");
            } else {
                cmd.add("-c:v");
                cmd.add("libvpx");
                cmd.add("-auto-alt-ref");
                cmd.add("0");
                cmd.add("-pix_fmt");
                cmd.add("yuva420p");
                cmd.add("-b:v");
                cmd.add("0");
                cmd.add("-crf");
                cmd.add("32");
                cmd.add("-an");
            }
            cmd.add(outputFile.toAbsolutePath().toString().replace("\\", "/"));
            if (runFfmpeg(cmd) != 0) {
                throw new IOException("导出视频失败（FFmpeg 退出码非 0）");
            }
        } finally {
            try (var stream = Files.list(tmpDir)) {
                for (Path p : stream.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(p);
                }
            }
            Files.deleteIfExists(tmpDir);
        }
    }

    private static BufferedImage flattenOnWhite(BufferedImage src) {
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, src.getWidth(), src.getHeight());
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

}
