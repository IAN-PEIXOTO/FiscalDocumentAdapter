# Manual de Integração — NFS-e (Nota Fiscal de Serviços Eletrônica)

> Ver [00-visao-geral.md](00-visao-geral.md) para autenticação, certificado digital,
> idempotência, ambientes, formato de erro e demais aspectos comuns a todos os
> documentos. Este manual cobre apenas o que é específico da NFS-e.

## 1. Visão geral — por que a NFS-e é diferente

Diferente de NF-e/NFC-e/CT-e/MDF-e (documentos estaduais, com um schema XSD nacional
único definido pela SEFAZ), a NFS-e é **municipal**: cada prefeitura é responsável pelo
próprio sistema de emissão, e cada uma pode (e frequentemente faz) divergir do padrão
mais comum. Não existe uma chave de acesso de 44 dígitos nem um webservice nacional —
cada município tem seu próprio endpoint, só conhecido durante o credenciamento do
contribuinte junto àquela prefeitura.

Por isso, tanto a geração do XML quanto a representação impressa são estruturadas de
forma **extensível por município**, com um padrão genérico coberto de fábrica:

- **`PadraoNfse`** enumera os padrões suportados — hoje só `ABRASF_V2_01` (Associação
  Brasileira das Secretarias de Finanças das Capitais, o mais adotado, usado por
  centenas de prefeituras).
- **`NfseXmlGeneratorRegistry`** resolve, pelo código IBGE do município de prestação,
  qual gerador de XML usar — municípios sem entrada explícita caem no padrão ABRASF.
- **`RepresentacaoImpressaNfseGeneratorRegistry`** faz o mesmo para o layout impresso
  (seção 6) — hoje só o layout genérico, nenhum município customizado ainda.

## 2. O que é o RPS

O prestador gera e declara um **RPS** (Recibo Provisório de Serviços) para a
prefeitura, que devolve a NFS-e definitiva com número e código de verificação. O
domínio deste adapter (`Nfse`) modela exatamente esse RPS:

```
Nfse
├── rps: InfRps { identificacao: IdentificacaoRps { numero, serie, tipo }, dataEmissao, status }
├── competencia: LocalDate
├── servico: DadosServicoNfse
│   ├── valores: ValoresServicoNfse { valorServicos, valorDeducoes?, valorIss?, aliquota? }
│   ├── issRetido: boolean
│   ├── itemListaServico: String   (código da lista de serviços LC 116/03)
│   ├── discriminacao: String
│   ├── codigoMunicipioPrestacao: String
│   └── exigibilidadeIss: int      (1..7, tabela abaixo)
├── prestador: PrestadorServicoNfse { cpfOuCnpj, inscricaoMunicipal }
├── tomador: TomadorServicoNfse?   (opcional — cupom para consumidor não identificado)
│   ├── cpfOuCnpj, inscricaoMunicipal?, razaoSocial
│   ├── endereco: EnderecoNfse?
│   └── telefone?, email?
├── optanteSimplesNacional: boolean
└── incentivoFiscal: boolean
```

`tipo` do RPS (`TipoRps`): `RPS` (comum), `NOTA_FISCAL_CONJUGADA_MISTA`, `CUPOM`.

`exigibilidadeIss` (tabela oficial do XSD ABRASF): `1`=Exigível, `2`=Não incidência,
`3`=Isenção, `4`=Exportação, `5`=Imunidade, `6`=Suspensa por decisão judicial,
`7`=Suspensa por decisão administrativa.

**O prestador não carrega razão social/endereço no RPS** — só documento e inscrição
municipal. A prefeitura já tem esse cadastro e o XSD ABRASF não exige repeti-lo no
envio (diferente do tomador, que precisa vir por extenso quando informado).

> **`prestador`/`tomador` no domínio não têm razão social separada do CPF/CNPJ para o
> prestador** — isso afeta diretamente o layout impresso (seção 6): o prestador
> aparece na representação impressa só com documento + inscrição municipal.

## 3. Geração do XML (ABRASF v2.01)

Cobre os campos obrigatórios do RPS no formato `GerarNfseEnvio`
(identificação, prestador, serviço, valores, tomador opcional), validado contra o XSD
oficial da ABRASF. **Não cobre ainda**: retenções detalhadas (PIS/COFINS/INSS/IR/CSLL),
construção civil, intermediário, regime especial de tributação, RPS de substituição,
nem o envio em lote (`EnviarLoteRpsEnvio` — só a via individual e síncrona,
`GerarNfseEnvio`, é suportada).

**Assinatura digital do RPS é opcional**, ao contrário de NF-e/CT-e/MDF-e (sempre
obrigatória) — o XSD ABRASF permite `GerarNfseEnvio` sem `dsig:Signature`; cada
prefeitura decide se exige.

## 4. ⚠️ Emissão via REST ainda não tem endpoint

**Diferente dos outros quatro documentos, não existe hoje um `POST /api/v1/nfse` (ou
equivalente) para gerar e transmitir um novo RPS/NFS-e via API REST.** O que existe:

- Domínio (`Nfse`) e gerador de XML (`AbrasfNfseXmlGenerator`) — prontos.
- Cliente SOAP de comunicação com a prefeitura (`AbrasfNfseClient.gerarNfse(...)`) —
  pronto, testado contra um servidor SOAP local.
- **Nenhum controller REST** expõe esse fluxo de ponta a ponta ainda — só cancelamento
  e consulta de status têm endpoint (seção 5). Emitir uma NFS-e hoje exige código
  Java chamando `AbrasfNfseXmlGenerator`/`AbrasfNfseClient` diretamente, não uma
  chamada HTTP.

Isso é uma lacuna real do backlog atual, não uma omissão deste manual — documentado
aqui para não sugerir uma capacidade que não existe.

## 5. Cancelamento e consulta de status (via REST)

```
POST /api/v1/nfse/cancelamento
Authorization: Bearer <access_token>
Content-Type: application/x-www-form-urlencoded (ou query params)

codigoIbgeMunicipio=3550308
numeroNfse=789
cpfCnpjPrestador=12345678000199
inscricaoMunicipalPrestador=123456   (opcional)
codigoMunicipioPrestacao=3550308
ambiente=HOMOLOGACAO
```

```json
{
  "numeroNfse": "789",
  "cancelada": true,
  "dataHoraCancelamento": "2026-03-20T10:00:00-03:00",
  "codigoErro": null,
  "mensagemErro": null
}
```

**Validação de entrada (FIS-57)**: `numeroNfse` e `codigoMunicipioPrestacao` devem ser
só dígitos; `inscricaoMunicipalPrestador` e `serieRps` não podem conter `< > & " '`
(HTTP 400 caso contrário) - esses valores são concatenados diretamente no XML SOAP
enviado à prefeitura (o padrão ABRASF não exige assinatura do RPS, então não há outra
camada de proteção estrutural além do escape feito nesse XML).

```
POST /api/v1/nfse/consulta

codigoIbgeMunicipio=3550308
numeroRps=42
serieRps=1
cpfCnpjPrestador=12345678000199
inscricaoMunicipalPrestador=123456   (opcional)
ambiente=HOMOLOGACAO
```

```json
{
  "autorizada": true,
  "numeroNfse": "789",
  "codigoVerificacao": "ABC123XYZ",
  "codigoErro": null,
  "mensagemErro": null
}
```

Consulta de status é feita **pela identificação do RPS que originou a NFS-e**
(`numeroRps`+`serieRps`+CPF/CNPJ do prestador) — não pelo número da NFS-e diretamente,
já que nem toda prefeitura permite esse segundo caminho.

**Certificado resolvido pelo CPF/CNPJ do prestador informado no request** (não por
chave de acesso, que a NFS-e não tem) — mesma garantia multi-tenant dos demais
documentos.

**Não há catálogo de rejeição** (como o `CatalogoRejeicaoSefaz` da NF-e) para NFS-e —
os códigos de erro da ABRASF variam por prefeitura, não há uma tabela nacional única de
motivos; `codigoErro`/`mensagemErro` são repassados **exatamente como a prefeitura
devolveu**.

### Mensagem clara quando o município não tem endpoint cadastrado

`nfse-webservices.properties` começa **vazio** — cada município atendido precisa ser
cadastrado manualmente durante o *onboarding* daquele cliente específico (não existe um
catálogo público confiável de webservices de NFS-e por município, ao contrário da NF-e,
que tem uma lista nacional pública por UF). Tentar operar um município sem entrada
cadastrada devolve **HTTP 400** com mensagem explícita:

```json
{"mensagem": "Endpoint de NFS-e nao cadastrado para 9999999.HOMOLOGACAO.CANCELAR_NFSE - cadastre o endpoint da prefeitura em nfse-webservices.properties durante o onboarding do cliente"}
```

## 6. Representação impressa

Não existe DANFE nacional padrão para NFS-e — a representação impressa segue o mesmo
padrão de extensibilidade por município do XML (seção 1):

- **`RepresentacaoImpressaNfseGenerator`** — interface de extensão: cada layout
  implementa `gerar(nfse, resposta)` e `suporta(codigoMunicipioIbge)`.
- **`RepresentacaoImpressaNfseGeneratorRegistry`** — resolve o layout certo pelo
  código IBGE (o primeiro registrado que declarar suporte); sem nenhum customizado,
  cai no layout genérico.
- **Layout genérico** (PDF A4 retrato, mesmo estilo do DANFE/DACTE/DAMDFE):
  identificação (RPS + número/código de verificação quando autorizada), prestador
  (documento + inscrição municipal — sem razão social, ver seção 2), tomador (ou
  "consumidor não identificado"), discriminação do serviço, e o bloco de valores/ISS
  (valor dos serviços, deduções, alíquota, valor do ISS, retenção na fonte,
  exigibilidade).
- **Municípios com layout customizado suportado hoje: nenhum.** Todos usam o layout
  genérico até que a demanda concreta de um município específico surja. Para adicionar
  um: implementar `RepresentacaoImpressaNfseGenerator`, registrar como `@Component`
  (entra automaticamente no registry) e declarar os municípios em `suporta(...)`.

> Este componente também ainda não está conectado a um endpoint REST de emissão
> (seção 4) — hoje é invocável apenas via código Java, recebendo um `Nfse` e um
> `NfseResponse` já obtidos por outro meio.

## 7. Referências

- LC 116/03 — lista de serviços tributáveis pelo ISS (`itemListaServico`).
- Padrão técnico ABRASF v2.01 (Associação Brasileira das Secretarias de Finanças das
  Capitais) — schema XSD do RPS/NFS-e, o mais adotado entre municípios brasileiros.
- Cada prefeitura publica seu próprio manual de integração (quando o município não
  segue ABRASF puro) — não há uma fonte nacional única, ao contrário dos documentos
  estaduais.
