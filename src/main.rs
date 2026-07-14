// Точка входа NotepadM.
// Логика приложения: состояние документов и обработчики меню/вкладок.
// Разметка окна — в ui/main.slint.

// Не открывать консольное окно на Windows в release-сборке.
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::cell::RefCell;
use std::fs;
use std::path::{Path, PathBuf};
use std::rc::Rc;

use slint::{Model, ModelRc, VecModel};

// Подключает Rust-код, сгенерированный build.rs из ui/main.slint
// (структуры MainWindow и TabInfo).
slint::include_modules!();

/// Один открытый документ (вкладка).
/// Источник истины — этот Vec в Rust; UI лишь отображает его.
struct Document {
    /// Путь к файлу; None = новый, ещё не сохранённый документ.
    path: Option<PathBuf>,
    /// Полный текст документа (в памяти — всегда UTF-8).
    text: String,
    /// Есть несохранённые изменения.
    dirty: bool,
    /// Кодировка исходного файла — при сохранении используется она же,
    /// чтобы не менять кодировку чужих файлов втихую.
    encoding: &'static encoding_rs::Encoding,
}

// Default нельзя сгенерировать через #[derive]: для &'static Encoding
// нет «значения по умолчанию» — задаём своё (UTF-8 для новых файлов).
impl Default for Document {
    fn default() -> Self {
        Self {
            path: None,
            text: String::new(),
            dirty: false,
            encoding: encoding_rs::UTF_8,
        }
    }
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
    let (known_path, suggested_name, encoding) = {
        let docs = docs.borrow();
        let doc = &docs[index];
        let known = if always_ask { None } else { doc.path.clone() };
        (known, doc.title(), doc.encoding)
    };

    let Some(path) = known_path.or_else(|| {
        rfd::FileDialog::new()
            .add_filter("Текстовые файлы", &["txt"])
            .add_filter("HTML", &["html", "htm"])
            .add_filter("Все файлы", &["*"])
            .set_file_name(suggested_name)
            .save_file()
    }) else {
        return; // пользователь отменил диалог
    };

    let mut docs = docs.borrow_mut();
    // Кодируем текст обратно в кодировку исходного файла. Вложенный блок,
    // чтобы заём текста (bytes) закончился до изменения docs[index] ниже.
    let write_result = {
        let (bytes, _, _) = encoding.encode(&docs[index].text);
        fs::write(&path, &bytes)
    };
    match write_result {
        Ok(()) => {
            docs[index].path = Some(path);
            docs[index].dirty = false;
            tabs.set_row_data(index, docs[index].tab_info());
        }
        Err(err) => show_error("Ошибка сохранения", &err),
    }
}

/// Один и тот же ли это файл? Прямое сравнение путей ловит точное совпадение,
/// канонизация — разные написания одного пути (относительный/абсолютный,
/// `..`, разный регистр букв диска и т.п.).
fn is_same_file(a: &Path, b: &Path) -> bool {
    if a == b {
        return true;
    }
    match (fs::canonicalize(a), fs::canonicalize(b)) {
        (Ok(ca), Ok(cb)) => ca == cb,
        _ => false,
    }
}

/// Открыть файл по пути — общий код для диалога «Открыть…», аргументов
/// командной строки и перетаскивания файла в окно.
/// Если файл уже открыт — просто переключаемся на его вкладку.
fn open_path(
    ui: &MainWindow,
    docs: &RefCell<Vec<Document>>,
    tabs: &VecModel<TabInfo>,
    path: PathBuf,
) {
    let existing = docs
        .borrow()
        .iter()
        .position(|doc| doc.path.as_deref().is_some_and(|p| is_same_file(p, &path)));
    if let Some(index) = existing {
        show_doc(ui, &docs.borrow(), index);
        return;
    }

    // Читаем сырые байты и определяем кодировку сами:
    // fs::read_to_string умеет только UTF-8, а старые русские
    // .txt часто в Windows-1251.
    let (text, encoding) = match fs::read(&path) {
        Ok(bytes) => decode_bytes(&bytes),
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
        encoding,
    };

    let pristine =
        docs[current].path.is_none() && docs[current].text.is_empty() && !docs[current].dirty;
    let index = if pristine {
        docs[current] = opened; // не плодим пустую вкладку
        current
    } else {
        docs.push(opened);
        docs.len() - 1
    };
    refresh_tabs(tabs, &docs);
    show_doc(ui, &docs, index);
}

/// Превратить сырые байты файла в текст.
/// Сначала пробуем строгий UTF-8; если байты им не являются — определяем
/// кодировку эвристикой (chardetng) и декодируем через encoding_rs.
fn decode_bytes(bytes: &[u8]) -> (String, &'static encoding_rs::Encoding) {
    if let Ok(text) = std::str::from_utf8(bytes) {
        // Убираем BOM (метку порядка байтов), если файл начинается с неё.
        let text = text.strip_prefix('\u{feff}').unwrap_or(text);
        return (text.to_owned(), encoding_rs::UTF_8);
    }
    let mut detector = chardetng::EncodingDetector::new();
    detector.feed(bytes, true);
    let encoding = detector.guess(None, true);
    let (text, _, _) = encoding.decode(bytes);
    (text.into_owned(), encoding)
}

/// Состояние поиска: последний запрос и позиция, с которой искать дальше.
#[derive(Default)]
struct SearchState {
    term: String,
    next_from: usize,
    /// Последнее подсвеченное вхождение (байтовые границы) — цель «Заменить».
    current: Option<(usize, usize)>,
}

/// Все вхождения `needle` в `haystack` — список (начало, конец) в байтах.
fn find_matches(haystack: &str, needle: &str, case_sensitive: bool) -> Vec<(usize, usize)> {
    if needle.is_empty() {
        return Vec::new();
    }
    if case_sensitive {
        let mut out = Vec::new();
        let mut from = 0;
        while let Some(p) = haystack[from..].find(needle) {
            let start = from + p;
            out.push((start, start + needle.len()));
            from = start + needle.len();
        }
        return out;
    }
    // Регистронезависимый поиск. Просто сравнить to_lowercase() обеих строк
    // нельзя: у некоторых символов при смене регистра меняется длина в байтах,
    // и смещения «поплывут». Поэтому строим строчную копию вместе с картой
    // соответствия её байтов байтам исходной строки.
    let mut lower = String::new();
    let mut map = Vec::new(); // байт в lower → байт начала символа в haystack
    for (offset, ch) in haystack.char_indices() {
        for low_ch in ch.to_lowercase() {
            let before = lower.len();
            lower.push(low_ch);
            for _ in before..lower.len() {
                map.push(offset);
            }
        }
    }
    let needle = needle.to_lowercase();
    let mut out = Vec::new();
    let mut from = 0;
    while let Some(p) = lower[from..].find(&needle) {
        let start = from + p;
        let end = start + needle.len();
        let orig_start = map[start];
        let orig_end = if end < map.len() {
            map[end]
        } else {
            haystack.len()
        };
        out.push((orig_start, orig_end));
        from = end;
    }
    out
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
    let search: Rc<RefCell<SearchState>> = Rc::new(RefCell::new(SearchState::default()));

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
        let search = search.clone();
        move |index| {
            let ui = ui.unwrap();
            let docs = docs.borrow();
            show_doc(&ui, &docs, index as usize);
            // Другая вкладка — другой текст: позиция поиска неактуальна.
            *search.borrow_mut() = SearchState::default();
            ui.set_search_status("".into());
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

    // «Файл → Открыть…»: в текущую вкладку, если она пустая, иначе в новую;
    // уже открытый файл — переключение на его вкладку (логика в open_path).
    ui.on_open_file({
        let ui = ui.as_weak();
        let docs = docs.clone();
        let tabs = tabs_model.clone();
        move || {
            let Some(path) = rfd::FileDialog::new()
                .add_filter("Текстовые файлы", &["txt", "md", "rs", "toml"])
                .add_filter("HTML", &["html", "htm"])
                .add_filter("Все файлы", &["*"])
                .pick_file()
            else {
                return; // пользователь отменил диалог
            };
            open_path(&ui.unwrap(), &docs, &tabs, path);
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

    // ---------- Поиск и замена ----------

    // Записать новый текст в активный документ (после замены):
    // обновляет и UI, и модель данных, и признак dirty.
    fn apply_replacement(
        ui: &MainWindow,
        docs: &Rc<RefCell<Vec<Document>>>,
        tabs: &VecModel<TabInfo>,
        new_text: String,
    ) {
        let index = ui.get_current_tab() as usize;
        let mut docs = docs.borrow_mut();
        docs[index].text = new_text;
        docs[index].dirty = true;
        ui.set_document_text(docs[index].text.as_str().into());
        tabs.set_row_data(index, docs[index].tab_info());
    }

    // «Найти далее»: следующее вхождение от текущей позиции, с переходом
    // по кругу; найденное подсвечивается выделением (invoke_highlight).
    ui.on_find_next({
        let ui = ui.as_weak();
        let search = search.clone();
        move |term, case_sensitive| {
            let ui = ui.unwrap();
            let text = ui.get_document_text();
            let matches = find_matches(text.as_str(), term.as_str(), case_sensitive);
            let mut search = search.borrow_mut();

            if search.term != term.as_str() {
                // Новый запрос — искать с начала.
                search.term = term.to_string();
                search.next_from = 0;
            }
            if matches.is_empty() {
                search.current = None;
                ui.set_search_status(if term.is_empty() {
                    "".into()
                } else {
                    "Не найдено".into()
                });
                return;
            }
            if search.next_from > text.len() {
                search.next_from = 0; // текст стал короче — начинаем сначала
            }
            let from = search.next_from;
            let (pos, m) = matches
                .iter()
                .enumerate()
                .find(|(_, (start, _))| *start >= from)
                .map(|(i, m)| (i, *m))
                .unwrap_or((0, matches[0])); // дошли до конца — по кругу

            search.next_from = m.1;
            search.current = Some(m);
            ui.set_search_status(format!("{}/{}", pos + 1, matches.len()).into());
            ui.invoke_highlight(m.0 as i32, m.1 as i32);
        }
    });

    // «Заменить»: заменяет подсвеченное вхождение и ищет следующее.
    ui.on_replace_one({
        let ui = ui.as_weak();
        let docs = docs.clone();
        let tabs = tabs_model.clone();
        let search = search.clone();
        move |term, replacement, case_sensitive| {
            let ui = ui.unwrap();
            let text = ui.get_document_text().to_string();

            // Есть ли актуальное подсвеченное вхождение? (Текст могли
            // отредактировать после поиска — тогда границы устарели.)
            let current = search.borrow().current.filter(|(start, end)| {
                text.get(*start..*end).is_some_and(|slice| {
                    if case_sensitive {
                        slice == term.as_str()
                    } else {
                        slice.to_lowercase() == term.to_lowercase()
                    }
                })
            });

            if let Some((start, end)) = current {
                let mut new_text = String::with_capacity(text.len());
                new_text.push_str(&text[..start]);
                new_text.push_str(replacement.as_str());
                new_text.push_str(&text[end..]);
                apply_replacement(&ui, &docs, &tabs, new_text);

                let mut search = search.borrow_mut();
                search.current = None;
                search.next_from = start + replacement.len();
            }
            // И в любом случае — найти следующее (или первое) вхождение.
            ui.invoke_find_next(term, case_sensitive);
        }
    });

    // «Заменить все»: одним проходом, статус — сколько заменили.
    ui.on_replace_all({
        let ui = ui.as_weak();
        let docs = docs.clone();
        let tabs = tabs_model.clone();
        let search = search.clone();
        move |term, replacement, case_sensitive| {
            let ui = ui.unwrap();
            let text = ui.get_document_text().to_string();
            let matches = find_matches(&text, term.as_str(), case_sensitive);
            if matches.is_empty() {
                ui.set_search_status("Не найдено".into());
                return;
            }

            // Склеиваем результат из кусков: [до вхождения] + замена + …
            let mut new_text = String::with_capacity(text.len());
            let mut tail_start = 0;
            for (start, end) in &matches {
                new_text.push_str(&text[tail_start..*start]);
                new_text.push_str(replacement.as_str());
                tail_start = *end;
            }
            new_text.push_str(&text[tail_start..]);
            apply_replacement(&ui, &docs, &tabs, new_text);

            let mut search = search.borrow_mut();
            search.current = None;
            search.next_from = 0;
            ui.set_search_status(format!("Заменено: {}", matches.len()).into());
        }
    });

    // «Файл → Предпросмотр в браузере»: сохранённый файл открывается
    // как есть (работают относительные ссылки и картинки рядом с ним);
    // несохранённый текст выгружается во временный файл.
    ui.on_preview_in_browser({
        let ui = ui.as_weak();
        let docs = docs.clone();
        move || {
            let ui = ui.unwrap();
            let index = ui.get_current_tab() as usize;

            let target = {
                let docs = docs.borrow();
                let doc = &docs[index];
                match (&doc.path, doc.dirty) {
                    // Файл на диске актуален — открываем его самого.
                    (Some(path), false) => Ok(path.clone()),
                    // Иначе — текущий текст во временный файл.
                    _ => {
                        let tmp = std::env::temp_dir().join("notepadm-preview.html");
                        fs::write(&tmp, doc.text.as_bytes()).map(|_| tmp)
                    }
                }
            };

            match target.and_then(|path| open::that(&path).map(|_| path)) {
                Ok(_) => {}
                Err(err) => show_error("Ошибка предпросмотра", &err),
            }
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

    // Перетаскивание файлов из проводника в окно. Slint пока не отдаёт
    // это событие сам, поэтому перехватываем его у нижележащей библиотеки
    // winit (нестабильное API за фичей unstable-winit-030 в Cargo.toml).
    {
        use slint::winit_030::{winit, EventResult, WinitWindowAccessor};
        ui.window().on_winit_window_event({
            let ui = ui.as_weak();
            let docs = docs.clone();
            let tabs = tabs_model.clone();
            move |_, event| {
                // При перетаскивании нескольких файлов событие приходит
                // на каждый по отдельности.
                if let winit::event::WindowEvent::DroppedFile(path) = event {
                    // Внутри этого обработчика работать нельзя: на Windows
                    // событие приходит из вложенного системного цикла
                    // сообщений (OLE drag-and-drop), и открытие файла прямо
                    // здесь замораживает окно. Поэтому только откладываем
                    // работу в очередь цикла событий Slint (таймер на 0 мс)
                    // и сразу возвращаем управление системе.
                    slint::Timer::single_shot(std::time::Duration::ZERO, {
                        let ui = ui.clone();
                        let docs = docs.clone();
                        let tabs = tabs.clone();
                        let path = path.clone();
                        move || {
                            if let Some(ui) = ui.upgrade() {
                                open_path(&ui, &docs, &tabs, path);
                            }
                        }
                    });
                    return EventResult::PreventDefault;
                }
                EventResult::Propagate
            }
        });
    }

    // Файлы из аргументов командной строки: notepadm.exe файл.txt
    // (в т.ч. «Открыть с помощью…» из проводника). args_os, а не args:
    // args паникует на путях, которые не являются корректным Юникодом.
    for arg in std::env::args_os().skip(1) {
        open_path(&ui, &docs, &tabs_model, PathBuf::from(arg));
    }

    ui.run()
}

// Юнит-тесты. Компилируются только при `cargo test` (#[cfg(test)]),
// в обычную сборку не попадают. Живут в том же файле, что и код, —
// стандартное для Rust расположение тестов «чистых» функций.
#[cfg(test)]
mod tests {
    use super::*;

    // ---------- find_matches ----------

    #[test]
    fn finds_all_occurrences() {
        assert_eq!(find_matches("aXbXc", "X", true), vec![(1, 2), (3, 4)]);
    }

    #[test]
    fn case_sensitive_skips_other_case() {
        assert_eq!(find_matches("Xx", "x", true), vec![(1, 2)]);
    }

    #[test]
    fn case_insensitive_ascii() {
        assert_eq!(find_matches("Rust rust RUST", "rust", false).len(), 3);
    }

    #[test]
    fn case_insensitive_cyrillic_offsets_are_valid() {
        // Кириллица в UTF-8 — 2 байта на букву: проверяем, что границы
        // вхождений указывают на настоящие символы, а не «поплыли».
        let text = "Привет, мир! привет. ПРИВЕТ!";
        let matches = find_matches(text, "привет", false);
        assert_eq!(matches.len(), 3);
        for (start, end) in matches {
            assert_eq!(text[start..end].to_lowercase(), "привет");
        }
    }

    #[test]
    fn adjacent_matches_do_not_overlap() {
        assert_eq!(find_matches("aaaa", "aa", true), vec![(0, 2), (2, 4)]);
    }

    #[test]
    fn empty_needle_finds_nothing() {
        assert!(find_matches("abc", "", true).is_empty());
    }

    #[test]
    fn absent_needle_finds_nothing() {
        assert!(find_matches("abc", "z", false).is_empty());
    }

    // ---------- decode_bytes ----------

    #[test]
    fn utf8_decodes_as_is() {
        let (text, encoding) = decode_bytes("Привет, мир!".as_bytes());
        assert_eq!(text, "Привет, мир!");
        assert_eq!(encoding, encoding_rs::UTF_8);
    }

    #[test]
    fn utf8_bom_is_stripped() {
        let (text, _) = decode_bytes("\u{feff}abc".as_bytes());
        assert_eq!(text, "abc");
    }

    #[test]
    fn windows_1251_is_detected_and_decoded() {
        let original = "Привет, мир! Это старый текстовый файл, сохранённый в Windows.";
        // encode() возвращает байты в указанной кодировке — Windows-1251
        // однобайтовая, для UTF-8-декодера эти байты невалидны.
        let (bytes, _, _) = encoding_rs::WINDOWS_1251.encode(original);
        let (text, encoding) = decode_bytes(&bytes);
        assert_eq!(text, original);
        assert_ne!(encoding, encoding_rs::UTF_8);
    }

    #[test]
    fn detected_encoding_roundtrips_on_save() {
        // Сценарий «открыли 1251 → правим → сохраняем»: текст должен
        // вернуться в исходную кодировку без потерь.
        let original = "Съешь ещё этих мягких французских булок, да выпей же чаю.";
        let (bytes_1251, _, _) = encoding_rs::WINDOWS_1251.encode(original);
        let (text, encoding) = decode_bytes(&bytes_1251);
        let (bytes_back, _, _) = encoding.encode(&text);
        assert_eq!(bytes_back.as_ref(), bytes_1251.as_ref());
    }
}
