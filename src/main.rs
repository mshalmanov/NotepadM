// Точка входа NotepadM.
// Логика приложения: состояние документов и обработчики меню/вкладок.
// Разметка окна — в ui/main.slint.

// Не открывать консольное окно на Windows в release-сборке.
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::cell::RefCell;
use std::fs;
use std::path::PathBuf;
use std::rc::Rc;

use slint::{Model, ModelRc, VecModel};

// Подключает Rust-код, сгенерированный build.rs из ui/main.slint
// (структуры MainWindow и TabInfo).
slint::include_modules!();

/// Один открытый документ (вкладка).
/// Источник истины — этот Vec в Rust; UI лишь отображает его.
#[derive(Default)]
struct Document {
    /// Путь к файлу; None = новый, ещё не сохранённый документ.
    path: Option<PathBuf>,
    /// Полный текст документа.
    text: String,
    /// Есть несохранённые изменения.
    dirty: bool,
}

impl Document {
    /// Заголовок вкладки: имя файла или «Новый файл».
    fn title(&self) -> String {
        self.path
            .as_ref()
            .and_then(|p| p.file_name())
            .map(|name| name.to_string_lossy().into_owned())
            .unwrap_or_else(|| String::from("Новый файл"))
    }

    /// Представление документа для модели вкладок в UI.
    fn tab_info(&self) -> TabInfo {
        TabInfo {
            title: self.title().into(),
            dirty: self.dirty,
        }
    }
}

/// Полностью перестроить модель вкладок по списку документов.
fn refresh_tabs(tabs: &VecModel<TabInfo>, docs: &[Document]) {
    tabs.set_vec(docs.iter().map(Document::tab_info).collect::<Vec<_>>());
}

/// Сделать документ с данным индексом активным в UI.
fn show_doc(ui: &MainWindow, docs: &[Document], index: usize) {
    ui.set_current_tab(index as i32);
    ui.set_document_text(docs[index].text.as_str().into());
}

fn show_error(title: &str, err: &std::io::Error) {
    rfd::MessageDialog::new()
        .set_title(title)
        .set_description(format!("{err}"))
        .set_level(rfd::MessageLevel::Error)
        .show();
}

/// Сохранить документ по индексу. `always_ask` = режим «Сохранить как…»:
/// диалог выбора файла показывается даже при известном пути.
fn save_document(
    docs: &Rc<RefCell<Vec<Document>>>,
    tabs: &VecModel<TabInfo>,
    index: usize,
    always_ask: bool,
) {
    // Короткий заём: берём из документа только нужное и отпускаем,
    // чтобы не держать RefCell занятым во время модального диалога.
    let (known_path, suggested_name) = {
        let docs = docs.borrow();
        let doc = &docs[index];
        let known = if always_ask { None } else { doc.path.clone() };
        (known, doc.title())
    };

    let Some(path) = known_path.or_else(|| {
        rfd::FileDialog::new()
            .add_filter("Текстовые файлы", &["txt"])
            .add_filter("Все файлы", &["*"])
            .set_file_name(suggested_name)
            .save_file()
    }) else {
        return; // пользователь отменил диалог
    };

    let mut docs = docs.borrow_mut();
    match fs::write(&path, docs[index].text.as_bytes()) {
        Ok(()) => {
            docs[index].path = Some(path);
            docs[index].dirty = false;
            tabs.set_row_data(index, docs[index].tab_info());
        }
        Err(err) => show_error("Ошибка сохранения", &err),
    }
}

/// Заголовки всех несохранённых документов.
fn dirty_titles(docs: &[Document]) -> Vec<String> {
    docs.iter()
        .filter(|doc| doc.dirty)
        .map(Document::title)
        .collect()
}

/// Спросить пользователя, выходить ли, бросив несохранённые файлы.
fn confirm_discard(titles: &[String]) -> bool {
    rfd::MessageDialog::new()
        .set_title("Несохранённые изменения")
        .set_description(format!(
            "Не сохранено: {}.\nВыйти без сохранения?",
            titles.join(", ")
        ))
        .set_buttons(rfd::MessageButtons::YesNo)
        .set_level(rfd::MessageLevel::Warning)
        .show()
        == rfd::MessageDialogResult::Yes
}

fn main() -> Result<(), slint::PlatformError> {
    let ui = MainWindow::new()?;

    // Общее состояние: список документов и модель вкладок для UI.
    // Rc — совместное владение между обработчиками,
    // RefCell — контролируемая изменяемость.
    let docs: Rc<RefCell<Vec<Document>>> = Rc::new(RefCell::new(vec![Document::default()]));
    let tabs_model: Rc<VecModel<TabInfo>> = Rc::new(VecModel::default());

    refresh_tabs(&tabs_model, &docs.borrow());
    ui.set_tabs(ModelRc::from(tabs_model.clone()));
    ui.set_current_tab(0);

    // «Файл → Новый»: добавить пустую вкладку и переключиться на неё.
    ui.on_new_file({
        let ui = ui.as_weak();
        let docs = docs.clone();
        let tabs = tabs_model.clone();
        move || {
            let ui = ui.unwrap();
            let mut docs = docs.borrow_mut();
            docs.push(Document::default());
            refresh_tabs(&tabs, &docs);
            show_doc(&ui, &docs, docs.len() - 1);
        }
    });

    // Клик по вкладке: показать её документ.
    ui.on_select_tab({
        let ui = ui.as_weak();
        let docs = docs.clone();
        move |index| {
            let ui = ui.unwrap();
            let docs = docs.borrow();
            show_doc(&ui, &docs, index as usize);
        }
    });

    // Ввод текста: сохранить в документ и пометить вкладку изменённой.
    ui.on_text_edited({
        let ui = ui.as_weak();
        let docs = docs.clone();
        let tabs = tabs_model.clone();
        move |text| {
            let ui = ui.unwrap();
            let index = ui.get_current_tab() as usize;
            let mut docs = docs.borrow_mut();
            let doc = &mut docs[index];
            doc.text = text.to_string();
            if !doc.dirty {
                doc.dirty = true;
                tabs.set_row_data(index, doc.tab_info());
            }
        }
    });

    // Закрытие вкладки (с подтверждением, если есть несохранённые изменения).
    ui.on_close_tab({
        let ui = ui.as_weak();
        let docs = docs.clone();
        let tabs = tabs_model.clone();
        move |index| {
            let ui = ui.unwrap();
            let index = index as usize;

            // Читаем нужные данные и сразу отпускаем заём (borrow),
            // чтобы не держать его открытым во время модального диалога.
            let (is_dirty, title) = {
                let docs = docs.borrow();
                (docs[index].dirty, docs[index].title())
            };
            if is_dirty {
                let answer = rfd::MessageDialog::new()
                    .set_title("Несохранённые изменения")
                    .set_description(format!("«{title}» не сохранён. Закрыть без сохранения?"))
                    .set_buttons(rfd::MessageButtons::YesNo)
                    .set_level(rfd::MessageLevel::Warning)
                    .show();
                if answer != rfd::MessageDialogResult::Yes {
                    return;
                }
            }

            let mut docs = docs.borrow_mut();
            docs.remove(index);
            // Всегда держим хотя бы одну вкладку.
            if docs.is_empty() {
                docs.push(Document::default());
            }
            let old_current = ui.get_current_tab() as usize;
            let new_current = if index < old_current {
                old_current - 1 // закрыли вкладку левее — текущая сместилась
            } else {
                old_current.min(docs.len() - 1)
            };
            refresh_tabs(&tabs, &docs);
            show_doc(&ui, &docs, new_current);
        }
    });

    // «Файл → Открыть…»: в текущую вкладку, если она пустая, иначе в новую.
    ui.on_open_file({
        let ui = ui.as_weak();
        let docs = docs.clone();
        let tabs = tabs_model.clone();
        move || {
            let ui = ui.unwrap();
            let Some(path) = rfd::FileDialog::new()
                .add_filter("Текстовые файлы", &["txt", "md", "rs", "toml"])
                .add_filter("Все файлы", &["*"])
                .pick_file()
            else {
                return; // пользователь отменил диалог
            };
            let text = match fs::read_to_string(&path) {
                Ok(text) => text,
                Err(err) => {
                    show_error("Ошибка открытия", &err);
                    return;
                }
            };

            let mut docs = docs.borrow_mut();
            let current = ui.get_current_tab() as usize;
            let opened = Document {
                path: Some(path),
                text,
                dirty: false,
            };

            let pristine = docs[current].path.is_none()
                && docs[current].text.is_empty()
                && !docs[current].dirty;
            let index = if pristine {
                docs[current] = opened; // не плодим пустую вкладку
                current
            } else {
                docs.push(opened);
                docs.len() - 1
            };
            refresh_tabs(&tabs, &docs);
            show_doc(&ui, &docs, index);
        }
    });

    // «Файл → Сохранить»: в известный путь, иначе диалог выбора файла.
    ui.on_save_file({
        let ui = ui.as_weak();
        let docs = docs.clone();
        let tabs = tabs_model.clone();
        move || {
            let index = ui.unwrap().get_current_tab() as usize;
            save_document(&docs, &tabs, index, false);
        }
    });

    // «Файл → Сохранить как…»: диалог показывается всегда.
    ui.on_save_file_as({
        let ui = ui.as_weak();
        let docs = docs.clone();
        let tabs = tabs_model.clone();
        move || {
            let index = ui.unwrap().get_current_tab() as usize;
            save_document(&docs, &tabs, index, true);
        }
    });

    // «Файл → Выход»: с проверкой несохранённых документов.
    ui.on_quit({
        let docs = docs.clone();
        move || {
            let unsaved = dirty_titles(&docs.borrow());
            if unsaved.is_empty() || confirm_discard(&unsaved) {
                let _ = slint::quit_event_loop();
            }
        }
    });

    // Крестик окна — та же проверка. Возвращаемое значение говорит Slint,
    // закрывать окно или оставить открытым.
    ui.window().on_close_requested({
        let docs = docs.clone();
        move || {
            let unsaved = dirty_titles(&docs.borrow());
            if unsaved.is_empty() || confirm_discard(&unsaved) {
                slint::CloseRequestResponse::HideWindow
            } else {
                slint::CloseRequestResponse::KeepWindowShown
            }
        }
    });

    // «Справка → О программе». env! читает переменную на этапе компиляции:
    // версия берётся из Cargo.toml и «вшивается» в бинарник.
    ui.on_show_about(|| {
        rfd::MessageDialog::new()
            .set_title("О программе")
            .set_description(concat!(
                "NotepadM ",
                env!("CARGO_PKG_VERSION"),
                "\nТекстовый редактор на Rust + Slint.\n",
                "\nЛицензия: GNU GPL v2 или новее.",
                "\nАвтор: Marat Shalmanov."
            ))
            .show();
    });

    ui.run()
}
