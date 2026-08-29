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
│   ├── Launcher.java                  # Точка входа для fat-jar (см. п.5)
│   ├── MainForm.java                  # javafx.application.Application, точка входа для dev-режима
│   ├── MainFormController.java        # Контроллер FXML, вся UI-логика
│   ├── SyntaxHighlighter.java         # Функциональный интерфейс подсветки: text → StyleSpans
│   ├── SyntaxHighlighters.java        # Реестр "расширение файла → SyntaxHighlighter" + PLAIN-заглушка
│   ├── LanguageSpec.java              # record: ключевые слова + синтаксис комментариев одного языка
│   ├── GenericSyntaxHighlighter.java  # Универсальный regex-подсветчик по LanguageSpec (C-подобные, скриптовые и т.п.)
│   ├── MarkupSyntaxHighlighter.java   # Подсветка тегов/атрибутов HTML/XML
│   ├── CssSyntaxHighlighter.java      # Подсветка CSS (селекторы/свойства/hex-цвета)
│   ├── MarkdownSyntaxHighlighter.java # Подсветка Markdown (заголовки/bold/italic/код/ссылки)
│   └── YamlSyntaxHighlighter.java     # Подсветка YAML (ключи/строки/числа/комментарии/дефисы списков)
├── src/main/resources/
│   ├── MainForm.fxml                  # Разметка главного окна (меню, тулбар, TabPane)
│   ├── css/syntax-highlighting.css    # Стили подсветки для всех языков (регистрируется на Scene)
│   └── images/                        # Иконки меню/тулбара (PNG) + NotepadM.ICO/PNG
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

Сам JDK-лаунчер (`java`, начиная с версии, где JavaFX вынесен из JDK)
отказывается запускать класс, который **напрямую** указан как main-class,
если этот класс наследуется от `javafx.application.Application`, а
`javafx.graphics` не найден на **module-path** (JavaFX на classpath не
считается). При нарушении этого правила процесс падает с

```
Error: JavaFX runtime components are missing, and are required to run this application
```

В проекте нет `module-info.java` (раздел 2) — весь код и JavaFX работают на
classpath, поэтому это ограничение бьёт по **любому** способу запуска, где
main-class = `ru.filive.MainForm` напрямую: `java -jar app.jar` с таким
`Main-Class` в манифесте, `java -cp ... ru.filive.MainForm` без jar вообще
(проверено эмпирически при отладке VS Code launch-конфигурации, см.
`docs/steps.md`, запись №14) — и, скорее всего, отладочный запуск из
VS Code/IntelliJ, если их Java-расширение не делает специальной
JavaFX-обработки. Ограничение проверяется только по **явно запрошенному**
main-class — если запущен другой класс, который уже *внутри себя* вызывает
`Application.launch(...)`, проверка не срабатывает.

Решение — отдельный класс без наследования от `Application`, который сам
вызывает `MainForm.main(...)`:

```java
public class Launcher {
    public static void main(String[] args) {
        MainForm.main(args);   // тут уже настоящий Application.launch()
    }
}
```

Три места, где нужно указывать именно `ru.filive.Launcher`, а не
`ru.filive.MainForm`, как main-class:
- `maven-shade-plugin` — через `ManifestResourceTransformer` прописывает
  `Main-Class: ru.filive.Launcher` в манифесте fat-jar (см. `pom.xml`);
- `.vscode/launch.json` — `mainClass: "ru.filive.Launcher"` (запуск/отладка
  из VS Code, включая точки останова);
- `javafx-maven-plugin` (используется только для `mvn javafx:run` в
  разработке) — исключение: он сам добавляет `--module-path`/
  `--add-modules`, поэтому единственный указывает напрямую
  `ru.filive.MainForm` в своей конфигурации в `pom.xml`.

**Если добавляете новый способ упаковки/запуска (ещё одна IDE, скрипт,
Docker-образ и т.п.) — не забывайте: если он не добавляет
`--module-path`/`--add-modules` сам, main-class должен быть
`ru.filive.Launcher`.**

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
    File file;                    // null, пока вкладка не привязана к файлу на диске
    boolean dirty;                // true после первого изменения текста пользователем
    String baseTitle;             // имя файла или "New File N", без индикатора "*"
    SyntaxHighlighter highlighter; // подбирается по расширению файла, см. раздел 7.1
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

### 7.2 Диалог Find/Replace

Немодальное окно (`javafx.stage.Stage`, `Modality.NONE`), построенное
программно в `MainFormController.buildFindReplaceStage()` — по образцу
остальных диалогов проекта (`Alert`), но `Alert`/`showAndWait()` не подошёл:
окно должно оставаться открытым и допускать повторные клики Find Next/Replace
без блокировки работы с текстом. Создаётся лениво при первом `Ctrl+F`
(`onFindAction`) и переиспользуется дальше — поля `findReplaceStage`,
`findField`, `replaceField`, `matchCaseCheckBox`, `findStatusLabel` хранятся
в контроллере. Поиск/замена всегда берут `getCurrentCodeArea()` заново в
момент клика — переключение вкладок при открытом диалоге работает
естественно, без отдельной синхронизации. `Replace All` собирает новый
текст целиком и делает один вызов `codeArea.replaceText(0, length, ...)` —
это одна запись в undo-стеке `CodeArea`, а не N последовательных.

### 7.1 Механизм подсветки синтаксиса (многоязычная)

Подсветка выбирается **по расширению файла**, а не жёстко зашита под один
язык. Ключевая абстракция — функциональный интерфейс:

```java
public interface SyntaxHighlighter {
    StyleSpans<Collection<String>> computeHighlighting(String text);
}
```

Реализации делятся на две группы, в зависимости от того, насколько похожа
структура токенов языка на "ключевые слова + строки + числа + комментарии +
скобки":

- **Табличная (`GenericSyntaxHighlighter` + `LanguageSpec`)** — покрывает
  большинство языков: Java, C, C++, C#, JavaScript/TypeScript (+JSX/TSX),
  Python, Go, Rust, Kotlin, Swift, PHP, Ruby, SQL, JSON, Shell/Bash,
  PowerShell, Perl, Lua, R, INI, Batch. `LanguageSpec` — `record` с набором
  ключевых слов, однострочными и (опционально) блочными комментариями и
  флагом `caseInsensitiveKeywords` (нужен для SQL/PowerShell/Batch).
  `GenericSyntaxHighlighter` на основе `LanguageSpec` в конструкторе один раз
  собирает единый `Pattern` с именованными группами `COMMENT`, `STRING`,
  `KEYWORD`, `NUMBER`, `PUNCTUATION` — то есть сам класс не знает о
  конкретном языке, вся специфика — в переданном `LanguageSpec`. Все
  ~20 языковых констант и их ключевые слова собраны в
  `SyntaxHighlighters` (см. ниже) — заводить по отдельному `.java`-файлу на
  язык здесь не нужно, это была бы лишняя абстракция ради самой абстракции.
- **Выделенные классы** — для языков, где токены структурно другие и
  генеричный regex "ключевые слова + скобки" не подходит:
  - `MarkupSyntaxHighlighter` (HTML/XML) — двухпроходный разбор по образцу
    официального демо RichTextFX (`XMLEditor`): внешний `Pattern` находит
    теги/комментарии, внутренний — атрибуты внутри найденного тега. Классы:
    `.tagmark` (`< > </ />`), `.anytag` (имя тега), `.attribute`, `.avalue`.
  - `CssSyntaxHighlighter` — комментарии/строки/hex-цвета/`@`-правила/имена
    свойств (по lookahead перед `:`)/числа с единицами (`px`, `em`, ...).
    Классы: `.at-rule`, `.property`, `.hex-color` + общие `.comment`/
    `.string`/`.number`/`.punctuation`.
  - `MarkdownSyntaxHighlighter` — заголовки (`#`...`######`), `**bold**`,
    `*italic*`, `` `code` ``/```` ```блоки``` ````, `[ссылки](url)`,
    `> цитаты`. Классы `.md-*`.
  - `YamlSyntaxHighlighter` — не переиспользует `GenericSyntaxHighlighter`,
    хотя формально YAML тоже "ключи + значения + комментарии": в generic-схеме
    не было понятия "ключ перед `:`", и обычный файл вида `key: value` без
    строк/чисел/комментариев/булевых оставался практически без цвета — при
    ручной проверке с реальным `.yml`-файлом это выглядело как "подсветка не
    работает". Отдельный класс с явной группой `KEY` (lookahead перед `\h*:`,
    по аналогии с `PROPERTY` в CSS) и `DASH` (маркер элемента списка `- `)
    решает это — ключи красятся классом `.property`, дефисы — `.punctuation`.
  - Каждый из этих четырёх — синглтон (`INSTANCE`), без состояния, в отличие
    от `GenericSyntaxHighlighter`, у которого на каждый языковой `LanguageSpec`
    создаётся свой экземпляр с собственным скомпилированным `Pattern`.
- **`SyntaxHighlighters`** — реестр `Map<String, SyntaxHighlighter>` по
  расширению файла (без точки, в нижнем регистре), плюс константа `PLAIN`
  (пустые `StyleSpans` на весь текст) — используется для `.txt`/`.log` и
  любого нераспознанного расширения. Публичный метод
  `forFileName(String fileName)` вынимает расширение и возвращает
  подсветчик из карты или `PLAIN`.
- **Привязка к вкладке.** `TabContent.highlighter` подбирается один раз в
  конструкторе `TabContent` — по имени файла, если вкладка открыта из файла,
  иначе по `baseTitle` (для новой вкладки `"New File N"` расширения нет →
  `PLAIN`). В `createTab(...)` подсветка теперь применяется **сразу**, одним
  вызовом `codeArea.setStyleSpans(...)` сразу после создания `TabContent`
  — раньше (до многоязычной поддержки) первая подписка на подсветку
  регистрировалась уже *после* начальной загрузки текста, из-за чего только
  что открытый файл оставался без подсветки до первой правки; это было
  практически незаметно, пока был только Java, но стало заметной проблемой
  при диагностике подсветки Markdown/YAML — исправлено попутно. Если файл
  сохраняется впервые (`Save`/`Save As`, `saveTab(...)` при `file == null`),
  расширение становится известно только в момент сохранения —
  `tabContent.highlighter` пересчитывается через `forFileName(...)` и
  подсветка применяется повторно тем же вызовом `setStyleSpans(...)`.
- В `MainFormController.createTab(...)` при создании `CodeArea` подписка
  `codeArea.multiPlainChanges().successionEnds(Duration.ofMillis(500)).subscribe(...)`
  пересчитывает подсветку через 500 мс после того, как пользователь
  перестал печатать (debounce, чтобы не гонять regex на каждое нажатие
  клавиши), используя `tabContent.highlighter` (то есть подхватывает смену
  подсветки после Save As без дополнительной синхронизации). Подписка **не
  отписывается** при закрытии вкладки — реальная (хоть и небольшая) утечка,
  см. `docs/todo.md`, п.24.
- Номера строк — `codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea))`,
  готовая фабрика из RichTextFX.
- Цвета — `src/main/resources/css/syntax-highlighting.css` (переименован из
  `java-keywords.css`), зарегистрирован один раз на уровне `Scene` в
  `MainForm.java` (`scene.getStylesheets().add(...)`) — CSS каскадно
  применяется ко всем вкладкам/`CodeArea`, создаваемым позже, регистрировать
  стиль на каждом `CodeArea` отдельно не нужно. Файл сгруппирован по общим
  классам (`.keyword`, `.string`, `.number`, `.comment`, `.punctuation`) и
  классам, специфичным для HTML/XML, CSS и Markdown.
- **Диагностика "подсветка не отображается" без доступа к GUI.** Среда
  ассистента не может открыть реальное окно NotepadM и посмотреть на него
  глазами, но может запустить JavaFX `Application` с офф-скрин `Stage`,
  применить те же классы `SyntaxHighlighters`/CSS к тестовому `CodeArea` и
  сделать `scene.snapshot(...)` → `PixelReader` → собранный вручную BMP
  (без `javafx-swing`, которого нет в локальном `.m2`) → конвертация в PNG
  через PowerShell (`System.Drawing`) для визуального просмотра. Это
  вспомогательный одноразовый код вне репозитория (в scratchpad, не
  коммитится) — тот же приём, что и JavaFX-смоук-тест из
  `docs/steps.md`, запись №12, но с рендерингом в файл вместо
  рефлексии по `@FXML`-методам.

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
- **Единственный класс-контроллер на всё приложение.** Диалог Find/Replace
  (раздел 7.2) уже нарушает этот принцип осознанно — построен программно
  внутри `MainFormController`, а не как отдельный `.fxml`+контроллер, по
  аналогии с уже существующими программными `Alert`-диалогами (`showError`,
  `askSaveChanges`, About). При следующем подобном диалоге (Preferences и
  т.п.) стоит решить отдельно: тот же лёгкий программный подход или уже
  вынесение в свой `.fxml` — второе оправдано, когда UI диалога станет
  заметно сложнее текущего Find/Replace.

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
