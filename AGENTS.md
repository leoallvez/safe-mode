# AGENTS.md

Guia para agentes de código que trabalham neste repositório.

## O que é o projeto

**Safe Mode** (`com.poc.safemode`) é uma prova de conceito (POC) Android de um "Modo Seguro": o usuário define uma **área de confiança** combinando uma rede Wi-Fi e uma geolocalização com raio de alcance, e o app reconhece quando o aparelho está dentro dela.

O app tem **duas telas**: a lista de áreas de confiança e o formulário de criar/editar uma área. Ele lê o Wi-Fi conectado e a localização do aparelho, salva **várias** áreas (cada uma com nome, rede, pontos de acesso e tipo de segurança da rede, ponto geográfico e raio) e testa sob demanda se o aparelho está dentro de alguma delas. Não há verificação em segundo plano.

O histórico das decisões está em `PLANO_DE_IMPLEMENTACAO.md` e `DUVIDAS_DO_PLANO.md`. O plano e as seções 1 e 2 das dúvidas foram escritos quando o app tinha uma única área e não descrevem a lista; a seção 3 das dúvidas (tipo de segurança) descreve o app atual.

## Stack

- Kotlin 2.2.10, Jetpack Compose (BOM 2026.02.01) com Material 3
- AGP 9.4.1, Gradle 9.6.0 (wrapper), JDK toolchain 25, bytecode Java 11
- `minSdk` 24, `compileSdk`/`targetSdk` 37
- Módulo único `:app`, single-activity, Navigation Compose (rotas em string), ViewModel + `StateFlow`, DataStore Preferences, Google Play Services Location
- Sem DI: as dependências são montadas na `Factory` de cada ViewModel
- Versões ficam em `gradle/libs.versions.toml` (exceção: `material-icons-extended` está declarado direto em `app/build.gradle.kts`, com versão vinda do BOM)

## Estrutura

```
app/src/main/java/com/poc/safemode/
├── MainActivity.kt               # enableEdgeToEdge + SafeModeTheme { SafeModeApp() }
├── domain/                       # Kotlin puro, sem android.* (testável em JVM)
│   ├── TrustedArea.kt            # TrustedArea (id, nome, rede, BSSIDs, ponto, raio), LocationReading
│   ├── CoordinateParser.kt       # texto <-> coordenada validada
│   ├── GeoDistance.kt            # haversine
│   ├── Wifi.kt                   # WifiSecurity, WifiProtocol, ConnectedWifi, NearbyWifi, normalização e agrupamento
│   └── SafeStatusEvaluator.kt    # regra dentro/fora sobre a lista de áreas
├── data/                         # interfaces + implementações Android
│   ├── TrustedAreaRepository.kt  # lista de áreas no DataStore
│   ├── TrustedAreaJson.kt        # formato gravado em disco (JSON)
│   ├── WifiInfoProvider.kt       # rede conectada (dois caminhos: API <= 30 e >= 31) e varredura
│   └── LocationProvider.kt       # FusedLocationProviderClient
└── ui/
    ├── SafeModeApp.kt            # NavHost e rotas
    ├── UiMessage.kt              # mensagens de snackbar compartilhadas
    ├── WifiSecurityLabel.kt      # texto de cada tipo de segurança
    ├── LocationPermission.kt     # pedido de permissão compartilhado
    ├── areas/                    # AreasScreen + AreasContent, AreasViewModel
    ├── editor/                   # AreaEditorScreen + AreaEditorContent, ViewModel, UiState
    ├── components/               # SectionCard, WifiCard, LocationCard, RadiusSlider
    └── theme/                    # Color.kt, Theme.kt, Type.kt (template padrão do Android Studio)
```

## Comportamento

**Lista de áreas** (tela inicial)
- Cada item mostra nome, rede, raio e coordenadas. Tocar abre o editor; o ícone de lixeira exclui, após confirmação.
- **Nova Área de Confiança** abre o editor vazio.
- **Testar Status Atual** compara a rede e a posição atuais com todas as áreas. Regra: o aparelho está dentro quando, para **uma mesma** área, a rede é reconhecida **e** a distância ≤ raio.
- **Rede reconhecida** = o nome (SSID) bate, o ponto de acesso conectado (BSSID) está entre os registrados na área **e** o tipo de segurança é o mesmo do cadastro. O tipo é comparado por categoria (`WifiSecurity`: aberta, WEP, com senha, corporativa); `Unknown` em qualquer dos lados não é comparado. Tipo diferente dá uma mensagem própria citando os dois. Nome certo em ponto de acesso não registrado dá "ponto de acesso não reconhecido". Área sem BSSID registrado (gravada antes dessa regra) nunca é reconhecida e não pode ser salva até a rede ser registrada de novo. Leitura com precisão pior que o raio de uma área ao alcance resulta em "não foi possível confirmar".

**Editor de área**
- **Nome**: obrigatório; nomes repetidos são aceitos.
- **Rede Wi-Fi**: campo somente leitura. Numa área nova, com a permissão já concedida, abre preenchido com a rede conectada (nunca ao editar). Também é preenchido por dois botões: "Usar Wi-Fi Conectado" (rede atual) e "Buscar Outras Redes" (varredura das redes ao alcance, escolhidas num diálogo). Não há digitação: um nome digitado errado faria a área nunca ser reconhecida. O Android limita a frequência das varreduras e não permite listar as redes salvas do aparelho. O campo mostra quantos pontos de acesso estão registrados. "Usar Wi-Fi Conectado" registra o BSSID conectado mais os de mesmo nome vistos na última varredura do sistema; a busca registra todos os BSSIDs vistos com o nome escolhido. Registrar de novo a **mesma** rede soma pontos de acesso (é como se adiciona um repetidor ou outra banda); escolher outra rede substitui. O teste de status exige estar conectado a um ponto de acesso registrado.
- **Tipo de segurança**: aparece no campo da rede, na lista de áreas e no diálogo de redes próximas, como categoria mais protocolo quando identificado ("Protegida por senha (WPA2/WPA3)"). O protocolo (`WifiProtocol`) é só exibido; a validação compara a categoria. Rede aberta ou WEP mostra um aviso de que é fácil de falsificar, sem bloquear o salvamento. Mesmo nome com tipos diferentes são tratados como redes distintas. As limitações estão na seção 3 do `DUVIDAS_DO_PLANO.md`.
- **Geolocalização**: latitude/longitude digitadas (vírgula ou ponto) ou preenchidas por "Capturar Local Atual"; raio de 50 a 500 m em passos de 10 m, com trilha e polegar desenhados à mão.
- **Salvar Área de Confiança**: habilitado só com nome, rede e coordenadas válidas; salva e volta para a lista. Voltar sem salvar descarta o rascunho sem confirmação.

**Geral**
- **Permissão**: `ACCESS_FINE_LOCATION` nunca é pedida na abertura de uma tela, só por toque. No editor, enquanto ela não está concedida, os cards de Wi-Fi e de Geolocalização mostram um texto explicando o motivo e o botão "Permitir Localização" no lugar dos botões de ação (a permissão é uma só; conceder em um card libera o outro). Na lista, "Testar Status Atual" pede a permissão no próprio toque. Localização aproximada é tratada como insuficiente. Sem a permissão não dá para preencher a rede, logo não dá para salvar uma área.
- Resultados e erros aparecem em snackbar; os ViewModels emitem ids de `strings.xml`, não texto.
- **Persistência**: a lista inteira é um texto JSON numa chave do DataStore. Suficiente para poucas áreas; para crescer, migrar para Room.

### O que ainda não existe

- Verificação em segundo plano (geofencing, serviço, notificação)
- Digitação manual do SSID
- Ver ou remover pontos de acesso (BSSID) individualmente
- Confirmação ao sair do editor com alterações não salvas, e preservação do rascunho após morte do processo
- Reordenação ou busca na lista
- DI

### Não verificado em aparelho

A implementação compila e passa nos testes de JVM, mas até o momento **não foi executada em device/emulador**. Seguem sem verificação: o layout das duas telas, a navegação, o fluxo de permissão, a leitura de SSID, BSSID e tipo de segurança nas duas faixas de API, a varredura de redes próximas, a captura de localização e se o teclado decimal oferece a tecla "-".

### Cuidados ao alterar

- **Teclado decimal** (`LocationCard.kt`): o Compose não tem teclado "decimal com sinal". Em alguns teclados (há relatos com o da Samsung) falta a tecla "-", e coordenadas do Brasil são negativas. Se faltar, trocar por `KeyboardType.Text`.
- **Valores falsos do Android** (`Wifi.kt`): sem permissão ou com a localização desligada, o sistema devolve `<unknown ssid>` e o BSSID `02:00:00:00:00:00` em vez de falhar. Os dois viram `null`; salvar esse BSSID faria a área aceitar qualquer rede lida na mesma condição.
- **Leitura da rede conectada** (`WifiInfoProvider.kt`): na API 31+ o callback precisa de `FLAG_INCLUDE_LOCATION_INFO`, senão SSID e BSSID vêm mascarados; sem Wi-Fi o callback nunca dispara e é o tempo limite que vira "sem rede".
- **Varredura** (`WifiInfoProvider.kt`): o Android limita a 4 varreduras a cada 2 minutos; acima disso `startScan()` devolve `false` e lê-se o cache do sistema. `startScan()` está depreciado, sem substituto para apps comuns.
- **Tipo de segurança da rede conectada** (`WifiInfoProvider.kt`): só a API 31+ informa direto (`currentSecurityType`); abaixo disso é deduzido do BSSID na última varredura do sistema e pode sair `Unknown`.
- **Pontos de acesso de terceiros**: o agrupamento da varredura é por nome e tipo de segurança, então um roteador alheio com o mesmo nome e o mesmo tipo ao alcance no cadastro entra no conjunto da área.
- **Permissão "negada em definitivo"** (`LocationPermission.kt`): o Android não informa isso; a heurística usa `shouldShowRequestPermissionRationale` e dá falso positivo quando o diálogo é fechado sem resposta na primeira vez. A ação pendente fica em `rememberSaveable` porque a activity pode ser recriada com o diálogo aberto.
- **Navegação** (`SafeModeApp.kt`): o editor volta com `popBackStack(Routes.AREAS, inclusive = false)`; um `popBackStack()` simples, em toque duplo, removeria a própria lista. O argumento `areaId` é lido pelo nome em `AreaEditorViewModel`.
- **Preenchimento automático da rede** (`AreaEditorViewModel.prefillConnectedWifi`): só com a permissão já concedida, só em área nova, uma vez, e silencioso em caso de falha.
- **Mensagens**: os ViewModels usam `Channel`, não `StateFlow`, para o snackbar não reaparecer a cada rotação.
- **Precisão da localização** (`SafeStatusEvaluator.kt`): leitura com precisão pior que o raio só gera "não foi possível confirmar" para áreas ao alcance do erro; `GeoDistance` usa haversine em Kotlin puro para rodar em teste de JVM.
- **Coordenadas** (`CoordinateParser.kt`): o formato é validado por regex porque `toDouble()` aceita `NaN`, `Infinity`, `1e5` e `1f`; a formatação usa sempre `Locale.US`.

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
- **Strings**: todas em `strings.xml`, lidas com `stringResource`; nada de texto hardcoded nos composables.
- **Comentários**: o código não tem comentários, por decisão do dono do projeto; não os adicione. Os cuidados que antes estavam em comentários ficam neste arquivo.
- **Cores**: sempre via `MaterialTheme.colorScheme.*`, nunca `Color(0x...)` direto na tela. O tema usa **cor dinâmica** no Android 12+ (as cores vêm do papel de parede) e o roxo padrão do template abaixo disso — não assuma um tom específico e confira o contraste em tema claro e escuro.
- **Hierarquia de botões na tela**: `Button` preenchido (`primary`) para ações dentro dos cards; `Button` com `primaryContainer` e formato pílula para a ação de salvar; `OutlinedButton` para ação secundária.
- **Formas**: cards com `RoundedCornerShape(16.dp)`; campos e botões com `8.dp`.
- **Composables**: recebem `modifier: Modifier = Modifier` como primeiro parâmetro opcional e têm um `@Preview` envolto em `SafeModeTheme`. Cada tela tem um par `XxxScreen` (ViewModel, permissão, snackbars) e `XxxContent` (layout stateless); previews e testes de UI usam o `Content`, nunca o `Screen` (a Factory do ViewModel precisa de `Application`).
- **Estado**: fica no `UiState` de cada ViewModel; composables de `components/` são stateless, salvo estado puramente visual (menu aberto, interação do slider).
- **Regra de negócio** vai em `domain/`, sem imports de `android.*`, com teste de JVM.
- **Formato em disco**: os nomes de campo em `TrustedAreaJson.kt` são o formato salvo; renomear exige aceitar o nome antigo na leitura.
- **Backup**: o arquivo do DataStore está excluído em `res/xml/backup_rules.xml` e `data_extraction_rules.xml`; renomear o DataStore exige atualizar os dois.
- APIs experimentais do Material 3 estão em uso (`@OptIn(ExperimentalMaterial3Api::class)`).

## Cuidados com o repositório

- `.gradle/`, `.idea/`, `build/` e `local.properties` são ignorados pelo `.gitignore` da raiz e não devem ser versionados.
- Branch de trabalho: `develop`; branch principal: `main`.
