# Manual de Integração — NF-e (Nota Fiscal Eletrônica, modelo 55)

> Ver [00-visao-geral.md](00-visao-geral.md) para autenticação, certificado digital,
> idempotência, ambientes, formato de erro e demais aspectos comuns a todos os
> documentos. Este manual cobre apenas o que é específico da NF-e.

## 1. Visão geral

A NF-e é o documento fiscal eletrônico que acoberta operações de circulação de
mercadorias e algumas prestações de serviço sujeitas ao ICMS, modelo **55**, regida
pelo Ajuste SINIEF 07/05 e pelo Manual de Orientação do Contribuinte (MOC) da NF-e
(leiaute vigente: 4.00). Este adapter cobre o ciclo completo: emissão, consulta,
cancelamento, Carta de Correção Eletrônica (CC-e), inutilização de numeração,
manifestação do destinatário e consulta de NF-e destinadas.

## 2. Chave de acesso

Chave de 44 dígitos, formada e verificada pelo próprio adapter
(`ChaveAcessoService`) segundo o leiaute oficial:

```
cUF(2) AAMM(4) CNPJ(14) mod(2) serie(3) nNF(9) tpEmis(1) cNF(8) cDV(1)  = 44 dígitos
```

- `cUF` — código IBGE da UF do emitente.
- `AAMM` — ano/mês de emissão (2 dígitos cada).
- `mod` — `55` para NF-e (`65` NFC-e, `57` CT-e, `58` MDF-e — mesmo layout de chave
  para os quatro documentos estaduais).
- `cNF` — código numérico aleatório de 8 dígitos (evita previsibilidade da chave).
- `cDV` — dígito verificador, módulo 11 com pesos 2..9 cíclicos, calculado sobre os 43
  dígitos anteriores.

## 3. Fluxo de emissão

```
POST /api/v1/nfe
Authorization: Bearer <access_token>
Idempotency-Key: <chave única do cliente>
Content-Type: application/json
```

Pipeline interno (`NfeEmissaoService`): mapeamento do JSON → **RVN** (regras de negócio,
seção 5) → resolução do certificado pelo CNPJ do emitente → cálculo da chave de acesso →
geração do XML → assinatura digital (XML-DSig, enveloped, C14N, RSA-SHA1) → validação
contra o XSD oficial → transmissão à SEFAZ com **retry e contingência** (ver seção 4) →
reserva do número (só se autorizada/liberada via EPEC) → arquivamento do XML para
retenção legal → geração do DANFE (seção 9).

O payload espelha deliberadamente o formato da **API paga da ACBr**
(`NfeSefazInfNFe`/`NfeSefazIde`/etc.), para que integrações já existentes troquem apenas
a URL de destino. Uma única divergência proposital: o grupo `total` **não é aceito no
payload** — os totais são sempre recalculados no servidor a partir dos itens, para não
confiar em soma de imposto vinda do cliente.

### 3.1 Estrutura do payload

```json
{
  "ambiente": "homologacao",
  "referencia": "id opcional do seu sistema, ecoado em logs",
  "infNFe": {
    "ide": { ... },
    "emit": { ... },
    "dest": { ... },
    "det": [ { "nItem": 1, "prod": { ... }, "imposto": { ... } } ],
    "transp": { "modFrete": 9 },
    "pag": { "detPag": [ { "tPag": "01", "vPag": 1000.00 } ] }
  }
}
```

**`ide`** (identificação):

| Campo | Tipo | Obrigatório | Observação |
|---|---|---|---|
| `cUF` | inteiro | sim | código IBGE da UF |
| `natOp` | texto | sim | natureza da operação (ex.: "Venda de mercadoria") |
| `serie` | inteiro | sim | série do documento |
| `nNF` | inteiro | sim | número do documento — **escolhido pelo cliente**, validado/reservado pelo adapter (seção 8) |
| `dhEmi` | data | sim | data de emissão — não pode ser futura (RVN-006) |
| `tpNF` | inteiro | sim | 0=entrada, 1=saída |
| `idDest` | inteiro | sim | 1=interna, 2=interestadual, 3=exterior |
| `cMunFG` | texto | sim | código IBGE do município de ocorrência do fato gerador |
| `tpImp` | inteiro | sim | formato do DANFE (1=retrato, 2=paisagem) |
| `tpEmis` | inteiro | sim | forma de emissão (1=normal — os demais tipos de contingência são geridos internamente pelo orquestrador, seção 4) |
| `tpAmb` | inteiro | sim | 1=produção, 2=homologação |
| `finNFe` | inteiro | sim | 1=normal, 2=complementar, 3=ajuste, 4=devolução |
| `indFinal` | inteiro | sim | 0=não, 1=consumidor final |
| `indPres` | inteiro | sim | indicador de presença do comprador |
| `procEmi` | inteiro | sim | processo de emissão (0=aplicativo do contribuinte) |
| `verProc` | texto | sim | versão do seu sistema emissor |

**`emit`** (emitente): `CNPJ` ou `CPF`, `xNome`, `xFant` (opcional), `enderEmit`
(endereço completo — `xLgr`, `nro`, `xCpl` opcional, `xBairro`, `cMun`, `xMun`, `UF`,
`CEP`, `fone` opcional), `IE`, `IEST`/`IM`/`CNAE` (opcionais), `CRT` (Código de Regime
Tributário: `1`=Simples Nacional, `2`=Simples Nacional (excesso de sublimite), `3`=Regime
Normal — determina qual grupo de ICMS os itens podem usar, RVN-005).

**`dest`** (destinatário) — **obrigatório para NF-e** (o campo é opcional no schema JSON
só para permitir reaproveitamento pela NFC-e, onde consumidor pode não ser
identificado): `CNPJ`/`CPF`/`idEstrangeiro`, `xNome`, `enderDest`, `indIEDest`
(1=contribuinte, 2=isento, 9=não contribuinte), `IE`/`ISUF`/`IM`/`email` (opcionais).

**`det[]`** (itens) — `nItem` sequencial a partir de 1; `prod` (produto: `cProd`,
`cEAN` — usar literal `"SEM GTIN"` quando não houver código de barras, `xProd`, `NCM`,
`CFOP` — RVN-003 valida o prefixo 5/6 conforme UF do destinatário, `uCom`, `qCom`,
`vUnCom`, `vProd` — RVN-001 valida `vProd = qCom × vUnCom`, `cEANTrib`/`uTrib`/`qTrib`/
`vUnTrib` espelhando os campos tributáveis, `indTot` — 1 se o item compõe o valor total
da nota); `imposto` (ver a seguir).

**`imposto`** por item — `ICMS` (exatamente um dos três grupos abaixo), `IPI`
(opcional), `PIS`, `COFINS`:

- **`ICMS00`** — tributação integral: `orig`, `CST` (fixo "00"), `modBC`, `vBC`,
  `pICMS`, `vICMS` (RVN-002 valida `vICMS = vBC × pICMS / 100`, tolerância de R$ 0,02).
- **`ICMS40`** — isenta (`CST` "40"), não tributada ("41") ou suspensão ("50"): só
  `orig`+`CST`, sem valores.
- **`ICMSSN102`** — Simples Nacional: `orig` + `CSOSN` ("102" sem crédito, "103" isenção
  por faixa de receita, "300" imune, "400" não tributada), sem valores.

RVN-005 barra a combinação errada: emitente do Simples Nacional (CRT 1/2/4) só pode
usar `ICMSSN102`; emitente do Regime Normal (CRT 3) só pode usar `ICMS00`/`ICMS40`.

`IPI` (quando presente): `cEnq` + `IPITrib` (`CST`, `vBC`, `pIPI`, `vIPI`). `PIS`:
`PISAliq` (`CST`, `vBC`, `pPIS`, `vPIS`). `COFINS`: `COFINSAliq` (`CST`, `vBC`,
`pCOFINS`, `vCOFINS`). Apenas os grupos "Aliq" (alíquota) são suportados hoje — os
demais CST de PIS/COFINS (isenção, substituição tributária, outras) ficam para expansão
futura.

**`transp`**: `modFrete` (0=emitente, 1=destinatário, 2=terceiros, 9=sem frete —
subconjunto suportado hoje).

**`pag`**: `detPag[]` — `tPag` (forma de pagamento: `01` dinheiro, `03` cartão de
crédito, `04` cartão de débito, `15` boleto, `99` outros — tabela oficial completa no
MOC) e `vPag`. RVN-004 valida que a soma de `vPag` bate com o valor total da nota
(tolerância de R$ 0,01).

## 4. Contingência (transmissão à SEFAZ)

`EmissaoNfeOrquestrador` tenta, em ordem, até conseguir autorização ou esgotar as
opções: **endpoint normal da UF** (2 tentativas) → **SVC-AN** (Sefaz Virtual de
Contingência) → **EPEC** (Evento Prévio de Emissão em Contingência) como último
recurso. Quando a NF-e é liberada via EPEC, a resposta traz `autorizada: false` e
`viaEpec: true` — **isso não é uma rejeição** (o documento está provisoriamente válido
para acompanhar a mercadoria; o protocolo definitivo fica pendente até a retomada da
transmissão normal). O número só é reservado (seção 8) e o XML só é arquivado quando
`autorizada || viaEpec`.

> Este mecanismo de contingência (SVC-AN/EPEC) é **exclusivo da NF-e** — NFC-e, CT-e e
> MDF-e não o reusam (ver os respectivos manuais); uma falha de comunicação nesses
> outros documentos propaga como erro HTTP 502.

## 5. Regras de Negócio (RVN)

Validadas **antes** de assinar e transmitir — evita gastar uma tentativa de protocolo
com algo que a SEFAZ certamente rejeitaria. Violação → HTTP 422 com um item por regra
violada em `detalhes` (`RVN-XXX: mensagem`).

| Código | Regra | O que verifica |
|---|---|---|
| RVN-001 | `RegraTotalItemConsistente` | `vProd = qCom × vUnCom` por item (tolerância R$ 0,01) |
| RVN-002 | `RegraIcmsConsistente` | `vICMS = vBC × pICMS / 100` por item (tolerância R$ 0,02) |
| RVN-003 | `RegraCfopCompativelComOperacao` | CFOP começa com `5` (operação interna) ou `6` (interestadual) conforme UF do emitente vs. destinatário |
| RVN-004 | `RegraSomaPagamentosIgualTotal` | soma de `vPag` = valor total da nota (tolerância R$ 0,01) |
| RVN-005 | `RegraRegimeTributarioCompativelComIcms` | CRT do emitente compatível com o grupo de ICMS usado (CST × CSOSN) |
| RVN-006 | `RegraDataEmissaoNaoFutura` | `dhEmi` não é posterior à data atual |

Estes códigos (`RVN-XXX`) são **próprios deste adapter**, não os códigos numéricos
oficiais da SEFAZ (que têm centenas de regras — a planilha "Regras de Validação de
Negócios" da NF-e). O critério de escolha foi cobrir as inconsistências
aritméticas/estruturais mais comuns na prática, verificáveis só com os dados da própria
nota, sem depender de tabelas externas de referência (NCM válido, CFOP × CST permitido
por produto etc., fora do escopo).

## 6. Resposta da emissão

```json
{
  "chaveAcesso": "35260112345678000199550010000000421969631333",
  "xmlAssinado": "<NFe>...</NFe>",
  "autorizada": true,
  "codigoStatusSefaz": "100",
  "motivoSefaz": "Autorizado o uso da NF-e",
  "numeroProtocolo": "135260000000001",
  "viaContingencia": false,
  "viaEpec": false,
  "danfePdfBase64": "<PDF em base64, ou null se rejeitada>",
  "mensagemErro": null,
  "categoriaErro": null
}
```

`mensagemErro`/`categoriaErro` só vêm preenchidos quando a nota foi de fato rejeitada
(`!autorizada && !viaEpec`) — ver seção 10 (catálogo de rejeições).

## 7. Consulta, cancelamento e eventos

Todos os endpoints abaixo resolvem o certificado automaticamente pelo CNPJ embutido na
chave de acesso — não é preciso reenviá-lo.

### 7.1 Consulta de situação

```
POST /api/v1/nfe/{chaveAcesso}/consulta?uf=SP&ambiente=HOMOLOGACAO
```
→ `{chaveAcesso, autorizada, codigoStatusSefaz, motivoSefaz, numeroProtocolo, mensagemErro, categoriaErro}`

### 7.2 Cancelamento

```
POST /api/v1/nfe/{chaveAcesso}/cancelamento?uf=SP&ambiente=HOMOLOGACAO&numeroProtocolo=...&justificativa=...
```
→ `{chaveAcesso, cancelado, codigoStatusSefaz, motivoSefaz, mensagemErro, categoriaErro}`

Prazo legal histórico da NF-e: normalmente até 24h após a autorização, mas o prazo
efetivo varia por UF (algumas adotam prazos maiores para determinados tipos de
operação) — **este adapter não bloqueia preventivamente o cancelamento de NF-e por
prazo** (diferente de NFC-e/CT-e/MDF-e, onde o prazo é uniforme o bastante para validar
localmente); a SEFAZ é quem decide se aceita, e o `codigoStatusSefaz`/`motivoSefaz`
refletem a decisão dela.

**Validação de entrada (FIS-58)**: `numeroProtocolo` deve conter só dígitos (HTTP 400
caso contrário) - é concatenado diretamente no XML do evento de cancelamento, que é
assinado digitalmente logo em seguida.

### 7.3 Carta de Correção Eletrônica (CC-e)

```
POST /api/v1/nfe/{chaveAcesso}/cartaCorrecao?uf=SP&ambiente=HOMOLOGACAO&numeroSequencial=1&textoCorrecao=...
```
→ `{chaveAcesso, registrada, codigoStatusSefaz, motivoSefaz, numeroProtocolo, mensagemErro, categoriaErro}`

`numeroSequencial` começa em 1 e incrementa a cada CC-e da mesma NF-e (a SEFAZ mantém
o histórico de todas). A CC-e **não pode alterar** valores, dados de cálculo de
impostos, dados cadastrais que impliquem mudança do remetente/destinatário, nem a data
de emissão/saída — é para correções de dados que não afetam a apuração de tributos ou a
identificação das partes (ver Ajuste SINIEF 01/07).

### 7.4 Inutilização de numeração

```
POST /api/v1/nfe/inutilizacao?uf=SP&ambiente=HOMOLOGACAO&cnpjEmitente=...&serie=1&numeroInicial=100&numeroFinal=105&justificativa=...
```
→ `{inutilizada, codigoStatusSefaz, motivo, numeroProtocolo, mensagemErro, categoriaErro}`

Não está associada a uma chave de acesso existente (o número nunca chegou a ser
emitido) — usada para formalizar perante o fisco uma faixa de numeração pulada por
erro de sistema ou nota cancelada antes da transmissão. Deve ser solicitada **até o
10º dia do mês subsequente** (prazo legal do MOC da NF-e).

### 7.5 Manifestação do destinatário e NF-e destinadas

A manifestação é feita pelo **destinatário** da NF-e, com o certificado do próprio
CNPJ dele (não o do emitente) — por isso `cnpjManifestante` é explícito:

```
POST /api/v1/nfe/{chaveAcesso}/manifestacao?ambiente=HOMOLOGACAO&cnpjManifestante=...&tipo=CONFIRMACAO_DA_OPERACAO
```

`tipo` (`TipoManifestacaoDestinatario`):

| Valor | Evento SEFAZ | Justificativa |
|---|---|---|
| `CONFIRMACAO_DA_OPERACAO` | 210200 — Confirmação da Operação | não |
| `CIENCIA_DA_OPERACAO` | 210210 — Ciência da Operação | não |
| `DESCONHECIMENTO_DA_OPERACAO` | 210220 — Desconhecimento da Operação | não |
| `OPERACAO_NAO_REALIZADA` | 210240 — Operação não Realizada | **sim, mínimo 15 caracteres** |

**Consulta de NF-e destinadas** (descobre o que ainda falta manifestar, sem precisar já
conhecer a chave de acesso de antemão):

```
GET /api/v1/nfe/destinadas?cnpjDestinatario=...&uf=SP&ambiente=HOMOLOGACAO
```

→ lista de `NfeDestinadaResponse`: `chaveAcesso`, `cnpjEmitente`, `nomeEmitente`,
`dataEmissao`, `dataAutorizacao`, `valorNota`, `situacao`, e três campos **calculados
pelo adapter** (a SEFAZ não os devolve prontos): `dataLimiteManifestacao` (data de
autorização + **90 dias corridos** — Ajuste SINIEF 14/2026, vigente desde 01/06/2026,
reduziu o prazo anterior de 180 dias), `diasRestantesParaManifestar`,
`prazoExpirado` e `alertaProximoDoPrazo` (`true` nos últimos 15 dias do prazo).

> **Ciência da Operação não tem prazo/efeito fiscal próprio**, mas a consulta de
> destinadas não informa qual manifestação (se alguma) já foi registrada para cada
> resumo — por isso o adapter calcula a mesma data-limite para todos os resumos
> devolvidos, independentemente do tipo de manifestação já feita.

> **Limite de consulta**: no máximo **1 consulta de destinadas por hora, por CNPJ** — a
> SEFAZ rejeita consultas repetidas sem novidade (cStat 656, "consumo indevido"); o
> adapter bloqueia preventivamente com HTTP 429
> (`ConsultaDistribuicaoDfeMuitoFrequenteException`) em vez de gastar a tentativa.

> **Atenção (não verificado contra homologação real nesta base)**: a estrutura do
> envelope SOAP do webservice nacional `NFeDistribuicaoDFe` foi implementada com base em
> bibliotecas de referência da comunidade, sem confirmação empírica.

## 8. Numeração sequencial

O número do documento (`nNF`) continua sendo **escolhido pelo cliente** (ERP), não
gerado pelo adapter — mas é validado e reservado atomicamente
(`NumeracaoSequencialService`, chave única `cnpj_emissor + uf + serie + tipo_documento +
numero`). A reserva só acontece quando o documento **efetivamente valeu** perante o
fisco (autorizado, ou liberado via EPEC) — uma submissão rejeitada não consome o
número. Tentar reutilizar um número já reservado → HTTP 409
(`NumeracaoIndisponivelException`).

## 9. DANFE

`danfePdfBase64` na resposta da emissão — PDF (A4, retrato ou paisagem conforme `tpImp`)
com identificação da nota, chave de acesso formatada e código de barras (Code128),
emitente, destinatário, tabela de itens e totais. Gerado apenas quando a nota foi
autorizada ou liberada via EPEC (rejeitada não tem DANFE válido — o campo vem `null`).
Emissão em contingência é sinalizada em destaque no próprio PDF.

## 10. Catálogo de códigos de rejeição

`codigoStatusSefaz`/`motivoSefaz` (cStat/xMotivo brutos) **sempre** vêm presentes na
resposta, mesmo para códigos não catalogados. Quando a operação falhou,
`mensagemErro`/`categoriaErro` traduzem o código para algo acionável:

- **`CORRIGIVEL_PELO_CLIENTE`** — dado inválido/incompatível no pedido; corrigir e
  reenviar.
- **`TRANSITORIO`** — falha do lado da SEFAZ (serviço parado, documento ainda não
  processado); tentar novamente tende a resolver sozinho.
- **`DESCONHECIDA`** — código fora do catálogo; usar o `motivoSefaz` bruto.

| cStat | Significado | Categoria |
|---|---|---|
| 100 | Autorizado o uso da NF-e | (sucesso) |
| 108 | Serviço da SEFAZ paralisado momentaneamente | TRANSITORIO |
| 109 | Serviço da SEFAZ paralisado sem previsão | TRANSITORIO |
| 110 | Uso denegado — irregularidade fiscal cadastral (emitente/destinatário) | CORRIGIVEL_PELO_CLIENTE |
| 204 | NF-e (mesma chave) já autorizada anteriormente — ver nota abaixo | CORRIGIVEL_PELO_CLIENTE |
| 215 | XML não passou na validação de schema | CORRIGIVEL_PELO_CLIENTE |
| 217 | NF-e ainda não consta na base da SEFAZ (consultar depois) | TRANSITORIO |
| 225 | Lote não passou na validação de schema | CORRIGIVEL_PELO_CLIENTE |
| 226 | UF do emitente não corresponde à UF autorizadora | CORRIGIVEL_PELO_CLIENTE |
| 234 | IE do destinatário não vinculada ao CNPJ dele | CORRIGIVEL_PELO_CLIENTE |
| 235 | Inscrição SUFRAMA inválida | CORRIGIVEL_PELO_CLIENTE |
| 241 | Número da NF-e (série/CNPJ) já utilizado | CORRIGIVEL_PELO_CLIENTE |
| 301 | Uso denegado — irregularidade cadastral do emitente | CORRIGIVEL_PELO_CLIENTE |
| 302 | Uso denegado — irregularidade cadastral do destinatário | CORRIGIVEL_PELO_CLIENTE |
| 539 | Já existe documento autorizado com mesmo número/série/CNPJ, chave diferente | CORRIGIVEL_PELO_CLIENTE |
| 590 | CST incompatível com emitente do Simples Nacional | CORRIGIVEL_PELO_CLIENTE |
| 656 | Consumo indevido (excesso de requisições) | CORRIGIVEL_PELO_CLIENTE |
| 999 | Erro não catalogado pela SEFAZ | DESCONHECIDA |

> Cobre os códigos mais frequentes na prática, não a tabela oficial completa (centenas
> de entradas). Descrições conferidas contra a tabela pública mantida por
> `nfephp-org/sped-nfe` — sem acesso direto ao PDF do MOC nesta base de conhecimento,
> mas consistentes entre múltiplas fontes independentes.

> **cStat 204 é recuperado automaticamente**: um reenvio da mesma chave pelo orquestrador
> de emissão (após timeout de rede numa tentativa anterior que na verdade já havia sido
> autorizada) faz a SEFAZ responder 204 em vez de 100. Antes de reportar essa rejeição ao
> integrador, o adapter consulta a situação real da chave (`nfeConsultaProtocolo4`) e, se
> ela já estiver autorizada, devolve sucesso com o protocolo verdadeiro — o integrador só
> vê 204 quando a consulta de confirmação também não indicar autorização.

## 11. Assíncrono e webhook

Ver [00-visao-geral.md § 7](00-visao-geral.md#7-emissão-assíncrona-e-webhook-hoje-só-para-nf-e)
— `POST /api/v1/nfe/assincrono` + `GET /api/v1/nfe/assincrono/{id}` + `PUT/GET
/api/v1/webhook`. É o único documento com esse modo implementado hoje.

## 12. Referências legais

- Ajuste SINIEF 07/05 — institui a NF-e.
- Ajuste SINIEF 01/07 — disciplina a Carta de Correção Eletrônica.
- Ajuste SINIEF 14/2026 — prazo de manifestação do destinatário (90 dias, vigente desde
  01/06/2026).
- Manual de Orientação do Contribuinte (MOC) da NF-e, leiaute 4.00 — Portal Nacional da
  NF-e.
