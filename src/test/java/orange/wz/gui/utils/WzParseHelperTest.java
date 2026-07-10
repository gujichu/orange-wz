package orange.wz.gui.utils;

import orange.wz.gui.MainFrame;
import orange.wz.provider.WzImage;
import orange.wz.provider.tools.WzFileStatus;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WzParseHelperTest {

    @Test
    void ensureParsed_returnsTrueWhenParseSucceeds() {
        WzImage image = mock(WzImage.class);
        when(image.parse()).thenReturn(true);

        try (MockedStatic<MainFrame> mainFrame = Mockito.mockStatic(MainFrame.class)) {
            MainFrame frame = mock(MainFrame.class);
            mainFrame.when(MainFrame::getInstance).thenReturn(frame);

            assertTrue(WzParseHelper.ensureParsed(image));
            verify(frame, never()).setStatusText(anyString());
        }
    }

    @Test
    void ensureParsed_returnsFalseAndSetsStatusWhenParseFails() {
        WzImage image = mock(WzImage.class);
        when(image.parse()).thenReturn(false);
        when(image.getName()).thenReturn("test.img");
        when(image.getStatus()).thenReturn(WzFileStatus.ERROR_KEY);

        try (MockedStatic<MainFrame> mainFrame = Mockito.mockStatic(MainFrame.class)) {
            MainFrame frame = mock(MainFrame.class);
            mainFrame.when(MainFrame::getInstance).thenReturn(frame);

            assertFalse(WzParseHelper.ensureParsed(image));
            verify(frame).setStatusText(contains("test.img"));
        }
    }

    @Test
    void requireParsed_throwsParseFailedException() {
        WzImage image = mock(WzImage.class);
        when(image.parse()).thenReturn(false);
        when(image.getName()).thenReturn("a.img");
        when(image.getStatus()).thenReturn(WzFileStatus.ERROR_KEY);

        try (MockedStatic<MainFrame> mainFrame = Mockito.mockStatic(MainFrame.class)) {
            MainFrame frame = mock(MainFrame.class);
            mainFrame.when(MainFrame::getInstance).thenReturn(frame);

            assertThrows(WzParseHelper.ParseFailedException.class, () -> WzParseHelper.requireParsed(image));
        }
    }
}
