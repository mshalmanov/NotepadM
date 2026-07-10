fn main() {
    // Компилирует разметку интерфейса (.slint) в Rust-код на этапе сборки.
    // Аналог того, как JavaFX загружает MainForm.fxml, но проверка происходит
    // при компиляции, а не при запуске.
    slint_build::compile("ui/main.slint").unwrap();
}
