package orange.wz.gui.utils;

import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class EdtRunnerTest {

    @Test
    void run_executesOnCurrentThreadWhenAlreadyOnEdt() throws Exception {
        AtomicBoolean ran = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> EdtRunner.run(() -> ran.set(true)));
        assertTrue(ran.get());
    }

    @Test
    void call_returnsValueFromEdt() {
        assertEquals("ok", EdtRunner.call(() -> "ok"));
    }
}
