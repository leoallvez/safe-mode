# Dúvidas sobre o plano — Modo Seguro

Perguntas levantadas durante a leitura do [plano de implementação](PLANO_DE_IMPLEMENTACAO.md), com as respostas e os pontos que ainda dependem de decisão.

## 1. Como vai ser o pedido de permissão?

Refere-se à Fase 3 do plano.

O pedido aparece só quando o usuário toca num botão que precisa da permissão, nunca ao abrir a tela.

### Quais botões pedem

| Botão | Pede permissão? | Motivo |
|---|---|---|
| Usar Wi-Fi Conectado | Sim | O Android exige localização precisa para revelar o nome da rede |
| Capturar Local Atual | Sim | Lê a posição do aparelho |
| Testar Status Atual | Sim | Precisa do nome da rede e da posição |
| Salvar Área de Confiança | Não | Só grava o que está no formulário |

### O que acontece ao tocar

1. Se a permissão já foi concedida, o app executa a ação direto.
2. Se não, o Android mostra o diálogo padrão de localização, com as opções "Precisa" / "Aproximada" (no Android 12+) e "Durante o uso do app" / "Apenas esta vez" / "Não permitir".
3. Conforme a resposta:

| Resposta do usuário | Comportamento do app |
|---|---|
| Concedeu precisa | A ação pedida continua sozinha, sem novo toque |
| Concedeu só aproximada | Tratada como insuficiente; snackbar explica o motivo |
| Negou | Snackbar explicando para que a permissão serve; no próximo toque o diálogo aparece de novo |
| Negou em definitivo | Snackbar com a ação "Abrir configurações", que leva à tela do app nas configurações do sistema |

A localização aproximada é insuficiente porque, sem a precisa, o Android não entrega o nome da rede e a posição é imprecisa demais para raios de 50–500 m.

"Negou em definitivo" é a segunda recusa: a partir dela o Android para de mostrar o diálogo.

### Localização do aparelho desligada

Mesmo com a permissão concedida, se a localização do aparelho estiver desligada, o Android não entrega nem o nome da rede nem a posição. O app checa isso e avisa num snackbar separado.

### O que não será pedido

- **Localização em segundo plano** ("Permitir o tempo todo"): a verificação só acontece com o app aberto, quando o usuário toca no botão.
- **Wi-Fi e rede**: são permissões concedidas na instalação, sem diálogo.

### No código

Um único pedido cobre as duas permissões de localização (precisa e aproximada), porque o Android 12+ exige que sejam solicitadas juntas.

### Ponto em aberto

O plano não prevê uma tela ou diálogo próprio explicando o motivo **antes** do diálogo do sistema; a explicação só vem depois de uma recusa. Decidir se essa explicação prévia entra na Fase 3.

## 2. O que é a decisão D1 (opções do dropdown de SSID)?

Refere-se à seção 2 do plano e afeta a Fase 4.

O D1 existe porque o dropdown da tela promete algo que o Android não entrega: uma lista de redes Wi-Fi para escolher. Hoje ele mostra três nomes fixos no código, e é preciso decidir de onde virão os nomes reais.

### O que não dá para fazer

A ideia mais natural seria listar as redes salvas no aparelho (casa, trabalho etc.). O Android bloqueou isso para apps comuns a partir do Android 10, por privacidade: a lista de redes salvas revela os lugares que a pessoa frequenta.

### Opção A (a que o plano segue): rede conectada + rede salva no app

O dropdown mostra no máximo duas opções:

- a rede em que o aparelho está conectado agora;
- a rede já salva na área de confiança, se for outra.

Na prática, o usuário vai até o local, conecta no Wi-Fi, toca em "Usar Wi-Fi Conectado" e salva. O dropdown quase não é usado para escolher; serve para mostrar qual rede está selecionada e permitir voltar à rede salva.

- **Vantagem**: só precisa da permissão de localização que o app já vai pedir, e funciona igual em todas as versões do Android.
- **Limitação**: só dá para cadastrar uma rede estando conectado a ela. Não dá para configurar "Trabalho" estando em casa.

### Opção B: varrer as redes próximas

O app pede ao Android a lista de redes ao alcance da antena, como a tela de Wi-Fi das configurações, e o dropdown mostra todas.

- **Vantagem**: dá para escolher uma rede próxima sem estar conectado a ela.
- **Custos**:
  - o Android limita a frequência de varreduras, então a lista pode vir desatualizada ou vazia;
  - exige permissão adicional, e as regras mudam conforme a versão do Android, o que significa mais caminhos de código e mais testes em aparelhos diferentes;
  - a lista traz as redes dos vizinhos, redes ocultas sem nome e nomes repetidos, que precisam ser filtrados.

Mesmo na opção B, o usuário continua precisando estar fisicamente perto da rede. Ela também não resolve o caso de configurar "Trabalho" estando em casa.

### Comparação

| | Opção A | Opção B |
|---|---|---|
| Opções no dropdown | Até 2 | Todas as redes ao alcance |
| Cadastrar rede sem estar conectado a ela | Não | Sim, se estiver próxima |
| Cadastrar rede à distância | Não | Não |
| Permissões além da localização | Nenhuma | Sim |
| Comportamento varia por versão do Android | Não | Sim |

### Por que a recomendação é a A

O ganho real da B é pequeno: escolher uma rede próxima à qual o aparelho não está conectado. Como a área de confiança também exige a localização do local, o usuário já precisa estar lá para capturar o ponto, e conectar ao Wi-Fi nesse momento é um passo a mais, não um obstáculo.

### Pontos em aberto

- **Manter o dropdown?** Se a opção A for confirmada, com uma ou duas opções um campo de texto somente leitura com o botão "Usar Wi-Fi Conectado" ao lado comunicaria melhor. O plano mantém o dropdown para preservar o layout atual.
- **Digitação manual do nome da rede?** Seria a única forma de configurar uma rede à distância, mas um erro de digitação faz a área nunca ser reconhecida. Não está no plano.

## 3. Faz sentido mostrar o tipo de segurança da rede Wi-Fi?

Sim, e mais como validação do que como informação. Foi implementado em três níveis.

> As seções 1 e 2 acima são anteriores à lista de áreas e à validação por BSSID. Esta seção descreve o app como está hoje.

### Por que importa

- **Rede com senha (WPA2/WPA3)**: para o aparelho se conectar a um roteador falso com o mesmo nome e o mesmo BSSID, o atacante também precisa saber a senha. A própria conexão é uma prova razoável de que a rede é a verdadeira.
- **Rede aberta ou WEP**: qualquer pessoa monta uma rede idêntica. Nome e BSSID clonados bastam.

### O que foi implementado

| Nível | Comportamento |
|---|---|
| 1. Mostrar | O tipo aparece no card de Wi-Fi do editor, na lista de áreas e em cada rede do diálogo "Redes próximas" |
| 2. Avisar | Rede aberta ou WEP mostra um alerta de que é fácil de falsificar. O cadastro não é bloqueado |
| 3. Validar | O tipo é salvo com a área. No teste de status, a rede conectada precisa ter o mesmo tipo; se for outro, o resultado é "fora", com uma mensagem que cita os dois tipos |

O texto mostra a **categoria** e, entre parênteses, o **protocolo** quando o aparelho consegue identificá-lo, por exemplo "Protegida por senha (WPA2/WPA3)" ou "Segurança fraca (WEP)".

| Categoria | Protocolos que aparecem | Considerada fraca |
|---|---|---|
| Aberta, sem senha | nenhum, ou OWE | Sim |
| Segurança fraca | WEP | Sim |
| Protegida por senha | WPA, WPA2, WPA3 e combinações (WPA/WPA2, WPA2/WPA3) | Não |
| Corporativa | WPA, WPA2, WPA3 com login (EAP) | Não |
| Segurança desconhecida | nenhum | Não se aplica |

A validação do nível 3 compara só a **categoria**. O protocolo é informativo.

### Limitações

- **Android 11 e anteriores não informam o tipo da rede conectada.** Nessas versões o app deduz o tipo procurando o ponto de acesso conectado na última varredura de redes feita pelo sistema. Se a varredura estiver desatualizada ou não contiver esse ponto de acesso, o tipo fica "desconhecido". A partir do Android 12 o sistema informa o tipo diretamente.
- **"Desconhecido" não é comparado.** Se o tipo salvo na área ou o tipo lido no momento do teste for desconhecido, a validação de segurança é pulada e valem só o nome, o BSSID e a localização. Isso evita que a área deixe de ser reconhecida de forma intermitente em aparelhos antigos, mas significa que neles a proteção do nível 3 nem sempre atua.
- **O protocolo exibido é só informativo e pode variar entre leituras.** Quando o ponto de acesso conectado aparece na última varredura do sistema, o texto mostra o que o roteador anuncia (por exemplo "WPA2/WPA3"). Quando não aparece, no Android 12+ o texto mostra o protocolo da conexão em uso, que o sistema informa de forma menos precisa ("WPA/WPA2" para qualquer rede WPA ou WPA2 com senha); no Android 11 e anteriores, não mostra protocolo nenhum. Uma nova leitura da mesma rede substitui o protocolo exibido.
- **Redes corporativas WPA3 podem aparecer como WPA2.** Só o modo WPA3 de 192 bits é distinguível pela varredura; o WPA3 corporativo comum é anunciado da mesma forma que o WPA2.
- **A comparação é por categoria.** Trocar o roteador de WPA2 para WPA3 não quebra a área (as duas são "protegida por senha"). Em compensação, o app não distingue WPA2 de WPA3, nem percebe um rebaixamento dentro da mesma categoria.
- **Roteadores em modo misto** (WPA2/WPA3 ao mesmo tempo) podem ser informados de formas diferentes entre uma leitura e outra. Como ambos caem na mesma categoria, isso não afeta a validação.
- **OWE conta como rede aberta.** Ela criptografa o tráfego, mas não exige senha, então qualquer pessoa pode montar uma igual.
- **Áreas salvas antes desta mudança ficam como "desconhecido".** Elas continuam funcionando e passam a ter o tipo quando a rede é registrada de novo no editor (botão "Usar Wi-Fi Conectado" ou "Buscar Outras Redes").
- **Mudar o tipo de segurança do roteador quebra a área.** Passar de rede aberta para rede com senha, por exemplo, faz o teste responder "fora" até a área ser editada e a rede registrada de novo.
- **Mesmo nome com tipos diferentes vira duas redes.** No diálogo "Redes próximas", uma rede com senha e uma rede aberta de mesmo nome aparecem como itens separados, e os pontos de acesso de uma não são misturados aos da outra. Registrar de novo a mesma rede com outro tipo substitui os pontos de acesso em vez de somá-los.
- **O aviso de rede fraca não bloqueia.** É possível salvar uma área em rede aberta; ela só fica menos confiável.
- **O tipo de segurança não prova a identidade da rede.** Quem souber a senha consegue montar uma rede falsa com o mesmo nome, BSSID e tipo. A localização continua sendo o outro fator.

## 4. O usuário pode escolher entre localização precisa e aproximada?

Proposta em discussão. **Ainda não implementada.**

### Por que não pela permissão do Android

O Android 12+ deixa o usuário escolher "Precisa" ou "Aproximada" no diálogo de permissão. Com a aproximada:

- **O Wi-Fi para de funcionar**: o sistema só revela o nome e o BSSID da rede com a localização precisa. Sem ela não dá para registrar nem validar a rede.
- **A posição vem com erro de 1 a 3 km**: os raios de 50 a 500 m deixam de ter sentido.

Por isso o app trata a permissão aproximada como insuficiente, e a proposta mantém isso.

### A proposta: escolher dentro do app, por área

O app continua pedindo a permissão precisa, e cada área ganha um campo "Precisão da localização" no card de Geolocalização:

| | Precisa | Aproximada |
|---|---|---|
| Como o app lê a posição | GPS | Redes Wi-Fi e torres de celular |
| Erro típico | 5 a 20 m | 100 m a alguns quilômetros |
| Tempo e bateria | Mais lento, gasta mais | Rápido, gasta pouco |
| Funciona em ambiente interno | Mal | Bem |
| Faixa de raio | 50 a 500 m (como hoje) | Maior, por exemplo 300 m a 3 km |

O que mudaria no comportamento:

- **Capturar Local Atual** usa o modo escolhido na área.
- **O slider de raio** troca de faixa conforme o modo.
- **Testar Status Atual** lê a posição uma vez para todas as áreas: usa GPS se alguma área candidata for precisa, senão usa a leitura rápida.
- **A regra de "precisão pior que o raio"** continua valendo; uma área aproximada com raio grande passa a ser confirmada onde hoje daria "não foi possível confirmar".
- **Áreas já salvas** ficam como "Precisa".

O que resolve e o que custa:

- **Resolve** o problema de ambiente interno, em que o GPS costuma ter erro maior que 50 m.
- **Custa** confiança: uma área aproximada com raio de 1 km é bem mais frouxa. A localização vira um fator fraco e quem segura a validação é o Wi-Fi (nome, BSSID e tipo de segurança).

Alternativa mais simples: uma única opção global em vez de uma por área. O app ainda não tem tela de configurações.

### E abaixo do Android 12?

Abaixo do Android 12 o usuário não escolhe a precisão: se o app pede a localização precisa e a pessoa concede, a permissão é sempre precisa.

| Versão | O que o usuário decide no diálogo |
|---|---|
| Android 7 a 9 | Permitir ou negar |
| Android 10 | Permitir sempre, só durante o uso do app, ou negar |
| Android 11 | Só durante o uso, só desta vez, ou negar |
| Android 12+ | O mesmo do 11, mais a escolha entre precisa e aproximada |

O projeto suporta do Android 7.0 (`minSdk` 24) em diante. Como o app pede a precisa, nas versões abaixo do 12 ele recebe a precisa ou nada; o caso "concedeu só a aproximada" não acontece nelas.

**Configuração do aparelho nas versões antigas.** Do Android 7 ao 8.1 existe um ajuste do próprio aparelho, fora do app, chamado "Modo de localização":

- **Alta precisão**: GPS, Wi-Fi e redes móveis.
- **Economia de bateria**: só Wi-Fi e redes móveis, sem GPS.
- **Somente dispositivo**: só GPS.

No Android 9 isso virou um liga/desliga único, com uma opção separada de "Precisão de local do Google".

Esse ajuste afeta o app: num aparelho em "Economia de bateria", mesmo com a permissão precisa, a leitura vem sem GPS e com erro maior. Hoje isso aparece como "não foi possível confirmar" em áreas de raio pequeno.

**Efeito na proposta.** A escolha dentro do app não depende da versão do Android: funciona igual do Android 7 em diante, porque é o app que decide pedir a leitura por GPS ou a leitura rápida por redes, e a permissão é a mesma nos dois casos.

### Ponto em aberto

Confirmar se a intenção é essa (escolher no app como a posição é lida) ou fazer o app funcionar só com a permissão aproximada do Android. A segunda não é recomendada, pelo bloqueio do Wi-Fi.

## Resumo das decisões

| # | Decisão | Situação |
|---|---|---|
| 1 | Explicar o motivo da permissão antes do diálogo do sistema? | Resolvida: os cards do editor explicam e oferecem "Permitir Localização" |
| 2 | Opção A ou B para a escolha da rede? | Resolvida: as duas ("Usar Wi-Fi Conectado" e "Buscar Outras Redes") |
| 3 | Manter o dropdown ou trocar por campo somente leitura? | Resolvida: campo somente leitura |
| 4 | Permitir digitar o nome da rede à mão? | Resolvida: não |
| 5 | Precisão da localização por área, escolhida no app? | Pendente (seção 4) |
