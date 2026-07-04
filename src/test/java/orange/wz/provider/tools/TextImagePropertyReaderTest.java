package orange.wz.provider.tools;

import orange.wz.provider.WzImage;
import orange.wz.provider.properties.WzIntProperty;
import orange.wz.provider.properties.WzListProperty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextImagePropertyReaderTest {

    private static final byte[] IV = new byte[]{1, 2, 3, 4};
    private static final byte[] USER_KEY = new byte[128];

    @Test
    void shouldDetectPropertySignature() {
        byte[] data = "#Property\r\n92000001 =\r\n{\r\n}\r\n".getBytes();
        assertTrue(TextImagePropertyReader.isTextPropertyV1(data));
    }

    @Test
    void shouldParseRecipeLikeTextProperty() {
        String text = """
                #Property
                92000001 =
                {
                target =
                {
                0 =
                {
                item = 4023022
                count = 1
                probWeight = 100
                }
                }
                reqSkillLevel = 1
                }
                """.replace("\n", "\r\n");

        WzImage image = new WzImage("Recipe_9200.img", null, new BinaryReader(IV, USER_KEY));
        TextImagePropertyReader.parseV1(text, image);

        WzListProperty recipe = (WzListProperty) image.getChild("92000001");
        assertNotNull(recipe);

        WzListProperty target = (WzListProperty) recipe.getChild("target");
        assertNotNull(target);

        WzListProperty slot = (WzListProperty) target.getChild("0");
        assertNotNull(slot);
        assertEquals(4023022, ((WzIntProperty) slot.getChild("item")).getValue());
        assertEquals(1, ((WzIntProperty) slot.getChild("count")).getValue());
        assertEquals(100, ((WzIntProperty) slot.getChild("probWeight")).getValue());
        assertEquals(1, ((WzIntProperty) recipe.getChild("reqSkillLevel")).getValue());
    }

    @Test
    void wzImageParseShouldAcceptTextPropertyBytes() {
        byte[] data = """
                #Property
                92000001 =
                {
                reqSkillLevel = 1
                }
                """.replace("\n", "\r\n").getBytes();

        WzImage image = new WzImage("Recipe_9200.img", new BinaryReader(data), null);
        assertTrue(image.parse());
        assertNotNull(image.getChild("92000001"));
    }
}
