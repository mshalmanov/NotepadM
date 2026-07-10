# Архитектура NotepadM (Rust-версия)

> Актуальное состояние на 2026-07-10 (вечер: добавлены вкладки). Документ перезаписывается
> по мере развития проекта; история решений — в [dev-log.md](dev-log.md).

## Обзор

NotepadM — кроссплатформенный текстовый редактор (Windows / Linux / macOS).
Стек: **Rust + Slint** (GUI) + **rfd** (нативные файловые диалоги) +
**open** (открытие файлов программой по умолчанию — предпросмотр HTML в браузере).

## Структура

```
Cargo.toml       манифест пакета и зависимости
build.rs         компиляция ui/main.slint в Rust-код на этапе сборки
ui/
  main.slint     разметка главного окна (меню, тулбар, вкладки, редактор)
  images/        иконки (встраиваются в бинарник через @image-url)
src/
  main.rs        точка входа + обработчики меню/вкладок
docs/            документация (эта папка)
```

Панель инструментов: Новый/Открыть/Сохранить дёргают те же callbacks, что и меню;
Вырезать/Копировать/Вставить вызывают функции `TextEdit` прямо в разметке
(`editor.cut()` и т.д.) — до Rust-кода не доходят.

Файлы исходной Java-версии удалены с этой ветки (2026-07-10); они остаются
в ветках `master` (Swing) и `dev` (JavaFX).

## Модель данных

Источник истины — `Vec<Document>` в Rust (`Rc<RefCell<Vec<Document>>>`,
разделяется между обработчиками):

```rust
struct Document {
    path: Option<PathBuf>,  // None = новый несохранённый файл
    text: String,           // полный текст документа (в памяти всегда UTF-8)
    dirty: bool,            // есть несохранённые изменения
    encoding: &'static Encoding, // кодировка исходного файла; сохраняем в ней же
}
```

Чтение файла: `fs::read` (байты) → `decode_bytes` (строгий UTF-8, иначе
автоопределение chardetng + декодирование encoding_rs). Запись — обратное
кодирование в `document.encoding`. Тесты этой логики — `mod tests` в main.rs.

Каждый документ — вкладка. UI получает производные данные: модель вкладок
(`VecModel<TabInfo>`, где `TabInfo { title, dirty }` — общая структура, объявленная
в `.slint`) и текст активного документа. Инвариант: всегда открыта хотя бы одна вкладка.

## Поток данных: разметка ⇄ логика

Разметка объявляет **свойства** и **callbacks**; Rust-код читает/пишет свойства
и вешает обработчики на callbacks. Прямая аналогия FXML ⇄ Controller из JavaFX.

```
ui/main.slint                       src/main.rs
─────────────                       ───────────
property <[TabInfo]> tabs      ⇄    VecModel<TabInfo> (set_vec / set_row_data)
property <int> current-tab     ⇄    индекс активного документа
property <string> document-text ⇄   текст активного документа
callback new-file()            →    добавить пустой Document, показать
callback open-file()           →    rfd + fs::read_to_string → в пустую или новую вкладку
callback save-file()           →    save_document(..., always_ask: false)
callback save-file-as()        →    save_document(..., always_ask: true)
callback preview-in-browser()  →    сохранённый файл или temp-копия → open::that()
callback select-tab(int)       →    показать документ по индексу
callback close-tab(int)        →    подтверждение при dirty; убрать из Vec
callback text-edited(string)   →    обновить text, выставить dirty
callback show-about()          →    rfd::MessageDialog (версия из CARGO_PKG_VERSION)
callback quit()                →    проверка dirty-документов → quit_event_loop()
callback find-next(...)        →    find_matches + invoke_highlight (выделение)
callback replace-one(...)      →    замена подсвеченного + find-next
callback replace-all(...)      →    все вхождения одним проходом
```

Обратный канал «Rust → UI»: публичная функция `highlight(start, end)` в разметке
(выделяет найденное в редакторе), вызывается как `ui.invoke_highlight(...)`.
Поиск: состояние в `SearchState` (запрос, позиция, текущее вхождение),
`find_matches` возвращает байтовые границы; регистронезависимый режим — через
строчную копию с картой смещений (см. dev-log, запись 8).

Горячие клавиши: `FocusScope` вокруг содержимого окна ловит «всплывшие» нажатия —
Ctrl+N/O/S, Ctrl+Shift+S, Ctrl+F/H, Ctrl+W, F3, Esc.

Закрытие окна крестиком перехватывается `on_close_requested` — та же проверка
несохранённых документов, что и у «Выход».

Заголовок окна и метки вкладок (`*` у изменённых) — реактивные выражения в разметке,
пересчитываются при изменении модели автоматически.

## Соглашения

- Кроссплатформенность: пути — только `PathBuf`, диалоги — только через `rfd`,
  никаких платформо-зависимых путей и команд в коде.
- Ошибки ввода-вывода не глотаются: показываются пользователю через `rfd::MessageDialog`.
- Разметка не содержит логики; вся логика — в Rust-обработчиках.

## Сборка

- `cargo run` — сборка и запуск (debug).
- `cargo build --release` — оптимизированный бинарник `target/release/notepadm(.exe)`.
- В release-сборке на Windows консольное окно отключено
  (`windows_subsystem = "windows"` в `src/main.rs`).
- Перед коммитом: `cargo fmt` и `cargo clippy` — CI проверяет оба
  (`fmt --check`, `clippy -- -D warnings`) и валит сборку при нарушениях.

## CI

`.github/workflows/ci.yml`: на push/PR в ветку `rust` — матрица
Windows / Linux / macOS: проверка форматирования, clippy, release-сборка,
бинарники выкладываются артефактами `NotepadM-<ОС>`.
