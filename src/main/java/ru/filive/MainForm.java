package ru.filive;

/**
 *
 * @author Marat Shalmanov
 */

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class MainForm extends Application
{
    //private static final long serialVersionUID = 1L;

    /**
     * Конструктор класса
     */
    @Override
    public void start(Stage primaryStage) throws Exception
    {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/MainForm.fxml"));
        AnchorPane root = loader.load();
        MainFormController controller = loader.getController();
        Scene scene = new Scene(root, 800, 600);
        scene.getStylesheets().add(getClass().getResource("/css/java-keywords.css").toExternalForm());

        primaryStage.setTitle("Notepad");
        primaryStage.getIcons().add(
            new Image(getClass().getResourceAsStream("/images/NotepadM.png"))
        );
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(600);
        primaryStage.setOnCloseRequest(closeEvent -> {
            if (!controller.canClose())
            {
                closeEvent.consume();
            }
        });
        primaryStage.setScene(scene);
        primaryStage.show();

        // ни один файл не открыт при старте — создаём пустой документ по умолчанию
        controller.newTab();
    }   

    public static void main(String[] args)
    {
        launch(args);
    }
}