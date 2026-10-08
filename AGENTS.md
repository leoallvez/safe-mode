# AGENTS.md

Guia para agentes de código que trabalham neste repositório.

## O que é o projeto

**Safe Mode** (`com.poc.safemode`) é uma prova de conceito (POC) Android de um "Modo Seguro": o usuário define uma **área de confiança** combinando uma rede Wi-Fi e uma geolocalização com raio de alcance, e o app deveria reconhecer quando o aparelho está dentro dela.

Hoje o projeto é **apenas a interface de uma tela**. Não existe lógica de negócio, persistência, leitura real de Wi-Fi ou de localização, nem navegação.

## Stack

- Kotlin 2.2.10, Jetpack Compose (BOM 2026.02.01) com Material 3
- AGP 9.4.1, Gradle 9.6.0 (wrapper), JDK toolchain 25, bytecode Java 11
- `minSdk` 24, `compileSdk`/`targetSdk` 37
- Módulo único `:app`, single-activity, sem DI, sem ViewModel, sem Navigation
- Versões ficam em `gradle/libs.versions.toml` (exceção: `material-icons-extended` está declarado direto em `app/build.gradle.kts`, com versão vinda do BOM)

## Estrutura

```
app/src/main/java/com/poc/safemode/
├── MainActivity.kt          # enableEdgeToEdge + SafeModeTheme { SafeModeScreen() }
└── ui/
    ├── SafeModeScreen.kt    # a tela inteira, em um único composable
    └── theme/               # Color.kt, Theme.kt, Type.kt (template padrão do Android Studio)
```

## Escopo da tela atual (`SafeModeScreen`)

Um `Scaffold` com coluna rolável e `SnackbarHost`, contendo de cima para baixo:

1. **Título** "Modo Seguro".
2. **Card "Rede Wi-Fi"**
   - Dropdown (`ExposedDropdownMenuBox`) de SSID com três opções fixas no código: `MinhaCasa_5G`, `Trabalho_Secure`, `Wi-Fi_Visitantes`.
   - Botão **"Usar Wi-Fi Conectado"** — `onClick` vazio.
3. **Card "Geolocalização"**
   - Campos de texto livres **Latitude** e **Longitude** (valores iniciais `-23.5505` / `-46.6333`), sem validação nem teclado numérico.
   - Botão **"Capturar Local Atual"** — `onClick` vazio.
   - **Slider "Raio de Alcance Seguro"**: 50 a 500 m, passos de 10 m, inicial 100 m. Trilha e polegar são desenhados à mão (`Canvas` com degradê + `Box` circular), não os padrões do Material. O valor aparece num selo à direita do título.
4. Botão **"Salvar Área de Confiança"** — `onClick` vazio.
5. Botão **"Testar Status Atual"** — única ação implementada: mostra sempre o snackbar "Você está dentro de um local seguro!", sem checar nada.

Todo o estado é local (`remember { mutableStateOf(...) }`) dentro do composable: não sobrevive a rotação nem a recriação da activity.

### O que ainda não existe

- Permissões no `AndroidManifest.xml` (localização, estado do Wi-Fi) e o fluxo de pedido em runtime
- Leitura do SSID conectado e captura da localização do aparelho
- Persistência da área de confiança
- Cálculo real de "dentro/fora" da área (hoje o resultado é fixo)
- ViewModel / state hoisting, navegação, tratamento de erros
- Testes: só os exemplos gerados pelo template (`ExampleUnitTest`, `ExampleInstrumentedTest`)

Ao implementar qualquer um desses itens, não presuma que há infraestrutura pronta — ela precisa ser criada.

## Comandos

```bash
./gradlew :app:compileDebugKotlin     # checagem rápida de compilação
./gradlew :app:assembleDebug          # gera o APK de debug
./gradlew :app:testDebugUnitTest      # testes unitários (JVM)
./gradlew :app:connectedDebugAndroidTest  # testes instrumentados (exige device/emulador)
./gradlew :app:lintDebug              # Android Lint
```

Rode ao menos `:app:compileDebugKotlin` depois de qualquer alteração em Kotlin. Mudança visual só está verificada depois de vista no Preview do Android Studio ou em um device; compilar não basta, e isso deve ser dito ao reportar o trabalho.

## Convenções

- **Idioma**: textos de interface em português (pt-BR); identificadores e código em inglês.
- **Strings**: hoje estão hardcoded nos composables; `strings.xml` só tem `app_name`. Siga o padrão existente em ajustes pequenos; ao criar telas novas, prefira `stringResource`.
- **Cores**: sempre via `MaterialTheme.colorScheme.*`, nunca `Color(0x...)` direto na tela. O tema usa **cor dinâmica** no Android 12+ (as cores vêm do papel de parede) e o roxo padrão do template abaixo disso — não assuma um tom específico e confira o contraste em tema claro e escuro.
- **Hierarquia de botões na tela**: `Button` preenchido (`primary`) para ações dentro dos cards; `Button` com `primaryContainer` e formato pílula para a ação de salvar; `OutlinedButton` para ação secundária.
- **Formas**: cards com `RoundedCornerShape(16.dp)`; campos e botões com `8.dp`.
- **Composables**: recebem `modifier: Modifier = Modifier` como primeiro parâmetro opcional e têm um `@Preview` envolto em `SafeModeTheme`.
- `SafeModeScreen.kt` já passa de 400 linhas em um único composable. Ao adicionar comportamento, extraia os cards em composables privados e suba o estado, em vez de aumentar a função.
- APIs experimentais do Material 3 estão em uso (`@OptIn(ExperimentalMaterial3Api::class)`).

## Cuidados com o repositório

- `.gradle/`, `.idea/`, `build/` e `local.properties` são ignorados pelo `.gitignore` da raiz e não devem ser versionados.
- Branch de trabalho: `develop`; branch principal: `main`.
