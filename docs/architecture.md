# Архитектура проекта NotepadM

Документ для разработчиков, присоединяющихся к проекту. Описывает структуру
кода, сборочный пайплайн, ключевые архитектурные решения и их обоснование,
а также известные ограничения. Держите документ в актуальном состоянии при
структурных изменениях (новые модули, смена UI-фреймворка компонентов и т.п.).

## 1. Обзор

NotepadM — учебный десктопный текстовый редактор на **JavaFX**, собираемый
**Maven**-ом в три артефакта:
- обычный `.jar` (`mvn package` без shade — содержит только классы проекта);
- самодостаточный fat-`.jar` через `maven-shade-plugin`;
- нативный Windows-`.exe` через `launch4j-maven-plugin` (обёртка над fat-jar).

Архитектурный паттерн — классический **JavaFX FXML MVC**: разметка UI
(`.fxml`) отделена от логики (`Controller`), связка между ними происходит
через аннотации `@FXML` и рефлексию `FXMLLoader`.

## 2. Стек технологий

| Компонент | Версия | Назначение |
|---|---|---|
| Java (JDK) | 21 | Язык и рантайм |
| JavaFX | 19 (`javafx-controls`, `javafx-fxml`) | UI-фреймворк |
| RichTextFX | 0.11.7 (`richtextfx`) | Редактор кода (`CodeArea`) с подсветкой синтаксиса; тянет транзитивно `flowless` (виртуализированная прокрутка), `reactfx` (реактивные подписки на изменения текста), `undofx`, `wellbehavedfx` |
| Maven | 3.9.x | Сборка, управление зависимостями |
| `maven-compiler-plugin` | 3.13.0 | Компиляция под `source`/`target` 21 |
| `javafx-maven-plugin` | 0.0.8 | Запуск (`mvn javafx:run`) в dev-режиме |
| `maven-shade-plugin` | 3.5.2 | Упаковка fat-jar с JavaFX-зависимостями внутри |
| `launch4j-maven-plugin` | 2.5.2 | Обёртка fat-jar в нативный `.exe` для Windows |
| GitHub Actions | — | CI: сборка на `windows-latest` |

Важно: **в проекте нет `module-info.java`** — приложение не является
JPMS-модулем, все классы работают в *unnamed module* и на *classpath*, а не
на *module path*. Это осознанное упрощение: не нужно объявлять `requires
javafx.controls`, `opens ru.filive to javafx.fxml` и т.п., и не нужны
`--module-path`/`--add-modules` при запуске через IDE/VS Code. Обратная
сторона — при запуске fat-jar видно предупреждение
`Unsupported JavaFX configuration: classes were loaded from 'unnamed module'`
— оно безвредно и ожидаемо для такой конфигурации.

## 3. Структура репозитория

```
NotepadM/
├── .github/workflows/main.yml     # CI: сборка jar + exe на push/PR
├── .vscode/                       # Конфигурация запуска/отладки для VS Code
│   ├── launch.json
│   ├── extensions.json
│   └── settings.json
├── docs/                          # Документация проекта (этот каталог)
│   ├── architecture.md
│   ├── todo.md
│   └── steps.md
├── src/main/java/ru/filive/
│   ├── Launcher.java              # Точка входа для fat-jar (см. п.5)
│   ├── MainForm.java              # javafx.application.Application, точка входа для dev-режима
│   ├── MainFormController.java    # Контроллер FXML, вся UI-логика
│   └── JavaSyntaxHighlighter.java # Подсветка синтаксиса Java для CodeArea (regex + StyleSpans)
├── src/main/resources/
│   ├── MainForm.fxml              # Разметка главного окна (меню, тулбар, TabPane)
│   ├── css/java-keywords.css      # Стили подсветки синтаксиса (регистрируется на Scene)
│   └── images/                    # Иконки меню/тулбара (PNG) + NotepadM.ICO/PNG
├── pom.xml                        # Единственный модуль сборки
├── COPYING.txt / LICENSE          # GNU GPL v2+
└── README.md
```

Пакет для всего кода — `ru.filive` (единственный пакет, без под-пакетов —
проект небольшой, разделение на `ru.filive.ui`/`ru.filive.io` и т.п.
оправдано только после появления реальной файловой логики, см.
`docs/todo.md`, пункты 1–3).

## 4. Поток запуска приложения

```
                    ┌─────────────────────┐
   dev / IDE  ────► │  MainForm.main()    │──► Application.launch()
                    └─────────────────────┘
                              │
   fat-jar    ────► ┌─────────────────────┐
   (java -jar)       │  Launcher.main()    │──► MainForm.main()
                    └─────────────────────┘
                              │
                              ▼
                    MainForm.start(Stage)
                              │
                    FXMLLoader.load("/MainForm.fxml")
                              │
                    fx:controller="ru.filive.MainFormController"
                              │
                    ┌─────────────────────┐
                    │ MainFormController   │  @FXML-поля инъецируются,
                    │ (обработчики onAction)│  onAction-методы биндятся
                    └─────────────────────┘
                              │
                    Scene(root, 800, 600) → primaryStage.show()
```

Два способа запуска (`MainForm.main` напрямую и через `Launcher`) существуют
по конкретной причине — см. следующий раздел.

## 5. Зачем нужен `Launcher.java`

`javafx.application.Application` требует, чтобы класс-точка-входа **не**
наследовался напрямую от `Application`, когда приложение запускается из
"толстого" (fat/shaded) jar через `java -jar app.jar`, — иначе JavaFX
Launcher не может корректно определить модуль приложения после того, как
`maven-shade-plugin` объединил все классы и ресурсы (включая
`module-info.class` из зависимостей JavaFX) в один jar. Это известная
проблема связки shade-plugin + JavaFX.

Решение — отдельный класс без наследования от `Application`:

```java
public class Launcher {
    public static void main(String[] args) {
        MainForm.main(args);   // тут уже настоящий Application.launch()
    }
}
```

`maven-shade-plugin` настроен через `ManifestResourceTransformer` указывать
`Main-Class: ru.filive.Launcher` в манифесте fat-jar (см. `pom.xml`,
секция `maven-shade-plugin`), в то время как `javafx-maven-plugin`
(используется для `mvn javafx:run` в разработке) указывает напрямую
`ru.filive.MainForm`. **Если добавляете новый способ упаковки/запуска —
не забывайте, какой main-class ему нужен.**

## 6. Связка FXML ↔ Controller

Файл `MainForm.fxml` объявляет `fx:controller="ru.filive.MainFormController"`
и описывает дерево UI (`MenuBar` → `Menu` → `MenuItem`, `ToolBar` → `Button`,
`TabPane`). Правила проекта:

- Любой элемент, к которому нужно обращаться из Java-кода, получает
  `fx:id="..."` — и в контроллере должно быть поле
  `@FXML private <Тип> <тотЖеId>;` **с точно совпадающим именем**. При
  несовпадении `FXMLLoader` не бросает ошибку — поле просто останется `null`
  (см. `docs/steps.md`, шаг 3 — реальный баг такого рода уже был в проекте).
- Каждый интерактивный элемент (`Button`, `MenuItem`) с действием получает
  `onAction="#onXxxAction"` — и в контроллере обязателен метод
  `@FXML private void onXxxAction(ActionEvent event) { ... }`.
- `fx:id` должны быть **уникальными в пределах всего документа**, даже если
  соответствующее поле не объявлено в контроллере — иначе при добавлении
  поля позже получите `LoadException: Duplicate fx:id`.
- Каждая иконка в тулбаре — свой `ImageView` с собственным `fx:id`
  (`newIcon`, `saveIcon`, ...), даже если сейчас ни одно из этих полей не
  используется в контроллере — это профилактика той же проблемы с дублями.

## 7. Модель данных редактора (текущее состояние)

Редактируемый виджет вкладки — `org.fxmisc.richtext.CodeArea` (RichTextFX),
а не обычный `javafx.scene.control.TextArea`: он умеет построчную стилизацию
текста (`setStyleSpans`), что и используется для подсветки синтаксиса (см.
раздел 8.1). `CodeArea` для производительной виртуализированной прокрутки
оборачивается в `org.fxmisc.flowless.VirtualizedScrollPane<CodeArea>`, и
именно эта обёртка кладётся как `tab.setContent(...)` — то есть
`tab.getContent()` возвращает `VirtualizedScrollPane`, а не сам `CodeArea`
напрямую.

С реализацией Open/Save (`docs/steps.md`, запись №11) появилась лёгкая
модель документа — приватный статический класс
`MainFormController.TabContent`:

```java
private static class TabContent {
    final CodeArea codeArea;
    File file;      // null, пока вкладка не привязана к файлу на диске
    boolean dirty;  // true после первого изменения текста пользователем
}
```

Экземпляр кладётся как `tab.setUserData(tabContent)` (вместо голого
`CodeArea`, как было раньше) и извлекается через
`getCurrentTabContent()`/`getCurrentCodeArea()` в `MainFormController` по
шаблону `tab.getUserData() instanceof TabContent`. `dirty` выставляется в
`true` подпиской `codeArea.plainTextChanges().subscribe(...)`, оформленной
**после** начальной загрузки текста в `createTab(...)`, чтобы сама загрузка
не считалась правкой пользователя; сбрасывается в `false` при успешном
сохранении (`saveTab(...)`). Используется диалогом подтверждения закрытия
(Save/Don't Save/Cancel) — см. раздел 7.1 и `docs/todo.md`, п.7.

`Files`/`Path` (кодировка, режимы записи) в модель пока не выносились —
чтение/запись делаются напрямую через `java.nio.file.Files.readString`/
`writeString` в местах вызова (`onOpenAction`, `saveTab`).

### 7.1 Механизм подсветки синтаксиса (только Java)

- `ru.filive.JavaSyntaxHighlighter` — единственный класс, отвечающий за
  подсветку. Содержит скомпилированный `Pattern` с именованными группами
  (`KEYWORD`, `PAREN`, `BRACE`, `BRACKET`, `SEMICOLON`, `STRING`, `COMMENT`)
  и статический метод `computeHighlighting(String text)`, возвращающий
  `StyleSpans<Collection<String>>` — RichTextFX сопоставляет каждому спану
  CSS-класс (`.keyword`, `.string`, ...).
- В `MainFormController.createTab(...)` при создании `CodeArea` подписка
  `codeArea.multiPlainChanges().successionEnds(Duration.ofMillis(500)).subscribe(...)`
  пересчитывает подсветку через 500 мс после того, как пользователь
  перестал печатать (debounce, чтобы не гонять regex на каждое нажатие
  клавиши). Подписка **не отписывается** при закрытии вкладки — теперь, когда
  вкладки штатно закрываются (`Tab.setOnCloseRequest`, см. раздел 7), это
  реальная (хоть и небольшая) утечка, а не гипотетическая, см. `docs/todo.md`,
  п.24.
- Номера строк — `codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea))`,
  готовая фабрика из RichTextFX.
- Цвета — `src/main/resources/css/java-keywords.css`, зарегистрирован один
  раз на уровне `Scene` в `MainForm.java`
  (`scene.getStylesheets().add(...)`) — CSS каскадно применяется ко всем
  вкладкам/`CodeArea`, создаваемым позже, регистрировать стиль на каждом
  `CodeArea` отдельно не нужно.
- Подсветка жёстко привязана к Java — нет определения языка по расширению
  файла (Open ещё не реализован) и нет абстракции "highlighter на язык".
  Если/когда появится многоязычная поддержка, `JavaSyntaxHighlighter` стоит
  оставить как есть и добавить рядом реализации для других языков за общим
  интерфейсом — не переусложнять сейчас ради гипотетического будущего (см.
  `docs/todo.md`, п.23).

## 8. Сборочный пайплайн (`pom.xml`)

Порядок плагинов в фазе `package`:

1. `maven-compiler-plugin` — компиляция `.java` в `.class` (source/target 21).
2. `maven-shade-plugin` (`phase=package`, `goal=shade`) — собирает
   `target/NotepadM-1.0-SNAPSHOT.jar` (fat-jar) из classes + всех
   зависимостей (JavaFX controls/fxml + их транзитивные native-jar для
   Windows), переписывает `Main-Class` на `ru.filive.Launcher`. Оригинальный
   тонкий jar сохраняется рядом как `original-NotepadM-1.0-SNAPSHOT.jar`.
3. `launch4j-maven-plugin` (`phase=package`, `goal=launch4j`) — оборачивает
   fat-jar в `target/NotepadM.exe`, требует JRE ≥ 21 на машине запуска
   (`jre.minVersion`), ищет Java через `%JAVA_HOME%;%PATH%`.

`javafx-maven-plugin` не участвует в `package`-цепочке — он используется
отдельно командой `mvn javafx:run` для быстрого запуска в разработке
(без сборки jar/exe), напрямую вызывая `ru.filive.MainForm`.

**Важно держать в синхроне три места с версией Java:**
`pom.xml → maven.compiler.source/target`, `pom.xml → launch4j.jre.minVersion`,
`.github/workflows/main.yml → actions/setup-java.java-version`. Рассинхрон
не сломает сборку сразу, но создаёт риск: exe откажется запускаться на
машине с JRE ниже заявленной, либо CI будет собирать другим байткодом, чем
тестирует локально разработчик.

## 9. CI/CD (`.github/workflows/main.yml`)

- Триггеры: `push` в `master`/`dev`, `pull_request` в `master`,
  ручной `workflow_dispatch`.
- Раннер: `windows-latest` (обязательно — `launch4j` собирает `.exe`,
  специфичный для Windows; на Linux-раннере сборка `.exe`-части не будет
  иметь смысла для тестирования запуска, хотя технически может
  кросс-компилироваться).
- Шаги: checkout → `setup-java` (Temurin JDK 21) → `mvn clean install` →
  публикация `target/NotepadM-1.0-SNAPSHOT.jar` и `target/NotepadM.exe` как
  build-артефактов (`actions/upload-artifact@v4`).
- CI **не запускает тесты отдельно** — тестов в проекте пока нет
  (`mvn clean install` включает фазу `test`, но там нечего гонять).
  Как только появятся тесты (`docs/todo.md`, п.21), они будут подхвачены
  автоматически без изменений в workflow.

## 10. Локальная среда разработки

- **JDK 21** (Oracle или любой другой дистрибутив, например Temurin) —
  обязателен, `pom.xml` целится в byte-code уровня 21.
- **Maven 3.9+** — на машине основного разработчика установлен вручную (не
  через инсталлятор) в `%LOCALAPPDATA%\maven`; при работе на другой машине
  используйте любой удобный способ установки (SDKMAN, choco, winget,
  вручную с `dlcdn.apache.org`) — обвязка pom.xml от способа установки не
  зависит.
- **VS Code**: откройте папку проекта, поставьте рекомендованные расширения
  из `.vscode/extensions.json` (`Extension Pack for Java`, XML-поддержка для
  `.fxml`), запуск/отладка — через конфигурацию `NotepadM (JavaFX)` в
  `.vscode/launch.json` (F5). Дополнительные JVM-флаги для module-path не
  нужны (см. раздел 2).
- **IntelliJ IDEA**: в репозитории есть `NotepadM.iml` — исторический
  файл проекта IDEA, закоммиченный до того, как `*.iml` попал в
  `.gitignore`. Технически не мешает сборке, но по-хорошему должен быть
  удалён из индекса git (`git rm --cached NotepadM.iml`), см.
  `docs/todo.md`, п.22.

## 11. Известные архитектурные ограничения

- **Нет rich-text.** `CodeArea` (RichTextFX) — стилизуемый *plain-text*
  редактор: стили (`StyleSpans`) применяются программно по regex ко всему
  тексту, а не выбираются пользователем для произвольного диапазона. Этого
  достаточно для подсветки синтаксиса (см. раздел 7.1), но недостаточно для
  WYSIWYG-форматирования. Кнопки Bold/Italic/Underline в тулбаре по-прежнему
  ничего не делают и не *могут* ничего сделать без отдельного rich-text
  компонента (`HTMLEditor` или кастомный редактор) — см. `docs/todo.md`,
  п.18.
- **Нет модели документа** — см. раздел 7. Реализация Open/Save потребует
  минимум `Path` + dirty-флаг на вкладку.
- **Логирование через `System.out.println`** — приемлемо для текущего
  масштаба, но не масштабируется; при росте проекта стоит перейти на
  `java.util.logging` (входит в JDK, не требует новой зависимости).
- **Единственный класс-контроллер на всё приложение.** При росте
  функционала (диалоги Find, Preferences и т.п.) стоит выносить каждое
  диалоговое окно в свой `.fxml` + свой контроллер, а не разрастать
  `MainFormController`.

## 12. Как добавить новый пункт меню/кнопку — чек-лист для контрибьютора

1. Добавить `MenuItem`/`Button` в `MainForm.fxml` с уникальным `fx:id` и
   `onAction="#onYourAction"`. Если есть иконка — `ImageView` внутри
   `<graphic>` тоже с уникальным `fx:id`.
2. Добавить в `MainFormController.java`:
   - при необходимости — поле `@FXML private <Тип> <fx:id>;` (имя должно
     совпадать с `fx:id` из FXML символ-в-символ);
   - метод `@FXML private void onYourAction(ActionEvent event) { ... }`.
3. Если иконки ещё нет — положить `.png` в
   `src/main/resources/images/` (уже есть неиспользуемые заготовки:
   `Find.png`, `Print.png`, `Spelling.png`, `Undo.png` — сначала проверьте,
   нет ли нужной иконки там).
4. Собрать (`mvn clean install`) и проверить, что FXML грузится без
   `LoadException` — самый частый источник ошибок здесь: несовпадение
   `fx:id` или дублирующийся `fx:id`.
5. Обновить `docs/todo.md` (перевести пункт из ❌/⚠️ в ✅) и добавить запись
   в `docs/steps.md`.
