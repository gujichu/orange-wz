package orange.wz.gui.component.panel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EditPaneAnchorPathTest {

    @Test
    void anchorPathSuffix_stripsFolderPrefix() {
        assertEquals("Map.wz/String.img/info/name",
                EditPane.anchorPathSuffix("Data/Map/Map.wz/String.img/info/name"));
    }

    @Test
    void anchorPathSuffix_keepsPathWhenAlreadyAnchored() {
        assertEquals("Quest.img/say/0/string",
                EditPane.anchorPathSuffix("Quest.img/say/0/string"));
    }

    @Test
    void isAnchorFileName() {
        assertTrue(EditPane.isAnchorFileName("Map.wz"));
        assertTrue(EditPane.isAnchorFileName("String.img"));
        assertFalse(EditPane.isAnchorFileName("info"));
    }
}
