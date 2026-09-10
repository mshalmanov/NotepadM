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
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
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
    private MenuItem undoMenuItem;

    @FXML
    private MenuItem redoMenuItem;

    @FXML
    private MenuItem cutMenuItem;

    @FXML
    private MenuItem copyMenuItem;

    @FXML
    private MenuItem pasteMenuItem;

    @FXML
    private MenuItem findMenuItem;

    // --- строка состояния
    @FXML
    private Label statusPositionLabel;

    @FXML
    private Label statusSelectionLabel;

    @FXML
    private Label statusLinesCharsLabel;

    @FXML
    private Label statusLanguageLabel;

    @FXML
    private Label statusEncodingLabel;

    // --- Find/Replace: немодальное окно, создаётся лениво при первом Ctrl+F и переиспользуется дальше
    private Stage findReplaceStage;
    private TextField findField;
    private TextField replaceField;
    private CheckBox matchCaseCheckBox;
    private Label findStatusLabel;

    // --- содержимое вкладки: CodeArea + файл, с которым она связана (null, пока не сохранена/открыта)
    private static class TabContent
    {
        final CodeArea codeArea;
        File file;
        boolean dirty;
        String baseTitle; // имя файла или "New File N", без индикатора "*"
        SyntaxHighlighter highlighter; // подбирается по расширению файла

        TabContent(CodeArea codeArea, File file, String baseTitle)
        {
            this.codeArea = codeArea;
            this.file = file;
            this.baseTitle = baseTitle;
            this.highlighter = SyntaxHighlighters.forFileName(file != null ? file.getName() : baseTitle);
        }
    }

    // ButtonData задан явно (YES/NO/CANCEL_CLOSE) — иначе ButtonBar относит кнопки без типа
    // в отдельную группу "OTHER" и расставляет их с большим отступом от Cancel
    private static final ButtonType SAVE_BUTTON = new ButtonType("Save", ButtonBar.ButtonData.YES);
    private static final ButtonType DONT_SAVE_BUTTON = new ButtonType("Don't Save", ButtonBar.ButtonData.NO);
    private static final ButtonType CANCEL_BUTTON = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

    @FXML
    private void initialize()
    {
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> refreshStatusBar());
    }

    // --- обновляет строку состояния по текущей вкладке: позиция каретки, выделение, объём текста, язык, кодировка
    private void refreshStatusBar()
    {
        TabContent tabContent = getCurrentTabContent();
        if (tabContent == null)
        {
            statusPositionLabel.setText("");
            statusSelectionLabel.setText("");
            statusLinesCharsLabel.setText("");
            statusLanguageLabel.setText("");
            statusEncodingLabel.setText("");
            return;
        }

        CodeArea codeArea = tabContent.codeArea;
        statusPositionLabel.setText("Ln " + (codeArea.getCurrentParagraph() + 1)
                + ", Col " + (codeArea.getCaretColumn() + 1));

        int selectionLength = codeArea.getSelection().getLength();
        statusSelectionLabel.setText(selectionLength > 0 ? "Selected: " + selectionLength : "");

        statusLinesCharsLabel.setText("Lines: " + codeArea.getParagraphs().size()
                + "  Chars: " + codeArea.getLength());

        statusLanguageLabel.setText(SyntaxHighlighters.nameForFileName(
                tabContent.file != null ? tabContent.file.getName() : tabContent.baseTitle));

        statusEncodingLabel.setText("UTF-8");
    }

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
                new FileChooser.ExtensionFilter("C/C++ Files (*.c, *.h, *.cpp, *.hpp)",
                        "*.c", "*.h", "*.cpp", "*.cc", "*.cxx", "*.hpp", "*.hh"),
                new FileChooser.ExtensionFilter("C# Files (*.cs)", "*.cs"),
                new FileChooser.ExtensionFilter("Web Files (*.js, *.ts, *.html, *.css)",
                        "*.js", "*.jsx", "*.ts", "*.tsx", "*.html", "*.htm", "*.css"),
                new FileChooser.ExtensionFilter("Python Files (*.py)", "*.py", "*.pyw"),
                new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"),
                new FileChooser.ExtensionFilter("XML Files (*.xml)", "*.xml"),
                new FileChooser.ExtensionFilter("Markdown Files (*.md)", "*.md", "*.markdown"),
                new FileChooser.ExtensionFilter("Shell/Script Files (*.sh, *.bat, *.ps1)",
                        "*.sh", "*.bash", "*.zsh", "*.bat", "*.cmd", "*.ps1"),
                new FileChooser.ExtensionFilter("All Files (*.*)", "*.*"));
    }

    // --- обновляет текст вкладки: "*" перед именем, если есть несохранённые изменения
    private void updateTabTitle(Tab tab, TabContent tabContent)
    {
        tab.setText((tabContent.dirty ? "*" : "") + tabContent.baseTitle);
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
        ButtonType result = askSaveChanges(tabContent.baseTitle);
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
        boolean isNewFile = file == null;
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
            tabContent.baseTitle = file.getName();
            tabContent.dirty = false;
            updateTabTitle(tab, tabContent);
            if (isNewFile)
            {
                // расширение файла стало известно только сейчас — подбираем подсветку и применяем сразу
                tabContent.highlighter = SyntaxHighlighters.forFileName(file.getName());
                tabContent.codeArea.setStyleSpans(0,
                        tabContent.highlighter.computeHighlighting(tabContent.codeArea.getText()));
                refreshStatusBar();
            }
            return true;
        }
        catch (IOException e)
        {
            showError("Save Error", "Could not save file:\n" + e.getMessage());
            return false;
        }
    }

    private Tab createTab(String baseTitle, String content, File file)
    {
        CodeArea codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        if (content != null)
        {
            codeArea.replaceText(content);
        }

        TabContent tabContent = new TabContent(codeArea, file, baseTitle);
        // подсвечиваем содержимое сразу при создании/открытии вкладки, не дожидаясь первой правки
        codeArea.setStyleSpans(0, tabContent.highlighter.computeHighlighting(codeArea.getText()));

        Tab tab = new Tab(baseTitle);
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
                        0, tabContent.highlighter.computeHighlighting(codeArea.getText())));
        // регистрируется после начального replaceText(), чтобы загрузка контента не считалась правкой
        codeArea.plainTextChanges().subscribe(change -> {
            tabContent.dirty = true;
            updateTabTitle(tab, tabContent);
        });
        // строка состояния следит за кареткой/выделением только активной вкладки,
        // но подписка ставится на каждую — фон-вкладки её просто не двигают
        codeArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> refreshStatusBar());
        codeArea.selectionProperty().addListener((obs, oldSel, newSel) -> refreshStatusBar());

        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
        codeArea.requestFocus();
        return tab;
    }

    private int newFileCounter = 0;

    // --- создаёт пустую вкладку с уникальным именем (используется при старте приложения и по действию New)
    public void newTab()
    {
        newFileCounter++;
        createTab("New File " + newFileCounter, null, null);
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
        // при открытии по умолчанию показываем все файлы, а не только .txt
        fileChooser.setSelectedExtensionFilter(fileChooser.getExtensionFilters().get(
                fileChooser.getExtensionFilters().size() - 1));
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
    private void onUndoAction(ActionEvent event)
    {
        CodeArea currentCodeArea = getCurrentCodeArea();
        if (currentCodeArea != null)
        {
            currentCodeArea.undo();
        }
    }

    @FXML
    private void onRedoAction(ActionEvent event)
    {
        CodeArea currentCodeArea = getCurrentCodeArea();
        if (currentCodeArea != null)
        {
            currentCodeArea.redo();
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

    // --- строит немодальное окно Find/Replace (один раз, дальше переиспользуется)
    private void buildFindReplaceStage()
    {
        findField = new TextField();
        replaceField = new TextField();
        matchCaseCheckBox = new CheckBox("Match case");
        findStatusLabel = new Label();

        Button findNextButton = new Button("Find Next");
        findNextButton.setOnAction(e -> findNext());
        Button replaceButton = new Button("Replace");
        replaceButton.setOnAction(e -> replaceCurrent());
        Button replaceAllButton = new Button("Replace All");
        replaceAllButton.setOnAction(e -> replaceAll());
        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> findReplaceStage.hide());

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        grid.addRow(0, new Label("Find:"), findField);
        grid.addRow(1, new Label("Replace with:"), replaceField);
        grid.add(matchCaseCheckBox, 1, 2);
        grid.add(new HBox(6, findNextButton, replaceButton, replaceAllButton, closeButton), 1, 3);
        grid.add(findStatusLabel, 1, 4);
        GridPane.setHgrow(findField, javafx.scene.layout.Priority.ALWAYS);
        GridPane.setHgrow(replaceField, javafx.scene.layout.Priority.ALWAYS);

        findReplaceStage = new Stage();
        findReplaceStage.setTitle("Find and Replace");
        findReplaceStage.initOwner(getWindow());
        findReplaceStage.initModality(Modality.NONE);
        findReplaceStage.setScene(new Scene(grid));
        findReplaceStage.setResizable(false);
    }

    @FXML
    private void onFindAction(ActionEvent event)
    {
        if (findReplaceStage == null)
        {
            buildFindReplaceStage();
        }

        CodeArea currentCodeArea = getCurrentCodeArea();
        if (currentCodeArea != null && !currentCodeArea.getSelectedText().isEmpty())
        {
            findField.setText(currentCodeArea.getSelectedText());
        }
        findStatusLabel.setText("");

        findReplaceStage.show();
        findReplaceStage.toFront();
        findField.requestFocus();
        findField.selectAll();
    }

    // --- ищет findField.getText() в текущей CodeArea, начиная с конца текущего выделения, с переходом в начало (wrap-around)
    private void findNext()
    {
        CodeArea currentCodeArea = getCurrentCodeArea();
        String searchText = findField.getText();
        if (currentCodeArea == null || searchText.isEmpty())
        {
            return;
        }

        String text = currentCodeArea.getText();
        String haystack = matchCaseCheckBox.isSelected() ? text : text.toLowerCase();
        String needle = matchCaseCheckBox.isSelected() ? searchText : searchText.toLowerCase();

        int from = currentCodeArea.getSelection().getEnd();
        int index = haystack.indexOf(needle, from);
        if (index == -1)
        {
            index = haystack.indexOf(needle); // wrap-around: поиск с начала
        }

        if (index == -1)
        {
            findStatusLabel.setText("Phrase not found.");
            return;
        }

        currentCodeArea.selectRange(index, index + needle.length());
        currentCodeArea.requestFollowCaret();
        findStatusLabel.setText("");
    }

    // --- заменяет текущее выделение (если оно совпадает с findField.getText()) и переходит к следующему совпадению
    private void replaceCurrent()
    {
        CodeArea currentCodeArea = getCurrentCodeArea();
        String searchText = findField.getText();
        if (currentCodeArea == null || searchText.isEmpty())
        {
            return;
        }

        String selected = currentCodeArea.getSelectedText();
        boolean matchesSelection = matchCaseCheckBox.isSelected()
                ? selected.equals(searchText)
                : selected.equalsIgnoreCase(searchText);

        if (matchesSelection)
        {
            var selection = currentCodeArea.getSelection();
            currentCodeArea.replaceText(selection.getStart(), selection.getEnd(), replaceField.getText());
        }

        findNext();
    }

    // --- заменяет все вхождения одним действием (одна запись в undo-стеке)
    private void replaceAll()
    {
        CodeArea currentCodeArea = getCurrentCodeArea();
        String searchText = findField.getText();
        if (currentCodeArea == null || searchText.isEmpty())
        {
            return;
        }

        String text = currentCodeArea.getText();
        String replacement = replaceField.getText();
        StringBuilder result = new StringBuilder();
        int count = 0;
        int pos = 0;

        if (matchCaseCheckBox.isSelected())
        {
            int index;
            while ((index = text.indexOf(searchText, pos)) != -1)
            {
                result.append(text, pos, index).append(replacement);
                pos = index + searchText.length();
                count++;
            }
        }
        else
        {
            String haystack = text.toLowerCase();
            String needle = searchText.toLowerCase();
            int index;
            while ((index = haystack.indexOf(needle, pos)) != -1)
            {
                result.append(text, pos, index).append(replacement);
                pos = index + needle.length();
                count++;
            }
        }
        result.append(text.substring(pos));

        if (count == 0)
        {
            findStatusLabel.setText("Phrase not found.");
            return;
        }

        currentCodeArea.replaceText(0, currentCodeArea.getLength(), result.toString());
        findStatusLabel.setText("Replaced " + count + " occurrence(s).");
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
