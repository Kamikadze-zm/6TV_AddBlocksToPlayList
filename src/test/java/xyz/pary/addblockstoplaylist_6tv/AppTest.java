package xyz.pary.addblockstoplaylist_6tv;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import xyz.pary.addblockstoplaylistcore.Parameters;
import xyz.pary.addblockstoplaylistcore.adblocks.AdBlocks;
import xyz.pary.addblockstoplaylistcore.adblocks.AdSheetException;
import xyz.pary.addblockstoplaylistcore.adblocks.JExcelAdBlocks;
import xyz.pary.addblockstoplaylistcore.adblocks.TimeFormatter;
import xyz.pary.addblockstoplaylistcore.scheduleprocessor.AnnouncerNowInserter;
import xyz.pary.addblockstoplaylistcore.scheduleprocessor.CrawlLineInserter;
import xyz.pary.addblockstoplaylistcore.scheduleprocessor.ScheduleProcessingManager;
import xyz.pary.addblockstoplaylistcore.scheduleprocessor.ScheduleProcessor;
import xyz.pary.addblockstoplaylistcore.scheduleprocessor.TobaccoInserter;
import xyz.pary.addblockstoplaylistcore.scheduleprocessor.adblocks.AdBlocksInserter;
import xyz.pary.addblockstoplaylistcore.scheduleprocessor.adblocks.NewsAdBlocksInserter;
import xyz.pary.addblockstoplaylistcore.settings.Settings;
import xyz.pary.addblockstoplaylistcore.settings.Settings.SettingsKeys;
import xyz.pary.addblockstoplaylistcore.settings.SettingsException;
import xyz.pary.addblockstoplaylistcore.util.FileUtils;
import xyz.pary.onair.Parser;
import xyz.pary.onair.command.Command;

public class AppTest {

    private static final String SETTINGS_PATH = "target\\test-classes\\settings\\settings.txt";
    private static final String SCHEDULES_FOLDER = "target\\test-classes\\schedules\\";
    private static final String AD_SHEETS_FOLDER = "target\\test-classes\\adsheets\\";
    private static final String PARAMETERS_FOLDER = "target\\test-classes\\params\\";

    private static Settings settings;
    private static SimpleDateFormat sdf;
    private static Map<Long, List<Command>> schedules;
    private static Map<Long, List<String>> readySchedules;
    private static Map<Long, AdBlocks> adSheets;
    private static Map<Long, Parameters> parameters;

    @BeforeClass
    public static void setUpClass() {
        try {
            settings = new Settings(new File(SETTINGS_PATH));
        } catch (SettingsException e) {
            throw new RuntimeException(e.getMessage());
        }
        sdf = new SimpleDateFormat("yyyy-MM-dd");
        schedules = new HashMap<>();
        readySchedules = new HashMap<>();
        adSheets = new HashMap<>();
        parameters = new HashMap<>();

        File schedulesFolder = new File(SCHEDULES_FOLDER);

        for (File f : schedulesFolder.listFiles()) {
            try {
                if (f.getName().startsWith("ready")) {
                    Date date = sdf.parse(f.getName().substring(5, f.getName().lastIndexOf(".")));
                    readySchedules.put(date.getTime(), FileUtils.getLinesFromFile(f.getAbsolutePath()));
                } else {
                    Date date = sdf.parse(f.getName().substring(0, f.getName().lastIndexOf(".")));
                    schedules.put(date.getTime(), Parser.parse(f));

                }

            } catch (ParseException | IOException e) {
                throw new RuntimeException(e.getMessage());
            }
        }

        File adSheetsFolder = new File(AD_SHEETS_FOLDER);
        TimeFormatter timeFormatter = new TimeFormatter(settings.getParameter(SettingsKeys.AD_BLOCK_TIME_FORMAT));
        for (File f : adSheetsFolder.listFiles()) {
            try {
                Date date = sdf.parse(f.getName().substring(0, f.getName().lastIndexOf(".")));
                adSheets.put(date.getTime(), new JExcelAdBlocks(f, timeFormatter, settings.getParameter(SettingsKeys.AD_PATH)));
            } catch (ParseException | AdSheetException e) {
                throw new RuntimeException(e.getMessage());
            }
        }

        File parametersFolder = new File(PARAMETERS_FOLDER);
        for (File f : parametersFolder.listFiles()) {
            try (InputStreamReader is = new InputStreamReader(new FileInputStream(f))) {
                Date date = sdf.parse(f.getName().substring(0, f.getName().lastIndexOf(".")));
                Parameters p = new Parameters();
                Properties props = new Properties();
                props.load(is);
                p.setAnnouncementName(props.getProperty("an"));
                p.setAnnouncementStartTime(Integer.valueOf(props.getProperty("as")));
                p.setAnnouncementEndTime(Integer.valueOf(props.getProperty("ae")));
                p.setTrailersNumber(Integer.valueOf(props.getProperty("tc")));
                p.setSecondTrailersNumber(Integer.valueOf(props.getProperty("stc")));
                p.setCommerceCrawlLineDuration(Integer.valueOf(props.getProperty("ccld")));
                parameters.put(date.getTime(), p);
            } catch (ParseException | IOException e) {
                throw new RuntimeException(e.getMessage());
            }
        }
    }

    @Test
    public void testNoChangesAfterSecondExecuting() {
        for (Map.Entry<Long, List<Command>> e : schedules.entrySet()) {
            Long key = e.getKey();
            AdBlocks adBlocks = adSheets.get(key);
            Date date = new Date(key);
            Parameters p = parameters.get(key);

            List<ScheduleProcessor> scheduleProcessors = new ArrayList<>();
            if (settings.getBoolParameter(SettingsKeys.ON_AD_BLOCKS)) {
                scheduleProcessors.add(new AdBlocksInserter(settings, adBlocks, date, p));
            }
            if (settings.getBoolParameter(SettingsKeys.ON_NEWS_AD_BLOCK)) {
                scheduleProcessors.add(new NewsAdBlocksInserter(settings));
            }
            if (settings.getBoolParameter(SettingsKeys.ON_TOBACCO)) {
                scheduleProcessors.add(new TobaccoInserter(settings, p));
            }
            if (settings.getBoolParameter(SettingsKeys.ON_CRAWL_LINE)) {
                scheduleProcessors.add(new CrawlLineInserter(settings, p, date));
            }
            if (settings.getBoolParameter(SettingsKeys.ON_ANNOUNCER_NOW)) {
                scheduleProcessors.add(new AnnouncerNowInserter(settings, date));
            }
            ScheduleProcessingManager spm = new ScheduleProcessingManager(e.getValue());
            List<Command> firstEx = spm.processSchedule(scheduleProcessors);
            spm = new ScheduleProcessingManager(firstEx);
            List<Command> secondEx = spm.processSchedule(scheduleProcessors);

            System.out.println("testing " + sdf.format(date));
            if (firstEx.size() != secondEx.size()) {
                String firstExName = SCHEDULES_FOLDER + "firstex" + sdf.format(date) + ".air";
                saveScheduleToFile(firstExName, firstEx);
                String secondExName = SCHEDULES_FOLDER + "secondex" + sdf.format(date) + ".air";
                saveScheduleToFile(secondExName, secondEx);
                fail("Size not match. Date: " + sdf.format(date)
                        + ", schedule after first executing size: " + firstEx.size()
                        + ", schedule after second executing size: " + secondEx.size());
            }

            for (int i = 0; i < firstEx.size(); i++) {
                assertEquals(firstEx.get(i).toSheduleRow(), secondEx.get(i).toSheduleRow());
            }
        }
    }

    private void saveScheduleToFile(String fileName, List<Command> schedule) {
        try (BufferedWriter out = new BufferedWriter(new FileWriter(fileName, false))) {
            for (Command c : schedule) {
                out.write(c.toSheduleRow() + System.lineSeparator());
                out.flush();
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }
}
