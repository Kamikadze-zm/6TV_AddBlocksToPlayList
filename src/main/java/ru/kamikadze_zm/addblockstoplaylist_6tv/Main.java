package ru.kamikadze_zm.addblockstoplaylist_6tv;

import java.io.File;

public class Main {
    
    private static final String DEFAULT_APP_NAME = "6TV_AddBlocksToPlayList";
    
    public static final String HOME_DIRECTORY = System.getProperty("user.home") + File.separator
            + "documents" + File.separator + getAppName() + File.separator;
    
    public static void main(String[] args) {
        File homeDirectory = new File(HOME_DIRECTORY);
        if (!homeDirectory.exists()) {
            homeDirectory.mkdirs();
        }
    }
    
    private static String getAppName() {
        return DEFAULT_APP_NAME;
    }
}
