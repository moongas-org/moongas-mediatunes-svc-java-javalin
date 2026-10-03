package org.moongas.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Root configuration, mirroring the mediatunes-config.yml of the Python
 * BlackSheep implementation. Unknown YAML keys are ignored, fields absent
 * from the file keep their defaults.
 */
public class MediatunesServiceConfig {

    public static class LocalPlaybackMethod {
        public boolean enabled = true;
        public String mediaPath = "/data/";
    }

    public static class WebSearchPlaybackMethod {
        public String name = "";
        public boolean enabled = true;
        public String searchQueryURLFormat = "";
    }

    public static class PlaybackMethods {
        public LocalPlaybackMethod local = new LocalPlaybackMethod();
        public List<WebSearchPlaybackMethod> webSearch = new ArrayList<>();
    }

    public static class ServerConfig {
        public String host = "0.0.0.0";
        public int port = 5000;
        public boolean debug = true;
        public String staticUrlPath = null;
        public String urlPrefix = null;
    }

    public int version = 1;
    public String mediascanDatabaseFilePath = "sqlite:///../mediascan.db";
    public String albumCoversPath = "/data/";
    public boolean ageVerification = true;
    public boolean limitBandwidth = true;
    public int maxResults = 50000;
    public int maxResultsAlbumCovers = 500;
    public ServerConfig serverConfig = new ServerConfig();
    public PlaybackMethods playbackMethods = new PlaybackMethods();
}
