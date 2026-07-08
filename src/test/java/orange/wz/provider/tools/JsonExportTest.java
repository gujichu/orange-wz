package orange.wz.provider.tools;

import orange.wz.provider.WzImage;
import orange.wz.provider.properties.WzFloatProperty;
import orange.wz.provider.properties.WzIntProperty;
import orange.wz.provider.properties.WzListProperty;
import orange.wz.provider.properties.WzSoundProperty;
import orange.wz.provider.properties.WzStringProperty;
import orange.wz.provider.properties.WzUOLProperty;
import orange.wz.provider.properties.WzVectorProperty;
import orange.wz.provider.properties.WzVideoProperty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonExportTest {

    private static final byte[] IV = new byte[]{1, 2, 3, 4};
    private static final byte[] USER_KEY = new byte[128];

    @Test
    void emptyImageShouldExportRootSubOnly() {
        WzImage image = new WzImage("Empty.img", null, new BinaryReader(IV, USER_KEY));
        JsonExport export = new JsonExport(image, 0);
        assertEquals("{\"_dirType\":\"sub\"}", export.toJson(export.buildImageRootForTest()));
    }

    @Test
    void populatedImageRootShouldNotContainDirTypeWrapper() {
        WzImage image = new WzImage("Pet.img", null, new BinaryReader(IV, USER_KEY));
        WzListProperty info = new WzListProperty("info", image, image);
        info.addChild(new WzIntProperty("cash", 1, info, image), true);
        image.addChild(info, true);
        image.addChild(new WzListProperty("5002201", image, image), true);

        JsonExport export = new JsonExport(image, 0);
        String json = export.toJson(export.buildImageRootForTest());

        assertFalse(json.startsWith("{\"_dirType\""));
        assertTrue(json.contains("\"info\":{\"_dirType\":\"sub\""));
        assertTrue(json.contains("\"cash\":{\"_dirType\":\"int\",\"_value\":\"1\"}"));
        assertTrue(json.endsWith("\"5002201\":{\"_dirType\":\"sub\"}}"));
    }

    @Test
    void prettyIndentShouldUseTwoSpacesPerLevel() {
        WzImage image = new WzImage("Mob.img", null, new BinaryReader(IV, USER_KEY));
        WzListProperty info = new WzListProperty("info", image, image);
        info.addChild(new WzStringProperty("link", "0100100", info, image), true);
        image.addChild(info, true);

        JsonExport export = new JsonExport(image, 2);
        String json = export.toJson(export.buildImageRootForTest());

        assertEquals(
                """
                {
                  "info": {
                    "_dirType": "sub",
                    "link": {
                      "_dirType": "string",
                      "_value": "0100100"
                    }
                  }
                }
                """.trim(),
                json.trim()
        );
    }

    @Test
    void floatWholeNumberShouldSerializeWithoutDecimalSuffix() {
        WzImage image = new WzImage("Mob.img", null, new BinaryReader(IV, USER_KEY));
        WzListProperty info = new WzListProperty("info", image, image);
        info.addChild(new WzFloatProperty("fs", 10f, info, image), true);
        image.addChild(info, true);

        JsonExport export = new JsonExport(image, 0);
        String json = export.toJson(export.buildImageRootForTest());

        assertTrue(json.contains("\"_value\":\"10\""));
        assertFalse(json.contains("\"_value\":\"10.0\""));
    }

    @Test
    void shouldMatchTms273PetEquipCompactStyle() {
        WzImage image = new WzImage("01802774.img", null, new BinaryReader(IV, USER_KEY));
        WzListProperty info = new WzListProperty("info", image, image);
        info.addChild(new WzIntProperty("tuc", 9, info, image), true);
        info.addChild(new WzIntProperty("reqLevel", 0, info, image), true);
        info.addChild(new WzIntProperty("cash", 1, info, image), true);
        info.addChild(new WzIntProperty("incPAD", 5, info, image), true);
        info.addChild(new WzIntProperty("incMAD", 5, info, image), true);
        image.addChild(info, true);
        image.addChild(new WzListProperty("5002201", image, image), true);

        JsonExport export = new JsonExport(image, 0);
        assertEquals(
                "{\"info\":{\"_dirType\":\"sub\",\"tuc\":{\"_dirType\":\"int\",\"_value\":\"9\"},\"reqLevel\":{\"_dirType\":\"int\",\"_value\":\"0\"},\"cash\":{\"_dirType\":\"int\",\"_value\":\"1\"},\"incPAD\":{\"_dirType\":\"int\",\"_value\":\"5\"},\"incMAD\":{\"_dirType\":\"int\",\"_value\":\"5\"}},\"5002201\":{\"_dirType\":\"sub\"}}",
                export.toJson(export.buildImageRootForTest())
        );
    }

    @Test
    void resolveJsonFileNameShouldStripImgSuffix() {
        assertEquals("09000000.json", JsonExport.resolveJsonFileName("09000000.img"));
        assertEquals("Effect.json", JsonExport.resolveJsonFileName("Effect.img"));
        assertEquals("Effect.json", JsonExport.resolveJsonFileName("Effect.xml"));
    }

    @Test
    void shouldStripCanvasVisualResourcesLikeTms273Skill() {
        WzImage image = new WzImage("2110.img", null, new BinaryReader(IV, USER_KEY));

        WzListProperty info = new WzListProperty("info", image, image);
        WzListProperty icon = new WzListProperty("icon", info, image);
        icon.addChild(new WzVectorProperty("origin", -4, 30, icon, image), true);
        icon.addChild(new WzStringProperty("_outlink", "Skill/_Canvas/110.img/info/icon", icon, image), true);
        info.addChild(icon, true);
        image.addChild(info, true);

        WzListProperty skillRoot = new WzListProperty("skill", image, image);
        WzListProperty skill = new WzListProperty("21101004", skillRoot, image);

        WzListProperty effect = new WzListProperty("effect", skill, image);
        WzListProperty frame = new WzListProperty("0", effect, image);
        frame.addChild(new WzVectorProperty("origin", 311, 307, frame, image), true);
        frame.addChild(new WzIntProperty("z", 0, frame, image), true);
        frame.addChild(new WzIntProperty("delay", 60, frame, image), true);
        frame.addChild(new WzStringProperty("_outlink", "Skill/_Canvas/2110.img/skill/21101004/effect/0", frame, image), true);
        effect.addChild(frame, true);
        skill.addChild(effect, true);

        WzListProperty hit = new WzListProperty("hit", skill, image);
        WzListProperty hitFrame = new WzListProperty("0", hit, image);
        hitFrame.addChild(new WzIntProperty("randomHitOrigin", 25, hitFrame, image), true);
        hitFrame.addChild(new WzVectorProperty("origin", 1, 2, hitFrame, image), true);
        hit.addChild(hitFrame, true);
        skill.addChild(hit, true);

        WzListProperty effect0 = new WzListProperty("effect0", skill, image);
        effect0.addChild(new WzIntProperty("z", -1, effect0, image), true);
        skill.addChild(effect0, true);

        skillRoot.addChild(skill, true);
        image.addChild(skillRoot, true);

        JsonExport export = new JsonExport(image, 0);
        String json = export.toJson(export.buildImageRootForTest());

        assertFalse(json.contains("_outlink"));
        assertFalse(json.contains("_Canvas"));
        assertFalse(json.contains("\"delay\""));
        assertFalse(json.contains("\"icon\""));
        assertTrue(json.contains("\"info\":{\"_dirType\":\"sub\"}"));
        assertTrue(json.contains("\"effect\":{\"_dirType\":\"sub\",\"0\":{\"_dirType\":\"sub\"}}"));
        assertTrue(json.contains("\"hit\":{\"_dirType\":\"sub\",\"0\":{\"_dirType\":\"sub\",\"randomHitOrigin\""));
        assertTrue(json.contains("\"origin\":{\"_dirType\":\"vector\",\"_x\":1,\"_y\":2}"));
        assertTrue(json.contains("\"randomHitOrigin\":{\"_dirType\":\"int\",\"_value\":\"25\"}"));
        assertTrue(json.contains("\"effect0\":{\"_dirType\":\"sub\",\"z\":{\"_dirType\":\"int\",\"_value\":\"-1\"}}"));
    }

    @Test
    void shouldKeepDelayOutsideEffectAnimationFrames() {
        WzImage image = new WzImage("FieldSkill.img", null, new BinaryReader(IV, USER_KEY));
        WzListProperty skill = new WzListProperty("100013", image, image);
        WzListProperty level = new WzListProperty("level", skill, image);
        WzListProperty level1 = new WzListProperty("1", level, image);
        level1.addChild(new WzIntProperty("delay", 1860, level1, image), true);
        level.addChild(level1, true);
        skill.addChild(level, true);
        image.addChild(skill, true);

        JsonExport export = new JsonExport(image, 0);
        String json = export.toJson(export.buildImageRootForTest());

        assertTrue(json.contains("\"delay\":{\"_dirType\":\"int\",\"_value\":\"1860\"}"));
    }

    @Test
    void shouldKeepIntOriginButStripVectorOrigin() {
        WzImage image = new WzImage("6114.img", null, new BinaryReader(IV, USER_KEY));
        WzListProperty skill = new WzListProperty("skill", image, image);
        WzListProperty node = new WzListProperty("61141500", skill, image);
        node.addChild(new WzIntProperty("origin", 1, node, image), true);
        skill.addChild(node, true);

        WzListProperty effect = new WzListProperty("effect", node, image);
        WzListProperty frame = new WzListProperty("0", effect, image);
        frame.addChild(new WzVectorProperty("origin", 10, 20, frame, image), true);
        effect.addChild(frame, true);
        node.addChild(effect, true);

        image.addChild(skill, true);

        JsonExport export = new JsonExport(image, 0);
        String json = export.toJson(export.buildImageRootForTest());

        assertTrue(json.contains("\"origin\":{\"_dirType\":\"int\",\"_value\":\"1\"}"));
        assertFalse(json.contains("\"_dirType\":\"vector\""));
    }

    @Test
    void shouldKeepEmptyNumericFramesOutsideEffectContainers() {
        WzImage image = new WzImage("2110.img", null, new BinaryReader(IV, USER_KEY));
        WzListProperty skillRoot = new WzListProperty("skill", image, image);
        WzListProperty skill = new WzListProperty("21101004", skillRoot, image);

        WzListProperty tile = new WzListProperty("tile", skill, image);
        tile.addChild(new WzListProperty("0", tile, image), true);
        skill.addChild(tile, true);

        WzListProperty level = new WzListProperty("level", skill, image);
        level.addChild(new WzListProperty("1", level, image), true);
        skill.addChild(level, true);

        skillRoot.addChild(skill, true);
        image.addChild(skillRoot, true);

        JsonExport export = new JsonExport(image, 0);
        String json = export.toJson(export.buildImageRootForTest());

        assertTrue(json.contains("\"tile\":{\"_dirType\":\"sub\",\"0\":{\"_dirType\":\"sub\"}"));
        assertTrue(json.contains("\"level\":{\"_dirType\":\"sub\",\"1\":{\"_dirType\":\"sub\"}"));
    }

    @Test
    void resolveImageBaseNameShouldStripImgSuffix() {
        assertEquals("0400", JsonExport.resolveImageBaseName("0400.img"));
        assertEquals("2110", JsonExport.resolveImageBaseName("2110.img"));
        assertEquals("Effect", JsonExport.resolveImageBaseName("Effect.xml"));
    }

    @Test
    void resolveExportRootFolderNameShouldStripShardSuffix() {
        assertEquals("Skill", JsonExport.resolveExportRootFolderName("Skill_00000.ms"));
        assertEquals("Skill", JsonExport.resolveExportRootFolderName("Skill_00001.wz"));
        assertEquals("Skill", JsonExport.resolveExportRootFolderName("Skill_00002.wz"));
        assertEquals("Item", JsonExport.resolveExportRootFolderName("Item_00000.wz"));
        assertEquals("Character", JsonExport.resolveExportRootFolderName("Character.wz"));
        assertEquals("Effect", JsonExport.resolveExportRootFolderName("Effect_00003.ms"));
    }

    @Test
    void isListContainerImageShouldDetectNumericListWrappers() {
        WzImage container = new WzImage("0400.img", null, new BinaryReader(IV, USER_KEY));
        container.addChild(new WzListProperty("04000000", container, container), true);
        container.addChild(new WzListProperty("04000001", container, container), true);
        assertTrue(container.isListContainerImage());

        WzImage skill = new WzImage("2110.img", null, new BinaryReader(IV, USER_KEY));
        skill.addChild(new WzListProperty("info", skill, skill), true);
        skill.addChild(new WzListProperty("skill", skill, skill), true);
        assertFalse(skill.isListContainerImage());
    }

    @Test
    void listContainerChildShouldExportItemContentOnly() {
        WzImage container = new WzImage("0400.img", null, new BinaryReader(IV, USER_KEY));
        WzListProperty item = new WzListProperty("04000000", container, container);
        WzListProperty info = new WzListProperty("info", item, container);
        info.addChild(new WzIntProperty("price", 100, info, container), true);
        item.addChild(info, true);
        container.addChild(item, true);

        WzImage exported = container.exportFromListWrapper(item);
        JsonExport export = new JsonExport(exported, 0);
        String json = export.toJson(export.buildImageRootForTest());

        assertFalse(json.contains("04000000"));
        assertTrue(json.contains("\"info\":{\"_dirType\":\"sub\",\"price\":{\"_dirType\":\"int\",\"_value\":\"100\"}}"));
    }

    @Test
    void shouldKeepRelativeUolIconLinks() {
        WzImage image = new WzImage("800002.img", null, new BinaryReader(IV, USER_KEY));
        WzListProperty skillRoot = new WzListProperty("skill", image, image);
        WzListProperty skill = new WzListProperty("80000273", skillRoot, image);
        skill.addChild(new WzUOLProperty("icon", "../80000218/icon", skill, image), true);
        skill.addChild(new WzUOLProperty("iconMouseOver", "../80000218/iconMouseOver", skill, image), true);
        skillRoot.addChild(skill, true);
        image.addChild(skillRoot, true);

        JsonExport export = new JsonExport(image, 0);
        String json = export.toJson(export.buildImageRootForTest());

        assertTrue(json.contains("\"icon\":{\"_dirType\":\"uol\",\"_value\":\"../80000218/icon\"}"));
        assertTrue(json.contains("\"iconMouseOver\":{\"_dirType\":\"uol\",\"_value\":\"../80000218/iconMouseOver\"}"));
    }

    @Test
    void isCanvasResourceLinkShouldDetectCanvasPaths() {
        assertTrue(JsonExport.isCanvasResourceLink("Skill/_Canvas/2110.img/skill/21101004/effect/0"));
        assertFalse(JsonExport.isCanvasResourceLink("../80000218/icon"));
    }

    @Test
    void shouldKeepOriginallyEmptyEffectAnimationFrames() {
        WzImage image = new WzImage("100.img", null, new BinaryReader(IV, USER_KEY));
        WzListProperty skillRoot = new WzListProperty("skill", image, image);
        WzListProperty skill = new WzListProperty("1001011", skillRoot, image);
        WzListProperty effect = new WzListProperty("effect", skill, image);
        effect.addChild(new WzListProperty("0", effect, image), true);
        skill.addChild(effect, true);
        skillRoot.addChild(skill, true);
        image.addChild(skillRoot, true);

        JsonExport export = new JsonExport(image, 0);
        String json = export.toJson(export.buildImageRootForTest());

        assertTrue(json.contains("\"effect\":{\"_dirType\":\"sub\",\"0\":{\"_dirType\":\"sub\"}}"));
    }

    @Test
    void shouldKeepZeroZOutsideEffectAnimationFrames() {
        WzImage image = new WzImage("RuleFixture.img", null, new BinaryReader(IV, USER_KEY));
        WzListProperty skillRoot = new WzListProperty("skill", image, image);
        WzListProperty skill = new WzListProperty("skillWithTimeline", skillRoot, image);
        WzListProperty start = new WzListProperty("start", skill, image);
        start.addChild(new WzIntProperty("z", 0, start, image), true);
        skill.addChild(start, true);
        skillRoot.addChild(skill, true);
        image.addChild(skillRoot, true);

        JsonExport export = new JsonExport(image, 0);
        String json = export.toJson(export.buildImageRootForTest());

        assertTrue(json.contains("\"z\":{\"_dirType\":\"int\",\"_value\":\"0\"}"));
    }

    @Test
    void shouldExportVideoPlaceholderLikeTms273() {
        WzImage image = new WzImage("RuleFixture.img", null, new BinaryReader(IV, USER_KEY));
        WzListProperty skillRoot = new WzListProperty("skill", image, image);
        WzListProperty skill = new WzListProperty("skillWithScreenVideo", skillRoot, image);
        WzListProperty screen = new WzListProperty("screen", skill, image);
        screen.addChild(new WzVideoProperty("video", screen, image), true);
        skill.addChild(screen, true);
        skillRoot.addChild(skill, true);
        image.addChild(skillRoot, true);

        JsonExport export = new JsonExport(image, 0);
        String json = export.toJson(export.buildImageRootForTest());

        assertTrue(json.contains("\"video\":{\"_dirType\":\"wz_video\",\"_value\":\"WzComparerR2.WzLib.Wz_Video\"}"));
    }

    @Test
    void shouldUseByteLengthForSpineSoundResources() {
        WzImage image = new WzImage("RuleFixture.img", null, new BinaryReader(IV, USER_KEY));
        WzListProperty skillRoot = new WzListProperty("skill", image, image);
        WzListProperty skill = new WzListProperty("skillWithSpineResource", skillRoot, image);
        WzListProperty spine = new WzListProperty("spine", skill, image);
        WzListProperty frame = new WzListProperty("0", spine, image);
        frame.addChild(new WzSoundProperty("material", 1000, new byte[0], new byte[29360], frame, image), true);
        spine.addChild(frame, true);
        skill.addChild(spine, true);
        skillRoot.addChild(skill, true);
        image.addChild(skillRoot, true);

        JsonExport export = new JsonExport(image, 0);
        String json = export.toJson(export.buildImageRootForTest());

        assertTrue(json.contains("\"material\":{\"_dirType\":\"sound\",\"_length\":\"29360\"}"));
    }

    @Test
    void shouldKeepDirectEffectOriginVector() {
        WzImage image = new WzImage("RuleFixture.img", null, new BinaryReader(IV, USER_KEY));
        WzListProperty skillRoot = new WzListProperty("skill", image, image);
        WzListProperty skill = new WzListProperty("skillWithDirectEffectOrigin", skillRoot, image);
        WzListProperty effect = new WzListProperty("effect", skill, image);
        effect.addChild(new WzVectorProperty("origin", 57, 154, effect, image), true);
        skill.addChild(effect, true);
        skillRoot.addChild(skill, true);
        image.addChild(skillRoot, true);

        JsonExport export = new JsonExport(image, 0);
        String json = export.toJson(export.buildImageRootForTest());

        assertTrue(json.contains("\"origin\":{\"_dirType\":\"vector\",\"_x\":57,\"_y\":154}"));
    }

    @Test
    void shouldKeepVideoOriginVector() {
        WzImage image = new WzImage("RuleFixture.img", null, new BinaryReader(IV, USER_KEY));
        WzListProperty skillRoot = new WzListProperty("skill", image, image);
        WzListProperty skill = new WzListProperty("skillWithVideoOrigin", skillRoot, image);
        WzListProperty screen = new WzListProperty("screen", skill, image);
        WzVideoProperty video = new WzVideoProperty("video", screen, image);
        video.addChild(new WzVectorProperty("origin", 684, 1615, video, image), true);
        screen.addChild(video, true);
        skill.addChild(screen, true);
        skillRoot.addChild(skill, true);
        image.addChild(skillRoot, true);

        JsonExport export = new JsonExport(image, 0);
        String json = export.toJson(export.buildImageRootForTest());

        assertTrue(json.contains("\"video\":{\"_dirType\":\"wz_video\",\"_value\":\"WzComparerR2.WzLib.Wz_Video\",\"origin\":{\"_dirType\":\"vector\",\"_x\":684,\"_y\":1615}}"));
    }

    @Test
    void shouldKeepOriginInNumericDataFramesOutsideVisualEffects() {
        WzImage image = new WzImage("RuleFixture.img", null, new BinaryReader(IV, USER_KEY));
        WzListProperty skillRoot = new WzListProperty("skill", image, image);
        WzListProperty skill = new WzListProperty("skillWithDataFrames", skillRoot, image);
        WzListProperty tile = new WzListProperty("tile", skill, image);
        WzListProperty frame = new WzListProperty("0", tile, image);
        frame.addChild(new WzVectorProperty("origin", 12, 34, frame, image), true);
        tile.addChild(frame, true);
        skill.addChild(tile, true);
        skillRoot.addChild(skill, true);
        image.addChild(skillRoot, true);

        JsonExport export = new JsonExport(image, 0);
        String json = export.toJson(export.buildImageRootForTest());

        assertTrue(json.contains("\"tile\":{\"_dirType\":\"sub\",\"0\":{\"_dirType\":\"sub\",\"origin\":{\"_dirType\":\"vector\",\"_x\":12,\"_y\":34}}}"));
    }
}
