// Точка входа NotepadM.
// Заменяет сразу Launcher.java + MainForm.java + MainFormController.java:
// создаёт окно из ui/main.slint и подключает обработчики меню.

// Не открывать консольное окно на Windows в release-сборке.
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::cell::RefCell;
use std::fs;
use std::path::PathBuf;
use std::rc::Rc;

// Подключает Rust-код, сгенерированный build.rs из ui/main.slint
// (в том числе структуру MainWindow).
slint::include_modules!();

fn main() -> Result<(), slint::PlatformError> {
    let ui = MainWindow::new()?;

    // Путь к текущему файлу. Rc<RefCell<...>> позволяет нескольким
    // обработчикам-замыканиям разделять одно изменяемое значение —
    // идиоматичная замена изменяемого поля класса из Java.
    let current_path: Rc<RefCell<Option<PathBuf>>> = Rc::new(RefCell::new(None));

    // «Файл → Новый»: очистить редактор и забыть путь.
    ui.on_new_file({
        let ui = ui.as_weak();
        let current_path = current_path.clone();
        move || {
            let ui = ui.unwrap();
            ui.set_document_text("".into());
            ui.set_current_file("".into());
            *current_path.borrow_mut() = None;
        }
    });

    // «Файл → Открыть…»: системный диалог выбора файла (crate rfd),
    // чтение файла в строку.
    ui.on_open_file({
        let ui = ui.as_weak();
        let current_path = current_path.clone();
        move || {
            let ui = ui.unwrap();
            let picked = rfd::FileDialog::new()
                .add_filter("Текстовые файлы", &["txt", "md", "rs", "toml"])
                .add_filter("Все файлы", &["*"])
                .pick_file();
            if let Some(path) = picked {
                match fs::read_to_string(&path) {
                    Ok(text) => {
                        ui.set_document_text(text.into());
                        ui.set_current_file(path.display().to_string().into());
                        *current_path.borrow_mut() = Some(path);
                    }
                    Err(err) => {
                        rfd::MessageDialog::new()
                            .set_title("Ошибка открытия")
                            .set_description(format!("Не удалось прочитать файл:\n{err}"))
                            .set_level(rfd::MessageLevel::Error)
                            .show();
                    }
                }
            }
        }
    });

    // «Файл → Сохранить»: в известный путь, иначе диалог «Сохранить как».
    ui.on_save_file({
        let ui = ui.as_weak();
        let current_path = current_path.clone();
        move || {
            let ui = ui.unwrap();
            let path = current_path.borrow().clone().or_else(|| {
                rfd::FileDialog::new()
                    .add_filter("Текстовые файлы", &["txt"])
                    .save_file()
            });
            if let Some(path) = path {
                match fs::write(&path, ui.get_document_text().as_str()) {
                    Ok(()) => {
                        ui.set_current_file(path.display().to_string().into());
                        *current_path.borrow_mut() = Some(path);
                    }
                    Err(err) => {
                        rfd::MessageDialog::new()
                            .set_title("Ошибка сохранения")
                            .set_description(format!("Не удалось сохранить файл:\n{err}"))
                            .set_level(rfd::MessageLevel::Error)
                            .show();
                    }
                }
            }
        }
    });

    // «Файл → Выход».
    ui.on_quit(|| {
        let _ = slint::quit_event_loop();
    });

    ui.run()
}
