package xyz.pary.addblockstoplaylist_6tv;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import static xyz.pary.addblockstoplaylist_6tv.Main.HOME_DIRECTORY;
import xyz.pary.addblockstoplaylistcore.Parameters;
import xyz.pary.addblockstoplaylistcore.adblocks.AdBlocks;
import xyz.pary.addblockstoplaylistcore.adblocks.AdSheetException;
import xyz.pary.addblockstoplaylistcore.adblocks.BlockTime;
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
import xyz.pary.onair.OnAirParserException;
import xyz.pary.onair.Parser;
import xyz.pary.onair.command.Command;

public class App {

    private static final Logger LOG = LogManager.getLogger(App.class);

    private static final String SETTINGS_FILE = HOME_DIRECTORY + "settings.txt";
    private static final String LAST_PARAMETERS_FILE = HOME_DIRECTORY + "last-params";

    private static final String FILE_NOT_SELECTED_MESSAGE = "Файл не выбран. Работа программы завершена.";

    private Settings settings;

    private String outputFileName = "";

    private List<Command> schedule;
    private Date scheduleDate = new Date(0);
    private AdBlocks adBlocks;
    private Parameters parameters;

    public App() {
        try {
            settings = new Settings(new File(SETTINGS_FILE));
        } catch (SettingsException e) {
            showErrorAndExit(e.getMessage());
        }
    }

    public void run() {
        loadSchedule();

        if (settings.getBoolParameter(SettingsKeys.ON_AD_BLOCKS)) {
            String sheetPath = formAdSheetPath();
            loadAdSheet(sheetPath);
        }

        loadParameters();

        ScheduleProcessingManager spm = new ScheduleProcessingManager(schedule);
        List<Command> outputSchedule = spm.processSchedule(createScheduleProcessors());

        writeScheduleToFile(outputSchedule);
        showMessage("Количество строк после рекламных блоков",
                "Количество строк после рекламных блоков: " + spm.getAdditionalInfo().get(Settings.KEY_AD_BLOCKS_CRAWL_LINE_COUNTER));
        showAndWriteErrors(spm.getErrors());
        writeParametersToFile();
        System.exit(0);
    }

    private List<ScheduleProcessor> createScheduleProcessors() {
        List<ScheduleProcessor> sps = new ArrayList<>(5);
        if (settings.getBoolParameter(SettingsKeys.ON_AD_BLOCKS)) {
            sps.add(new AdBlocksInserter(settings, adBlocks, scheduleDate, parameters));
        }
        if (settings.getBoolParameter(SettingsKeys.ON_NEWS_AD_BLOCK)) {
            sps.add(new NewsAdBlocksInserter(settings));
        }
        if (settings.getBoolParameter(SettingsKeys.ON_TOBACCO)) {
            sps.add(new TobaccoInserter(settings, parameters));
        }
        if (settings.getBoolParameter(SettingsKeys.ON_CRAWL_LINE)) {
            sps.add(new CrawlLineInserter(settings, parameters, scheduleDate));
        }
        if (settings.getBoolParameter(SettingsKeys.ON_ANNOUNCER_NOW)) {
            sps.add(new AnnouncerNowInserter(settings, scheduleDate));
        }
        return sps;
    }

    private void loadSchedule() {
        JFileChooser fileChooser = new JFileChooser(settings.getParameter(SettingsKeys.SCHEDULE_PATH));
        fileChooser.setDialogTitle("Выберите расписание");
        fileChooser.setFileFilter(new FileNameExtensionFilter("AIR", "air"));

        int chooserResult = fileChooser.showOpenDialog(null);
        if (chooserResult == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            this.outputFileName = file.getAbsolutePath().substring(0, file.getAbsolutePath().length() - 4) + "_сРекламой";

            String fileName = file.getName();
            String dateFormat = settings.getParameter(SettingsKeys.SCHEDULE_DATE_FORMAT);
            //Паттерн даты
            Pattern datePattern = Pattern.compile(dateFormat.replaceAll("[dMy]", "\\\\d"));
            //Поиск даты в имени файла
            Matcher matcher = datePattern.matcher(fileName);
            if (matcher.find()) {
                //Получение строковой даты из имени файла
                String stringDate = fileName.substring(matcher.start(), matcher.end());
                //Преобразование строковой даты в объект Date
                SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
                try {
                    this.scheduleDate = sdf.parse(stringDate);
                } catch (ParseException pe) {
                    LOG.warn("Schedule date parse exception: ", pe);
                }
            }

            //парсинг расписания
            try {
                this.schedule = Parser.parse(file);
            } catch (OnAirParserException e) {
                showErrorAndExit(e.getMessage());
            }
        } else {
            showErrorAndExit(FILE_NOT_SELECTED_MESSAGE);
        }
    }

    private String formAdSheetPath() {
        String[] sheetParams = settings.getParameter(SettingsKeys.AD_SHEET_PATH).split("\\|");
        String sheetPath = sheetParams[0].trim();
        if (sheetParams.length > 1) {
            SimpleDateFormat df = new SimpleDateFormat();
            for (int i = 1; i < sheetParams.length; i++) {
                df.applyPattern(sheetParams[i].trim());
                sheetPath = sheetPath.replaceFirst("<>", df.format(scheduleDate));
            }
        }
        return sheetPath;
    }

    private void loadAdSheet(String sheetPath) {
        JFileChooser fileChooser = new JFileChooser(sheetPath);
        fileChooser.setDialogTitle("Выберите эфирный лист");
        fileChooser.setFileFilter(new FileNameExtensionFilter("XLS", "xls"));

        int chooserResult = fileChooser.showOpenDialog(null);
        if (chooserResult == JFileChooser.APPROVE_OPTION) {
            try {
                TimeFormatter tf = new TimeFormatter(settings.getParameter(SettingsKeys.AD_BLOCK_TIME_FORMAT));
                this.adBlocks = new JExcelAdBlocks(fileChooser.getSelectedFile(), tf, settings.getParameter(SettingsKeys.AD_PATH));
            } catch (AdSheetException e) {
                showErrorAndExit(e.getMessage());
            }
        } else {
            showErrorAndExit(FILE_NOT_SELECTED_MESSAGE);
        }
    }

    private void loadParameters() {
        //Загрузка последних параметров из файла
        Parameters parameters;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(new File(LAST_PARAMETERS_FILE)))) {
            parameters = (Parameters) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            LOG.warn("Load last parameters from file exception: ", e);
            parameters = new Parameters();
        }

        //кол-во анонсов-трейлеров
        if (settings.getBoolParameter(SettingsKeys.ON_AD_BLOCKS)) {
            parameters.setTrailersNumber(showTrailersDialog("Кол-во чередующихся анонсов-трейлеров (число >= 1)", parameters.getTrailersNumber()));
            if (settings.getBoolParameter(SettingsKeys.ON_SECOND_TRAILER)) {
                parameters.setSecondTrailersNumber(showTrailersDialog("Кол-во вторых анонсов-трейлеров (число >= 1)", parameters.getSecondTrailersNumber()));
            }
        }
        //анонс-плашка
        if (settings.getBoolParameter(SettingsKeys.ON_ANNOUNCEMENT)) {
            try {
                String announcementName = JOptionPane.showInputDialog(null, "Введите название анонса-плашки", parameters.getAnnouncementName());
                if (announcementName == null) {
                    announcementName = "";
                }
                parameters.setAnnouncementName(announcementName);
                if (!announcementName.isEmpty()) {
                    parameters.setAnnouncementStartTime(showAnnouncementTimeDialog(
                            "Введите начальное время анонса-плашки в формате чч:мм (08:15) - в это время будет показан 1-ый раз",
                            parameters.getAnnouncementStartTime()));

                    parameters.setAnnouncementEndTime(showAnnouncementTimeDialog(
                            "Введите конечное время анонса-плашки в формате чч:мм (16:45) - в это время будет показан последний раз",
                            parameters.getAnnouncementStartTime()));
                }
            } catch (Exception e) {
                LOG.warn("Not valid input of announcement", e);
                parameters.setAnnouncementName("");
            }
        }

        //длительность коммерческой строки
        if (settings.getBoolParameter(SettingsKeys.ON_POP_UP_AD)) {
            try {
                String commerceDurationInput = JOptionPane.showInputDialog(
                        null,
                        "Введите длительность коммерческой строки (которую пишет OnAir), в секундах",
                        parameters.getCommerceCrawlLineDuration());
                int commerceDuration = Integer.parseInt(commerceDurationInput);
                parameters.setCommerceCrawlLineDuration(commerceDuration);
            } catch (Exception e) {
                LOG.warn("Not valid input of duration commerce crawl line", e);
                parameters.setCommerceCrawlLineDuration(120);
            }
        }
        this.parameters = parameters;
    }

    private void writeScheduleToFile(List<Command> schedule) {
        String fileName = outputFileName + ".air";
        try (BufferedWriter out = new BufferedWriter(new FileWriter(fileName, false))) {
            for (Command c : schedule) {
                out.write(c.toSheduleRow() + System.lineSeparator());
                out.flush();
            }
            showMessage("Выполнение успешно завершено", "Записано в файл: " + fileName);
        } catch (IOException e) {
            LOG.warn("Cannot write schedule to file", e);
            showErrorAndExit("Ошибка записи в файл: " + fileName);
        }
    }

    private void showAndWriteErrors(List<String> errors) {
        if (errors != null && !errors.isEmpty()) {
            try (BufferedWriter out = new BufferedWriter(new FileWriter(outputFileName + "_ERRORS.txt"))) {
                for (String e : errors) {
                    showMessage("Ошибка", e);
                    out.write(e + System.lineSeparator());
                    out.flush();
                }
            } catch (IOException e) {
                LOG.warn("Cannot write errors to file", e);
            }

        }
    }

    private void writeParametersToFile() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(new File(LAST_PARAMETERS_FILE)))) {
            oos.writeObject(parameters);
        } catch (Exception e) {
            LOG.warn("Save last parameters to file exception: ", e);
        }
    }

    private int showTrailersDialog(String message, int currentTrailers) {
        try {
            String input = JOptionPane.showInputDialog(null, message, currentTrailers);
            int trailersNumber = Integer.parseInt(input);
            return trailersNumber;
        } catch (NumberFormatException e) {
            return 2;
        }
    }

    private int showAnnouncementTimeDialog(String message, int timeInMinutes) {
        String startTimeStr = JOptionPane.showInputDialog(
                null,
                message,
                new BlockTime(timeInMinutes));
        int hours = Integer.parseInt(startTimeStr.substring(0, 2));
        int mins = Integer.parseInt(startTimeStr.substring(3));
        return hours * 60 + mins;
    }

    public static void showErrorAndExit(String message) {
        showMessage("Ошибка", message);
        System.exit(1);
    }

    public static void showMessage(String title, String message) {
        JOptionPane.showMessageDialog(null, message, title, JOptionPane.INFORMATION_MESSAGE);
    }
}
