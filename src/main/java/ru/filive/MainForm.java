package ru.filive;

/**
 *
 * @author Marat Shalmanov
 */

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
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
        Scene scene = new Scene(root, 800, 600);
        
        primaryStage.setTitle("Notepad");
        //frmMain.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); //exit from close app
        primaryStage.setScene(scene);
        primaryStage.show();
    }   

    public static void main(String[] args)
    {
        launch(args);
    }
}