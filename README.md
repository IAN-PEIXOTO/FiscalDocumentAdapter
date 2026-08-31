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

O teste de carga (`NfeEmissaoCargaTest`, FIS-41) fica fora do `./mvnw test`
padrao (tag JUnit5 `carga`, mais lento e menos deterministico que a suite
normal) - ver secao "Teste de carga e performance" para como rodar e os
resultados documentados.

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

## Representacao impressa da NFS-e (FIS-50)

Como nao existe um DANFE nacional padrao para NFS-e (cada prefeitura pode
exigir o proprio layout), a geracao segue o mesmo padrao de extensibilidade
por municipio ja usado para o XML (`NfseXmlGeneratorRegistry`, FIS-20):

- **`RepresentacaoImpressaNfseGenerator`** (`documento.nfse.impressao`) - a
  interface de extensao. Cada layout implementa `gerar(...)` e
  `suporta(codigoMunicipioIbge)`.
- **`RepresentacaoImpressaNfseGeneratorRegistry`** - resolve, a partir do
  codigo IBGE do municipio de prestacao, qual implementacao usar (a primeira
  registrada que declarar suporte aquele municipio); sem nenhuma
  customizada, cai no layout generico.
- **`RepresentacaoImpressaNfseGenericaGenerator`** (criterio de aceite 1) -
  layout generico em PDF (A4 retrato, mesmo estilo dos DANFE/DACTE/DAMDFE):
  identificacao (RPS + numero/codigo de verificacao da NFS-e quando
  autorizada), prestador, tomador (ou "consumidor nao identificado"),
  discriminacao do servico e o bloco de valores/ISS (valor dos servicos,
  deducoes, aliquota, valor do ISS, retencao na fonte, exigibilidade). O RPS
  nao carrega razao social/endereco do **prestador** (so documento e
  inscricao municipal - a prefeitura ja tem esse cadastro e o XSD ABRASF nao
  exige repeti-lo no envio, diferente do tomador); o layout imprime o que o
  dominio de fato tem.
- **Municipios com layout customizado suportado (criterio de aceite 3):
  nenhum ainda.** Todos os municipios usam o layout generico ate que a
  demanda concreta de algum municipio especifico surja (mesmo principio do
  `nfse-municipios.properties` do FIS-20: comeca vazio, cresce sob demanda).
  Para adicionar um layout customizado: implementar
  `RepresentacaoImpressaNfseGenerator`, registrar como `@Component` (entra
  automaticamente no registry) e declarar os municipios suportados em
  `suporta(...)`.

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

## Emissao, consulta e cancelamento de NFC-e (FIS-17/FIS-43)

A geracao do XML da NFC-e (modelo 65) e o QR Code (FIS-17) ja existiam
(`NfeXmlGenerator`/`NfeXsdValidator` leem `tipoDocumento` e ja geram
`mod=65` corretamente; `NfceQrCodeService`/`NfceQrCodeUrlRegistry` geram o
conteudo do QR Code e resolvem a URL de consulta publica por UF/ambiente) -
faltava o endpoint REST e a integracao com a autorizacao real da SEFAZ.

- **`POST /api/v1/nfce`** (novo) - mesmo payload JSON da NFe
  (`NfePedidoEmissaoRequest`; `infNFe.dest` opcional para consumidor nao
  identificado, ja suportado desde o FIS-17). Pipeline: mapeamento -> RVN ->
  certificado -> chave/XML/assinatura -> QR Code online inserido no XML ->
  validacao XSD -> autorizacao **sincrona** (indSinc=1) -> numeracao ->
  retencao. So o modo sincrono e implementado: e o unico usado na pratica
  para NFC-e (lote de um unico documento - a SEFAZ rejeita indSinc=0 nesse
  caso, cStat 452, desde a NT 2025.001) - "assincrona quando aplicavel" (AC1)
  nunca se aplica a NFC-e por esse motivo, entao nao ha um modo assincrono
  analogo ao `EmissaoAssincronaController` da NFe para a NFC-e.
- **Consulta publica (usada no QR Code)**: e a URL por UF/ambiente ja
  resolvida por `NfceQrCodeUrlRegistry`/`NfceQrCodeService` (FIS-17),
  devolvida na resposta da emissao (`urlConsultaPublica`) - nao existe um
  webservice SOAP separado para isso (cada UF expoe so um endpoint HTTP de
  portal, nao SOAP). A consulta *autenticada* por chave de acesso (mTLS,
  para o proprio emissor) continua sendo `GET`/`POST
  /api/v1/nfe/{chaveAcesso}/consulta` - o mesmo endpoint ja usado pela NFe,
  ja que `NfeConsultaProtocoloClient` e agnostico de modelo.
- **Cancelamento dentro do prazo legal da NFC-e**: reusa `POST
  /api/v1/nfe/{chaveAcesso}/cancelamento` (mesmo evento SOAP da NFe), mas
  quando a chave de acesso e de uma NFC-e (`mod=65`, detectado via
  `ChaveAcessoService.modeloDocumento`), o controller primeiro consulta a
  SEFAZ para saber a data/hora real de autorizacao e bloqueia
  (`422`) se ja passaram mais de **30 minutos** (Ajuste SINIEF 07/18, que
  reduziu o prazo anterior de 24h - alguns estados adotam um prazo ainda
  menor, nunca maior) - evita gastar uma tentativa que a SEFAZ rejeitaria de
  qualquer forma.

**NAO reusa `EmissaoNfeOrquestrador`** para a emissao: aquele orquestrador
cai em contingencia SVC-AN e, por ultimo, EPEC quando o endpoint normal
falha - ambos mecanismos **exclusivos da NFe**. A contingencia especifica da
NFC-e e o modo offline (tpEmis=9, QR Code assinado localmente - ver
`NfceQrCodeService.gerarConteudoOffline`, ja implementado desde o FIS-17),
que exige decisao explicita do PDV (implica guardar o XML localmente e
retransmitir depois) - fora do escopo deste card, registrado como debito
tecnico (mesma natureza do FIS-30 para o EPEC da NFe). Por isso, aqui, uma
falha de comunicacao com a SEFAZ propaga como erro (502) em vez de
contingencia automatica.

**Correcoes feitas ao longo do caminho, ao revisar o codigo reusado:**
- `EmissaoNfeOrquestrador.prepararDocumento` tinha `TipoDocumentoFiscal.NFE`
  fixo ao calcular a chave de acesso, em vez de ler `nfe.identificacao().tipoDocumento()`
  (que o `NfeXmlGenerator` ja lia corretamente) - inofensivo enquanto so a
  NFe usava esse orquestrador, mas geraria uma chave com `mod=55`
  incompativel com o `mod=65` do XML se esse orquestrador algum dia
  processasse NFC-e. Corrigido, com teste de regressao.
- `IdempotenciaService`/`requisicao_idempotente` eram exclusivos da
  `NfeResponse` (tipo fixo no codigo). Com a NFC-e reusando o mesmo
  mecanismo de idempotencia, um client_id que reusasse a mesma
  `Idempotency-Key` entre `POST /api/v1/nfe` e `POST /api/v1/nfce`
  colidiria na mesma chave `(client_id, chave)` e receberia de volta a
  resposta cacheada do outro endpoint. Generalizado (`Class<T>` +
  `tipo_operacao` como parte da chave unica, migration V11) - coberto por
  teste especifico de nao-colisao entre os dois endpoints.

## Geracao do DANFE NFC-e (FIS-47)

`POST /api/v1/nfce` agora devolve `danfePdfBase64` (nulo quando a NFC-e foi
rejeitada, mesma logica do `danfePdfBase64` da NFe) - um DANFE NFC-e (modelo
65) em PDF, gerado por `DanfeNfceGenerator`
(`com.fiscaladapter.documento.nfce.danfe`).

Diferente do `DanfeGenerator` da NFe (A4 retrato/paisagem, tabela de itens em
grade), o DANFE NFC-e segue o formato "cupom", pensado para impressoras
termicas de bobina continua:

- **Largura fixa de 80mm** (`226.772pt`), o padrao mais comum de impressora
  termica de PDV no Brasil (a outra largura comum, 58mm, ficou fora de
  escopo). Altura fixa e generosa (`3000pt`) porque o OpenPDF exige uma
  dimensao numerica de pagina - uma bobina continua nao tem "fim de pagina"
  real, entao isso e uma aproximacao deliberada, nao uma replica exata de uma
  impressora fisica especifica.
- **QR Code de consulta publica embutido** (AC2): o conteudo (`conteudoQrCode`,
  ja calculado por `NfceQrCodeService.gerarConteudoOnline` no pipeline de
  emissao do FIS-43) e renderizado como imagem via **ZXing**
  (`com.google.zxing:core`/`:javase`, novas dependencias) -
  `QRCodeWriter.encode` -> `BitMatrix` -> `MatrixToImageWriter.toBufferedImage`
  -> PNG -> `com.lowagie.text.Image`. O OpenPDF (ja usado no DANFE da NFe) so
  gera codigos de barra 1D (`Barcode128`), nao QR Code, daí a dependencia
  adicional.
- **Indicacao de contingencia** (AC3): mesmo padrao visual do DANFE da NFe
  (aviso em destaque, vermelho) quando `contingencia=true`. Na pratica esse
  campo sempre chega `false` no pipeline atual, ja que a NFC-e so gera DANFE
  para documento efetivamente autorizado (ver FIS-43: a contingencia offline
  da NFC-e - tpEmis=9 - fica registrada como debito tecnico, nao
  implementada) - o suporte a exibicao fica pronto para quando esse modo for
  implementado.

Reusa o modelo de dominio da NFe (`NotaFiscalEletronica`/`ItemNota`), ja que a
NFC-e (FIS-43) reaproveita o mesmo mapeamento (`NfeRequestMapper`) - nao ha um
modelo de dominio `Nfce` separado.

## Geracao do DACTE (FIS-48)

`POST /api/v1/cte` agora devolve `dactePdfBase64` (nulo quando o CT-e foi
rejeitado, mesma logica do `danfePdfBase64` da NFe/NFC-e) - um DACTE em PDF,
gerado por `DacteGenerator` (`com.fiscaladapter.documento.cte.dacte`), no
mesmo padrao do `DanfeGenerator` da NFe (FIS-8): A4 retrato, blocos de texto
por secao, codigo de barras Code128 da chave de acesso (AC2).

- **Layout conforme o manual do DACTE** (AC1): identificacao do CT-e +
  chave, emitente/transportador, remetente, destinatario, percurso (origem e
  destino, municipio/UF, dado ja existente em `IdentificacaoCte`), RNTRC,
  informacao da carga (produto predominante, valor, peso bruto) e valores da
  prestacao (valor total, valor a receber, base/aliquota/valor do ICMS).
- **Suporte aos diferentes modais de transporte no layout** (AC3): o dominio
  `Cte` (FIS-18/FIS-44) so implementa emissao no modal **rodoviario** -
  outros modais (aereo, aquaviario, ferroviario, dutoviario, multimodal)
  exigem grupos XML proprios que ainda nao existem no dominio (mesma
  limitacao ja documentada no javadoc de `Cte.java`). O DACTE imprime o modal
  como cabecalho fixo ("Modal: Rodoviario") e o RNTRC (dado obrigatorio
  apenas nesse modal); dar suporte real aos demais modais fica registrado
  como debito tecnico condicionado a evoluir o dominio primeiro - nao da
  para "suportar no layout" um modal para o qual a emissao nem gera dados.

## Emissao, consulta e cancelamento de CT-e (FIS-44)

CT-e (modelo 57) tem dominio, mapeamento e schema JSON proprios - diferente
da NFC-e (FIS-43), que reusou quase tudo da NFe, o CT-e transporta (tomador,
remetente/destinatario, informacao de carga, NF-e vinculadas), nao vende
mercadoria, entao o payload (`CtePedidoEmissaoRequest`) e a pipeline sao
novos, so reaproveitando o que ja e generico (`Emitente`/`Endereco` da NFe,
`ChaveAcessoService`, `NumeracaoSequencialService`/`RetencaoDocumentoFiscalService`
via `TipoDocumentoFiscal.CTE`).

- **`POST /api/v1/cte`** (novo) - mapeamento -> certificado ->
  chave/XML/assinatura -> validacao XSD -> autorizacao **sincrona**
  (`CTeRecepcaoSincV4`) -> numeracao -> retencao. Sem RVN (nao existe ainda
  um conjunto de regras de negocio proprio do CT-e - seria um card a parte,
  no espirito do FIS-24) e sem contingencia automatica SVC-RS/SVC-SP (mesma
  decisao da NFC-e/FIS-43 - falha de comunicacao vira erro 502, nao
  failover automatico).
- **Autorizacao e consulta (criterio de aceite 1)**: o CT-e 4.00 **nao tem
  mais um modo em lote/assincrono** - a SEFAZ desativou `CTeRecepcao`/
  `CTeRetRecepcao` em 30/06/2024 (NT 2024.001), migrando tudo para
  `CTeRecepcaoSincV4` (um documento por chamada, resposta imediata, sem
  `idLote`/`indSinc` como a NFe). "Consulta de lote" no criterio de aceite
  reflete a terminologia anterior a essa mudanca - o que existe hoje e
  `POST /api/v1/cte/{chaveAcesso}/consulta` (`CTeConsultaV4`), consulta de
  situacao por chave, igual ao padrao ja usado na NFe/NFC-e.
- **Cancelamento respeitando o prazo legal (criterio de aceite 2)**: `POST
  /api/v1/cte/{chaveAcesso}/cancelamento` consulta a SEFAZ para a data real
  de autorizacao e bloqueia (HTTP 422) alem de **168 horas (7 dias)**
  (Ajuste SINIEF 09/07, clausula 14 - algumas UFs reduzem esse prazo, ex.:
  MT para 24h, nao verificado por UF nesta sessao). Cancelamento
  extemporaneo (apos o prazo, processo especifico de cada UF) fica fora do
  escopo.
- **Vinculo com os documentos transportados (criterio de aceite 3)**: tanto
  a resposta da emissao quanto a da consulta trazem
  `notasFiscaisTransportadas` (as chaves de NF-e vinculadas) - na emissao,
  ecoada do proprio pedido; na consulta, extraida do XML arquivado por este
  adapter (`RetencaoDocumentoFiscalService`, FIS-26/34), ja que a SEFAZ nao
  devolve essa lista na consulta de situacao (so cStat/protocolo). Fica
  vazia se o CT-e consultado nao foi emitido por este adapter.

## Vinculo entre CT-e e MDF-e na consulta e no cancelamento (FIS-53)

`POST /api/v1/cte/{chaveAcesso}/consulta` agora tambem devolve
`mdfeVinculado` - a chave do MDF-e que ja manifestou este CT-e para
transporte, ou `null` se nenhum. A SEFAZ nao expoe esse vinculo na consulta
de situacao do CT-e (so o proprio MDF-e sabe quais CT-e ele transporta) -
por isso `CteConsultaController` varre os MDF-e ja arquivados por este
adapter (`RetencaoDocumentoFiscalService.recuperarPorEmissorETipo`, novo
metodo) para o mesmo CNPJ emissor do CT-e (a transportadora e sempre a
mesma nos dois documentos), procurando uma referencia a chave do CT-e em
`infCTe/chCTe` - mesma tecnica de regex sobre XML arquivado ja usada para
`notasFiscaisTransportadas` (FIS-44) e para o `MdfeXmlParser` (FIS-49).

`POST /api/v1/cte/{chaveAcesso}/cancelamento` agora bloqueia (HTTP 422,
`CteJaManifestadoEmMdfeException`) quando `mdfeVinculado` nao e nulo -
cancelar um CT-e que ja foi incluido num MDF-e deixaria o manifesto
referenciando um documento inexistente perante o fisco; o procedimento
correto e cancelar ou encerrar o MDF-e vinculado primeiro.

**Limitacao conhecida:** o arquivamento legal (`DocumentoFiscalArquivado`)
guarda so o XML autorizado, sem status de cancelamento - o bloqueio vale
enquanto existir qualquer MDF-e autorizado que referencie o CT-e, mesmo que
esse MDF-e tenha sido cancelado depois. Corrigir isso exigiria rastrear o
status de cancelamento dos documentos arquivados, fora do escopo deste card
(documentado tambem no javadoc de `CteJaManifestadoEmMdfeException`).

**Estrutura SOAP diferente da NFe/NFC-e (verificado contra a implementacao
de referencia nfephp-org/sped-cte, `Common/Tools.php`):** a autorizacao
exige o XML **gzip+base64** dentro de `cteDadosMsg` (a NFe envia texto puro
em `nfeDadosMsg`) - consulta e cancelamento continuam em texto puro. Header
SOAP `cteCabecMsg` (nao `nfeCabecMsg`). Endpoints proprios do CT-e
(`cte-webservices.properties`, fonte ACBrCTeServicos.ini) - infraestrutura
separada da NFe mesmo quando o host e o mesmo (SP/SVRS hospedam ambos, mas
em caminhos distintos).

**ATENCAO (nao verificavel nesta sessao):** a versao do evento de
cancelamento (`"4.00"`) foi assumida por alinhamento com a URL do servico
(`CTeRecepcaoEventoV4`), nao confirmada contra homologacao real - a NFe usa
uma versao de evento fixa ("1.00") independente do layout do documento, e
nao ha garantia de que o CT-e siga o mesmo padrao.

## Geracao do DAMDFE (FIS-49)

`POST /api/v1/mdfe` agora devolve `damdfePdfBase64` (nulo quando o MDF-e foi
rejeitado) - um DAMDFE em PDF, gerado por `DamdfeGenerator`
(`com.fiscaladapter.documento.mdfe.damdfe`), no mesmo padrao do
`DanfeGenerator`/`DacteGenerator`: A4 retrato, codigo de barras Code128 da
chave de acesso (AC2), blocos de emitente, veiculo/motorista(s), percurso e
documentos fiscais vinculados (CT-e/NF-e, AC1).

- **"Indicacao de encerramento quando aplicavel" (AC3)** e o unico dos tres
  criterios que nao se resolve com um unico PDF gerado na emissao: o
  manifesto acabou de ser autorizado nesse momento, a viagem ainda nao
  terminou. Por isso, `POST /api/v1/mdfe/{chaveAcesso}/encerramento` (FIS-45)
  agora tambem devolve `damdfePdfBase64` - um **DAMDFE reimpresso** com o
  aviso "MDF-e ENCERRADO" e a data/municipio de encerramento, gerado so
  quando `encerrado=true`.
  - Esse endpoint so recebe a chave de acesso, nao o objeto de dominio
    original da emissao - por isso o MDF-e e **reconstruido a partir do XML
    assinado ja arquivado** (`RetencaoDocumentoFiscalService`, FIS-26/34) via
    o novo `MdfeXmlParser` (`documento.mdfe`), a mesma tecnica ja usada pelo
    `CteConsultaController.notasFiscaisTransportadas` (FIS-44) para nao
    duplicar estado em outra tabela. Coberto por `MdfeXmlParserTest`
    (round-trip contra o proprio `MdfeXmlGenerator`).

## Documentos vinculados e bloqueio de cancelamento apos encerramento (FIS-54)

A consulta e o encerramento com data/local ja existiam desde o FIS-45; este
card cobriu as duas lacunas reais: "documentos vinculados" na consulta e o
bloqueio de cancelamento apos o encerramento.

- **Consulta retorna documentos vinculados (criterio de aceite 3)**: `POST
  /api/v1/mdfe/{chaveAcesso}/consulta` agora tambem devolve
  `chavesCteTransportados`/`chavesNfeTransportadas` - extraidos do XML
  arquivado por este adapter na emissao via `MdfeXmlParser` (FIS-49), ja que
  a SEFAZ nao devolve essa lista na consulta de situacao. Ficam vazias se o
  MDF-e consultado nao foi emitido por este adapter.
- **Cancelamento permitido apenas antes do encerramento (criterio de aceite
  2)**: a SEFAZ nao expoe "encerrado" na consulta de situacao usada por
  este adapter, entao o proprio encerramento precisa registrar esse fato -
  novo `MdfeEncerramentoRegistroService` (pacote `mdfe`, tabela
  `mdfe_encerramento`, migration V12) grava chave + municipio + data quando
  `POST /api/v1/mdfe/{chaveAcesso}/encerramento` e aceito pela SEFAZ.
  `POST /api/v1/mdfe/{chaveAcesso}/cancelamento` consulta esse registro
  antes do prazo legal (24h, FIS-45) e bloqueia (HTTP 422,
  `MdfeJaEncerradoException`) se o manifesto ja foi encerrado - cancelar
  depois do encerramento nao faz sentido, o documento ja cumpriu seu
  proposito perante o fisco. O mesmo `encerrado` tambem e devolvido na
  consulta (`ConsultaMdfeResponse.encerrado`).

## Emissao, consulta, encerramento e cancelamento de MDF-e (FIS-19/FIS-45)

MDF-e (modelo 58) tem dominio, mapeamento e schema JSON proprios, mesmo
espirito do CT-e (FIS-44) - o manifesto agrupa CT-e/NF-e transportados por
um veiculo/condutor numa viagem, nao vende nem transporta um unico
documento. A geracao do XML principal e do evento de Encerramento (fim de
percurso) ja existiam desde o FIS-19 (`MdfeXmlGenerator`,
`MdfeEncerramentoXmlGenerator` - so gerava o XML do evento, sem
assinar/transmitir, deliberadamente deixado "para o FIS-45" no proprio
javadoc da classe); faltava a integracao real com a SEFAZ.

- **`POST /api/v1/mdfe`** (novo) - mapeamento -> certificado ->
  chave/XML/assinatura -> validacao XSD -> autorizacao **sincrona**
  (`MDFeRecepcaoSinc`) -> numeracao -> retencao. Mesma migracao do CT-e: a
  SEFAZ desativou o modo em lote (`MDFeRecepcao`/`MDFeRetRecepcao`) em
  30/06/2024 (NT 2024.001) - "consulta de lote" no criterio de aceite
  reflete a terminologia anterior a essa mudanca. Sem RVN propria e sem
  contingencia automatica (mesma decisao do CT-e/FIS-44).
- **Consulta**: `POST /api/v1/mdfe/{chaveAcesso}/consulta` (`MDFeConsulta`).
- **Encerramento do manifesto - fim de percurso (criterio de aceite 2)**:
  `POST /api/v1/mdfe/{chaveAcesso}/encerramento` (evento tpEvento=110112,
  servico `MDFeRecepcaoEvento`) - completa o `MdfeEncerramentoXmlGenerator`
  ja existente assinando e transmitindo o evento que ele gera. O municipio
  de encerramento e informado pelo chamador (pode diferir do municipio de
  descarga previsto na emissao).
- **Cancelamento dentro do prazo legal (criterio de aceite 3)**: `POST
  /api/v1/mdfe/{chaveAcesso}/cancelamento` consulta a SEFAZ para a data
  real de autorizacao e bloqueia (HTTP 422) alem de **24 horas** (Ajuste
  SINIEF 21/2010) - o prazo mais curto entre os quatro documentos deste
  adapter (NFC-e 30 min, MDF-e 24h, NFe historicamente 24h/variavel por UF,
  CT-e 168h). A condicao adicional do Ajuste ("desde que o transporte ainda
  nao tenha iniciado") nao e verificavel localmente - fica a cargo da
  propria SEFAZ rejeitar se for o caso.

**Infraestrutura 100% centralizada na SVRS** (diferente do CT-e, onde
MG/MS/MT/PR/SP tem endpoint proprio) - as 27 UFs delegam para o mesmo
endereco (`mdfe-webservices.properties`, fonte ACBrMDFeServicos.ini).
Mesma estrutura SOAP do CT-e (header `mdfeCabecMsg`, corpo `mdfeDadosMsg`,
autorizacao gzip+base64, consulta/evento em texto puro), verificada contra
a implementacao de referencia nfephp-org/sped-mdfe.

**ATENCAO (nao verificavel nesta sessao):** uma fonte secundaria (nao a
implementacao de referencia) sugere que o binding especifico do
`MDFeRecepcaoSinc` nao declara `mdfeCabecMsg` no WSDL - a implementacao de
referencia, porem, envia esse header uniformemente para todos os servicos
(inclusive o sincrono), e e o que este adapter segue, ja que um header
SOAP nao declarado no binding e tipicamente ignorado pelo servidor (sem
`mustUnderstand`), nao rejeitado.

## Mapeamento de codigos de rejeicao da SEFAZ (FIS-39)

O cStat/xMotivo bruto da SEFAZ (ex.: `"539"` / `"Duplicidade de NF-e"`)
continua sempre presente na resposta (fallback, criterio de aceite 3), mas
`NfeResponse` (emissao), `ConsultaNfeResponse`, `CancelamentoNfeResponse`,
`CceNfeResponse`, `InutilizacaoNfeResponse` e `ManifestacaoNfeResponse`
ganharam dois campos novos, preenchidos so quando a operacao *nao* teve
sucesso:

- **`mensagemErro`** - versao clara e acionavel do erro (ou o proprio
  `xMotivo` bruto, quando o codigo nao esta catalogado - nunca inventa uma
  explicacao para um codigo desconhecido).
- **`categoriaErro`** - `CORRIGIVEL_PELO_CLIENTE` (dado invalido/incompativel
  no pedido - corrigir e reenviar), `TRANSITORIO` (falha do lado da SEFAZ -
  servico parado ou documento ainda nao processado - tentar novamente tende
  a resolver sozinho) ou `DESCONHECIDA` (codigo fora do catalogo).

`CatalogoRejeicaoSefaz` (`com.fiscaladapter.sefaz.rejeicao`) cobre os cStat
mais comuns na pratica (204, 215, 217, 225, 226, 234, 235, 241, 301, 302,
539, 590, 656, 108, 109, 110, 999) - nao a tabela oficial completa (centenas
de codigos, muitos raros ou especificos de CT-e/MDF-e/NFS-e). Descricoes
conferidas contra a tabela publica mantida por `nfephp-org/sped-nfe`
(biblioteca de referencia da comunidade de integradores NFe), ja que nao ha
acesso direto ao PDF do MOC (Manual de Orientacao do Contribuinte) da SEFAZ
nesta sessao - os codigos documentados abaixo sao consistentes entre
multiplas fontes independentes consultadas.

**NFe liberada via EPEC nao e tratada como rejeicao**: quando a NFe e
liberada provisoriamente via EPEC (`viaEpec=true`), `autorizada` fica
`false` mas isso nao e um erro do cliente - por isso `NfeEmissaoService` so
consulta o catalogo quando `!autorizada && !viaEpec` (rejeicao de fato).

## Manifestacao do destinatario e consulta de NF-e destinadas (FIS-40)

`POST /api/v1/nfe/{chaveAcesso}/manifestacao` (ja existente desde o FIS-9)
suporta os quatro tipos de manifestacao previstos pela SEFAZ
(`TipoManifestacaoDestinatario`): Confirmacao da Operacao, Ciencia da
Operacao, Desconhecimento da Operacao e Operacao nao Realizada (essa ultima
exige justificativa com pelo menos 15 caracteres, igual ao evento oficial).

**Consulta de NF-e destinadas (novo neste card)**: `GET
/api/v1/nfe/destinadas?cnpjDestinatario=...&uf=...&ambiente=...` descobre
quais NF-e foram destinadas a um CNPJ mas nao emitidas por ele - o cenario
que faltava para o destinatario saber *o que* manifestar, sem precisar ja
conhecer a chave de acesso de antemao. Implementado via
`NfeDistribuicaoDfeClient` (webservice nacional `NFeDistribuicaoDFe`,
consulta incremental por NSU) + `DistribuicaoDfeService`, que guarda o
cursor de NSU por CNPJ (`DistribuicaoDfeCursor`/migration V10) para nunca
reconsultar do zero, e pagina automaticamente ate esgotar o lote disponivel
numa mesma chamada (limite de 20 paginas por chamada - documentado via log
se atingido sem esgotar, os documentos restantes saem na proxima consulta).

**ATENCAO (nao verificavel nesta sessao):** a estrutura do envelope SOAP da
`NFeDistribuicaoDFe` e diferente dos demais servicos da NFe 4.00 usados
neste projeto (Header vazio, Body com um elemento extra em volta de
`nfeDadosMsg`) - baseada na implementacao de referencia do
`nfephp-org/sped-nfe`, sem acesso a um ambiente de homologacao real para
validar empiricamente. Revisar contra homologacao real antes do primeiro
uso em producao.

**Consumo indevido (cStat 656)**: a SEFAZ rejeita consultas repetidas em
curto intervalo sem novidade. O adapter bloqueia preventivamente
(`ConsultaDistribuicaoDfeMuitoFrequenteException`, HTTP 429) quando a
ultima consulta bem-sucedida para aquele CNPJ foi ha menos de 1 hora, em
vez de gastar a tentativa e ser rejeitado pela SEFAZ.

**Prazo de manifestacao controlado e alertado (criterio de aceite 3)**: a
SEFAZ nao devolve prazo pronto - `DistribuicaoDfeService` calcula, para
cada NF-e destinada, `dataLimiteManifestacao` (data de autorizacao + 90
dias corridos) e `diasRestantesParaManifestar`, marcando
`alertaProximoDoPrazo=true` nos ultimos 15 dias e `prazoExpirado=true`
depois de vencido. **90 dias** e o prazo vigente desde 01/06/2026 (Ajuste
SINIEF 14/2026, que reduziu o prazo anterior de 180 dias) para Confirmacao,
Desconhecimento e Operacao nao Realizada - Ciencia da Operacao nao tem
prazo/efeito fiscal proprio, mas a NFeDistribuicaoDFe nao informa qual
manifestacao (se alguma) ja foi registrada para cada resumo, entao o
adapter calcula a mesma data limite para todos os resumos devolvidos.

## Teste de carga e performance (FIS-41)

`NfeEmissaoCargaTest` (`src/test/java/com/fiscaladapter/carga`) simula picos
de emissao simultanea de NFe contra o pipeline real da aplicacao
(mapeamento -> RVN -> assinatura XML -> SOAP -> numeracao -> retencao),
substituindo so o webservice da SEFAZ por um servidor SOAP local
(`ServidorSoapDeTeste`, com um pool de threads de verdade - sem isso o
servidor de teste serializaria as respostas numa unica thread e o teste
mediria a serializacao do stub, nao o pipeline) com uma latencia artificial
de 50ms por chamada, para aproximar (sem depender de) um ambiente de
homologacao real. Dois cenarios, cada um imprime um relatorio no console:

1. **Pico seguro** (55 requisicoes, concorrencia 20, dentro do limite de
   taxa) - mede a latencia real do pipeline sob concorrencia.
2. **Pico acima da capacidade configurada** (70 requisicoes num unico
   minuto) - prova que o rate limit por client_id (`RateLimitFilter`, FIS-14)
   e aplicado de fato sob carga concorrente, nao so em chamadas sequenciais.

Fica fora do `./mvnw test`/`./mvnw verify` padrao (tag JUnit5 `carga`,
`excludedGroups` no `maven-surefire-plugin`) por ser mais lento e menos
deterministico (mede tempo de parede) que a suite normal. Rodar
explicitamente:

```bash
./mvnw test -Dsurefire.excludedGroups= -Dtest=NfeEmissaoCargaTest
```

### Metricas documentadas (criterio de aceite 2)

Ultima execucao nesta sessao (H2 em memoria, perfil dev, uma unica JVM local
- ver limitacao abaixo):

| Cenario | Requisicoes | Taxa de erro | Throughput | p50 | p95 | p99 |
|---|---|---|---|---|---|---|
| Pico seguro (concorrencia 20) | 55 | 0% | ~9,5 emissoes/s | 546 ms | 4916 ms | 4918 ms |
| Pico acima da capacidade | 70 num 1 min | 10/70 (14%, todos HTTP 429) | ~46 emissoes/s | 382 ms | 730 ms | 741 ms |

O achado mais importante nao e um numero isolado, e o formato da curva: **o
throughput nao melhora ao dobrar a concorrencia** (testes exploratorios
fora do arquivo final: ~18 emissoes/s em concorrencia 10, ~17 emissoes/s em
concorrencia 20 - mesma faixa, latencia de cauda quase dobrando) - sinal
classico de saturacao num recurso compartilhado, nao de capacidade elastica.
No cenario "pico seguro" documentado acima, p50 e p95 distam quase 10x
(546ms vs 4916ms), confirmando que uma fracao das requisicoes fica
esperando por esse recurso em vez de processar em paralelo de verdade.

**ATENCAO (limitacao conhecida, nao contornavel nesta sessao):** os numeros
acima vem de H2 em memoria (perfil dev) numa unica JVM local, nao de
Postgres real nem de infraestrutura de producao - servem como sinal
*relativo* de onde o pipeline gasta tempo e onde a concorrencia degrada, nao
como capacidade absoluta de producao. Rodar este mesmo teste (ou uma
ferramenta de carga externa, tipo k6/Gatling, contra uma instancia real com
Postgres) antes de qualquer compromisso de capacidade com stakeholders.

### Gargalos identificados e debito tecnico (criterio de aceite 3)

1. **Rate limit por client_id e o primeiro teto atingido na pratica**
   (`RateLimitFilter`, FIS-14): 60 requisicoes/minuto, fixo no codigo, em
   memoria (nao sobrevive a multiplas instancias da aplicacao - o proprio
   javadoc da classe ja registrava isso). Debito tecnico: (a) tornar o
   limite configuravel por client_id/plano em vez de uma constante global,
   (b) migrar para um contador compartilhado (ex.: Redis) antes de rodar
   mais de uma instancia da aplicacao atras de um load balancer - sem isso,
   cada instancia aplicaria seu proprio limite de 60/min, multiplicando o
   limite real pelo numero de instancias de forma nao intencional.

2. **`SefazHttpClientFactory.criar` reconstroi o `SSLContext`/`KeyStore`
   mTLS a cada chamada** a SEFAZ (`NfeAutorizacaoClient` chama `criar()` em
   toda tentativa de autorizacao, nunca reusa) - trabalho de
   crypto/parsing de certificado repetido desnecessariamente quando o
   mesmo certificado emite varias notas em sequencia, contribuindo para a
   degradacao de latencia sob concorrencia (contencao de CPU). Debito
   tecnico: cachear o `HttpClient`/`SSLContext` por certificado
   (invalidando quando o certificado for re-registrado via `POST
   /api/v1/certificados`), em vez de reconstruir em toda emissao.

3. **Pool de conexoes do banco no tamanho default do HikariCP** (10 -
   nenhum profile define `maximum-pool-size` explicitamente): cada emissao
   sincrona faz pelo menos 3 escritas (numeracao, idempotencia, retencao)
   mais a leitura do certificado - acima de ~10 emissoes verdadeiramente
   concorrentes, a fila de espera por conexao vira um gargalo visivel.
   Debito tecnico: tornar o tamanho do pool configuravel por ambiente
   (`application-prod.yml`/`application-homolog.yml`) e dimensionar contra
   o volume esperado de producao - nao fixado agora por nao haver ainda um
   numero real de volume esperado para calibrar contra.

4. **Fila assincrona (FIS-25/30) processa em lotes pequenos e sequenciais**
   (`EmissaoAssincronaWorker`, 5 jobs por poll, um poll por vez via
   `@Scheduled`) - adequada para o volume atual, mas e o proximo teto
   depois do rate limit se o volume de emissoes em fila crescer bastante.
   Nao redesenhado agora (seria trabalho especulativo sem um numero real de
   volume para justificar) - fica registrado como debito tecnico a
   revisitar com metricas de producao reais.

Nenhum destes foi corrigido nesta sessao: mudar qualquer um deles sem um
numero real de volume de producao para calibrar contra seria otimizacao
especulativa. O objetivo deste card era medir e identificar, nao redesenhar
- ficam documentados aqui para quando o volume real justificar o trabalho.

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
