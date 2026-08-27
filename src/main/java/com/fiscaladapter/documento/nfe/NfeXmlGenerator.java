package com.fiscaladapter.documento.nfe;

import com.fiscaladapter.documento.TipoDocumentoFiscal;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * Gera o XML da NFe (layout 4.00) a partir do modelo de dominio.
 *
 * NOTA: a validacao estrutural contra o XSD oficial da SEFAZ (nfe_v4.00.xsd)
 * ainda nao esta plugada aqui - depende de obter o arquivo XSD oficial do
 * portal da NFe. Ate la, a corretude dos nomes/ordem das tags depende de
 * revisao manual contra a documentacao oficial antes de qualquer envio real.
 */
@Component
public class NfeXmlGenerator {

    private static final String VERSAO_LAYOUT = "4.00";
    private static final DateTimeFormatter DATA_EMISSAO_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");

    private final ChaveAcessoService chaveAcessoService;

    public NfeXmlGenerator(ChaveAcessoService chaveAcessoService) {
        this.chaveAcessoService = chaveAcessoService;
    }

    public String gerar(NotaFiscalEletronica nfe) {
        String chaveAcesso = chaveAcessoService.gerar(
                nfe.identificacao().uf(),
                nfe.identificacao().dataEmissao(),
                nfe.emitente().cnpjSemMascara(),
                chaveAcessoService.modeloPara(nfe.identificacao().tipoDocumento()),
                nfe.identificacao().serie(),
                nfe.identificacao().numero(),
                1
        );
        return gerar(nfe, chaveAcesso);
    }

    public String gerar(NotaFiscalEletronica nfe, String chaveAcesso) {
        try {
            StringWriter destino = new StringWriter();
            XMLStreamWriter xml = XMLOutputFactory.newInstance().createXMLStreamWriter(destino);

            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeStartElement("NFe");
            xml.writeDefaultNamespace("http://www.portalfiscal.inf.br/nfe");

            xml.writeStartElement("infNFe");
            xml.writeAttribute("versao", VERSAO_LAYOUT);
            xml.writeAttribute("Id", "NFe" + chaveAcesso);

            escreverIde(xml, nfe, chaveAcesso);
            escreverEmitente(xml, nfe.emitente());
            if (nfe.destinatario() != null) {
                escreverDestinatario(xml, nfe.destinatario());
            }
            for (ItemNota item : nfe.itens()) {
                escreverItem(xml, item);
            }
            escreverTotal(xml, nfe);
            escreverTransporte(xml);
            escreverPagamento(xml, nfe);

            xml.writeEndElement(); // infNFe
            xml.writeEndElement(); // NFe
            xml.writeEndDocument();
            xml.flush();

            return destino.toString();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("Falha ao gerar XML da NFe", e);
        }
    }

    private void escreverIde(XMLStreamWriter xml, NotaFiscalEletronica nfe, String chaveAcesso) throws XMLStreamException {
        IdentificacaoNfe ide = nfe.identificacao();
        boolean ehNfce = ide.tipoDocumento() == TipoDocumentoFiscal.NFCE;
        xml.writeStartElement("ide");
        tag(xml, "cUF", chaveAcesso.substring(0, 2));
        tag(xml, "cNF", chaveAcesso.substring(35, 43));
        tag(xml, "natOp", ide.naturezaOperacao());
        tag(xml, "mod", chaveAcessoService.modeloPara(ide.tipoDocumento()));
        tag(xml, "serie", String.valueOf(ide.serie()));
        tag(xml, "nNF", String.valueOf(ide.numero()));
        tag(xml, "dhEmi", ide.dataEmissao().atStartOfDay(java.time.ZoneId.systemDefault()).format(DATA_EMISSAO_FORMAT));
        tag(xml, "tpNF", "1"); // 1 = saida
        // sem destinatario (NFC-e para consumidor nao identificado): operacao sempre interna (mesma UF)
        boolean operacaoInterna = nfe.destinatario() == null || nfe.destinatario().endereco().uf().equals(ide.uf());
        tag(xml, "idDest", operacaoInterna ? "1" : "2");
        tag(xml, "cMunFG", ide.codigoMunicipioFatoGerador());
        tag(xml, "tpImp", ehNfce ? "4" : "1"); // NFC-e: 4 = DANFE NFC-e; NFe: 1 = retrato
        tag(xml, "tpEmis", chaveAcesso.substring(34, 35)); // extraido da chave, nao pode divergir dela (ver FIS-37)
        tag(xml, "cDV", chaveAcesso.substring(43));
        tag(xml, "tpAmb", String.valueOf(ide.ambiente().codigo()));
        tag(xml, "finNFe", String.valueOf(ide.finalidadeEmissao()));
        tag(xml, "indFinal", ide.consumidorFinal() ? "1" : "0");
        tag(xml, "indPres", ehNfce ? "1" : "9"); // NFC-e: 1 = operacao presencial; NFe: 9 = nao se aplica (emissao via API)
        tag(xml, "procEmi", "0"); // emissao por aplicativo do contribuinte
        tag(xml, "verProc", "1.0.0");
        xml.writeEndElement();
    }

    private void escreverEmitente(XMLStreamWriter xml, Emitente emitente) throws XMLStreamException {
        xml.writeStartElement("emit");
        tag(xml, "CNPJ", emitente.cnpjSemMascara());
        tag(xml, "xNome", emitente.razaoSocial());
        if (emitente.nomeFantasia() != null) {
            tag(xml, "xFant", emitente.nomeFantasia());
        }
        escreverEndereco(xml, "enderEmit", emitente.endereco());
        tag(xml, "IE", emitente.inscricaoEstadual());
        tag(xml, "CRT", emitente.regimeTributario());
        xml.writeEndElement();
    }

    private void escreverDestinatario(XMLStreamWriter xml, Destinatario destinatario) throws XMLStreamException {
        xml.writeStartElement("dest");
        tag(xml, destinatario.ehPessoaJuridica() ? "CNPJ" : "CPF", destinatario.documentoSemMascara());
        tag(xml, "xNome", destinatario.razaoSocial());
        escreverEndereco(xml, "enderDest", destinatario.endereco());
        tag(xml, "indIEDest", destinatario.indicadorInscricaoEstadual());
        if (destinatario.inscricaoEstadual() != null) {
            tag(xml, "IE", destinatario.inscricaoEstadual());
        }
        if (destinatario.email() != null) {
            tag(xml, "email", destinatario.email());
        }
        xml.writeEndElement();
    }

    private void escreverEndereco(XMLStreamWriter xml, String tagPai, Endereco endereco) throws XMLStreamException {
        xml.writeStartElement(tagPai);
        tag(xml, "xLgr", endereco.logradouro());
        tag(xml, "nro", endereco.numero());
        tag(xml, "xBairro", endereco.bairro());
        tag(xml, "cMun", endereco.codigoMunicipio());
        tag(xml, "xMun", endereco.municipio());
        tag(xml, "UF", endereco.uf());
        tag(xml, "CEP", endereco.cep());
        if (endereco.telefone() != null) {
            tag(xml, "fone", endereco.telefone());
        }
        xml.writeEndElement();
    }

    private void escreverItem(XMLStreamWriter xml, ItemNota item) throws XMLStreamException {
        xml.writeStartElement("det");
        xml.writeAttribute("nItem", String.valueOf(item.numero()));

        xml.writeStartElement("prod");
        tag(xml, "cProd", item.codigoProduto());
        tag(xml, "cEAN", "SEM GTIN");
        tag(xml, "xProd", item.descricao());
        tag(xml, "NCM", item.ncm());
        tag(xml, "CFOP", item.cfop());
        tag(xml, "uCom", item.unidadeComercial());
        tag(xml, "qCom", quantidade(item.quantidade()));
        tag(xml, "vUnCom", quantidade(item.valorUnitario()));
        tag(xml, "vProd", moeda(item.valorTotal()));
        tag(xml, "cEANTrib", "SEM GTIN");
        tag(xml, "uTrib", item.unidadeComercial());
        tag(xml, "qTrib", quantidade(item.quantidade()));
        tag(xml, "vUnTrib", quantidade(item.valorUnitario()));
        tag(xml, "indTot", "1");
        xml.writeEndElement(); // prod

        xml.writeStartElement("imposto");
        escreverIcms(xml, item.imposto());
        if (item.imposto().valorIpi().compareTo(BigDecimal.ZERO) > 0) {
            escreverIpi(xml, item);
        }
        xml.writeStartElement("PIS");
        xml.writeStartElement("PISAliq");
        tag(xml, "CST", "01");
        tag(xml, "vBC", moeda(item.valorTotal()));
        tag(xml, "pPIS", percentualSobre(item.imposto().valorPis(), item.valorTotal()));
        tag(xml, "vPIS", moeda(item.imposto().valorPis()));
        xml.writeEndElement();
        xml.writeEndElement();
        xml.writeStartElement("COFINS");
        xml.writeStartElement("COFINSAliq");
        tag(xml, "CST", "01");
        tag(xml, "vBC", moeda(item.valorTotal()));
        tag(xml, "pCOFINS", percentualSobre(item.imposto().valorCofins(), item.valorTotal()));
        tag(xml, "vCOFINS", moeda(item.imposto().valorCofins()));
        xml.writeEndElement();
        xml.writeEndElement();
        xml.writeEndElement(); // imposto

        xml.writeEndElement(); // det
    }

    private void escreverIcms(XMLStreamWriter xml, ImpostoItem imposto) throws XMLStreamException {
        xml.writeStartElement("ICMS");
        xml.writeStartElement(imposto.grupoIcms());
        tag(xml, "orig", imposto.origemIcms());
        tag(xml, imposto.grupoIcms().startsWith("ICMSSN") ? "CSOSN" : "CST", imposto.codigoIcms());
        if (imposto.grupoIcms().equals("ICMS00")) {
            tag(xml, "modBC", "3"); // 3 = valor da operacao
            tag(xml, "vBC", moeda(imposto.baseCalculoIcms()));
            tag(xml, "pICMS", percentual(imposto.aliquotaIcms()));
            tag(xml, "vICMS", moeda(imposto.valorIcms()));
        }
        // ICMS40 (isenta/nao tributada/suspensao) e ICMSSN102 (Simples Nacional sem
        // permissao de credito) nao levam base/aliquota/valor - so orig + CST/CSOSN.
        xml.writeEndElement();
        xml.writeEndElement();
    }

    private void escreverIpi(XMLStreamWriter xml, ItemNota item) throws XMLStreamException {
        xml.writeStartElement("IPI");
        tag(xml, "cEnq", "999"); // 999 = nao enquadrado em incentivo especifico
        xml.writeStartElement("IPITrib");
        tag(xml, "CST", "50"); // 50 = saida tributada
        tag(xml, "vBC", moeda(item.valorTotal()));
        tag(xml, "pIPI", percentualSobre(item.imposto().valorIpi(), item.valorTotal()));
        tag(xml, "vIPI", moeda(item.imposto().valorIpi()));
        xml.writeEndElement();
        xml.writeEndElement();
    }

    /** TDec_1302: "0" para zero, senao exatamente 2 casas decimais. */
    private String moeda(BigDecimal valor) {
        return comCasas(valor, 2);
    }

    /** TDec_0302a04: "0" para zero, senao 2 a 4 casas decimais (usamos sempre 4). */
    private String percentual(BigDecimal valor) {
        return comCasas(valor, 4);
    }

    /** TDec_1104v/TDec_1110v: "0" para zero, senao 1 a N casas decimais (usamos sempre 4). */
    private String quantidade(BigDecimal valor) {
        return comCasas(valor, 4);
    }

    private String comCasas(BigDecimal valor, int casas) {
        if (valor.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        return valor.setScale(casas, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String percentualSobre(BigDecimal valor, BigDecimal base) {
        if (base.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        return percentual(valor.multiply(BigDecimal.valueOf(100)).divide(base, 4, java.math.RoundingMode.HALF_UP));
    }

    private void escreverTotal(XMLStreamWriter xml, NotaFiscalEletronica nfe) throws XMLStreamException {
        String zero = "0.00";
        xml.writeStartElement("total");
        xml.writeStartElement("ICMSTot");
        tag(xml, "vBC", moeda(nfe.itens().stream().map(i -> i.imposto().baseCalculoIcms())
                .reduce(BigDecimal.ZERO, BigDecimal::add)));
        tag(xml, "vICMS", moeda(nfe.valorTotalIcms()));
        tag(xml, "vICMSDeson", zero);
        tag(xml, "vFCP", zero);
        tag(xml, "vBCST", zero);
        tag(xml, "vST", zero);
        tag(xml, "vFCPST", zero);
        tag(xml, "vFCPSTRet", zero);
        tag(xml, "vProd", moeda(nfe.valorTotalProdutos()));
        tag(xml, "vFrete", zero);
        tag(xml, "vSeg", zero);
        tag(xml, "vDesc", zero);
        tag(xml, "vII", zero);
        tag(xml, "vIPI", moeda(nfe.valorTotalIpi()));
        tag(xml, "vIPIDevol", zero);
        tag(xml, "vPIS", moeda(nfe.valorTotalPis()));
        tag(xml, "vCOFINS", moeda(nfe.valorTotalCofins()));
        tag(xml, "vOutro", zero);
        tag(xml, "vNF", moeda(nfe.valorTotalNota()));
        tag(xml, "vTotTrib", moeda(nfe.valorTotalTributos()));
        xml.writeEndElement();
        xml.writeEndElement();
    }

    private void escreverTransporte(XMLStreamWriter xml) throws XMLStreamException {
        xml.writeStartElement("transp");
        tag(xml, "modFrete", "9"); // 9 = sem transporte (default seguro; deve ser parametrizavel no JSON de entrada)
        xml.writeEndElement();
    }

    private void escreverPagamento(XMLStreamWriter xml, NotaFiscalEletronica nfe) throws XMLStreamException {
        xml.writeStartElement("pag");
        for (DetalhePagamento pagamento : nfe.pagamentos()) {
            xml.writeStartElement("detPag");
            tag(xml, "tPag", pagamento.codigoFormaPagamento());
            tag(xml, "vPag", moeda(pagamento.valor()));
            xml.writeEndElement();
        }
        xml.writeEndElement();
    }

    private void tag(XMLStreamWriter xml, String nome, String valor) throws XMLStreamException {
        xml.writeStartElement(nome);
        xml.writeCharacters(valor);
        xml.writeEndElement();
    }
}
