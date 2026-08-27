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
                chaveAcessoService.modeloPara(TipoDocumentoFiscal.NFE),
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
            escreverDestinatario(xml, nfe.destinatario());
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
        xml.writeStartElement("ide");
        tag(xml, "cUF", chaveAcesso.substring(0, 2));
        tag(xml, "cNF", chaveAcesso.substring(35, 43));
        tag(xml, "natOp", ide.naturezaOperacao());
        tag(xml, "mod", chaveAcessoService.modeloPara(TipoDocumentoFiscal.NFE));
        tag(xml, "serie", String.valueOf(ide.serie()));
        tag(xml, "nNF", String.valueOf(ide.numero()));
        tag(xml, "dhEmi", ide.dataEmissao().atStartOfDay(java.time.ZoneId.systemDefault()).format(DATA_EMISSAO_FORMAT));
        tag(xml, "tpNF", "1"); // 1 = saida
        tag(xml, "idDest", nfe.destinatario().endereco().uf().equals(ide.uf()) ? "1" : "2");
        tag(xml, "cMunFG", ide.codigoMunicipioFatoGerador());
        tag(xml, "tpImp", "1"); // DANFE retrato
        tag(xml, "tpEmis", "1"); // emissao normal
        tag(xml, "cDV", chaveAcesso.substring(43));
        tag(xml, "tpAmb", String.valueOf(ide.ambiente().codigo()));
        tag(xml, "finNFe", String.valueOf(ide.finalidadeEmissao()));
        tag(xml, "indFinal", ide.consumidorFinal() ? "1" : "0");
        tag(xml, "indPres", "9"); // 9 = nao se aplica (ex.: emissao via API/integracao)
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
        tag(xml, "xProd", item.descricao());
        tag(xml, "NCM", item.ncm());
        tag(xml, "CFOP", item.cfop());
        tag(xml, "uCom", item.unidadeComercial());
        tag(xml, "qCom", item.quantidade().toPlainString());
        tag(xml, "vUnCom", item.valorUnitario().toPlainString());
        tag(xml, "vProd", item.valorTotal().toPlainString());
        xml.writeEndElement(); // prod

        xml.writeStartElement("imposto");
        escreverIcms(xml, item.imposto());
        if (item.imposto().valorIpi().compareTo(BigDecimal.ZERO) > 0) {
            xml.writeStartElement("IPI");
            tag(xml, "vIPI", item.imposto().valorIpi().toPlainString());
            xml.writeEndElement();
        }
        xml.writeStartElement("PIS");
        tag(xml, "vPIS", item.imposto().valorPis().toPlainString());
        xml.writeEndElement();
        xml.writeStartElement("COFINS");
        tag(xml, "vCOFINS", item.imposto().valorCofins().toPlainString());
        xml.writeEndElement();
        xml.writeEndElement(); // imposto

        xml.writeEndElement(); // det
    }

    private void escreverIcms(XMLStreamWriter xml, ImpostoItem imposto) throws XMLStreamException {
        xml.writeStartElement("ICMS");
        xml.writeStartElement("ICMS" + imposto.cstIcms());
        tag(xml, "orig", imposto.origemIcms());
        tag(xml, "CST", imposto.cstIcms());
        tag(xml, "vBC", imposto.baseCalculoIcms().toPlainString());
        tag(xml, "pICMS", imposto.aliquotaIcms().toPlainString());
        tag(xml, "vICMS", imposto.valorIcms().toPlainString());
        xml.writeEndElement();
        xml.writeEndElement();
    }

    private void escreverTotal(XMLStreamWriter xml, NotaFiscalEletronica nfe) throws XMLStreamException {
        xml.writeStartElement("total");
        xml.writeStartElement("ICMSTot");
        tag(xml, "vBC", nfe.itens().stream().map(i -> i.imposto().baseCalculoIcms())
                .reduce(BigDecimal.ZERO, BigDecimal::add).toPlainString());
        tag(xml, "vICMS", nfe.valorTotalIcms().toPlainString());
        tag(xml, "vProd", nfe.valorTotalProdutos().toPlainString());
        tag(xml, "vIPI", nfe.valorTotalIpi().toPlainString());
        tag(xml, "vNF", nfe.valorTotalNota().toPlainString());
        tag(xml, "vTotTrib", nfe.valorTotalTributos().toPlainString());
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
            tag(xml, "vPag", pagamento.valor().toPlainString());
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
