# Plano de implementação — Modo Seguro

Plano para transformar a tela `SafeModeScreen` (hoje só interface) em uma POC funcional: definir uma área de confiança (Wi-Fi + geolocalização + raio), salvá-la e verificar se o aparelho está dentro dela.

Base da análise: `app/src/main/java/com/poc/safemode/ui/SafeModeScreen.kt` no commit `fda665f`.

## 1. Diagnóstico da tela

| Elemento | Situação atual | O que falta |
|---|---|---|
| Título | `" Modo Seguro"` com espaço inicial (linha 99) | Remover o espaço |
| Dropdown de SSID | Três opções fixas no código (linha 74) | Fonte real de dados (ver decisão D1) |
| "Usar Wi-Fi Conectado" | `onClick` vazio (linha 186) | Ler o SSID conectado, com permissão |
| Texto `"Row"` | Rótulo solto acima dos campos (linhas 227–231), sobra de protótipo | Remover |
| Latitude / Longitude | Texto livre, teclado padrão, sem validação | Teclado decimal, validação de faixa, mensagem de erro |
| "Capturar Local Atual" | `onClick` vazio (linha 279) | Obter a localização, com permissão |
| Slider de raio | Funciona (50–500 m, passo 10) | Só subir o estado; revisar acessibilidade |
| "Salvar Área de Confiança" | `onClick` vazio (linha 387) | Validar e persistir |
| "Testar Status Atual" | Snackbar fixo "dentro de um local seguro" (linha 414) | Cálculo real e os resultados "fora" e "não foi possível verificar" |
| Estado | `remember` local; perde tudo na rotação | ViewModel + estado de UI único |

Fora da tela, também não existem: permissões no manifesto, dependências de ViewModel/persistência/localização, e testes além dos exemplos do template.

## 2. Decisões em aberto

Cada uma tem uma recomendação; o plano abaixo segue as recomendações. Se alguma mudar, as fases afetadas estão indicadas.

**D1 — De onde vêm as opções do dropdown de SSID?** (afeta a Fase 4)
O Android não permite que um app comum liste as redes salvas no aparelho. As alternativas reais são:
- **(recomendada)** Mostrar a rede conectada no momento e a rede já salva na área de confiança. Simples, sem permissão extra além da localização.
- Listar redes próximas via varredura (`WifiManager.scanResults`). Exige mais permissões, a varredura é limitada pelo sistema e as exigências mudam por versão do Android; só vale se escolher uma rede à qual o aparelho não está conectado for requisito.

**D2 — Regra de "dentro da área".** (afeta as Fases 2 e 7)
- **(recomendada)** Wi-Fi **e** localização: SSID conectado igual ao salvo e distância até o ponto ≤ raio. É o que a descrição do projeto indica ("combinando").
- Wi-Fi **ou** localização: mais tolerante, menos seguro.

**D3 — Provedor de localização.** (afeta a Fase 5)
- **(recomendada)** `FusedLocationProviderClient` (Google Play Services): leitura mais rápida e precisa.
- `LocationManagerCompat.getCurrentLocation` (só AndroidX): sem dependência do Play Services, porém mais lento e menos preciso em ambiente interno.

**D4 — Verificação em segundo plano.**
A tela só tem verificação sob demanda ("Testar Status Atual"). Monitoramento contínuo (geofencing, serviço, notificação) **fica fora deste plano**; exigiria permissão de localização em segundo plano e outro desenho de arquitetura.

**D5 — Quantas áreas de confiança?**
A tela edita uma só. O plano persiste **uma única** área. Várias áreas pediriam lista, navegação e banco de dados.

## 3. Arquitetura alvo

Sem DI e sem Navigation, como hoje. Acrescenta-se um ViewModel e uma camada fina de dados.

```
com.poc.safemode/
├── MainActivity.kt
├── domain/
│   ├── TrustedArea.kt            # ssid, latitude, longitude, radiusMeters
│   ├── CoordinateParser.kt       # texto -> Double validado
│   ├── GeoDistance.kt            # haversine em Kotlin puro
│   └── SafeStatusEvaluator.kt    # regra dentro/fora
├── data/
│   ├── TrustedAreaRepository.kt  # DataStore
│   ├── WifiInfoProvider.kt       # SSID conectado
│   └── LocationProvider.kt       # localização atual
└── ui/
    ├── SafeModeScreen.kt         # rota (com ViewModel) + conteúdo stateless
    ├── SafeModeUiState.kt
    ├── SafeModeViewModel.kt
    ├── components/
    │   ├── WifiCard.kt
    │   ├── LocationCard.kt
    │   └── RadiusSlider.kt
    └── theme/
```

- `domain/` não importa nada de `android.*`, para ser testável em JVM pura.
- `WifiInfoProvider` e `LocationProvider` são interfaces com implementação Android, para o ViewModel ser testado com fakes.
- O ViewModel recebe as dependências por uma `ViewModelProvider.Factory` manual.

Dependências novas (todas em `gradle/libs.versions.toml`): `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`, `datastore-preferences`, `play-services-location`, `kotlinx-coroutines-play-services`, `kotlinx-coroutines-test` (teste).

## 4. Fases

Cada fase termina compilando (`./gradlew :app:compileDebugKotlin`) e pode virar um commit próprio. Fases que mexem em layout só estão verificadas depois de vistas no Preview ou em um aparelho.

### Fase 0 — Correções da tela

- Remover o `Text("Row")` e o espaço inicial do título.
- Conferir se `menuAnchor()` sem parâmetro gera aviso de depreciação na versão atual do Material 3 e, se gerar, trocar pela sobrecarga com tipo de âncora.

**Pronto quando:** a tela não mostra mais o rótulo "Row" e o título está alinhado com os cards.

### Fase 1 — Extração de composables e estado de UI

Refatoração sem mudança de comportamento.

- Criar `SafeModeUiState` (SSID selecionado, opções de SSID, latitude e longitude como texto, erros de campo, raio, indicadores de carregamento, área salva).
- Criar `SafeModeViewModel` expondo `StateFlow<SafeModeUiState>` e um fluxo de eventos de uso único para snackbars.
- Separar `SafeModeScreen` em rota (obtém o ViewModel, coleta com `collectAsStateWithLifecycle`) e `SafeModeContent` stateless (recebe estado e callbacks).
- Extrair `WifiCard`, `LocationCard` e `RadiusSlider` como composables privados/internos, cada um com `modifier` e `@Preview` em `SafeModeTheme`.
- O estado `expanded` do dropdown e o `InteractionSource` do slider continuam locais (são estado de UI puro).

**Pronto quando:** a tela se comporta como antes e o estado sobrevive à rotação.

### Fase 2 — Domínio e validação

- `TrustedArea(ssid, latitude, longitude, radiusMeters)`.
- `CoordinateParser`: aceita vírgula ou ponto como separador decimal (teclado pt-BR); latitude em −90..90, longitude em −180..180.
- `GeoDistance.metersBetween(...)` com haversine. Não usar `Location.distanceBetween`, que não roda em teste de JVM.
- `SafeStatusEvaluator`: recebe a área salva, o SSID atual e a posição atual (com precisão) e devolve `Inside`, `Outside(motivo)` ou `Unknown(motivo)`. Motivos de `Outside`: rede diferente, fora do raio. Motivos de `Unknown`: sem área salva, sem permissão, localização desligada, sem leitura de posição.
- Na tela: `KeyboardType.Decimal` nos dois campos, `isError` + `supportingText` quando inválido, e "Salvar" desabilitado enquanto houver erro.

**Pronto quando:** testes unitários cobrem parser, distância e avaliador (ver Fase 8), e a tela recusa `91` em latitude e `abc` em qualquer campo.

### Fase 3 — Permissões

- Manifesto: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE`.
- Pedido em runtime com `rememberLauncherForActivityResult(RequestMultiplePermissions)`, disparado pelo botão que precisa da permissão, nunca na abertura da tela.
- Tratar os três desfechos: concedida; negada (snackbar explicando para que serve); negada em definitivo (snackbar com ação "Abrir configurações").
- No Android 12+ o usuário pode conceder só localização aproximada. Nesse caso o SSID não fica disponível e a posição é imprecisa demais para raios de 50–500 m: tratar como permissão insuficiente e explicar.
- Checar também se o serviço de localização do aparelho está ligado; sem ele, nem SSID nem posição são entregues.

**Pronto quando:** em um aparelho, negar, conceder aproximada e conceder precisa produzem cada qual a mensagem certa, sem crash.

### Fase 4 — Wi-Fi

- `WifiInfoProvider.currentSsid(): String?`
  - API 31+: `ConnectivityManager.NetworkCallback` com `FLAG_INCLUDE_LOCATION_INFO`, lendo `WifiInfo` de `NetworkCapabilities.transportInfo`.
  - API 24–30: `WifiManager.connectionInfo` (depreciado, mas é o caminho disponível).
  - Normalizar o retorno: tirar as aspas em volta do SSID e tratar `<unknown ssid>` como ausência.
- "Usar Wi-Fi Conectado": pede permissão se preciso, lê o SSID, seleciona no dropdown. Sem Wi-Fi conectado, snackbar informando.
- Opções do dropdown (D1): rede conectada + rede da área salva, sem duplicar. Sem nenhuma das duas, campo vazio com texto de apoio orientando a usar o botão.
- Remover a lista fixa `ssidOptions`.

**Pronto quando:** em um aparelho conectado a um Wi-Fi, o botão preenche o SSID real; com Wi-Fi desligado, aparece a mensagem.

### Fase 5 — Localização

- `LocationProvider.current(): LocationReading?` (latitude, longitude, precisão em metros), usando `getCurrentLocation` com prioridade de alta precisão e tempo limite.
- "Capturar Local Atual": pede permissão se preciso, mostra progresso no botão enquanto aguarda, preenche os dois campos. Em falha ou tempo esgotado, snackbar.
- Formatar as coordenadas com ponto e número fixo de casas (6), independentemente do locale, para o parser reler o que a tela escreveu.

**Pronto quando:** em um aparelho, o botão preenche a posição real e o estado de carregamento aparece e some.

### Fase 6 — Persistência

- `TrustedAreaRepository` com DataStore Preferences: `Flow<TrustedArea?>` e `save(area)`.
- "Salvar Área de Confiança": valida, grava e mostra snackbar de confirmação.
- Ao abrir o app, o ViewModel carrega a área salva e preenche o formulário; os valores iniciais fixos (`-23.5505` / `-46.6333`, `MinhaCasa_5G`) deixam de existir, e sem área salva os campos começam vazios.
- `android:allowBackup="true"` está ativo: a área salva (coordenadas de casa/trabalho) entraria no backup do sistema. Excluir o arquivo do DataStore em `backup_rules.xml` e `data_extraction_rules.xml`, ou desligar o backup.

**Pronto quando:** salvar, fechar o app e reabrir mostra os mesmos valores.

### Fase 7 — Teste de status real

- "Testar Status Atual": lê a área salva, o SSID e a posição atuais, passa pelo `SafeStatusEvaluator` e mostra o resultado.
- Compara com a área **salva**, não com o que está digitado no formulário; se houver edição não salva, avisar.
- Mensagens distintas para dentro, fora por rede diferente, fora por distância (com a distância medida) e não verificável (com o motivo).
- Quando a precisão da leitura for pior que o raio configurado, o resultado não é confiável: informar isso em vez de afirmar dentro/fora.
- Botão com indicador de progresso e bloqueio contra toque duplo enquanto verifica.

**Pronto quando:** em um aparelho, o resultado muda de "dentro" para "fora" ao trocar de rede ou ao salvar um ponto distante.

### Fase 8 — Testes e acabamento

- Unitários (JVM): `CoordinateParser` (vírgula, ponto, limites, texto inválido), `GeoDistance` (pontos conhecidos, mesma posição), `SafeStatusEvaluator` (todos os desfechos), `SafeModeViewModel` com fakes dos provedores e do repositório.
- Instrumentado (Compose): campo inválido desabilita "Salvar"; cada estado de resultado mostra a mensagem certa.
- Remover `ExampleUnitTest` e `ExampleInstrumentedTest`.
- Mover os textos novos para `strings.xml`; as mensagens emitidas pelo ViewModel devem ser ids de recurso, não texto.
- Acessibilidade: rótulo (`label`) nos campos em vez de `Text` solto acima, descrição do valor do slider para o TalkBack, contraste do botão "Testar Status Atual" (borda e texto em `onPrimaryContainer` sobre `background`) em tema claro, escuro e com cor dinâmica.
- `./gradlew :app:lintDebug` sem novos avisos.
- Atualizar o `AGENTS.md`, que hoje descreve o projeto como "apenas a interface de uma tela".

## 5. Ordem e dependências

```
Fase 0 ─> Fase 1 ─> Fase 2 ─┬─> Fase 6 ──────────┐
                            └─> Fase 3 ─┬─> Fase 4 ─┼─> Fase 7 ─> Fase 8
                                        └─> Fase 5 ─┘
```

As Fases 4, 5 e 6 são independentes entre si. As Fases 0 a 2 e a 6 podem ser validadas em emulador; as Fases 3, 4, 5 e 7 precisam de aparelho físico (o emulador não reporta um SSID real útil e a localização é simulada).

## 6. Riscos

- **Leitura de SSID varia por versão do Android.** Com `minSdk` 24 e `targetSdk` 37 são dois caminhos de código; testar ao menos em um aparelho com API ≤ 30 e um com API ≥ 31.
- **Precisão da localização em ambiente interno** pode passar de 50 m, o raio mínimo. A Fase 7 trata isso informando resultado não confiável; se for frequente, considerar subir o raio mínimo.
- **SSID não identifica a rede com segurança**: qualquer ponto de acesso pode anunciar o mesmo nome. Aceitável para a POC; um produto precisaria considerar BSSID ou outro fator.
- **Dependência do Google Play Services** (D3) exclui aparelhos sem ele.
