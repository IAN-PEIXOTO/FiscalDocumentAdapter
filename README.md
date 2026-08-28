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
