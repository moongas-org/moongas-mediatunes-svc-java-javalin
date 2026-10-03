/*
 * Copyright 2026
 * moongas.org
 */
package org.moongas.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.moongas.config.MediatunesServiceConfig;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.introspector.PropertyUtils;

public class ConfigLoader {

    private ConfigLoader() {
        throw new UnsupportedOperationException("Cannot instantiate this static class");
    }

    public static MediatunesServiceConfig load(Path configFile) throws IOException {
        LoaderOptions loaderOptions = new LoaderOptions();
        Constructor constructor = new Constructor(MediatunesServiceConfig.class, loaderOptions);
        PropertyUtils propertyUtils = new PropertyUtils();
        propertyUtils.setSkipMissingProperties(true);
        propertyUtils.setBeanAccess(BeanAccess.FIELD);
        constructor.setPropertyUtils(propertyUtils);
        Yaml yaml = new Yaml(constructor);
        yaml.setBeanAccess(BeanAccess.FIELD);
        try (var input = Files.newInputStream(configFile)) {
            MediatunesServiceConfig config = yaml.loadAs(input, MediatunesServiceConfig.class);
            return config != null ? config : new MediatunesServiceConfig();
        }
    }
}
