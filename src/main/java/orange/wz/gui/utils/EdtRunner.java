package orange.wz.gui.utils;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;

/**
 * 在 Swing 事件调度线程上执行 UI 相关操作；可从后台线程安全调用。
 */
public final class EdtRunner {

    private EdtRunner() {
    }

    public static void run(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(task);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("EDT 任务被中断", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("EDT 任务执行失败", cause);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T call(EdtCallable<T> task) {
        if (SwingUtilities.isEventDispatchThread()) {
            return task.call();
        }
        Object[] holder = new Object[1];
        EdtRunner.run(() -> holder[0] = task.call());
        return (T) holder[0];
    }

    @FunctionalInterface
    public interface EdtCallable<T> {
        T call();
    }
}
