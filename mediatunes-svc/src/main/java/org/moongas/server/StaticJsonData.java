/*
 * Copyright 2026
 * moongas.org
 */
package org.moongas.server;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Loads the static code-to-display-name maps (country/region/language)
 * shipped as classpath resources under static/json_data/.
 */
public class StaticJsonData {

    private static final Logger logger = LoggerFactory.getLogger(StaticJsonData.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private StaticJsonData() {
        throw new UnsupportedOperationException("Cannot instantiate this static class");
    }

    public static Map<String, String> load(String filename) {
        String resourcePath = "/static/json_data/" + filename;
        try (InputStream in = StaticJsonData.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                logger.error("Data file not found: {}", resourcePath);
                return Collections.emptyMap();
            }
            Map<String, String> data = MAPPER.readValue(in, new TypeReference<Map<String, String>>() {});
            return data != null ? data : Collections.emptyMap();
        } catch (IOException e) {
            logger.error("Could not decode JSON from file {}: {}", resourcePath, e.getMessage());
            return Collections.emptyMap();
        }
    }

    public static Map<String, String> countryCodeNameMap() {
        return load("country_code_name_map.json");
    }

    public static Map<String, String> regionCodeNameMap() {
        return load("region_code_name_map.json");
    }

    public static Map<String, String> languageCodeNameMap() {
        return load("language_code_name_map.json");
    }

    /** Defensive copy helper for callers that mutate the returned map. */
    public static Map<String, String> mutableCopy(Map<String, String> map) {
        return new HashMap<>(map);
    }
}
