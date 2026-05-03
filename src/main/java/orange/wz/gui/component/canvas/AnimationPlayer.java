package orange.wz.gui.component.canvas;

import lombok.Getter;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.function.Consumer;

/**
 * 动画播放器
 */
public class AnimationPlayer {
    
    private final List<AnimationFrame> frames;
    private Timer timer;
    @Getter
    private int currentFrameIndex = 0;
    @Getter
    private boolean playing = false;
    
    private Consumer<AnimationFrame> frameChangeListener;
    
    public AnimationPlayer(List<AnimationFrame> frames) {
        this.frames = frames;
    }
    
    /**
     * 设置帧变化监听器
     */
    public void setFrameChangeListener(Consumer<AnimationFrame> listener) {
        this.frameChangeListener = listener;
    }
    
    /**
     * 播放
     */
    public void play() {
        if (playing || frames.isEmpty()) return;
        
        playing = true;
        scheduleNextFrame();
    }
    
    /**
     * 暂停
     */
    public void pause() {
        playing = false;
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }
    
    /**
     * 停止（回到第一帧）
     */
    public void stop() {
        pause();
        currentFrameIndex = 0;
        notifyFrameChange();
    }
    
    /**
     * 重新开始
     */
    public void restart() {
        stop();
        play();
    }
    
    /**
     * 获取当前帧
     */
    public AnimationFrame getCurrentFrame() {
        if (frames.isEmpty()) return null;
        return frames.get(currentFrameIndex);
    }
    
    /**
     * 调度下一帧
     */
    private void scheduleNextFrame() {
        if (!playing || frames.isEmpty()) return;
        
        AnimationFrame frame = frames.get(currentFrameIndex);
        int delay = frame.getDelayMs();
        
        timer = new Timer(delay, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!playing) return;
                
                currentFrameIndex = (currentFrameIndex + 1) % frames.size();
                notifyFrameChange();
                scheduleNextFrame();
            }
        });
        timer.setRepeats(false);
        timer.start();
    }
    
    /**
     * 通知帧变化
     */
    private void notifyFrameChange() {
        if (frameChangeListener != null) {
            frameChangeListener.accept(getCurrentFrame());
        }
    }
    
    /**
     * 清理资源
     */
    public void dispose() {
        pause();
        frameChangeListener = null;
    }
}
