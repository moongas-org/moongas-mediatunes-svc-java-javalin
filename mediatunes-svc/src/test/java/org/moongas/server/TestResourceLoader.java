package org.moongas.server;

import static org.junit.jupiter.api.Assertions.fail;

import java.net.URL;
import java.nio.file.Path;


public class TestResourceLoader {
    private TestResourceLoader() {
        throw new UnsupportedOperationException("Cannot instantiate this static class");
    }

    /**
     * A bit of a hack to reliably load test resources, whether running tests from
     * gradle command-line or from within VSCode testing extension.
     * @param filename
     * @return
     */
    public static Path getTestResource(String filename) {
        Path ret = null;
        URL rootUrl = TestResourceLoader.class.getClassLoader().getResource("");
        String rootStr = rootUrl.getPath();
        if (rootStr.contains("classes/java/test")) {
            rootStr = rootStr.replace("classes/java/test", "resources/test");
        }
        try {
            ret = Path.of(rootStr).resolve(filename);
        }
        catch (Exception e) {
            fail("Failed to load test resource filename=" + filename);
        }

        return ret;
    }
}
