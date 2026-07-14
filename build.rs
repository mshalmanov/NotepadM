fn main() {
    // Компилирует разметку интерфейса (.slint) в Rust-код на этапе сборки.
    // Аналог того, как JavaFX загружает MainForm.fxml, но проверка происходит
    // при компиляции, а не при запуске.
    slint_build::compile("ui/main.slint").unwrap();

    // Иконка файла notepadm.exe (проводник, панель задач). Ресурсы .exe —
    // чисто виндовая штука, поэтому и зависимость, и код есть только там.
    #[cfg(windows)]
    winresource::WindowsResource::new()
        .set_icon("ui/images/NotepadM.ico")
        .compile()
        .unwrap();
}
