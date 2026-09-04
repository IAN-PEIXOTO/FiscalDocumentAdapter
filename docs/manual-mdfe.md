# Manual de Integração — MDF-e (Manifesto Eletrônico de Documentos Fiscais, modelo 58)

> Ver [00-visao-geral.md](00-visao-geral.md) para autenticação, certificado digital,
> idempotência, ambientes, formato de erro e demais aspectos comuns a todos os
> documentos. Este manual cobre apenas o que é específico do MDF-e.

## 1. Visão geral

O MDF-e agrupa, para uma viagem, os CT-e/NF-e transportados por um veículo/condutor —
não vende nem transporta um único documento, é o manifesto da viagem inteira. Modelo
**58**, regido pelo Ajuste SINIEF 21/2010, leiaute 3.00. Mesmo espírito do CT-e: domínio,
mapeamento e schema JSON próprios.

**Só o modal rodoviário é suportado**, com um único município de carregamento e um
único de descarregamento (o XSD permite vários; não implementado aqui).

## 2. Fluxo de emissão

```
POST /api/v1/mdfe
Authorization: Bearer <access_token>
Idempotency-Key: <chave única do cliente>
Content-Type: application/json
```

Pipeline (`MdfeEmissaoService`): mapeamento → certificado → chave de acesso (`mod=58`)
→ geração do XML → assinatura → validação XSD → autorização **síncrona**
(`MDFeRecepcaoSinc`) → numeração → retenção → DAMDFE (seção 5). Sem RVN própria e sem
contingência automática (mesma decisão do CT-e) — falha de comunicação propaga como
HTTP 502.

> Mesma migração do CT-e: a SEFAZ desativou o modo em lote
> (`MDFeRecepcao`/`MDFeRetRecepcao`) em 30/06/2024 (NT 2024.001).

### 2.1 Estrutura do payload

```json
{
  "ambiente": "homologacao",
  "referencia": "id opcional do seu sistema",
  "infMDFe": {
    "ide": { "uf": "SP", "UFIni": "SP", "UFFim": "RJ", "serie": 1, "nMDF": 950,
             "dhEmi": "2026-03-15", "cMunCarrega": "3550308", "xMunCarrega": "Sao Paulo" },
    "emit": { "CNPJ": "...", "xNome": "TRANSPORTADORA TESTE LTDA", "enderEmit": { ... } },
    "rntrc": "12345678",
    "veicTracao": { "placa": "ABC1D23", "tara": 8000, "tpRod": "03", "tpCar": "02", "UF": "SP" },
    "condutores": [ { "xNome": "JOAO DA SILVA", "CPF": "12345678900" } ],
    "cMunDescarga": "3304557",
    "xMunDescarga": "Rio de Janeiro",
    "infCte": ["35260112345678000199570010000000421000000012"],
    "infNFe": ["35260112345678000199550010000000421000000019"],
    "vCarga": 5000.00,
    "pesoBrutoKg": 1500.0000
  }
}
```

**`ide`**: `uf` (UF do estabelecimento emitente — usada para roteamento, distinta de
`UFIni`/`UFFim`), `UFIni`/`UFFim` (percurso), `serie`, `nMDF` (número — validado/
reservado), `dhEmi`, `cMunCarrega`/`xMunCarrega` (município de carregamento).

**`emit`** (`EmitMdfeRequest`) — **sem `CRT`**, diferente do emitente de NF-e/CT-e:
`CNPJ`, `xNome`, `xFant` (opcional), `IE` (opcional), `enderEmit`.

**`rntrc`** (opcional) — obrigatório na prática para o modal rodoviário.

**`veicTracao`** — `placa`, `tara` (peso do veículo vazio, kg — inteiro, sem casas
decimais, diferente dos demais campos monetários/quantidade do documento), `tpRod`
(tipo de rodado, ex. "03"=cavalo mecânico), `tpCar` (tipo de carroceria, ex.
"02"=fechada/baú), `UF` (licenciamento, opcional).

**`condutores`** — lista de `{xNome, CPF}`, pelo menos um.

**`cMunDescarga`/`xMunDescarga`** — município de descarregamento **previsto** (pode
diferir do informado no encerramento real, seção 3).

**`infCte`/`infNFe`** (opcionais) — listas de chaves (44 dígitos) dos CT-e/NF-e
transportados nesta viagem.

**`vCarga`/`pesoBrutoKg`** — valor total e peso bruto da carga.

## 3. Resposta da emissão

```json
{
  "chaveAcesso": "35260388999000111122580010000009511...",
  "xmlAssinado": "<MDFe>...</MDFe>",
  "autorizada": true,
  "codigoStatusSefaz": "100",
  "motivoSefaz": "Autorizado o uso do MDF-e",
  "numeroProtocolo": "935260000000001",
  "damdfePdfBase64": "<PDF em base64, ou null se rejeitado>",
  "mensagemErro": null,
  "categoriaErro": null
}
```

## 4. Consulta

```
POST /api/v1/mdfe/{chaveAcesso}/consulta?uf=SP&ambiente=HOMOLOGACAO
```

```json
{
  "chaveAcesso": "...",
  "autorizada": true,
  "codigoStatusSefaz": "100",
  "motivoSefaz": "Autorizado o uso do MDF-e",
  "numeroProtocolo": "935260000000001",
  "encerrado": false,
  "chavesCteTransportados": ["35260112345678000199570010000000421000000012"],
  "chavesNfeTransportadas": ["35260112345678000199550010000000421000000019"],
  "mensagemErro": null,
  "categoriaErro": null
}
```

`chavesCteTransportados`/`chavesNfeTransportadas` ("documentos vinculados") são
extraídas do XML arquivado por este adapter na emissão — a SEFAZ não devolve essa lista
na consulta de situação. Ficam vazias se o MDF-e não foi emitido por este adapter.

`encerrado` reflete se o encerramento (seção 5) já foi registrado — a SEFAZ também não
expõe esse status na consulta de situação usada por este adapter.

## 5. Encerramento (fim de percurso)

```
POST /api/v1/mdfe/{chaveAcesso}/encerramento?uf=SP&ambiente=HOMOLOGACAO&numeroProtocolo=...&codigoMunicipioEncerramento=3304557&dataEncerramento=2026-03-20
```

Evento `tpEvento=110112`, serviço `MDFeRecepcaoEvento`. `dataEncerramento` é opcional
(default: data atual). O município de encerramento é informado pelo chamador — pode
diferir do município de descarga previsto na emissão, se a viagem terminar em local
diferente do planejado.

```json
{
  "chaveAcesso": "...",
  "encerrado": true,
  "codigoStatusSefaz": "135",
  "motivoSefaz": "Evento registrado e vinculado ao MDF-e",
  "damdfePdfBase64": "<DAMDFE reimpresso com aviso de encerramento>",
  "mensagemErro": null,
  "categoriaErro": null
}
```

Um encerramento aceito é **registrado internamente** (tabela `mdfe_encerramento`) —
usado para (a) bloquear cancelamento depois do encerramento (seção 6) e (b) refletir
`encerrado=true` na consulta (seção 4), já que a SEFAZ não devolve esse status.

`damdfePdfBase64` aqui é um **DAMDFE reimpresso** com o aviso "MDF-e ENCERRADO" e a
data/município de encerramento — reconstruído a partir do XML assinado já arquivado na
emissão (este endpoint só recebe a chave de acesso, não o objeto original).

**Validação de entrada (FIS-58)**: `numeroProtocolo` e `codigoMunicipioEncerramento`
devem conter só dígitos (HTTP 400 caso contrário) - são concatenados diretamente no XML
do evento de encerramento, que é assinado digitalmente logo em seguida.

## 6. Cancelamento

```
POST /api/v1/mdfe/{chaveAcesso}/cancelamento?uf=SP&ambiente=HOMOLOGACAO&numeroProtocolo=...&justificativa=...
```

Bloqueado (**HTTP 422**) em duas situações, verificadas **nesta ordem**:

1. **Já encerrado** (`MdfeJaEncerradoException`) — cancelamento só é permitido antes do
   encerramento do manifesto; depois disso o documento já cumpriu seu propósito
   perante o fisco.
2. **Prazo legal vencido**: mais de **24 horas** desde a autorização (Ajuste SINIEF
   21/2010) — o prazo mais curto entre os quatro documentos deste adapter (NFC-e 30
   min, MDF-e 24h, NF-e variável por UF, CT-e 168h). A condição adicional do Ajuste
   ("desde que o transporte ainda não tenha iniciado") não é verificável localmente —
   fica a cargo da própria SEFAZ rejeitar se for o caso.

## 7. DAMDFE

`damdfePdfBase64` — PDF A4 retrato, código de barras (Code128) da chave de acesso,
emitente, veículo (placa, tara, tipo rodado/carroceria, RNTRC) e motorista(s), percurso
(carregamento, UFIni→UFFim, descarga prevista) e documentos fiscais vinculados
(CT-e/NF-e, valor da carga, peso bruto). Indicação de encerramento (seção 5) aparece só
na reimpressão do endpoint de encerramento — o PDF da emissão nunca traz esse aviso, já
que a viagem ainda não terminou nesse momento.

## 8. Estrutura SOAP (referência técnica)

**Infraestrutura 100% centralizada na SVRS** (diferente do CT-e, onde 5 UFs têm
endpoint próprio) — as 27 UFs delegam para o mesmo endereço. Mesma estrutura do CT-e:
header `mdfeCabecMsg`, corpo `mdfeDadosMsg`, autorização em gzip+base64, consulta/
evento em texto puro. Evento de cancelamento usa `nSeqEvento` com **2 dígitos**
("01"), diferente do padrão de 3 dígitos da NF-e/CT-e.

> **Atenção (não verificável nesta base)**: uma fonte secundária sugere que o binding
> específico do `MDFeRecepcaoSinc` não declara `mdfeCabecMsg` no WSDL — este adapter
> segue a implementação de referência, que envia o header uniformemente.

## 9. Referências legais

- Ajuste SINIEF 21/2010 — institui o MDF-e, prazo de cancelamento (24h).
- NT 2024.001 — desativação do modo em lote/assíncrono.
- Manual de Orientação do Contribuinte (MOC) do MDF-e, leiaute 3.00.
