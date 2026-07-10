# Архитектура NotepadM (Rust-версия)

> Актуальное состояние на 2026-07-10 (вечер: добавлены вкладки). Документ перезаписывается
> по мере развития проекта; история решений — в [dev-log.md](dev-log.md).

## Обзор

NotepadM — кроссплатформенный текстовый редактор (Windows / Linux / macOS).
Стек: **Rust + Slint** (GUI) + **rfd** (нативные файловые диалоги).

## Структура

```
Cargo.toml       манифест пакета и зависимости
build.rs         компиляция ui/main.slint в Rust-код на этапе сборки
ui/
  main.slint     разметка главного окна (меню, редактор, свойства, callbacks)
src/
  main.rs        точка входа + обработчики меню (Новый/Открыть/Сохранить/Выход)
docs/            документация (эта папка)
```

Файлы исходной Java-версии удалены с этой ветки (2026-07-10); они остаются
в ветках `master` (Swing) и `dev` (JavaFX).

## Модель данных

Источник истины — `Vec<Document>` в Rust (`Rc<RefCell<Vec<Document>>>`,
разделяется между обработчиками):

```rust
struct Document {
    path: Option<PathBuf>,  // None = новый несохранённый файл
    text: String,           // полный текст документа
    dirty: bool,            // есть несохранённые изменения
}
```

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
callback save-file()           →    fs::write (диалог rfd, если пути ещё нет)
callback select-tab(int)       →    показать документ по индексу
callback close-tab(int)        →    подтверждение при dirty; убрать из Vec
callback text-edited(string)   →    обновить text, выставить dirty
callback quit()                →    slint::quit_event_loop()
```

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
