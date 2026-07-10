# Архитектура NotepadM (Rust-версия)

> Актуальное состояние на 2026-07-10. Документ перезаписывается по мере развития проекта;
> история решений — в [dev-log.md](dev-log.md).

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

## Поток данных: разметка ⇄ логика

Разметка объявляет **свойства** и **callbacks**; Rust-код читает/пишет свойства
и вешает обработчики на callbacks. Прямая аналогия FXML ⇄ Controller из JavaFX.

```
ui/main.slint                          src/main.rs
─────────────                          ───────────
in-out property document-text  ⇄       ui.get_document_text() / ui.set_document_text()
in-out property current-file   ⇄       ui.set_current_file()   (путь для заголовка окна)
callback new-file()            →       ui.on_new_file(|| ...)
callback open-file()           →       ui.on_open_file(|| ...)  → rfd + fs::read_to_string
callback save-file()           →       ui.on_save_file(|| ...)  → rfd + fs::write
callback quit()                →       ui.on_quit(|| ...)       → slint::quit_event_loop()
```

Состояние вне UI одно: `Rc<RefCell<Option<PathBuf>>>` — путь к открытому файлу
(`None` = новый несохранённый документ). Разделяется между обработчиками через `Rc`.

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
