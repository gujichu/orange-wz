package orange.wz.provider.ms;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import orange.wz.provider.WzImage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WzMsCms225IntegrationTest {

    private static final Path CMS225_MOB =
            Path.of("E:\\0beidou\\cilent\\CMS225\\Mob_00000\\Mob_00000.ms");

    private static final byte[] IV = new byte[]{1, 2, 3, 4};
    private static final byte[] USER_KEY = new byte[128];

    static boolean cms225MobExists() {
        return Files.isRegularFile(CMS225_MOB);
    }

    @Test
    @EnabledIf("cms225MobExists")
    void loadCms225MobMs() throws Exception {
        List<WzMsFile.EntryImage> loaded = WzMsFile.load(CMS225_MOB, IV, USER_KEY);
        assertFalse(loaded.isEmpty(), "应至少解析出一个 img 条目");
        WzImage first = loaded.getFirst().getImage();
        assertNotEquals(0, first.getChildren().size(), "首个 img 应包含子节点");
    }
}
