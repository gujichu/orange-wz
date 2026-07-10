package orange.wz.gui.utils;

import lombok.extern.slf4j.Slf4j;
import orange.wz.gui.MainFrame;

import javax.swing.*;
import java.util.concurrent.ExecutionException;

/**
 * 统一处理 {@link SwingWorker} 完成时的异常，避免在 EDT 上抛出未捕获异常。
 */
@Slf4j
public final class SwingWorkerHelper {

    private SwingWorkerHelper() {
    }

    public static void finish(SwingWorker<?, ?> worker) {
        finish(worker, null);
    }

    public static void finish(SwingWorker<?, ?> worker, Runnable onSuccess) {
        try {
            worker.get();
            if (onSuccess != null) {
                onSuccess.run();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("后台任务被中断", e);
            MainFrame.getInstance().setStatusText("操作已中断");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("后台任务失败", cause);
            String message = cause.getMessage();
            if (message == null || message.isBlank()) {
                message = "操作失败，请查看日志";
            }
            MainFrame.getInstance().setStatusText(message);
            JMessageUtil.error(message);
        }
    }
}
