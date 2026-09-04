# Manual de Integração — NFC-e (Nota Fiscal de Consumidor Eletrônica, modelo 65)

> Ver [00-visao-geral.md](00-visao-geral.md) para autenticação, certificado digital,
> idempotência, ambientes, formato de erro e demais aspectos comuns a todos os
> documentos. Este manual cobre apenas o que é específico da NFC-e.

## 1. Visão geral

A NFC-e é o documento fiscal eletrônico de venda ao consumidor final no varejo,
modelo **65**, regida pela NT 2015.002 e pelo Ajuste SINIEF 07/18. Ela **reaproveita
quase toda a estrutura da NF-e** neste adapter: mesmo modelo de domínio
(`NotaFiscalEletronica`), mesmo gerador/validador de XML (`NfeXmlGenerator`/
`NfeXsdValidator`, que já leem o `tipoDocumento` para gerar `mod=65` corretamente), e o
mesmo payload JSON de entrada. As diferenças ficam só no que é conceitualmente distinto:
consumidor não identificado, QR Code de consulta pública, DANFE em formato cupom e
prazo de cancelamento mais curto.

## 2. Fluxo de emissão

```
POST /api/v1/nfce
Authorization: Bearer <access_token>
Idempotency-Key: <chave única do cliente>
Content-Type: application/json
```

**Payload: exatamente o mesmo schema da NF-e** (`NfePedidoEmissaoRequest`, ver
[manual-nfe.md § 3.1](manual-nfe.md#31-estrutura-do-payload)), com uma diferença de
uso: **`infNFe.dest` é opcional** — venda ao consumidor final permite não identificar o
comprador (o XSD oficial também trata `dest` como `minOccurs="0"` para NFC-e).

Pipeline (`NfceEmissaoService`): mapeamento → RVN (mesmas 6 regras da NF-e, seção 3) →
certificado → chave de acesso (`mod=65`) → geração do XML → assinatura → **inserção do
QR Code online** no XML já assinado (seção 4) → validação XSD → autorização
**síncrona** → numeração → retenção → DANFE (seção 5).

**Só o modo síncrono existe** — é o único usado na prática para NFC-e: a SEFAZ rejeita
lote assíncrono de um único documento (`indSinc=0`) desde a NT 2025.001, cStat 452. Não
há, portanto, um `POST /api/v1/nfce/assincrono` análogo ao da NF-e.

## 3. Regras de Negócio (RVN)

As mesmas 6 regras aplicadas à NF-e (`RegraNegocioService` opera sobre
`NotaFiscalEletronica`, sem distinguir NFC-e) — ver
[manual-nfe.md § 5](manual-nfe.md#5-regras-de-negócio-rvn). RVN-003 (CFOP × operação
interna/interestadual) sempre resolve para "interna" quando não há destinatário.

## 4. QR Code de consulta pública

Estrutura verificada contra o XSD oficial (`infNFeSupl/qrCode`) e a implementação de
referência `nfephp-org/sped-nfe` — versão 3 (NT 2025.001 v1.00, vigente desde
março/2025; a versão 2, baseada em CSC/token secreto por UF, não é implementada).

**Emissão online** (o caso comum, `tpEmis` 1/3/4): conteúdo gerado **sem nenhum
segredo** — só chave de acesso, versão e ambiente:

```
<url-de-consulta>?p=<chaveAcesso>|3|<códigoAmbiente>
```

A resposta da emissão traz `conteudoQrCode` (a string acima, pronta para virar imagem)
e `urlConsultaPublica` (resolvida por UF/ambiente — cada UF expõe um portal HTTP
próprio, não um webservice SOAP).

> **Emissão offline/contingência (tpEmis=9) não está conectada ao pipeline**: o método
> que gera esse conteúdo (assinatura RSA/SHA-1 com a própria chave privada do emissor,
> permitindo validação do QR Code sem contato com a SEFAZ) já existe no código
> (`NfceQrCodeService.gerarConteudoOffline`), mas exige uma decisão explícita do PDV
> (guardar o XML localmente e retransmitir depois) — fora do escopo implementado hoje,
> registrado como débito técnico. Uma falha de comunicação com a SEFAZ propaga hoje
> como erro HTTP 502, não como contingência automática.

## 5. Consulta, cancelamento e DANFE

**Não há endpoints próprios de consulta/cancelamento** — a NFC-e reusa os mesmos da
NF-e, já que os clientes SOAP são agnósticos de modelo de documento:

```
POST /api/v1/nfe/{chaveAcesso}/consulta?uf=SP&ambiente=HOMOLOGACAO
POST /api/v1/nfe/{chaveAcesso}/cancelamento?uf=SP&ambiente=HOMOLOGACAO&numeroProtocolo=...&justificativa=...
```

O controller detecta que a chave é de NFC-e pelos dígitos 20-22 (`mod=65`,
`ChaveAcessoService.modeloDocumento`) e, só nesse caso, aplica uma checagem adicional de
prazo **antes** de tentar o cancelamento: consulta a SEFAZ para obter a data/hora real
de autorização e bloqueia com **HTTP 422** se já passaram mais de **30 minutos**
(Ajuste SINIEF 07/18 — reduziu o prazo anterior de 24h; alguns estados adotam prazo
ainda menor, nunca maior). Isso evita gastar uma tentativa que a SEFAZ rejeitaria de
qualquer forma.

### Resposta da emissão

```json
{
  "chaveAcesso": "35260144888999000122650010000005031...",
  "xmlAssinado": "<NFe>...<infNFeSupl>...</infNFeSupl></NFe>",
  "autorizada": true,
  "codigoStatusSefaz": "100",
  "motivoSefaz": "Autorizado o uso da NF-e",
  "numeroProtocolo": "135260000000001",
  "conteudoQrCode": "https://www.homologacao.nfce.fazenda.sp.gov.br/qrcode?p=...|3|2",
  "urlConsultaPublica": "https://www.homologacao.nfce.fazenda.sp.gov.br/qrcode",
  "danfePdfBase64": "<PDF em base64, ou null se rejeitada>",
  "mensagemErro": null,
  "categoriaErro": null
}
```

### DANFE NFC-e (formato cupom)

`danfePdfBase64` traz um PDF em **layout compacto tipo cupom**, pensado para
impressoras térmicas de PDV — diferente do DANFE A4 retrato/paisagem da NF-e:

- **Largura fixa de 80mm** (o padrão mais comum de impressora térmica de PDV no
  Brasil), altura fixa e generosa (bobina contínua não tem "fim de página" real, mas o
  gerador de PDF exige uma dimensão numérica).
- **QR Code embutido como imagem** (não como texto) — renderizado a partir do
  `conteudoQrCode` já calculado.
- **Indicação de contingência** em destaque (vermelho) quando aplicável — hoje sempre
  `false` no pipeline atual, já que a NFC-e só gera DANFE para documento efetivamente
  autorizado (a contingência offline, seção 4, não está implementada).

## 6. Não reusa a contingência da NF-e

`EmissaoNfeOrquestrador` (fallback SVC-AN → EPEC da NF-e, ver
[manual-nfe.md § 4](manual-nfe.md#4-contingência-transmissão-à-sefaz)) é **exclusivo da
NF-e** e não é usado aqui. A contingência específica da NFC-e é o modo offline
(seção 4), não implementado — uma falha de comunicação com a SEFAZ propaga como HTTP
502 em vez de failover automático.

## 7. Catálogo de rejeições

Mesmo catálogo da NF-e — ver
[manual-nfe.md § 10](manual-nfe.md#10-catálogo-de-códigos-de-rejeição).

## 8. Referências legais

- Ajuste SINIEF 07/18 — institui/disciplina a NFC-e, prazo de cancelamento de 30 min.
- NT 2015.002 — leiaute da NFC-e.
- NT 2025.001 — QR Code versão 3; desativação do modo assíncrono de lote único.
