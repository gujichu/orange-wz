package orange.wz.provider.ms;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import orange.wz.provider.WzImage;
import orange.wz.provider.WzImageProperty;
import orange.wz.provider.WzMsImageFile;
import orange.wz.provider.properties.WzIntProperty;
import orange.wz.provider.properties.WzListProperty;
import orange.wz.provider.tools.BinaryWriter;
import orange.wz.provider.tools.WzMutableKey;
import orange.wz.provider.tools.BinaryReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WzMsFileLoadTest {

    private static final byte[] IV = new byte[]{1, 2, 3, 4};
    private static final byte[] USER_KEY = new byte[128];

    @TempDir
    Path tempDir;

    @Test
    void loadShouldParseNestedChildren() throws Exception {
        WzImage image = new WzImage("UnitTest.img", null, new BinaryReader(IV, USER_KEY));
        WzListProperty root = new WzListProperty("root", image, image);
        WzListProperty sub = new WzListProperty("sub", root, image);
        sub.addChild(new WzIntProperty("value", 123, sub, image), true);
        root.addChild(sub, true);
        image.addChild(root, true);

        List<WzMsFile.EntryImage> source = List.of(
                new WzMsFile.EntryImage("Unit/UnitTest.img", image, 0, 0, 0, new byte[16])
        );
        byte[] bytes = WzMsFile.save("Unit.ms", source, IV, USER_KEY);

        Path msFile = tempDir.resolve("Unit.ms");
        Files.write(msFile, bytes);

        List<WzMsFile.EntryImage> loaded = WzMsFile.load(msFile, IV, USER_KEY);
        assertEquals(1, loaded.size());

        WzImage loadedImage = loaded.getFirst().getImage();
        assertFalse(loadedImage.getChildren().isEmpty());

        WzImageProperty loadedRoot = loadedImage.getChild("root");
        assertNotNull(loadedRoot);
        assertTrue(loadedRoot.isListProperty());

        WzImageProperty loadedSub = loadedRoot.getChild("sub");
        assertNotNull(loadedSub);

        WzImageProperty loadedValue = loadedSub.getChild("value");
        assertInstanceOf(WzIntProperty.class, loadedValue);
        assertEquals(123, ((WzIntProperty) loadedValue).getValue());
    }

    @Test
    void msWrapperChildrenShouldKeepWzImageReference() throws Exception {
        WzImage image = new WzImage("Npc.img", null, new BinaryReader(IV, USER_KEY));
        image.addChild(new orange.wz.provider.properties.WzStringProperty("name", "foo", image, image), true);

        List<WzMsFile.EntryImage> source = List.of(
                new WzMsFile.EntryImage("Npc/Npc.img", image, 0, 0, 0, new byte[16])
        );
        byte[] bytes = WzMsFile.save("Npc.ms", source, IV, USER_KEY);
        Path msFile = tempDir.resolve("Npc.ms");
        Files.write(msFile, bytes);

        WzMsImageFile msImageFile = new WzMsImageFile("Npc.ms", msFile.toString(), "test", IV, USER_KEY);
        assertTrue(msImageFile.parse());
        WzImageProperty wrapper = msImageFile.getChildren().getFirst();
        WzImageProperty name = wrapper.getChild("name");
        assertNotNull(name);
        assertNotNull(name.getWzImage());
    }

    @Test
    void saveReloadShouldKeepImagePayloadStable() throws Exception {
        WzImage image = new WzImage("Stable.img", null, new BinaryReader(IV, USER_KEY));
        WzListProperty root = new WzListProperty("root", image, image);
        root.addChild(new WzIntProperty("value", 777, root, image), true);
        image.addChild(root, true);

        List<WzMsFile.EntryImage> source = List.of(
                new WzMsFile.EntryImage("Skill/Stable.img", image, 6, 0, 0, new byte[16])
        );

        byte[] firstBytes = WzMsFile.save("Skill_00001.ms", source, IV, USER_KEY);
        Path msFile = tempDir.resolve("Skill_00001.ms");
        Files.write(msFile, firstBytes);

        List<WzMsFile.EntryImage> firstLoad = WzMsFile.load(msFile, IV, USER_KEY);
        byte[] payload1 = toImagePayload(firstLoad.getFirst().getImage());

        byte[] secondBytes = WzMsFile.save("Skill_00001_copy.ms", firstLoad, IV, USER_KEY);
        Path msFile2 = tempDir.resolve("Skill_00001_copy.ms");
        Files.write(msFile2, secondBytes);

        List<WzMsFile.EntryImage> secondLoad = WzMsFile.load(msFile2, IV, USER_KEY);
        byte[] payload2 = toImagePayload(secondLoad.getFirst().getImage());

        assertArrayEquals(payload1, payload2, "写回后再次读取的 img 字节内容应保持一致");
    }

    private static byte[] toImagePayload(WzImage image) {
        BinaryWriter writer = new BinaryWriter();
        writer.setWzMutableKey(new WzMutableKey(IV, USER_KEY));
        image.save(writer);
        return writer.output();
    }
}
