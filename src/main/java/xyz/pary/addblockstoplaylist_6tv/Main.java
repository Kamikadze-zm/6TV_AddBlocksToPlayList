package xyz.pary.addblockstoplaylist_6tv;

import java.io.File;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {
    
    private static final Logger LOG = LogManager.getLogger(Main.class);
    
    private static final String DEFAULT_APP_NAME = "6TV_AddBlocksToPlayList";
    
    public static final String HOME_DIRECTORY = System.getProperty("user.home") + File.separator
            + "Documents" + File.separator + getAppName() + File.separator;
    
    public static void main(String[] args) {
        File homeDirectory = new File(HOME_DIRECTORY);
        if (!homeDirectory.exists()) {
            homeDirectory.mkdirs();
        }
        try {
            new App().run();
        } catch (Exception e) {
            LOG.error("Unexpected exception: ", e);
            App.showErrorAndExit(e.getMessage());
        }
    }
    
    private static String getAppName() {
        return DEFAULT_APP_NAME;
    }
}
