package com.lukechenshui;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.ResourceBundle;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class WallhavenFetcherController {
    private HashMap<String, String> categoryCodeMap = new HashMap<>();
    private boolean nsfwEnabled = false;
    @FXML
    private ResourceBundle resources;

    @FXML
    private URL location;

    @FXML
    private TextArea logArea;

    @FXML
    private ChoiceBox categoryMenu;

    @FXML // fx:id="numWallpaperTextBox"
    private TextField numWallpaperTextBox; // Value injected by FXMLLoader

    @FXML // fx:id="categorizedProgress"
    private ProgressBar categorizedProgress; // Value injected by FXMLLoader

    @FXML
    private ProgressBar randomProgress;

    private static int numberOfRetriesForRandomlySelectingId = 0;

    @FXML
    void toggleNSFW(MouseEvent event) {
        nsfwEnabled = !nsfwEnabled;
    }

    private void updateCategoryProgressIndicator(int val1, int val2){
        Platform.runLater(new Runnable(){
            @Override
            public void run(){
                categorizedProgress.setProgress((double)val1/val2);
            }
        });
    }

    private void updateRandomProgressIndicator(int val1, int val2){
        Platform.runLater(new Runnable(){
            @Override
            public void run(){
                randomProgress.setProgress((double)val1/val2);
            }
        });
    }

    private static boolean isSFW(int id){
        String URL = "https://alpha.wallhaven.cc/wallpaper/" + String.valueOf(id);
        try{
            Document page = Jsoup.connect(URL).
                    timeout(0)
                    .userAgent("Mozilla/5.0 (X11; Linux x86_64; rv:47.0) Gecko/20100101 Firefox/47.0")
                    .get();
            //For sketchy: Elements nsfwElements = page.select("label.sketchy");
            Elements nsfwElements = page.select("label.sfw");
            //System.out.println(page.html());
            //System.out.println(nsfwElements.html());
            /*
            The following code makes sure the following is present:
            <fieldset class="framed">
                <input type="radio" checked>
                <label class="purity sfw">SFW</label>
            </fieldset>
             */
            for(Element nsfwElement : nsfwElements){
                if(nsfwElement.parent().children().size() == 2){
                    return true;
                }
            }
            return false;
        }
        catch(IOException exc){
            return false;
        }

    }
    private ArrayList<Integer> getCategoryPictureIds(int number){
        String baseURL = "https://alpha.wallhaven.cc/search?page=";
        //System.out.println("Connecting...");
        ArrayList<Integer> ids = new ArrayList<>();
        int pageCounter = 1;
        int imageCounter = 0;
        try {
            while(true) {
                Document page = Jsoup.connect(baseURL + String.valueOf(pageCounter) + "&categories=" +
                        categoryCodeMap.get(categoryMenu.getValue())).
                        timeout(0)
                        .userAgent("Mozilla/5.0 (X11; Linux x86_64; rv:47.0) Gecko/20100101 Firefox/47.0")
                        .get();
                Elements images = page.select("a.preview");
                for(int counter = 0; counter < images.size() && imageCounter < number; counter++, imageCounter++){
                    Element element = images.get(counter);
                    String href = element.attr("href");
                    int pictureId = 0;
                    Pattern pictureIdRegex = Pattern.compile("\\d+");
                    Matcher matcher = pictureIdRegex.matcher(href);

                    if(matcher.find()){
                        pictureId = Integer.valueOf(matcher.group());
                    }
                    if(!(nsfwEnabled || isSFW(pictureId))){
                        imageCounter--;
                    }
                    else{
                        ids.add(pictureId);
                    }
                    System.out.printf("IMAGE COUNTER: %d\n", imageCounter);
                    appendToLogsNoNewLine(".");
                }
                if(imageCounter >= number){
                    break;
                }
                pageCounter++;
            }
        }
        catch(IOException exc){
            exc.printStackTrace();
        }
        return ids;
    }
    private void appendToLogs(String message){
        Platform.runLater(new Runnable(){
            @Override
            public void run(){
                logArea.appendText(message + System.lineSeparator());
            }
        });
    }
    private void appendToLogsNoNewLine(String message){
        Platform.runLater(new Runnable(){
            @Override
            public void run(){
                logArea.appendText(message);
            }
        });
    }
    private void getPicturesFromIds(ArrayList ids, String path, boolean... print){
        for(int counter = 0; counter < ids.size(); counter++){

            String downloadingProgressLine = String.valueOf(counter+1) + "/" + String.valueOf(ids.size())
            + " Downloading image :" +  ids.get(counter);

            if(print.length == 0){
                updateCategoryProgressIndicator(counter+1, ids.size());
                System.out.println(downloadingProgressLine);
                appendToLogs(downloadingProgressLine);
            }
            String[] exts = {".jpg", ".png", ".bmp"};

            for(String extension : exts){
                try{
                    URL imageURL = new URL("https://wallpapers.wallhaven.cc/wallpapers/full/wallhaven-" + ids.get(counter) + extension);
                    URLConnection conn = imageURL.openConnection();
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:47.0) Gecko/20100101 Firefox/47.0");
                    conn.connect();
                    //System.out.println(imageURL);
                    FileUtils.copyInputStreamToFile(conn.getInputStream(), Paths.get(path + File.separator + String.valueOf(ids.get(counter)) + extension).toFile());
                }
                catch(Exception exc){
                    //exc.printStackTrace();
                }
            }

        }
        if(print.length == 0){
            updateCategoryProgressIndicator(1, 0);
        }
    }
    private boolean downloadingCategories = false;
    private boolean downloadingRandomly = false;
    @FXML
    void startCategorizedDownloadOnClick(ActionEvent event) {
        Runnable task = () -> {
            if(numWallpaperTextBox.getText().length() > 0 && !downloadingCategories && !downloadingRandomly){
                try{
                    String category = ((String)categoryMenu.getValue()).toLowerCase();
                    int limit = Integer.valueOf(numWallpaperTextBox.getText());
                    updateCategoryProgressIndicator(-1, 0);
                    downloadingCategories = true;
                    appendToLogs("Getting ids");
                    ArrayList<Integer> ids = getCategoryPictureIds(limit);
                    appendToLogs("Done getting ids");
                    String outputDirectory = "categorized" + File.separator + category;
                    getPicturesFromIds(ids, outputDirectory);
                    appendToLogs("Done downloading images! They're in " + outputDirectory);
                    downloadingCategories = false;
                }
                catch(Exception exc){
                    exc.printStackTrace();
                    downloadingCategories = false;
                }

            }
        };
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }
    private static boolean checkIfRandomImageAlreadyDownloaded(int id){
        File random = new File("random");
        File[] contents = random.listFiles();
        for(File file : contents){
            String fileName = file.getName();
            fileName = FilenameUtils.removeExtension(fileName);
            if(Integer.valueOf(fileName) == id){
                numberOfRetriesForRandomlySelectingId = 0;
                return true;
            }
        }
        numberOfRetriesForRandomlySelectingId++;
        return false;
    }
    private static ArrayList<Integer> getPictureIds(long number){
        String baseURL = "https://alpha.wallhaven.cc/latest?page=";
        //System.out.println("Connecting...");
        ArrayList<Integer> ids = new ArrayList<>();
        try {
            for (long counter = 0; counter < number; counter++) {

                Document page = Jsoup.connect(baseURL + String.valueOf(counter + 1)).
                        timeout(0)
                        .userAgent("Mozilla/5.0 (X11; Linux x86_64; rv:47.0) Gecko/20100101 Firefox/47.0")
                        .get();
                Elements images = page.select("a.preview");
                for(Element element : images){
                    String href = element.attr("href");
                    int pictureId = 0;
                    Pattern pictureIdRegex = Pattern.compile("\\d+");
                    Matcher matcher = pictureIdRegex.matcher(href);
                    if(matcher.find()){
                        pictureId = Integer.valueOf(matcher.group());
                        ids.add(pictureId);
                    }
                }
            }
        }
        catch(IOException exc){
            exc.printStackTrace();
        }
        return ids;
    }
    @FXML
    void startRandomDownloadOnClick(ActionEvent event) {
        Runnable task = () -> {
            if(numWallpaperTextBox.getText().length() > 0 && !downloadingRandomly && !downloadingCategories) {
                try{
                    downloadingRandomly = true;
                    int iterations = Integer.valueOf(numWallpaperTextBox.getText());
                    appendToLogs("Initializing random downloads");

                    for(int counter = 0; counter < iterations; counter++){
                        updateRandomProgressIndicator(counter+1, iterations);
                        System.out.printf("%d / %d\n ", counter + 1, iterations);
                        appendToLogsNoNewLine(String.valueOf(counter + 1) + " / " + String.valueOf(iterations) + " ");
                        ArrayList<Integer> ids = getPictureIds(1);
                        int max = ids.get(0);
                        int random;
                        do{
                            random = ThreadLocalRandom.current().nextInt(1, max + 1);
                            //random=209773;
                            //System.out.println(random);
                            if(numberOfRetriesForRandomlySelectingId > max){
                                System.out.println("All randomly generated image ids already exist in the random folder as images.");
                                System.exit(1);
                            }
                        }while(checkIfRandomImageAlreadyDownloaded(random) || (!isSFW(random) && !nsfwEnabled));
                        updateRandomProgressIndicator(counter+1, iterations);
                        appendToLogs(String.valueOf(random));
                        ids = new ArrayList<>();
                        ids.add(random);
                        getPicturesFromIds(ids, "random", false);
                    }
                    updateRandomProgressIndicator(1, 0);
                    appendToLogs("Done downloading images! Check in the 'random folder");
                    downloadingRandomly = false;
                }
                catch(Exception exc){
                    exc.printStackTrace();
                    downloadingRandomly = false;
                }

            }
        };
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }
    private void initializeFolders(){
        String separator = File.separator;
        String[] folders = {"random", "categorized", "categorized" + separator + "all",
            "categorized" + separator + "anime", "categorized" + separator + "people",
                "categorized" + separator + "general", "categorized" + separator + "general-people",
                    "categorized" + separator + "general-anime"};
        for(int counter = 0; counter < folders.length; counter++){
            String folder = folders[counter];
            if(!Files.exists(Paths.get(folder))){
                try{
                    Files.createDirectory(Paths.get(folder));
                }
                catch(IOException exc){
                    exc.printStackTrace();
                }
            }
        }
    }
    @FXML
    public void initialize() {
        assert categoryMenu != null : "fx:id=\"categoryMenu\" was not injected: check your FXML file 'wallhaven-fetcher.fxml'.";
        assert logArea != null : "fx:id=\"logArea\" was not injected: check your FXML file 'wallhaven-fetcher.fxml'.";

        String[] categoryListArr = {"All", "Anime", "General", "General Anime", "General People", "People"};
        initializeFolders();
        categoryMenu.setItems(FXCollections.observableList(Arrays.asList(categoryListArr)));
        categoryMenu.setValue("All");
        categoryCodeMap.put("All", "111");
        categoryCodeMap.put("Anime", "010");
        categoryCodeMap.put("People", "001");
        categoryCodeMap.put("General", "100");
        categoryCodeMap.put("General Anime", "110");
        categoryCodeMap.put("General People", "101");
        logArea.setWrapText(true);
    }
}
