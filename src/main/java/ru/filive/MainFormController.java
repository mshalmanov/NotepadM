package ru.filive;

/**
 *
 * @author Marat Shalmanov
 */

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

public class MainFormController 
{
    @FXML
    private TabPane tabPane;
    
    @FXML
    private MenuItem newMenuItem;
    
    @FXML
    private MenuItem openMenuItem;
    
    @FXML
    private MenuItem saveMenuItem;
    
    @FXML
    private MenuItem exitMenuItem;
    
    @FXML
    private MenuItem cutMenuItem;
    
    @FXML
    private MenuItem copyMenuItem;
    
    @FXML
    private MenuItem pasteMenuItem;
    
    // --- get TextArea for the current tab (if tab exists)
    private TextArea getCurrentTextArea()
    {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab != null && tab.getContent() instanceof TextArea)
        {
            return (TextArea) tab.getContent();
        }
        return null;
    }
    
    @FXML
    private void onNewAction(ActionEvent event)
    {
        TextArea textArea = new TextArea();
        Tab tab = new Tab("New File");
        tab.setContent(textArea);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
    }
    
    @FXML
    private void onOpenAction(ActionEvent event)
    {   
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Open");
        alert.setHeaderText(null);
        alert.setContentText("Your pressed Open!");
        alert.showAndWait();
        System.out.println("MainFormController.onOpenAction()");
    }
    
    @FXML
    private void onSaveAction(ActionEvent event)
    {
        System.out.println("MainFormController.onSaveAction()");
    }
    
    @FXML
    private void onExitAction(ActionEvent event)
    {
        System.out.println("MainFormController.onExitAction()");
    }
    
    @FXML
    private void onCutAction(ActionEvent event)
    { 
        /* Вырезание текста */ 
    }
    
    @FXML
    private void onCopyAction(ActionEvent event)
    {
        TextArea currentTextArea = getCurrentTextArea();

        // Проверка, что текущая вкладка содержит TextArea и текст выбран
        if (currentTextArea != null && !currentTextArea.getSelectedText().isEmpty())
        {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(currentTextArea.getSelectedText());  // Копирование выделенного текста
            clipboard.setContent(content);
        } else {
            // Если текст не выбран, можно уведомить пользователя
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Text Selected");
            alert.setHeaderText(null);
            alert.setContentText("Please select some text to copy.");
            alert.showAndWait();
        }
    }

    @FXML
    private void onPasteAction(ActionEvent event)
    {
        /* Вставка текста */ 
    }
    
    @FXML
    private void onBoldAction(ActionEvent event)
    {
        /* Сделать выделенный текст жирным */
    }

    @FXML
    private void onItalicAction(ActionEvent event)
    {
        /* Сделать выделенный текст курсивом */
    }

    @FXML
    private void onUnderlineAction(ActionEvent event)
    {
        /* Сделать выделенный текст подчеркнутым */
    }

    @FXML
    private void onAboutAction(ActionEvent event)
    {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About");
        alert.setHeaderText("NotepadM Application");        
        alert.setContentText("Autor is Marat Shalmanov.\nVersion: 1.0\n© 2025 All rights reserved.");        
        alert.showAndWait();
    }
}
