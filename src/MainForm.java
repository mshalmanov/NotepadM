import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;

public class MainForm extends JFrame
{
    private static final long serialVersionUID = 1L;

    /**
     * Конструктор класса
     */
    public MainForm()
    {
        JFrame frmMain = new JFrame("Notepad");
        frmMain.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); //exit from close app
        frmMain.setBounds(100,200,800,600);

        // Создание строки главного меню
        JMenuBar menuBar = new JMenuBar();
        // Добавление в главное меню выпадающих пунктов меню
        menuBar.add(createFileMenu());
        menuBar.add(createEditMenu());
        menuBar.add(createHelpMenu());
        // Подключаем меню к интерфейсу приложения
        frmMain.setJMenuBar(menuBar);
        //Подключения toolbar к интерфейсу приложения
        toolBar();
        frmMain.getContentPane().add(toolBar(), BorderLayout.PAGE_START);
        //Подключение JTextPane
        editor = new JTextPane();
        // Открытие окна
        frmMain.setLocationRelativeTo(null); //show app on the center
        frmMain.setVisible(true);
    }

    /**
     * Функция создания меню "Файл"
     * @return
     */
    private JMenu createFileMenu()
    {
        // Создание выпадающего меню
        JMenu file = new JMenu("File");
        // Пункт меню "Открыть" с изображением
        JMenuItem open = new JMenuItem("Open", new ImageIcon("src/images/Open.png"));
        JMenuItem save = new JMenuItem("Save", new ImageIcon("src/images/Save.png"));
        JMenuItem save_as = new JMenuItem("Save as", new ImageIcon("src/images/Save_as.png"));
        // Пункт меню из команды с выходом из программы
        JMenuItem exit = new JMenuItem(new ExitAction());
        // Добавление к пункту меню изображения
        exit.setIcon(new ImageIcon("src/images/Exit.png"));
        // Добавим в меню пункта open
        file.add(open);
        file.add(save);
        file.add(save_as);
        // Добавление разделителя
        file.addSeparator();
        file.add(exit);

        open.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                System.out.println ("ActionListener.actionPerformed : open");
            }
        });
        return file;
    }

    /**
     * Функция создания меню
     * @return
     */
    private JMenu createEditMenu()
    {
        // создадим выпадающее меню
        JMenu editMenu = new JMenu("Edit");

        JMenuItem cut = new JMenuItem("Cut", new ImageIcon("src/images/Cut.png"));
        JMenuItem copy = new JMenuItem("Copy", new ImageIcon("src/images/Copy.png"));
        JMenuItem paste = new JMenuItem("Paste", new ImageIcon("src/images/Paste.png"));
        // добавим все в меню
        editMenu.add(cut);
        editMenu.add(copy);
        editMenu.add(paste);
        return editMenu;
    }

    private JMenu createHelpMenu()
    {
        // создадим выпадающее меню
        JMenu helpMenu = new JMenu("Help");
        // Пункт меню "About" с изображением
        JMenuItem about = new JMenuItem("About");
        // Добавим в меню пункта about
        helpMenu.add(about);
        return helpMenu;
    }

    private JToolBar toolBar()
    {
        JToolBar toolBar = new JToolBar("Application");

        JButton btnNew = new JButton(new ImageIcon("src/images/New.png"));
        JButton btnOpen = new JButton(new ImageIcon("src/images/Open.png"));
        JButton btnSave = new JButton(new ImageIcon("src/images/Save.png"));
        JButton btnCut = new JButton(new ImageIcon("src/images/Cut.png"));
        JButton btnCopy = new JButton(new ImageIcon("src/images/Copy.png"));
        JButton btnPaste = new JButton(new ImageIcon("src/images/Paste.png"));
        JButton btnText_bold = new JButton(new ImageIcon("src/images/Text_bold.png"));
        JButton btnText_italics = new JButton(new ImageIcon("src/images/Text_italics.png"));
        JButton btnText_underline = new JButton(new ImageIcon("src/images/Text_underline.png"));

        toolBar.add(btnNew);
        toolBar.add(btnOpen);
        toolBar.add(btnSave);
        toolBar.add(btnCut);
        toolBar.add(btnCopy);
        toolBar.add(btnPaste);
        toolBar.add(btnText_bold);
        toolBar.add(btnText_italics);
        toolBar.add(btnText_underline);

        return toolBar;
    }

    /**
     * Вложенный класс завершения работы приложения
     */
    class ExitAction extends AbstractAction
    {
        private static final long serialVersionUID = 1L;
        ExitAction() {
            putValue(NAME, "Exit");
        }
        public void actionPerformed(ActionEvent e) {
            System.exit(0);
        }
    }

    public static void main(String[] args)
    {
        new MainForm();
    }
}