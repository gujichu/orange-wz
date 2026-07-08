package orange.wz.gui.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CanvasOriginCacheTest {

    @Test
    void extractNodeRelativePath_fromCanvasSplitFile() {
        assertEquals(
                "Enchant.img/accessoryAugment/backgrnd",
                CanvasOriginCache.extractNodeRelativePath(
                        "_Canvas/_Canvas_001.wz/Enchant.img/accessoryAugment/backgrnd"));
    }

    @Test
    void extractNodeRelativePath_fromMapMetadataFile() {
        assertEquals(
                "Enchant.img/accessoryAugment/backgrnd",
                CanvasOriginCache.extractNodeRelativePath(
                        "Map_000.wz/Enchant.img/accessoryAugment/backgrnd"));
    }

    @Test
    void inferMetadataPrefixFromCanvasFilePath_mapDirectory() {
        assertEquals(
                "Map",
                CanvasOriginCache.inferMetadataPrefixFromCanvasFilePath(
                        "G:\\265\\client265\\Data\\Map\\_Canvas\\_Canvas_005.wz"));
    }

    @Test
    void inferMetadataPrefixFromCanvasFilePath_uiDirectory() {
        assertEquals(
                "UI",
                CanvasOriginCache.inferMetadataPrefixFromCanvasFilePath(
                        "G:\\265\\client265\\Data\\UI\\_Canvas\\_Canvas_001.wz"));
    }

    @Test
    void buildMetaLookupPaths_prefersInferredPrefix() {
        List<String> paths = CanvasOriginCache.buildMetaLookupPaths(
                "_Canvas/_Canvas_005.wz/Enchant.img/accessoryAugment/backgrnd",
                List.of("UI_000.wz", "Map_000.wz"),
                "Map");
        assertEquals("Map_000.wz/Enchant.img/accessoryAugment/backgrnd", paths.get(0));
        assertEquals("UI_000.wz/Enchant.img/accessoryAugment/backgrnd", paths.get(1));
    }

    @Test
    void extractCanvasWzTreePath() {
        assertEquals(
                "_Canvas/_Canvas_005.wz",
                CanvasOriginCache.extractCanvasWzTreePath(
                        "_Canvas/_Canvas_005.wz/Enchant.img/accessoryAugment/backgrnd"));
    }

    @Test
    void extractNodeRelativePath_fromNestedFolderCanvas() {
        assertEquals(
                "Enchant.img/accessoryAugment/backgrnd",
                CanvasOriginCache.extractNodeRelativePath(
                        "Map/_Canvas/_Canvas_005.wz/Enchant.img/accessoryAugment/backgrnd"));
    }

    @Test
    void extractCanvasWzTreePath_nestedFolder() {
        assertEquals(
                "Map/_Canvas/_Canvas_005.wz",
                CanvasOriginCache.extractCanvasWzTreePath(
                        "Map/_Canvas/_Canvas_005.wz/Enchant.img/accessoryAugment/backgrnd"));
    }

    @Test
    void extractCanvasWzTreePath_flatCanvasWz() {
        assertEquals(
                "_Canvas_005.wz",
                CanvasOriginCache.extractCanvasWzTreePath(
                        "_Canvas_005.wz/Enchant.img/accessoryAugment/backgrnd"));
    }

    @Test
    void buildMetaLookupPaths_withFolderPrefixedMetadata() {
        List<String> paths = CanvasOriginCache.buildMetaLookupPaths(
                "Map/_Canvas/_Canvas_005.wz/Enchant.img/accessoryAugment/backgrnd",
                List.of("Map/Map_000.wz", "UI/UI_000.wz"),
                "Map");
        assertEquals("Map/Map_000.wz/Enchant.img/accessoryAugment/backgrnd", paths.get(0));
        assertEquals("UI/UI_000.wz/Enchant.img/accessoryAugment/backgrnd", paths.get(1));
    }

    @Test
    void extractNodeRelativePath_returnsNullForUnknownPath() {
        assertNull(CanvasOriginCache.extractNodeRelativePath("Skill.img/foo/0"));
    }
}
