# FiscalDocumentAdapter

[![CI](https://github.com/IAN-PEIXOTO/FiscalDocumentAdapter/actions/workflows/ci.yml/badge.svg)](https://github.com/IAN-PEIXOTO/FiscalDocumentAdapter/actions/workflows/ci.yml)

Adapter fiscal gratuito: recebe JSON (no mesmo formato da API ACBr) e emite documentos fiscais eletronicos diretamente na SEFAZ, sem depender do ACBr.

## Rodando localmente

```bash
./mvnw spring-boot:run
```

## Testes

```bash
./mvnw test
```

Todos os testes rodam sem depender de acesso a internet, ao ambiente de
homologacao da SEFAZ ou de um certificado ICP-Brasil real. Cada cliente SOAP
(`NfeAutorizacaoClient`, `NfeCancelamentoClient`, `NfeCceClient`,
`NfeInutilizacaoClient`, `NfeEpecClient`, `NfeManifestacaoDestinatarioClient`)
tem seu proprio teste contra um servidor SOAP local com mTLS
(`ServidorSoapDeTeste`), e `NfeEmissaoPontaAPontaTest` exercita o fluxo
completo de ponta a ponta (POST /api/v1/nfe -> geracao/assinatura/validacao
XSD do XML -> SOAP com mTLS -> DANFE) contra esse mesmo servidor local,
substituindo apenas o endereco do webservice e a fabrica de HttpClient - o
resto (mapeamento, RVN, XML, assinatura, criptografia, banco) e o codigo de
producao de verdade. Esse e o padrao a seguir para testar mudancas sem
depender do ambiente real da SEFAZ.

Testes de ponta a ponta contra o ambiente de homologacao real da SEFAZ (com
certificado digital valido e CNPJ cadastrado) nao estao incluidos aqui -
exigem credenciais que so quem for rodar isso em homologacao de verdade tem.

## Bibliotecas e ferramentas de apoio (FIS-22)

Referencia do que este projeto efetivamente usa (ou deliberadamente nao usa)
em cada area tecnica generica - assinatura XML, comunicacao SOAP, PDF, banco,
testes etc. Objetivo: as demais historias tecnicas apontam para esta secao
em vez de decidir biblioteca de novo a cada vez. Cada linha reflete o que
esta de fato no `pom.xml`/codigo hoje, nao uma lista de intencoes.

**API REST / Framework** - adotado como sugerido: Spring Boot (`spring-boot-starter-web`),
Jackson (vem com o starter). **springdoc-openapi (Swagger) nao adotado ainda**
- nenhum endpoint publico documentado automaticamente ate agora; considerar
quando a API tiver consumidores externos alem dos testes.

**XML (geracao e validacao)** - **JAXB/xjc NAO adotado**, apesar de sugerido
(a dependencia `jaxb-runtime` chegou a ser adicionada e foi removida no
FIS-22 por estar sem uso). Na pratica, os documentos sao construidos a mao
com `javax.xml.stream.XMLStreamWriter` (ver `NfeXmlGenerator`, `CteXmlGenerator`,
`MdfeXmlGenerator`, `AbrasfNfseXmlGenerator`) - decisao pragmatica: os XSDs
oficiais (principalmente o de CT-e/MDF-e) sao grandes e cheios de grupos
opcionais que xjc geraria mas que este adapter nao usa na sua primeira
versao; escrever o XML diretamente da o mesmo resultado com menos
classes geradas para manter. Validacao estrutural usa `javax.xml.validation`
(nativo do JDK) direto contra os XSDs oficiais bundlados em `resources/xsd`
- ver `NfeXsdValidator`, `CteXsdValidator`, `MdfeXsdValidator`, `AbrasfXsdValidator`.

**Assinatura digital (XML-DSig)** - **Apache Santuario NAO adotado** (mesma
razao do JAXB: a dependencia `xmlsec` foi adicionada e removida no FIS-22
por estar sem uso). `AssinaturaXmlService` usa `javax.xml.crypto.dsig`
(JSR-105), que ja vem no JDK e cobre exatamente o que a SEFAZ exige
(enveloped signature, C14N, RSA-SHA1) sem dependencia externa. Bouncy Castle
(`bcpkix-jdk18on`) e usado para leitura de certificados PKCS#12 (`CertificadoDigitalService`)
e para gerar certificados de teste em memoria (`TestCertificadoFactory`).

**Comunicacao SOAP com SEFAZ/prefeituras** - **Apache CXF/JAX-WS NAO adotado**.
Os webservices da SEFAZ e das prefeituras seguem um envelope simples e
estavel (ver `SoapClient` para NFe/CTe/MDFe, `AbrasfSoapClient` para NFS-e);
montar o envelope como string e usar `java.net.http.HttpClient` (com mTLS
via `SefazHttpClientFactory`) evita a complexidade de um stack JAX-WS
completo (geracao de stubs a partir de WSDL, etc.) para um contrato que
raramente muda. **Resilience4j NAO adotado** - o retry/contingencia de NFe
(normal -> SVC -> EPEC, ver `EmissaoNfeOrquestrador`) e implementado a mao;
revisitar se mais fluxos precisarem do mesmo padrao de retry/circuit
breaker e a duplicacao começar a doer.

**DANFE / DACTE / DAMDFE (PDF)** - OpenPDF adotado como sugerido
(`DanfeGenerator`). **ZXing NAO adotado** - o conteudo do QR Code da NFC-e
e gerado como string (`NfceQrCodeService`), mas a renderizacao como imagem
de barras propriamente dita (para colar no PDF do DANFE NFC-e) ainda nao
foi implementada - fica para o FIS-47 (Geracao do DANFE NFC-e), que e onde
ZXing entraria.

**Banco de dados** - adotado como sugerido: Spring Data JPA + Hibernate,
Flyway. H2 em uso tanto em dev quanto nos testes (nao ha Postgres
configurado ainda).

**Seguranca** - Spring Security + OAuth2 (authorization server proprio,
`client_id`/`client_secret`) adotado como sugerido. **Jasypt NAO adotado**
- criptografia de certificados/segredos em repouso usa AES-256-GCM via
`javax.crypto` puro (`CriptografiaEmRepousoService`, FIS-14), evitando mais
uma dependencia para uma operacao que o JDK ja cobre bem.

**Testes** - JUnit 5 + Mockito adotado como sugerido (vem com
`spring-boot-starter-test`). **Testcontainers NAO adotado** - os testes
usam H2 em memoria em vez de Postgres em container; suficiente enquanto o
banco de producao alvo nao estiver definido. **WireMock NAO adotado** -
`ServidorSoapDeTeste` (HTTPS local com mTLS real via BouncyCastle) e usado
no lugar dele, porque o ponto mais fragil dos clientes SOAP daqui e
justamente o handshake mTLS com o certificado do emissor, que o WireMock
padrao nao exercita da mesma forma.

**Observabilidade** - Micrometer + Prometheus adotado como sugerido
(`NfeEmissaoMetrics`, FIS-11), Logback com correlacao via MDC
(`MdcRequisicaoFilter`, `MdcChaveAcesso`). **OpenTelemetry NAO adotado
ainda** - tracing distribuido nao e critico enquanto o fluxo
JSON->XML->SEFAZ roda dentro de um unico processo; revisitar se a
arquitetura ganhar mais servicos separados (ex.: fila assincrona do FIS-30).

**CI/CD** - Maven, GitHub Actions adotado como sugerido. Testcontainers/Docker
para ambiente reproduzivel de integracao nao se aplica ainda (ver "Testes" acima).

## NFS-e (FIS-20)

Diferente de NFe/NFC-e/CT-e/MDF-e, a NFS-e **nao tem um schema XSD nacional
unico** - cada municipio e responsavel pelo seu proprio sistema de emissao,
e cada um pode (e frequentemente faz) divergir do padrao. Por isso a
estrutura de geracao aqui e deliberadamente extensivel por municipio:

- `NfseXmlGenerator` e a interface que cada padrao suportado implementa.
- `PadraoNfse` enumera os padroes conhecidos (hoje so `ABRASF_V2_01`).
- `NfseXmlGeneratorRegistry` resolve, a partir do codigo IBGE do municipio de
  prestacao do servico, qual padrao (e portanto qual gerador) usar -
  municipios sem entrada explicita em `nfse-municipios.properties` caem no
  padrao ABRASF (o mais adotado, usado por centenas de prefeituras).

**Suportado hoje:** ABRASF versao 2.01 (`AbrasfNfseXmlGenerator`), o RPS
(Recibo Provisorio de Servicos) no formato `GerarNfseEnvio`, validado contra
o XSD oficial da ABRASF (Associacao Brasileira das Secretarias de Financas
das Capitais). Cobre os campos obrigatorios do RPS (identificacao,
prestador, servico, valores, tomador opcional) - nao cobre ainda:
retencoes detalhadas (PIS/COFINS/INSS/IR/CSLL), construcao civil,
intermediario, regime especial de tributacao, RPS de substituicao, nem o
envio em lote (`EnviarLoteRpsEnvio` - o `GerarNfseEnvio` usado aqui e a
via de envio individual e sincrona).

**Plano de expansao:** municipios que usam ABRASF v2.01 sem customizacoes
proprias funcionam sem nenhuma configuracao adicional. Um municipio com
variacao propria (schema diferente, campos extras obrigatorios, ou um
padrao totalmente distinto como GINFES/DSF) precisa de um novo
`NfseXmlGenerator` mapeado no `nfse-municipios.properties` - a comunicacao
com os webservices municipais propriamente dita (que variam por prefeitura)
fica para o FIS-21.

## Comunicacao com webservices municipais de NFS-e (FIS-21)

`AbrasfNfseClient` (pacote `sefaz.nfse`) envia o RPS gerado pelo FIS-20 para
o webservice da prefeitura (geracao/`GerarNfseEnvio`), consulta uma NFS-e ja
emitida a partir do RPS que a originou (`ConsultarNfseRpsEnvio`) e cancela
uma NFS-e (`CancelarNfseEnvio`) - as tres operacoes citadas no criterio de
aceite. Mesmo estilo dos clientes SOAP da SEFAZ estadual (`sefaz.nfe`):
mTLS com o certificado do prestador (`SefazHttpClientFactory`, reaproveitado
sem alteracoes) e interpretacao da resposta por busca de tags (mesma
tolerancia a variacao de envelope que os clientes de NFe ja usam).

Duas diferencas deliberadas em relacao ao cliente de NFe:

- **Endpoint por municipio, nao por UF.** `NfseEndpointRegistry` resolve o
  endereco a partir do codigo IBGE do municipio (`nfse-webservices.properties`),
  nao de uma UF. Diferente da NFe (uma lista nacional unica e publica por
  UF, `ACBrNFeServicos.ini`), **nao existe um catalogo publico confiavel de
  webservices de NFS-e por municipio** - cada prefeitura contrata sua propria
  plataforma e o endpoint so e conhecido durante o credenciamento do
  contribuinte junto aquela prefeitura. Por isso o properties comeca vazio:
  cada municipio atendido precisa ser cadastrado manualmente durante o
  onboarding do cliente daquele municipio especifico.
- **Assinatura digital do RPS e opcional**, ao contrario da NFe/CT-e/MDF-e
  (onde e sempre obrigatoria). O XSD da ABRASF permite `GerarNfseEnvio` sem
  `dsig:Signature` - cada prefeitura decide se exige. Por isso
  `AbrasfNfseClient` recebe o XML do RPS ja pronto (assinado ou nao,
  decisao de quem chama) em vez de assinar internamente.

Testado com o mesmo `ServidorSoapDeTeste` (mTLS local) ja usado pelos
clientes de NFe - prova o fluxo completo (handshake mTLS + envelope SOAP +
interpretacao da resposta) para as tres operacoes, sem depender de um
webservice municipal real.

## Numeracao sequencial de documentos fiscais (FIS-23)

`NumeracaoSequencialService` (pacote `numeracao`) garante que o numero de um
documento (nNF/nCT/nMDF etc.) nunca e reutilizado por engano para o mesmo
emissor/UF/serie/tipo de documento - duplicidade de numeracao e uma
violacao legal, nao so um bug.

Decisao de design: **o numero continua sendo escolhido pelo cliente** (o ERP,
no mesmo formato da API ACBr), nao gerado pelo adapter. O adapter apenas
**valida/reserva** o numero informado de forma atomica: `SequenciaDocumento`
e uma linha por numero ja utilizado (nao um contador), com uma constraint
unica em `(cnpj_emissor, uf, serie, tipo_documento, numero)` - duas
requisicoes concorrentes tentando reservar o mesmo numero disputam a mesma
linha no banco, so uma consegue inserir; a outra recebe
`NumeracaoIndisponivelException` (HTTP 409). Essa e a propria garantia de
atomicidade sob concorrencia, sem precisar de lock explicito.

A reserva so acontece quando o documento **efetivamente valeu** perante o
fisco - autorizado pela SEFAZ, ou liberado via EPEC (ver `NfeController`).
Uma submissao rejeitada nao reserva nada: o ERP pode legitimamente corrigir
e reenviar o mesmo numero, sem precisar "queimar" um numero para uma nota
que nunca foi de fato emitida. Se o ERP decidir pular para o proximo numero
em vez de corrigir, o numero pulado precisa ser formalmente inutilizado
junto a SEFAZ (`NfeInutilizacaoClient`, FIS-5) - isso e uma decisao do ERP,
nao algo que este adapter infere sozinho.

Hoje conectado apenas no fluxo de NFe (`POST /api/v1/nfe`, o unico endpoint
de emissao existente) - conectar nos futuros endpoints de NFC-e/CT-e/MDF-e
e so chamar `reservar(...)` no mesmo ponto (apos autorizacao confirmada).

## Validacao de Regras de Negocio - RVN (FIS-24)

`RegraNegocioService` (pacote `documento/nfe/rvn`) roda antes da assinatura
e do envio para a SEFAZ, para nao gastar uma tentativa de protocolo com algo
que ja sabemos que sera rejeitado. Cada regra e um bean Spring que implementa
`RegraNegocio`; a lista de regras e injetada automaticamente (adicionar uma
nova regra e so criar a classe com `@Component`, nao precisa registrar em
lugar nenhum).

Regras implementadas hoje (codigos `RVN-001` a `RVN-006`), cada uma
correspondendo a uma rejeicao real e comum da SEFAZ:

1. **RVN-001** `RegraTotalItemConsistente` - vProd = qCom * vUnCom.
2. **RVN-002** `RegraIcmsConsistente` - vICMS = vBC * pICMS / 100.
3. **RVN-003** `RegraCfopCompativelComOperacao` - CFOP comeca com 5
   (interna) ou 6 (interestadual) conforme a UF do destinatario.
4. **RVN-004** `RegraSomaPagamentosIgualTotal` - soma de vPag = vNF.
5. **RVN-005** `RegraRegimeTributarioCompativelComIcms` - emitente do
   Simples Nacional (CRT 1/2/4) so pode usar grupos CSOSN; emitente do
   Regime Normal (CRT 3) so pode usar grupos CST - uma das rejeicoes mais
   comuns na pratica (CST/CSOSN incompativel com o regime do emitente).
6. **RVN-006** `RegraDataEmissaoNaoFutura` - dhEmi nao pode ser posterior a
   data atual.

**Fora do escopo:** a SEFAZ publica centenas de regras de validacao oficiais
(a planilha "Regras de Validação de Negócios" da NFe) - cobrir todas nao e
viavel nem o objetivo aqui. O criterio de escolha foi: rejeicoes de
inconsistencia aritmetica/estrutural que dependem so dos dados da propria
nota (verificaveis sem tabelas externas de referencia, como NCM valido para
a UF ou CFOP x CST permitido por produto) - o conjunto que mais aparece na
pratica e que o adapter pode detectar com 100% de certeza antes de gastar
uma tentativa de protocolo. Os codigos `RVN-*` sao proprios (nao os codigos
numericos oficiais da SEFAZ) porque nao ha acesso a tabela oficial completa
de codigos de rejeicao para garantir alinhamento exato - ver FIS-39
(mapeamento de rejeicoes reais vindas da SEFAZ) para quando isso for
resolvido.

## Processamento assincrono e webhook (FIS-25)

Nota: esta card descreve exatamente o que as cards FIS-30 (fila assincrona)
e FIS-31 (webhook) fariam separadamente mais adiante no backlog - a pedido
do usuario, tudo foi implementado aqui de uma vez, para nao reescrever a
mesma coisa duas vezes; quando a ordem chegar em FIS-30/FIS-31, essas cards
devem apenas apontar para o que segue.

Alem do endpoint sincrono existente (`POST /api/v1/nfe`, que bloqueia ate a
SEFAZ responder), agora existe um modo assincrono:

- **`POST /api/v1/nfe/assincrono`** - mesmo corpo/formato do endpoint
  sincrono, mas retorna **202 Accepted** imediatamente com o id do job, sem
  esperar a SEFAZ. Idempotente por `(client_id, Idempotency-Key)`, igual ao
  sincrono - reenviar a mesma chave retorna o id do mesmo job em vez de
  enfileirar de novo.
- **`GET /api/v1/nfe/assincrono/{id}`** - consulta o status
  (`PENDENTE`/`PROCESSANDO`/`CONCLUIDA`/`FALHA`) e o resultado (o mesmo
  formato de `NfeResponse` do endpoint sincrono, quando `CONCLUIDA`).
- **`PUT /api/v1/webhook`** - cadastra a URL que recebe um POST quando um
  job enfileirado termina (`tipo`: `nfe.autorizada`, `nfe.rejeitada` ou
  `nfe.falha` - `nfe.cancelada` fica reservado para quando existir um
  endpoint de cancelamento de NFe, que ainda nao existe). `GET
  /api/v1/webhook` devolve a URL cadastrada (204 se nenhuma).

**Arquitetura:** `EmissaoAssincronaWorker` faz *poll* periodico
(`@Scheduled`, `fiscaladapter.assincrono.intervalo-poll-ms`, default 5s)
sobre a tabela `emissao_assincrona`, processando um lote pequeno por vez
atraves do mesmo `NfeEmissaoService` usado pelo endpoint sincrono (extraido
do `NfeController` nesta mesma mudanca, para nao duplicar o pipeline de
emissao). Sem broker externo (RabbitMQ/SQS/etc.) - simples o bastante para
o volume atual, mais direto de operar sem mais uma peca de infraestrutura;
revisitar se o volume justificar.

**Entrega do webhook e best-effort**: `WebhookNotifierService` tenta 3 vezes
com backoff curto e desiste - nao ha fila de redelivery persistente. Por
isso o GET de consulta de status sempre existe: o webhook e uma
conveniencia, nao a unica forma de saber o resultado. `http://` e aceito
alem de `https://` so para testes locais - em producao o consumidor deve
sempre cadastrar uma URL https, ja que o payload inclui chave de acesso e
status fiscal.

**Reprocessamento automatico com backoff (FIS-30):** o worker distingue
falha transitoria de falha definitiva. `SefazComunicacaoException` (timeout,
indisponibilidade) reagenda o job automaticamente (backoff exponencial: 30s,
60s, 120s... ate 5 tentativas) em vez de marcar `FALHA` na primeira
tentativa - qualquer outro erro (RVN violada, dado invalido) falha
imediatamente, ja que uma nova tentativa nao mudaria o resultado. Esgotadas
as 5 tentativas, o job vai para `FALHA` normalmente (e dispara o webhook de
`nfe.falha`, se cadastrado).

**Assinatura HMAC-SHA256 do webhook (FIS-31):** `PUT /api/v1/webhook` gera
(e retorna, uma unica vez, no corpo da resposta) um secret novo a cada
cadastro - guardado criptografado, nunca mais devolvido pela API depois
disso. Toda notificacao e assinada com esse secret e enviada no header
`X-Fiscaladapter-Signature: sha256=<hex>` (mesmo padrao GitHub/Stripe): o
consumidor recalcula o HMAC do corpo recebido e compara, para confirmar que
a notificacao realmente veio deste adapter. O payload tambem ganhou
`eventoId` (UUID), para o consumidor deduplicar notificacoes reentregues.
Clientes que cadastraram o webhook antes desse recurso existir nao tem
secret - a notificacao simplesmente sai sem o header ate recadastrarem.

## Operacao, ambientes e disaster recovery (FIS-26)

Nota: esta card descreve o que as cards FIS-32 (config por ambiente), FIS-33
(backup/DR do banco) e FIS-34 (retencao legal de XML) fariam separadamente
mais adiante no backlog - a pedido do usuario, tudo foi implementado aqui de
uma vez, mesma decisao tomada no FIS-25/FIS-30/FIS-31. Quando a ordem
chegar nessas cards, devem apenas apontar para o que segue.

### Configuracao por ambiente (FIS-32)

Tres profiles Spring (`application-dev.yml`, `application-homolog.yml`,
`application-prod.yml`), ativados via `SPRING_PROFILES_ACTIVE`:

- **dev** (default): H2 em memoria e a chave de criptografia de dev, ambos
  fixos em `application.yml` - sem nenhuma variavel de ambiente obrigatoria,
  para rodar localmente sem configuracao.
- **homolog**/**prod**: Postgres (`DATABASE_URL`/`DATABASE_USERNAME`/`DATABASE_PASSWORD`)
  e a chave de criptografia (`FISCALADAPTER_CHAVE_CRIPTOGRAFIA`) **sem
  nenhum default** - se a variavel de ambiente nao existir, a aplicacao
  falha ao subir (fail-fast) em vez de silenciosamente cair no H2 em
  memoria (perderia todos os dados a cada reinicio) ou na chave de dev
  hardcoded (falha grave de seguranca).

Essa mudanca corrigiu uma lacuna real que ja existia antes do FIS-26: os
profiles homolog/prod so mudavam `fiscaladapter.ambiente`/`tipo-ambiente`,
nunca o datasource - ou seja, um deploy em "producao" continuaria rodando
contra H2 em memoria sem avisar ninguem.

**Ambiente visivel em cada linha de log:** `logging.pattern.level` em
`application.yml` inclui `ambiente=${fiscaladapter.ambiente}` (resolvido a
partir do profile ativo antes do logback inicializar), junto de
`clientId`/`chaveAcesso` (MDC). Evita confundir logs de homolog e producao
quando agregados no mesmo lugar (ex.: mesmo dashboard/ELK). Isolamento de
certificado digital entre homolog e producao ja e garantido a nivel de
infraestrutura desde o FIS-26: cada ambiente e um deploy/banco separado
(`CertificadoEmissorService` guarda o certificado por `client_id` no banco
daquele ambiente - nao ha como um certificado de homologacao aparecer no
banco de producao sem alguem copiar os dados manualmente entre bancos
distintos).

**ATENCAO (nao verificavel nesta sessao):** as migrations Flyway
(`src/main/resources/db/migration`) foram escritas e sempre testadas contra
H2. A sintaxe usada (`GENERATED ALWAYS AS IDENTITY`, `CLOB`) segue o padrao
SQL/ANSI e deveria funcionar em Postgres, mas isso **nao foi validado de
ponta a ponta contra um Postgres real** nesta sessao (sem acesso a um
servidor Postgres neste ambiente). Antes do primeiro deploy de producao de
verdade, rodar a aplicacao com o profile `prod`/`homolog` apontando para um
Postgres de staging e confirmar que o Flyway migra e o Hibernate valida o
schema sem erro.

### Retencao legal de documentos fiscais (FIS-34)

Todo documento autorizado (ou liberado via EPEC) e arquivado
(`DocumentoFiscalArquivado`/`RetencaoDocumentoFiscalService`): o XML
assinado completo, criptografado (AES-256-GCM, mesmo padrao ja usado pela
idempotencia e pela fila assincrona), fica salvo indefinidamente - **nao ha
nenhuma rotina de exclusao**, o que ja satisfaz "retencao minima de 5 anos"
por definicao (a legislacao pede um minimo, nao um maximo). Documentos
rejeitados nao sao arquivados (nunca foram "emitidos" de fato).

`GET /api/v1/documentos/{chaveAcesso}` recupera o XML - restrito ao
client_id dono do CNPJ emissor (mesma regra multi-tenant do FIS-10, via
`AutorizacaoEmissorService`).

**Trade-off deliberado (gap conhecido do FIS-34):** o criterio de aceite do
FIS-34 pede armazenamento redundante/versionado separado do banco principal
(ex.: object storage tipo S3). O que foi implementado guarda o XML
criptografado no mesmo Postgres da aplicacao, e conta com
`scripts/backup-postgres.sh` (FIS-33, acima) para a redundancia - ou seja,
a durabilidade do arquivo fiscal depende da rotina de backup do banco estar
de fato agendada, e nao de um segundo sistema de armazenamento independente.
Essa escolha foi feita para nao introduzir mais uma dependencia de infra
(credenciais de object storage, SDK, etc.) num MVP que ja usa Postgres como
unica fonte de verdade em todo o resto do sistema. Se o volume ou uma
exigencia de auditoria justificar, migrar `RetencaoDocumentoFiscalService`
para gravar tambem (ou apenas) em object storage e uma extensao pontual,
sem mudar o contrato do `GET /api/v1/documentos/{chaveAcesso}`.

### Backup e disaster recovery do banco (FIS-33)

`scripts/backup-postgres.sh` e `scripts/restore-postgres.sh` - `pg_dump`/`pg_restore`
com as mesmas variaveis de ambiente da aplicacao (`DATABASE_URL`/`DATABASE_USERNAME`/`DATABASE_PASSWORD`),
formato `custom` (permite restore seletivo). Sem dependencia de nenhum
provedor de nuvem especifico - agendar a execucao (cron, CI agendado, etc.)
e copiar o dump gerado para o storage de retencao da empresa e
responsabilidade de quem opera o ambiente, fora do escopo deste
repositorio.

**Runbook de recuperacao de desastre (resumo):**
1. Provisionar um Postgres novo (ou identificar a instancia de destino).
2. `./scripts/restore-postgres.sh caminho/do/ultimo-backup.dump` apontando
   `DATABASE_URL`/`DATABASE_USERNAME`/`DATABASE_PASSWORD` para o banco de
   destino (o script pede confirmacao explicita antes de sobrescrever).
3. Apontar `DATABASE_URL` da aplicacao para o banco restaurado e subir a
   aplicacao normalmente - o Flyway confere que o schema restaurado bate
   com as migrations esperadas antes de aceitar trafego.
4. RPO/RTO dependem da frequencia com que o backup e executado e de quanto
   tempo leva o restore - ambos configuraveis por quem opera o ambiente,
   nao fixos no codigo. **Meta recomendada (default sugerido, ajustavel por
   quem opera producao):**
   - **RPO de ate 24h**: `pg_dump` via `scripts/backup-postgres.sh` agendado
     diariamente (ex.: cron/CI agendado fora deste repositorio).
   - **RTO de ate 2h**: tempo estimado para provisionar um Postgres novo e
     rodar `scripts/restore-postgres.sh` com o ultimo backup, ate a
     aplicacao aceitar trafego de novo - deve ser confirmado na pratica
     (um "restore drill" periodico) antes de tratar como garantido, ja que
     nunca foi cronometrado contra um banco de producao real neste
     sandbox.

## Versionamento de API e evolucao de schemas fiscais (FIS-27)

Nota: esta card descreve o que as cards FIS-35 (versionamento da API) e
FIS-36 (migracao entre versoes de schema fiscal) fariam separadamente mais
adiante no backlog - mesma decisao tomada no FIS-25/FIS-26. Quando a ordem
chegar nessas cards, devem apenas apontar para o que segue.

### Versionamento da API publica (FIS-35)

**Estrategia: versionamento por path** (`/api/v1/...`), ja em uso desde o
primeiro endpoint. Regras:

- **Mudanca aditiva** (novo campo opcional no request/response, novo
  endpoint, novo header opcional) - nao exige nova versao, entra direto em
  `/api/v1`.
- **Mudanca que quebra compatibilidade** (remover/renomear campo, mudar o
  tipo de um campo existente, mudar o significado de um campo, remover um
  endpoint) - exige `/api/v2`, com `/api/v1` continuando a funcionar em
  paralelo por um periodo de transicao (a definir quando isso realmente
  acontecer - nao ha ainda um `/api/v2` porque nao ha ainda uma mudanca que
  o justifique; criar um agora seria especulativo).
- **Descoberta de versao**: `GET /api/versao` (sem autenticacao - nao expoe
  nenhum dado fiscal) devolve a versao da API e a versao do layout de cada
  tipo de documento suportado nesta implantacao (`VersaoController`) -
  integradores podem checar programaticamente antes de gerar um documento.
  `GET /actuator/info` (tambem publico) devolve a versao de build da
  aplicacao (populada automaticamente do `pom.xml` via o goal `build-info`
  do `spring-boot-maven-plugin`).

### Evolucao de schemas fiscais (FIS-36)

A SEFAZ ja mudou a versao do layout da NFe varias vezes ao longo dos anos
(3.10 -> 4.00, por exemplo); o mesmo pode acontecer com CT-e/MDF-e, e cada
prefeitura de NFS-e evolui seu proprio padrao de forma independente. A
convivencia entre versoes de schema ja tem um precedente real e testado
neste projeto, nao e uma proposta teorica: **NFS-e ja suporta multiplos
padroes simultaneamente** via `PadraoNfse` (enum dos padroes conhecidos) +
`NfseXmlGeneratorRegistry` (resolve, por municipio, qual `NfseXmlGenerator`
usar - ver "NFS-e (FIS-20)" acima). Essa e a estrategia recomendada para
quando uma nova versao de layout de NFe/CT-e/MDF-e precisar conviver com a
atual:

1. Introduzir um enum `VersaoLayoutNfe` (ou CTe/MDFe) com as versoes
   suportadas (ex.: `V4_00`, `V5_00`).
2. Implementar um novo gerador (`NfeXmlGeneratorV5`, por exemplo) para a
   nova versao, com seu proprio XSD bundlado em `resources/xsd` (nunca
   sobrescrever o XSD da versao anterior - ambos precisam continuar
   validando o que emitiram).
3. Um registry (mesmo papel do `NfseXmlGeneratorRegistry`) resolve qual
   gerador usar - por emitente, por UF, ou por uma janela de tempo de
   transicao, dependendo de como a SEFAZ conduzir o rollout daquela vez
   (historicamente a SEFAZ costuma dar uma janela de coexistencia entre
   layouts, as vezes por UF).
4. `GET /api/versao` passa a refletir a nova versao suportada
   automaticamente (o valor vem do proprio gerador ativo).

Nenhuma mudanca de codigo especulativa foi feita agora para uma versao de
schema que nao existe ainda (isso seria trabalho morto, dificil de
verificar sem o schema real publicado) - o objetivo desta secao e deixar
documentado o caminho a seguir quando a mudanca real acontecer, apontando
para o padrao que ja existe e ja funciona no NFS-e.

**Cobertura dos criterios de aceite do FIS-36:** "suporte a mais de uma
versao simultaneamente" tem precedente real (`NfseXmlGeneratorRegistry`,
com `NfseXmlGeneratorRegistryTest` cobrindo a resolucao por padrao/municipio
- o mesmo padrao de teste se aplicaria a um futuro registry de NFe/CT-e/MDF-e).
"Monitoramento de prazo de descontinuacao anunciado pela SEFAZ" **nao foi
implementado** - nao ha hoje nenhum job/alerta automatizado acompanhando
comunicados da SEFAZ; isso e inerentemente um processo operacional (alguem
acompanhando o Portal Nacional da NFe/notas tecnicas), nao algo que o
codigo possa antecipar sem um schema real e um prazo real publicados. Fica
registrado como gap conhecido, nao como "feito".

## Documentacao interativa da API (FIS-35)

`springdoc-openapi-starter-webmvc-ui` gera a documentacao OpenAPI a partir
dos controllers/DTOs (records) ja existentes, sem anotacao manual adicional
na maioria dos casos:

- **`GET /v3/api-docs`** - especificacao OpenAPI em JSON.
- **`GET /swagger-ui.html`** - UI interativa para explorar/testar os
  endpoints (o Bearer token da API continua exigido para de fato chamar
  qualquer endpoint - a UI so documenta, nao contorna a autenticacao).

Ambos ficam publicos em `SecurityConfig` (mesmo tratamento de `/api/versao`
- e so schema/descricao gerado do proprio codigo, nenhum dado fiscal real).
