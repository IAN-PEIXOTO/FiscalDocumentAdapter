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
