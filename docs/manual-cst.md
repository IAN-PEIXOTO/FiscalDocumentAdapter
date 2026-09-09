# Manual de Referência — CST/CSOSN do ICMS (e PIS/COFINS)

> Ver [00-visao-geral.md](00-visao-geral.md) para autenticação, certificado digital e
> demais aspectos comuns. Ver [manual-nfe.md § 3.1](manual-nfe.md#31-estrutura-do-payload)
> para a estrutura completa do payload de emissão. Este manual é uma **referência de
> códigos tributários** (CST/CSOSN), no espírito das tabelas de referência que a ACBr
> mantém para seus componentes (`TACBrCST`, `TACBrCSOSN`) — organiza por código, indica o
> que significa, e principalmente **o que este adapter de fato suporta hoje**, sem fingir
> cobertura que não existe.

## 1. O que é CST e CSOSN

**CST** (Código de Situação Tributária, Tabela A do Anexo do Convênio S/N de 1970) é
usado por emitentes do **Regime Normal** (CRT 3) e do **Simples Nacional com excesso de
sublimite** (CRT 2, para os itens fora do Simples). **CSOSN** (Código de Situação da
Operação no Simples Nacional, Anexo do Ato COTEPE 09/2008) é o equivalente exclusivo para
emitentes do **Simples Nacional** (CRT 1 e 4). Os dois nunca se misturam na mesma nota:
`RVN-005` (`RegraRegimeTributarioCompativelComIcms`, ver
[manual-nfe.md § 5](manual-nfe.md#5-regras-de-negocio-rvn)) barra a combinação errada
antes mesmo de transmitir à SEFAZ.

## 2. Suportados hoje (`ImpostoItem.grupoIcms` / `codigoIcms`)

O adapter grava o ICMS em três grupos de XML distintos (`NfeXmlGenerator.escreverIcms`),
cada um com um subconjunto de campos diferente — só o grupo `ICMS00` leva base de
cálculo/alíquota/valor; os outros dois são propositalmente "sem valores" no XSD:

| `grupoIcms` | CST/CSOSN aceitos (`codigoIcms`) | Campos escritos | CRT compatível |
|---|---|---|---|
| `ICMS00` | `00` — Tributação integral | `orig`, `CST`, `modBC` (fixo "3"), `vBC`, `pICMS`, `vICMS` | 3 (Regime Normal) |
| `ICMS40` | `40` isenta \| `41` não tributada \| `50` suspensão | `orig`, `CST` — sem base/alíquota/valor | 3 (Regime Normal) |
| `ICMSSN102` | `102` sem crédito \| `103` isenção por faixa \| `300` imune \| `400` não tributada SN | `orig`, `CSOSN` — sem base/alíquota/valor | 1, 2, 4 (Simples Nacional) |

**Estes são, na prática, os CST/CSOSN mais usados no varejo/prestação de serviço comum**
(venda tributada normal, isenção, e a operação padrão do MEI/microempresa no Simples) —
não por coincidência: foi o critério de escolha do escopo original.

## 3. Não suportados (débito técnico declarado)

A tabela oficial completa tem bem mais códigos do que os 7 acima. Os mais relevantes que
**faltam** hoje, e por quê:

| CST | Significado | Por que falta |
|---|---|---|
| `10` | Tributada com cobrança de ICMS-ST | Precisa dos campos `vBCST`/`pICMSST`/`vICMSST` — grupo XML próprio (`ICMS10`), não implementado |
| `20` | Tributação com redução de base de cálculo | Precisa de `pRedBC` — grupo `ICMS20`, não implementado |
| `30` | Isenta/não tributada com cobrança de ICMS-ST | Combina isenção + ST — não implementado |
| `51` | Diferimento | Precisa de `pDif`/`vICMSOp`/`vICMSDif` — não implementado |
| `60` | Cobrado por substituição tributária (ICMS já retido antes) | Muito comum em distribuidoras/atacado — grupo `ICMS60`, não implementado |
| `70` | Redução de BC com cobrança de ICMS-ST | Combinação de 20+10 — não implementado |
| `90` | Outras | Grupo genérico com campos variáveis — não implementado |
| CSOSN `101` | Simples Nacional **com** permissão de crédito | Precisa de `pCredSN`/`vCredICMSSN` — não implementado |
| CSOSN `201`/`202`/`203` | Simples Nacional com ICMS-ST | Não implementado |
| CSOSN `500` | ICMS cobrado anteriormente por ST (recebido de terceiro) | Muito comum em revenda no Simples — não implementado |
| CSOSN `900` | Outros | Não implementado |

> Tentar usar qualquer `grupoIcms` fora dos três suportados hoje **não é validado
> explicitamente antes da geração do XML** — o gerador escreveria a tag literal do
> `grupoIcms` informado sem os campos obrigatórios daquele grupo (ex.: `ICMS60` sem
> `vBCSTRet`), o que a SEFAZ rejeitaria por schema (`cStat 215`/`225`, ver
> [manual-nfe.md § 10](manual-nfe.md#10-catálogo-de-códigos-de-rejeição)). Ou seja: hoje é
> responsabilidade de quem integra usar apenas os três grupos da seção 2 — expandir a
> validação de RVN para barrar grupos não suportados com uma mensagem clara (em vez de
> deixar a SEFAZ rejeitar por schema) é trabalho futuro natural.

## 4. PIS/COFINS

Análogo, mas mais restrito: só o grupo **"Aliq"** (alíquota comum) é suportado —
`PISAliq`/`COFINSAliq` com `CST` `01` (tributável a alíquota básica) fixo no gerador (ver
[manual-nfe.md § 3.1](manual-nfe.md#31-estrutura-do-payload)). CST `02` (alíquota
diferenciada), `04`/`05`/`06`/`07`/`08`/`09` (isenção/substituição tributária/outras
imunidades) e o grupo "NT" (não tributado, sem base/valor) não são suportados.

## 5. Exemplo por código (payload)

Usando a estrutura de `imposto` do [manual-nfe.md § 3.1](manual-nfe.md#31-estrutura-do-payload):

```json
// CST 00 — tributação integral (CRT 3)
"ICMS": { "ICMS00": { "orig": "0", "CST": "00", "modBC": 3, "vBC": 100.00, "pICMS": 18.00, "vICMS": 18.00 } }

// CST 40/41/50 — isenta / não tributada / suspensão (CRT 3), sem valores
"ICMS": { "ICMS40": { "orig": "0", "CST": "40" } }

// CSOSN 102/103/300/400 — Simples Nacional sem crédito (CRT 1/2/4), sem valores
"ICMS": { "ICMSSN": { "ICMSSN102": { "orig": "0", "CSOSN": "102" } } }
```

## 6. Cobertura de teste real (SEFAZ-PR homologação)

`Nfe50NotasPadraoRealTest` (ver [../src/test/java/com/fiscaladapter/homologacao](../src/test/java/com/fiscaladapter/homologacao))
emite **50 NF-e reais** contra a SEFAZ-PR de homologação, ciclando pelos 7 códigos da
seção 2 (o universo hoje suportado) com produtos/valores variados, para provar
empiricamente que a comunicação com a SEFAZ está de fato ocorrendo (mTLS, assinatura,
schema, interpretação de resposta) — não apenas em teste com servidor SOAP simulado.
Segue o mesmo mecanismo manual/opt-in de `SefazPrEmissaoNfeRealTest` (`@Tag
("homologacao-real")`, excluído do `mvn verify` por padrão, exige certificado real via
variável de ambiente) — ver o javadoc da classe para instruções de execução.

> **Resultado da execução real em 2026-09-09**: as 50 notas comunicaram-se de fato com a
> SEFAZ (nenhuma exceção de transporte/parsing — o que o teste garante). As três causas
> investigadas que impediam a autorização foram todas **resolvidas**:
>
> 1. **Endpoint normal de autorização da SEFAZ-PR devolvia HTTP 200 com corpo vazio**
>    (`Resposta da SEFAZ sem soap:Body`) — causa raiz: `NfeAutorizacaoClient` (usado por NFe
>    e NFC-e) e `NfeInutilizacaoClient` embutiam o XML assinado (que preserva a declaração
>    `<?xml ...?>`) no meio do envelope SOAP sem removê-la — XML mal formado que o servidor
>    antigo da SEFAZ-PR (JBossWeb) engolia silenciosamente, devolvendo 200 vazio em vez de
>    um erro. **Resolvido (FIS-114)**.
> 2. **Contingência SVC-RS devolvia HTTP 403 "Access is denied"** — usa o mesmo
>    `NfeAutorizacaoClient` do item 1; após o fix, deixou de ocorrer (o endpoint normal
>    passou a responder de primeira, sem precisar cair para a SVC-RS nas reexecuções
>    seguintes) — **resolvido junto com FIS-114**, mesma causa raiz.
> 3. **EPEC era rejeitado com `cStat 493 "Evento nao atende o Schema XML especifico"`** —
>    o XSD oficial publicamente disponível (nfephp-org/sped-nfe) mostra `vNF`/`vICMS` como
>    irmãos de `dest` dentro de `detEvento`, mas a SEFAZ real exige `vNF`/`vICMS`/`vST`
>    **dentro** de `dest` — confirmado contra o código-fonte do ACBr
>    (`TEventoNFe.Gerar_DestNFe`). **Resolvido (FIS-113)**.
>
> Uma reexecução do teste manual `SefazPrEmissaoNfeRealTest` após os três fixes recebeu, já
> na primeira tentativa (sem precisar de contingência nem EPEC), uma resposta de negócio
> legítima e nova: `cStat 434 "NFe sem indicativo do intermediador"` — o campo `indIntermed`
> (indicador de intermediador/marketplace), obrigatório por uma Nota Técnica mais recente,
> ainda não é gerado por este adapter (débito técnico novo, ver FIS-115).
>
> Ver o relatório impresso pelo próprio teste de 50 notas para o detalhe de cada tentativa.

## 7. Referências legais

- Convênio S/N, de 15/12/1970, Anexo — tabela de CST do ICMS.
- Ato COTEPE/ICMS 09/2008, Anexo — tabela de CSOSN.
- Manual de Orientação do Contribuinte (MOC) da NF-e, leiaute 4.00 — grupos de ICMS por
  CST/CSOSN.
