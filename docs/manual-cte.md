# Manual de Integração — CT-e (Conhecimento de Transporte Eletrônico, modelo 57)

> Ver [00-visao-geral.md](00-visao-geral.md) para autenticação, certificado digital,
> idempotência, ambientes, formato de erro e demais aspectos comuns a todos os
> documentos. Este manual cobre apenas o que é específico do CT-e.

## 1. Visão geral

O CT-e acoberta a **prestação de serviço de transporte** de cargas, modelo **57**,
regido pelo Ajuste SINIEF 09/07 e pelo leiaute 4.00. Diferente da NFC-e, o CT-e tem
domínio, mapeamento e schema JSON **próprios** neste adapter — ele transporta
(remetente, destinatário, informação de carga, notas fiscais vinculadas), não vende
mercadoria, então a estrutura do payload é bem diferente da NF-e; só reaproveita o que
já é genérico (`Emitente`/`Endereco`, `ChaveAcessoService`, numeração, retenção).

**Só o modal rodoviário é suportado hoje** — os demais modais (aéreo, aquaviário,
ferroviário, dutoviário, multimodal) exigem grupos XML próprios que ainda não existem
no domínio; ver seção 8.

## 2. Fluxo de emissão

```
POST /api/v1/cte
Authorization: Bearer <access_token>
Idempotency-Key: <chave única do cliente>
Content-Type: application/json
```

Pipeline (`CteEmissaoService`): mapeamento → certificado → chave de acesso (`mod=57`) →
geração do XML → assinatura → validação XSD → autorização **síncrona**
(`CTeRecepcaoSincV4`) → numeração → retenção → DACTE (seção 6). **Sem RVN própria**
(não existe ainda um conjunto de regras de negócio dedicado ao CT-e, análogo ao da
NF-e) e **sem contingência automática** (SVC-RS/SVC-SP) — falha de comunicação com a
SEFAZ propaga como HTTP 502.

> **CT-e 4.00 não tem mais modo em lote/assíncrono**: a SEFAZ desativou
> `CTeRecepcao`/`CTeRetRecepcao` em 30/06/2024 (NT 2024.001) — tudo migrou para
> `CTeRecepcaoSincV4` (um documento por chamada, resposta imediata, sem `idLote`/
> `indSinc` como a NF-e).

### 2.1 Estrutura do payload

```json
{
  "ambiente": "homologacao",
  "referencia": "id opcional do seu sistema",
  "infCte": {
    "ide": { ... },
    "emit": { ... },
    "remetente": { ... },
    "destinatario": { ... },
    "tomador": "REMETENTE",
    "vTPrest": 1000.00,
    "vRec": 1000.00,
    "imp": { "vBC": 1000.00, "pICMS": 12.00, "vICMS": 120.00 },
    "infCarga": { "vCarga": 5000.00, "proPred": "MERCADORIAS DIVERSAS", "pesoBrutoKg": 1500.0000 },
    "infNFe": [ { "chave": "35260112345678000199550010000000421000000019" } ],
    "rntrc": "12345678"
  }
}
```

**`ide`**: `uf`, `cfop`, `natOp`, `serie`, `nCT` (número — validado/reservado, ver
[manual-nfe.md § 8](manual-nfe.md#8-numeração-sequencial)), `dhEmi`, e o **percurso**:
`cMunEnv`/`xMunEnv`/`UFEnv` (início) e `cMunFim`/`xMunFim`/`UFFim` (destino).

**`emit`**: mesmo schema `EmitRequest` da NF-e (`CNPJ`/`CPF`, `xNome`, `enderEmit`,
`IE`, `CRT` etc.) — o emitente do CT-e é a transportadora.

**`remetente`/`destinatario`** (`ParticipanteCteRequest`, ambos opcionais, mesma
estrutura): `CNPJ`/`CPF`, `IE` (opcional), `xNome`, `endereco` (opcional), `email`
(opcional). O XSD oficial tem quatro grupos de participante (remetente, expedidor,
recebedor, destinatário) com a mesma estrutura — este adapter cobre remetente e
destinatário.

**`tomador`** (`TipoTomadorServico`) — quem contrata e paga o frete: `REMETENTE` (0),
`EXPEDIDOR` (1), `RECEBEDOR` (2), `DESTINATARIO` (3).

**`vTPrest`**/**`vRec`** — valor total da prestação e valor a receber.

**`imp`** — grupo ICMS único, CST fixo "00" (tributação normal): `vBC`, `pICMS`,
`vICMS`. Outros CST/regimes (isenção, Simples Nacional, substituição tributária) não
são suportados ainda.

**`infCarga`** — `vCarga` (valor da carga), `proPred` (produto predominante),
`pesoBrutoKg` (peso bruto em quilogramas — `infQ` é fixado em `cUnid=01` KG).

**`infNFe`** (opcional) — lista de `{"chave": "..."}`, as NF-e transportadas por este
CT-e (44 dígitos cada).

**`rntrc`** — Registro Nacional de Transportadores Rodoviários de Cargas, obrigatório
no modal rodoviário.

## 3. Resposta da emissão

```json
{
  "chaveAcesso": "35260166777888000133570010000000421...",
  "xmlAssinado": "<cteProc>...</cteProc>",
  "autorizada": true,
  "codigoStatusSefaz": "100",
  "motivoSefaz": "Autorizado o uso do CT-e",
  "numeroProtocolo": "135260000000001",
  "notasFiscaisTransportadas": ["35260112345678000199550010000000421000000019"],
  "dactePdfBase64": "<PDF em base64, ou null se rejeitado>",
  "mensagemErro": null,
  "categoriaErro": null
}
```

`notasFiscaisTransportadas` é ecoada do próprio pedido na emissão.

## 4. Consulta e cancelamento

```
POST /api/v1/cte/{chaveAcesso}/consulta?uf=SP&ambiente=HOMOLOGACAO
```

```json
{
  "chaveAcesso": "...",
  "autorizada": true,
  "codigoStatusSefaz": "100",
  "motivoSefaz": "Autorizado o uso do CT-e",
  "numeroProtocolo": "135260000000001",
  "notasFiscaisTransportadas": ["..."],
  "mdfeVinculado": null,
  "mensagemErro": null,
  "categoriaErro": null
}
```

`notasFiscaisTransportadas` na consulta é **extraída do XML arquivado** por este
adapter (a SEFAZ não devolve essa lista na consulta de situação — só cStat/protocolo);
fica vazia se o CT-e não foi emitido por este adapter.

`mdfeVinculado` (seção 5) — a chave do MDF-e que já manifestou este CT-e para
transporte, ou `null` se nenhum.

```
POST /api/v1/cte/{chaveAcesso}/cancelamento?uf=SP&ambiente=HOMOLOGACAO&numeroProtocolo=...&justificativa=...
```

Bloqueado (**HTTP 422**) em duas situações:

1. **Prazo legal vencido**: mais de **168 horas (7 dias)** desde a autorização (Ajuste
   SINIEF 09/07, cláusula 14 — algumas UFs reduzem esse prazo, ex.: MT para 24h, não
   verificado por UF nesta base). Cancelamento extemporâneo (após o prazo) é um
   procedimento específico de cada UF, fora do escopo.
2. **Já manifestado em MDF-e** (`mdfeVinculado != null`, `CteJaManifestadoEmMdfeException`)
   — ver seção 5.

**Validação de entrada (FIS-58)**: `numeroProtocolo` deve conter só dígitos (HTTP 400
caso contrário) - é concatenado diretamente no XML do evento de cancelamento, que é
assinado digitalmente logo em seguida.

## 5. Vínculo com MDF-e

A consulta expõe `mdfeVinculado`: como a SEFAZ não devolve esse vínculo na consulta de
situação do CT-e (só o próprio MDF-e sabe quais CT-e transporta), o adapter varre os
MDF-e já arquivados pelo mesmo CNPJ emissor (a transportadora é sempre a mesma nos dois
documentos) procurando uma referência à chave deste CT-e.

Cancelar um CT-e já incluído em um MDF-e é bloqueado — deixaria o manifesto
referenciando um documento inexistente perante o fisco; o procedimento correto é
cancelar ou encerrar o MDF-e vinculado primeiro (ver
[manual-mdfe.md](manual-mdfe.md)).

> **Limitação conhecida**: o arquivamento legal guarda só o XML autorizado, sem status
> de cancelamento — o bloqueio vale enquanto existir qualquer MDF-e autorizado que
> referencie o CT-e, mesmo que esse MDF-e tenha sido cancelado depois.

## 6. DACTE

`dactePdfBase64` — PDF A4 retrato com código de barras (Code128) da chave de acesso,
identificação do CT-e, emitente/transportador, remetente, destinatário, percurso
(origem/destino), RNTRC, informação da carga e valores da prestação
(total/a-receber/base-alíquota-valor do ICMS). Gerado só quando autorizado.

## 7. Estrutura SOAP (referência técnica)

Verificado contra a implementação de referência `nfephp-org/sped-cte`
(`Common/Tools.php`): a **autorização** exige o XML **gzip + base64** dentro de
`cteDadosMsg` (diferente da NF-e/NFC-e, que enviam texto puro) — consulta e
cancelamento continuam em texto puro. Header SOAP `cteCabecMsg` (não `nfeCabecMsg`).
Endpoints próprios do CT-e — 5 UFs (MG/MS/MT/PR/SP) têm infraestrutura dedicada; as
demais delegam para SVRS ou SP.

> **Atenção (não verificável nesta base)**: a versão do evento de cancelamento
> (`"4.00"`) foi assumida por alinhamento com a URL do serviço
> (`CTeRecepcaoEventoV4`), não confirmada contra homologação real.

## 8. Limitação de modal

O domínio `Cte` deste adapter só implementa o modal **rodoviário** — os demais (aéreo,
aquaviário, ferroviário, dutoviário, multimodal) exigem grupos XML próprios ainda não
implementados. O DACTE reflete isso imprimindo o modal como cabeçalho fixo
("Modal: Rodoviário") e o RNTRC.

## 9. Referências legais

- Ajuste SINIEF 09/07 — institui o CT-e, prazo de cancelamento (cláusula 14).
- NT 2024.001 — desativação do modo em lote/assíncrono (`CTeRecepcao`), migração para
  `CTeRecepcaoSincV4`.
- Manual de Orientação do Contribuinte (MOC) do CT-e, leiaute 4.00.
