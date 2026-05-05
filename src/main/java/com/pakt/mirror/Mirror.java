package com.pakt.mirror;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamException;
import com.github.sarxos.webcam.WebcamUtils;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.media.AudioClip;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Mirror extends Application {

    private StackPane root;
    private ImageView camView;
    private Webcam currentWebcam;
    private BufferedImage bufferedImage;

    private enum Resolution {
        VGA, HD, FHD, QHD, UHD
    }
    private final Resolution resolution = Resolution.HD;
    private final StringProperty errorMsg = new SimpleStringProperty("No Detected Webcam");
    private final List<Webcam> systemWebcams = new ArrayList<>();

    private Path compaktMirrorDir;
    private Label webcamNameLabel;
    private Label counterLabel;
    private int counter = 5;
    private boolean isCaptureDelayed = false;

    @Override
    public void start(Stage stage) {
        createCompaktMirrorDir();
        systemWebcams.addAll(Webcam.getWebcams());
        root = new StackPane();
        initRoot();
        var scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(
                Objects.requireNonNull(Mirror.class.getResource("/style/main.css")).toExternalForm());
        setSceneEvent(scene);
        stage.setScene(scene);
        stage.setTitle("Compakt Mirror");
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.getIcons().add(new Image(
                Objects.requireNonNull(Mirror.class.getResourceAsStream("/images/icon.png"))));
        stage.show();
        loadWebcam(Webcam.getDefault());
    }

    private void initRoot() {
        var screen = Screen.getPrimary().getBounds();
        root.setPrefWidth(screen.getWidth() - (screen.getWidth() * 0.12));
        root.setPrefHeight(screen.getHeight() - (screen.getHeight() * 0.12));
        root.setBackground(Background.fill(Color.BLACK));
        webcamNameLabel = new Label();
        camView = getCamView();
        var errorLabel = getErrorLabel();
        counterLabel = getCounterLabel();
        root.getChildren().addAll(camView, errorLabel, counterLabel, webcamNameLabel);
    }

    private void loadWebcam(Webcam webcam) {
        if (currentWebcam != null) if (currentWebcam.isOpen()) currentWebcam.close();
        currentWebcam = webcam;
        if (currentWebcam != null) {
            setWebcamName();
            errorMsg.setValue(""); //
            currentWebcam.setCustomViewSizes(customDimensions());
            currentWebcam.setViewSize(getDimension(resolution));
            try {
                currentWebcam.open();
            } catch (WebcamException e) {
                loadWebcam(Webcam.getDefault());
            }
            camView.setVisible(true);
            var camThread = getCamThread(currentWebcam, camView);
            camThread.start();
        }
    }

    private Thread getCamThread(Webcam webcam, ImageView camView) {
        var camThread = new Thread(() -> {
            while (true) {
                bufferedImage = webcam.getImage();
                if (bufferedImage != null) {
                    Image fxImage;
                    try {
                        fxImage = SwingFXUtils.toFXImage(bufferedImage, null);
                    } catch (NullPointerException e) {
                        return;
                    }
                    Image finalFxImage = fxImage;
                    Platform.runLater(() -> {
                        camView.setImage(finalFxImage);
                        camView.setScaleX(-1.0);
                    });
                }
            }
        });
        camThread.setDaemon(true);
        return camThread;
    }

    private void setSceneEvent(Scene scene) {
        scene.setOnMousePressed(event -> {
            if (event.getButton().equals(MouseButton.SECONDARY)) {
                if (!systemWebcams.isEmpty())
                    getContextMenu().show(scene.getWindow(), event.getX(), event.getY());
            }
        });
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) Platform.exit();
            if (event.getCode() == KeyCode.C) captureImage();
            if (event.getCode() == KeyCode.V) {
                if (!isCaptureDelayed) {
                    isCaptureDelayed = true;
                    captureDelayImage();
                }
            }
            if (event.getCode() == KeyCode.ALT) {
                if (!systemWebcams.isEmpty()) {
                    var node = scene.getWindow();
                    getContextMenu().show(node, node.getX(), node.getY());
                }
            }
        });
        scene.setOnMouseClicked(event -> {
            if (event.getButton().equals(MouseButton.MIDDLE)) captureImage();
        });
    }

    private void createCompaktMirrorDir() {
        compaktMirrorDir = Paths.get(
                System.getProperty("user.home"), "Pictures", "CompaktMirror");
        if (Files.notExists(compaktMirrorDir)) {
            try {
                Files.createDirectory(compaktMirrorDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void captureImage() {
        createCompaktMirrorDir();
        if (currentWebcam != null) {
            var picturesPath = Paths.get(compaktMirrorDir.toString(), getCaptureFileName());
            WebcamUtils.capture(currentWebcam, picturesPath.toFile());
            animateShutter();
        }
    }

    private void captureDelayImage() {
        createCompaktMirrorDir();
        if (currentWebcam != null) {
            var timeline = new Timeline();
            timeline.getKeyFrames().add(
                    new KeyFrame(Duration.seconds(1.5), _ -> {
                        var clockTick = new AudioClip(
                                Objects.requireNonNull(Mirror.class.getResource("/audio/tick.wav")).toExternalForm());
                        clockTick.play();
                        counterLabel.setText(String.valueOf(counter));
                        counter--;
                        if (counter < 0) {
                            timeline.stop();
                            counterLabel.setText("");
                            counter = 5;
                            isCaptureDelayed = false;
                            captureImage();
                        }
                    })
            );
            timeline.setCycleCount(Animation.INDEFINITE);
            timeline.play();
        }
    }

    private ImageView getCamView() {
        var camView = new ImageView();
        camView.setPreserveRatio(true);
        camView.fitWidthProperty().bind(root.widthProperty());
        camView.fitHeightProperty().bind(root.heightProperty());
        return camView;
    }

    private ContextMenu getContextMenu() {
        ObservableList<MenuItem> menuItems = FXCollections.observableArrayList();
        for (Webcam webcam : systemWebcams) {
            var label = new Label(webcam.getName());
            label.getStyleClass().add("item-label");
            var menuItem = new MenuItem();
            menuItem.setGraphic(label);
            menuItem.setOnAction(_ -> loadWebcam(webcam));
            menuItems.add(menuItem);
        }
        var menu = new ContextMenu();
        menu.getStyleClass().add("simple-context-menu");
        menu.getItems().addAll(menuItems);
        return menu;
    }

    private String getCaptureFileName() {
        var now = LocalDateTime.now();
        var formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy_HH:mm:ss");
        var formattedDate = now.format(formatter);
        return "compakt-mirror_capture_" + formattedDate + ".png";
    }

    private Label getErrorLabel() {
        var errorLabel = new Label();
        errorLabel.textProperty().bind(errorMsg);
        errorLabel.getStyleClass().add("error-label");
        return errorLabel;
    }

    private Label getCounterLabel() {
        var counterLabel = new Label();
        counterLabel.getStyleClass().add("counter-label");
        return counterLabel;
    }

    private Dimension getDimension(Resolution resolution) {
        return switch (resolution) {
            case VGA -> customDimensions()[0];
            case HD -> customDimensions()[1];
            case FHD -> customDimensions()[2];
            case QHD -> customDimensions()[3];
            case UHD -> customDimensions()[4];
        };
    }

    private void setWebcamName() {
        var name = currentWebcam.getName();
        StackPane.setAlignment(webcamNameLabel, Pos.TOP_RIGHT);
        webcamNameLabel.setText(name);
        webcamNameLabel.getStyleClass().add("webcam-name-label");
    }

    private void animateShutter() {
        var shutter = new AudioClip(
                Objects.requireNonNull(Mirror.class.getResource("/audio/shutter.wav")).toExternalForm());
        shutter.play();
        var region = new Region();
        region.setBackground(Background.fill(Color.BLACK));
        var fadeIn = new FadeTransition(Duration.millis(150), region);
        var fadeOut = new FadeTransition(Duration.millis(150), region);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(0.65);
        fadeOut.setFromValue(0.65);
        fadeOut.setToValue(0.0);
        root.getChildren().add(region);
        fadeIn.setOnFinished(_ -> fadeOut.play());
        fadeOut.setOnFinished(_ -> root.getChildren().remove(region));
        fadeIn.play();
    }

    private Dimension[] customDimensions() {
        return new Dimension[]{
                new Dimension(640, 480),
                new Dimension(1280, 720),
                new Dimension(1920, 1080),
                new Dimension(2560, 1440),
                new Dimension(3840, 2160)
        };
    }

    @Override
    public void stop() {
        if (currentWebcam != null) currentWebcam.close();
    }
}