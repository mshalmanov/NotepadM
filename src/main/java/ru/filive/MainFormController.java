package ru.filive;

/**
 *
 * @author Marat Shalmanov
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

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

    // --- содержимое вкладки: CodeArea + файл, с которым она связана (null, пока не сохранена/открыта)
    private static class TabContent
    {
        final CodeArea codeArea;
        File file;
        boolean dirty;

        TabContent(CodeArea codeArea, File file)
        {
            this.codeArea = codeArea;
            this.file = file;
        }
    }

    // ButtonData задан явно (YES/NO/CANCEL_CLOSE) — иначе ButtonBar относит кнопки без типа
    // в отдельную группу "OTHER" и расставляет их с большим отступом от Cancel
    private static final ButtonType SAVE_BUTTON = new ButtonType("Save", ButtonBar.ButtonData.YES);
    private static final ButtonType DONT_SAVE_BUTTON = new ButtonType("Don't Save", ButtonBar.ButtonData.NO);
    private static final ButtonType CANCEL_BUTTON = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

    private TabContent getCurrentTabContent()
    {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab != null && tab.getUserData() instanceof TabContent)
        {
            return (TabContent) tab.getUserData();
        }
        return null;
    }

    // --- get CodeArea for the current tab (if tab exists)
    private CodeArea getCurrentCodeArea()
    {
        TabContent tabContent = getCurrentTabContent();
        return tabContent != null ? tabContent.codeArea : null;
    }

    private Window getWindow()
    {
        return tabPane.getScene().getWindow();
    }

    // --- фильтры расширений для диалогов Open/Save, чтобы расширение файла было видно и подставлялось по умолчанию
    private void addTextFileExtensionFilters(FileChooser fileChooser)
    {
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text Files (*.txt)", "*.txt"),
                new FileChooser.ExtensionFilter("Java Files (*.java)", "*.java"),
                new FileChooser.ExtensionFilter("All Files (*.*)", "*.*"));
    }

    private void showError(String title, String message)
    {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // --- Save/Don't Save/Cancel для вкладки с несохранёнными изменениями (Exit, закрытие вкладки)
    private ButtonType askSaveChanges(String tabTitle)
    {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("NotepadM");
        alert.setHeaderText("Do you want to save changes to \"" + tabTitle + "\"?");
        alert.setContentText("Your changes will be lost if you don't save them.");
        alert.getButtonTypes().setAll(SAVE_BUTTON, DONT_SAVE_BUTTON, CANCEL_BUTTON);
        return alert.showAndWait().orElse(CANCEL_BUTTON);
    }

    // --- true = можно продолжать закрытие вкладки/приложения; false = пользователь отменил
    private boolean confirmClose(Tab tab)
    {
        if (!(tab.getUserData() instanceof TabContent tabContent) || !tabContent.dirty)
        {
            return true;
        }

        tabPane.getSelectionModel().select(tab);
        ButtonType result = askSaveChanges(tab.getText());
        if (result == DONT_SAVE_BUTTON)
        {
            return true;
        }
        if (result == SAVE_BUTTON)
        {
            return saveTab(tab, tabContent);
        }
        return false; // Cancel
    }

    private boolean saveTab(Tab tab, TabContent tabContent)
    {
        File file = tabContent.file;
        if (file == null)
        {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save File");
            addTextFileExtensionFilters(fileChooser);
            file = fileChooser.showSaveDialog(getWindow());
            if (file == null)
            {
                return false;
            }
        }

        try
        {
            Files.writeString(file.toPath(), tabContent.codeArea.getText());
            tabContent.file = file;
            tabContent.dirty = false;
            tab.setText(file.getName());
            return true;
        }
        catch (IOException e)
        {
            showError("Save Error", "Could not save file:\n" + e.getMessage());
            return false;
        }
    }

    private Tab createTab(String title, String content, File file)
    {
        CodeArea codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        if (content != null)
        {
            codeArea.replaceText(content);
        }

        TabContent tabContent = new TabContent(codeArea, file);
        Tab tab = new Tab(title);
        tab.setContent(new VirtualizedScrollPane<>(codeArea));
        tab.setUserData(tabContent);
        tab.setOnCloseRequest(closeEvent -> {
            if (!confirmClose(tab))
            {
                closeEvent.consume();
            }
        });

        codeArea.multiPlainChanges()
                .successionEnds(Duration.ofMillis(500))
                .subscribe(ignore -> codeArea.setStyleSpans(
                        0, JavaSyntaxHighlighter.computeHighlighting(codeArea.getText())));
        // регистрируется после начального replaceText(), чтобы загрузка контента не считалась правкой
        codeArea.plainTextChanges().subscribe(change -> tabContent.dirty = true);

        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
        codeArea.requestFocus();
        return tab;
    }

    // --- создаёт пустую вкладку по умолчанию (используется при старте приложения, если не открыт ни один файл)
    public void newTab()
    {
        createTab("New File", null, null);
    }

    // --- true = можно закрывать приложение (нет несохранённых изменений либо пользователь их разрешил сохранить/отбросить)
    public boolean canClose()
    {
        for (Tab tab : tabPane.getTabs())
        {
            if (!confirmClose(tab))
            {
                return false;
            }
        }
        return true;
    }

    @FXML
    private void onNewAction(ActionEvent event)
    {
        newTab();
    }

    @FXML
    private void onOpenAction(ActionEvent event)
    {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open File");
        addTextFileExtensionFilters(fileChooser);
        File file = fileChooser.showOpenDialog(getWindow());
        if (file == null)
        {
            return;
        }

        try
        {
            String content = Files.readString(file.toPath());
            createTab(file.getName(), content, file);
        }
        catch (IOException e)
        {
            showError("Open Error", "Could not open file:\n" + e.getMessage());
        }
    }

    @FXML
    private void onSaveAction(ActionEvent event)
    {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab != null && tab.getUserData() instanceof TabContent tabContent)
        {
            saveTab(tab, tabContent);
        }
    }

    @FXML
    private void onExitAction(ActionEvent event)
    {
        if (canClose())
        {
            Platform.exit();
        }
    }

    @FXML
    private void onCutAction(ActionEvent event)
    {
        CodeArea currentCodeArea = getCurrentCodeArea();
        if (currentCodeArea != null)
        {
            currentCodeArea.cut();
        }
    }

    @FXML
    private void onCopyAction(ActionEvent event)
    {
        CodeArea currentCodeArea = getCurrentCodeArea();

        // Проверка, что текущая вкладка содержит CodeArea и текст выбран
        if (currentCodeArea != null && !currentCodeArea.getSelectedText().isEmpty())
        {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(currentCodeArea.getSelectedText());  // Копирование выделенного текста
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
        CodeArea currentCodeArea = getCurrentCodeArea();
        if (currentCodeArea != null)
        {
            currentCodeArea.paste();
        }
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
