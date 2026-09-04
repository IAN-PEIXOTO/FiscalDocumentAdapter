# FiscalDocumentAdapter — Documentação de Emissão de Documentos Fiscais

Esta pasta reúne a documentação de integração do **FiscalDocumentAdapter**, organizada
no espírito do **Manual de Orientação do Contribuinte (MOC)** que a SEFAZ publica para
cada documento fiscal — visão geral, regras de negócio, leiaute dos campos principais,
eventos, códigos de rejeição e webservices — mas em **nível de integração de API**: cobre
o que um desenvolvedor precisa para integrar com este adapter, não repete o schema XSD
oficial campo a campo (esse já está bundlado em `src/main/resources/xsd` e é a fonte
normativa real).

Cada manual documenta **o que este adapter de fato implementa hoje**, com limitações e
decisões de escopo declaradas explicitamente — nunca finge cobertura que não existe.

## Documentos cobertos

| Documento | Modelo | Manual |
|---|---|---|
| Nota Fiscal Eletrônica | NF-e, modelo 55 | [manual-nfe.md](manual-nfe.md) |
| Nota Fiscal de Consumidor Eletrônica | NFC-e, modelo 65 | [manual-nfce.md](manual-nfce.md) |
| Conhecimento de Transporte Eletrônico | CT-e, modelo 57 | [manual-cte.md](manual-cte.md) |
| Manifesto Eletrônico de Documentos Fiscais | MDF-e, modelo 58 | [manual-mdfe.md](manual-mdfe.md) |
| Nota Fiscal de Serviços Eletrônica | NFS-e (padrão ABRASF) | [manual-nfse.md](manual-nfse.md) |

## 1. Autenticação

A API usa **OAuth2 client credentials** (`AuthorizationServerConfig`), no mesmo padrão
usado pela API paga da ACBr — servidor de autorização próprio, sem depender de terceiro.

```
POST /oauth2/token
Authorization: Basic base64(client_id:client_secret)
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&scope=nfe
```

Resposta: um JWT (`access_token`) assinado com uma chave RSA 2048 gerada em memória a
cada subida da aplicação, válido por **15 minutos**. Todo endpoint de negócio exige
`Authorization: Bearer <access_token>` e o scope `nfe` — único scope existente no
sistema, cobrindo todos os tipos de documento (o nome "nfe" é histórico, não limita o
escopo a NF-e). `client_id`/`client_secret` são aceitos tanto via Basic Auth quanto no
corpo da requisição (`client_secret_basic`/`client_secret_post`).

**Como obter um `client_id`/`client_secret`**: não há um endpoint público de
autoatendimento para isso — `ClienteApiService.cadastrar(nome)` é chamado
programaticamente (em `dev`, `BootstrapClienteDevConfig` cadastra um cliente
automaticamente ao subir a aplicação e loga as credenciais uma única vez). Provisionar
um novo cliente em homologação/produção é responsabilidade de quem opera o ambiente.

> **Atenção (débito técnico documentado no próprio código)**: o par de chaves RSA do
> servidor de autorização é gerado em memória a cada *restart* — tokens emitidos antes
> de um reinício ficam inválidos depois, e múltiplas instâncias em produção teriam
> chaves diferentes entre si. Antes de produção real, trocar por uma chave persistida
> (variável de ambiente / secret manager).

## 2. Certificado digital do emissor

Documentos fiscais eletrônicos exigem assinatura com certificado digital ICP-Brasil
(e-CNPJ ou e-CPF, A1). O certificado é registrado **uma vez** e reaproveitado em todas
as emissões seguintes daquele CNPJ — o cliente não reenvia o `.p12` a cada chamada.

```
POST /api/v1/certificados
Content-Type: multipart/form-data
Authorization: Bearer <access_token>

certificado=<arquivo .p12>
senhaCertificado=<senha do certificado>
```

Resposta (`CertificadoRegistradoResponse`): `cnpj`, `subjectDn`, `validoDe`, `validoAte`.

```
DELETE /api/v1/certificados/{cnpj}
```

**Resolução automática do certificado**: cada endpoint de emissão/consulta/evento
descobre sozinho qual certificado usar — pelo CNPJ do emitente presente no próprio
payload (NF-e/NFC-e/CT-e/MDF-e) ou extraído da chave de acesso de 44 dígitos (consultas
e eventos pós-emissão, via `ChaveAcessoService.cnpjEmitente`). Para NFS-e, que não tem
chave de acesso, o certificado é resolvido pelo CPF/CNPJ do prestador informado
explicitamente no request.

**Multi-tenant**: o CNPJ do certificado fica vinculado ao `client_id` que o registrou
primeiro (`AutorizacaoEmissorService`) — nenhum outro `client_id` consegue emitir,
consultar ou remover o certificado desse CNPJ, mesmo conhecendo o número.

**Erros possíveis**:

| Situação | Exceção | HTTP |
|---|---|---|
| `.p12`/senha inválidos, ou certificado vencido | `CertificadoInvalidoException` | 400 |
| Nenhum certificado registrado para o CNPJ resolvido | `CertificadoNaoEncontradoException` | 404 |
| CNPJ pertence a outro `client_id` | `EmissorNaoAutorizadoException` | 403 |

## 3. Idempotência

Todo endpoint de **emissão** (`POST /api/v1/nfe`, `/nfce`, `/cte`, `/mdfe`,
`/nfe/assincrono`) exige o header:

```
Idempotency-Key: <string única escolhida pelo cliente>
```

`IdempotenciaService` (chave única `(client_id, tipo_operacao, Idempotency-Key)`) garante
que reenviar a mesma chave devolve a **mesma resposta já processada**, em vez de emitir o
documento de novo — protege contra timeout de rede seguido de retry do lado do cliente.
`tipo_operacao` (migration V11) evita que a mesma `Idempotency-Key` reusada entre, por
exemplo, `POST /api/v1/nfe` e `POST /api/v1/nfce` colida e devolva a resposta do endpoint
errado. Se a mesma chave chegar **enquanto a primeira chamada ainda está em
processamento**, a segunda recebe `RequisicaoEmProcessamentoException` (HTTP 409) em vez
de esperar ou duplicar.

A janela de deduplicação é de **24 horas**: dentro desse período, a mesma chave sempre
devolve a resposta já cacheada (criptografada em repouso); depois de expirar, a mesma
`Idempotency-Key` pode ser reenviada e é tratada como uma requisição nova. O enfileiramento
assíncrono (`POST /api/v1/nfe/assincrono`) usa sua própria checagem por
`(client_id, Idempotency-Key)` — reenviar devolve o `id` do job já existente.

Header ausente onde é obrigatório → `MissingRequestHeaderException` (HTTP 400).

## 4. Ambientes (homologação / produção)

Todo payload de emissão traz um campo `"ambiente"` com o valor **minúsculo**
`"homologacao"` ou `"producao"` (`@Pattern(regexp = "homologacao|producao")`); todo
`@RequestParam` de consulta/evento usa o enum `TipoAmbiente` (`HOMOLOGACAO`/`PRODUCAO`,
maiúsculo, códigos SEFAZ 2/1 respectivamente). A aplicação em si roda em um dos três
profiles Spring (`dev`/`homolog`/`prod`, `SPRING_PROFILES_ACTIVE`) — isso é independente
do campo `ambiente` do payload, que indica a qual ambiente **da SEFAZ** aquele documento
específico deve ser transmitido.

## 5. Formato de erro padrão

Toda resposta de erro segue o mesmo formato (`ErroResposta`):

```json
{
  "mensagem": "Descrição legível do erro",
  "detalhes": ["lista opcional de detalhes, ex.: um por campo inválido"]
}
```

Tabela completa de exceções tratadas por `GlobalExceptionHandler` (a mais específica
aplicável é sempre usada; qualquer coisa fora desta lista cai no genérico 500):

| Situação | HTTP | Observação |
|---|---|---|
| Campo obrigatório ausente/inválido no JSON (`@Valid`) | 400 | `detalhes` tem um item por campo (`campo: mensagem`) |
| `RegraNegocioVioladaException` (RVN, ver cada manual) | 422 | `detalhes` lista `RVN-XXX: mensagem` |
| `XmlInvalidoException` (XML não bate com o XSD oficial) | 422 | `detalhes` lista os erros de schema |
| `CertificadoInvalidoException` | 400 | |
| `CertificadoNaoEncontradoException` | 404 | |
| `EmissorNaoAutorizadoException` (CNPJ de outro `client_id`) | 403 | |
| `AssinaturaDigitalException` | 422 | |
| `IllegalArgumentException` (genérica — inclui "endpoint não cadastrado para este município" em NFS-e) | 400 | |
| Corpo da requisição malformado (JSON inválido) | 400 | |
| Header obrigatório ausente (`Idempotency-Key`, etc.) | 400 | |
| `RequisicaoEmProcessamentoException` (mesma `Idempotency-Key` em voo) | 409 | |
| `NumeracaoIndisponivelException` (número já reservado) | 409 | |
| `PrazoCancelamentoNfceExpiradoException` / `...CteExpiradoException` / `...MdfeExpiradoException` | 422 | prazo legal de cancelamento vencido |
| `CteJaManifestadoEmMdfeException` | 422 | CT-e já incluído em MDF-e |
| `MdfeJaEncerradoException` | 422 | MDF-e já encerrado |
| `ConsultaDistribuicaoDfeMuitoFrequenteException` | 429 | menos de 1h desde a última consulta de NF-e destinadas |
| Limite de requisições excedido (rate limit) | 429 | ver seção 6 |
| `SefazComunicacaoException` (SEFAZ/prefeitura inacessível) | 502 | logado como erro de infraestrutura |
| Qualquer erro não mapeado | 500 | logado, corpo genérico ("Erro interno ao processar a requisicao") |

## 6. Rate limiting

`RateLimitFilter`: **60 requisições por minuto por `client_id`**, janela fixa de 1
minuto, contador em memória. Ao exceder: HTTP 429 com
`{"mensagem":"Limite de requisicoes excedido para este client_id"}`.

> **Limitação conhecida**: contador em memória de uma única instância — não sobrevive a
> múltiplas instâncias atrás de um load balancer (cada instância aplicaria seu próprio
> limite, multiplicando o limite real). Migrar para um contador compartilhado (Redis)
> antes de escalar horizontalmente.

## 7. Emissão assíncrona e webhook (hoje só para NF-e)

Além do modo síncrono (a chamada bloqueia até a SEFAZ responder), a NF-e tem um modo
assíncrono via fila:

```
POST /api/v1/nfe/assincrono         (mesmo corpo do POST /api/v1/nfe, mesmo header Idempotency-Key)
→ 202 Accepted  {"id": 123, "status": "PENDENTE"}

GET /api/v1/nfe/assincrono/{id}
→ 200  {"id": 123, "status": "PENDENTE|PROCESSANDO|CONCLUIDA|FALHA", "resultado": <NfeResponse ou null>, "erroMensagem": "..."}
```

Um worker (`EmissaoAssincronaWorker`, poll a cada 5s por padrão) processa a fila em
segundo plano pelo mesmo pipeline do endpoint síncrono. Falha de comunicação com a SEFAZ
(`SefazComunicacaoException`) é reagendada automaticamente com backoff exponencial (30s,
60s, 120s... até 5 tentativas) antes de marcar `FALHA`; qualquer outro erro (RVN violada,
dado inválido) falha na primeira tentativa.

**Webhook** — notificação automática quando o job termina:

```
PUT /api/v1/webhook   {"url": "https://seu-endpoint/..."}
→ devolve um secret novo (uma única vez — guardado criptografado, nunca mais exibido)

GET /api/v1/webhook   → devolve a URL cadastrada (204 se nenhuma)
```

Cada notificação (`tipo`: `nfe.autorizada`, `nfe.rejeitada` ou `nfe.falha`) é assinada no
header `X-Fiscaladapter-Signature: sha256=<hex>` (HMAC-SHA256 do corpo com o secret,
mesmo padrão GitHub/Stripe) e carrega um `eventoId` (UUID) para deduplicação do lado do
consumidor. Entrega é *best-effort* (3 tentativas, backoff curto, sem fila de
redelivery) — por isso o `GET /api/v1/nfe/assincrono/{id}` sempre existe como forma
confiável de saber o resultado, independente do webhook chegar.

**NFC-e/CT-e/MDF-e/NFS-e não têm modo assíncrono** — NFC-e porque a SEFAZ só aceita lote
de um único documento (não há ganho em enfileirar); CT-e/MDF-e/NFS-e simplesmente ainda
não tiveram esse modo implementado.

## 8. Retenção e recuperação do XML assinado

Todo documento **autorizado** (ou liberado via EPEC, no caso da NF-e) é arquivado
criptografado (AES-256-GCM) por tempo indeterminado — sem rotina de exclusão, o que já
satisfaz a retenção legal mínima de 5 anos. Documentos rejeitados não são arquivados.

```
GET /api/v1/documentos/{chaveAcesso}
```

Recupera o XML assinado — restrito ao `client_id` dono do CNPJ emissor.

**Validação de entrada (FIS-59)**: todo endpoint que recebe `{chaveAcesso}` como parte
do path (consulta, cancelamento, eventos de NFe/NFC-e/CT-e/MDF-e e este próprio) valida
o formato (44 dígitos numéricos) antes de qualquer processamento, devolvendo HTTP 400
com mensagem clara para uma chave malformada.

## 9. NF-e destinadas e manifestação do destinatário

Ver detalhes completos no [manual-nfe.md § 7.5](manual-nfe.md#75-manifestação-do-destinatário-e-nf-e-destinadas)
— aplicável apenas a NF-e (modelo 55).

## 10. Versionamento e descoberta

- **`GET /api/versao`** (público, sem autenticação) —
  `{"versaoApi": "v1", "layoutsDocumentosFiscais": {"NFE": "...", "NFCE": "...", "CTE": "...", "MDFE": "..."}, "padroesNfseSuportados": [...]}`.
  `NFCE` reflete a mesma versão de layout da NFe (reaproveita o mesmo gerador XML). Serve
  para um integrador confirmar programaticamente a versão de leiaute em uso nesta
  implantação, útil quando a SEFAZ ou uma prefeitura anunciar mudança de schema.
- **`GET /health`** (público) — `{"status": "UP"}`, health check simples sem detalhes de
  dependências.
- **`GET /actuator/info`** (público) — versão de build da aplicação (populada do
  `pom.xml` via `spring-boot-maven-plugin`).
- **`GET /v3/api-docs`** / **`GET /swagger-ui.html`** (públicos, geram a especificação a
  partir do código) — o Bearer token continua exigido para de fato chamar os endpoints.
- Estratégia de versionamento: por path (`/api/v1/...`); mudanças que quebram
  compatibilidade justificam um futuro `/api/v2` convivendo em paralelo — não existe
  ainda porque não há ainda uma mudança que o exija.

## Referências e limitações gerais

- Cada manual cita a fonte legal (Ajuste SINIEF, Nota Técnica) de cada prazo/regra
  mencionada, na medida em que foi possível verificar nesta base de conhecimento.
- Descrições de código de rejeição e comportamento de webservice foram conferidas contra
  implementações de referência da comunidade (`nfephp-org/sped-nfe`, `sped-cte`,
  `sped-mdfe`) quando não havia acesso direto ao PDF oficial do MOC — cada manual marca
  explicitamente os pontos "não verificáveis" que merecem confirmação contra homologação
  real antes do primeiro uso em produção.
- Nenhum manual aqui substitui a leitura do Manual de Orientação do Contribuinte oficial
  de cada documento, disponível no Portal Nacional da NF-e/CT-e/MDF-e — eles documentam
  **como usar este adapter**, não redefinem a legislação fiscal.
