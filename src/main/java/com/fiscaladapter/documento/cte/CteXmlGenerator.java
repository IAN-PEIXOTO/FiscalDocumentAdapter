package com.fiscaladapter.documento.cte;

import com.fiscaladapter.documento.TipoDocumentoFiscal;
import com.fiscaladapter.documento.nfe.ChaveAcessoService;
import com.fiscaladapter.documento.nfe.Emitente;
import com.fiscaladapter.documento.nfe.Endereco;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

/**
 * Gera o XML do CT-e (layout 4.00, modelo 57), modal rodoviario, tipo Normal.
 * Reaproveita o mesmo layout de chave de acesso e o mesmo estilo de geracao
 * por XMLStreamWriter usado no NfeXmlGenerator.
 */
@Component
public class CteXmlGenerator {

    private static final String VERSAO_LAYOUT = "4.00";
    private static final String VERSAO_MODAL_RODOVIARIO = "4.00";
    private static final DateTimeFormatter DATA_EMISSAO_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");

    private final ChaveAcessoService chaveAcessoService;

    public CteXmlGenerator(ChaveAcessoService chaveAcessoService) {
        this.chaveAcessoService = chaveAcessoService;
    }

    public String gerar(Cte cte) {
        String chaveAcesso = chaveAcessoService.gerar(
                cte.identificacao().uf(),
                cte.identificacao().dataEmissao(),
                cte.emitente().cnpjSemMascara(),
                chaveAcessoService.modeloPara(TipoDocumentoFiscal.CTE),
                cte.identificacao().serie(),
                cte.identificacao().numero(),
                1
        );
        return gerar(cte, chaveAcesso);
    }

    public String gerar(Cte cte, String chaveAcesso) {
        try {
            StringWriter destino = new StringWriter();
            XMLStreamWriter xml = XMLOutputFactory.newInstance().createXMLStreamWriter(destino);

            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeStartElement("CTe");
            xml.writeDefaultNamespace("http://www.portalfiscal.inf.br/cte");

            xml.writeStartElement("infCte");
            xml.writeAttribute("versao", VERSAO_LAYOUT);
            xml.writeAttribute("Id", "CTe" + chaveAcesso);

            escreverIde(xml, cte, chaveAcesso);
            escreverEmitente(xml, cte.emitente());
            if (cte.remetente() != null) {
                escreverParticipante(xml, "rem", "enderReme", cte.remetente());
            }
            if (cte.destinatario() != null) {
                escreverParticipante(xml, "dest", "enderDest", cte.destinatario());
            }
            escreverValoresPrestacao(xml, cte);
            escreverImposto(xml, cte.imposto());
            escreverInfCteNorm(xml, cte);

            xml.writeEndElement(); // infCte
            xml.writeEndElement(); // CTe
            xml.writeEndDocument();
            xml.flush();

            return destino.toString();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("Falha ao gerar XML do CT-e", e);
        }
    }

    private void escreverIde(XMLStreamWriter xml, Cte cte, String chaveAcesso) throws XMLStreamException {
        IdentificacaoCte ide = cte.identificacao();
        xml.writeStartElement("ide");
        tag(xml, "cUF", chaveAcesso.substring(0, 2));
        tag(xml, "cCT", chaveAcesso.substring(35, 43));
        tag(xml, "CFOP", ide.cfop());
        tag(xml, "natOp", ide.naturezaOperacao());
        tag(xml, "mod", chaveAcessoService.modeloPara(TipoDocumentoFiscal.CTE));
        tag(xml, "serie", String.valueOf(ide.serie()));
        tag(xml, "nCT", String.valueOf(ide.numero()));
        tag(xml, "dhEmi", ide.dataEmissao().atStartOfDay(java.time.ZoneId.systemDefault()).format(DATA_EMISSAO_FORMAT));
        tag(xml, "tpImp", "1"); // 1 = retrato
        tag(xml, "tpEmis", chaveAcesso.substring(34, 35));
        tag(xml, "cDV", chaveAcesso.substring(43));
        tag(xml, "tpAmb", String.valueOf(ide.ambiente().codigo()));
        tag(xml, "tpCTe", "0"); // 0 = CT-e normal
        tag(xml, "procEmi", "0"); // emissao por aplicativo do contribuinte
        tag(xml, "verProc", "1.0.0");
        tag(xml, "cMunEnv", ide.codigoMunicipioInicio());
        tag(xml, "xMunEnv", ide.municipioInicio());
        tag(xml, "UFEnv", ide.ufInicio());
        tag(xml, "modal", "01"); // 01 = rodoviario (unico modal suportado nesta fase - ver Cte.java)
        tag(xml, "tpServ", "0"); // 0 = normal
        tag(xml, "cMunIni", ide.codigoMunicipioInicio());
        tag(xml, "xMunIni", ide.municipioInicio());
        tag(xml, "UFIni", ide.ufInicio());
        tag(xml, "cMunFim", ide.codigoMunicipioFim());
        tag(xml, "xMunFim", ide.municipioFim());
        tag(xml, "UFFim", ide.ufFim());
        tag(xml, "retira", "1"); // 1 = recebedor nao retira no local (destino usual: entrega no endereco do destinatario)
        tag(xml, "indIEToma", "1"); // 1 = tomador contribuinte do ICMS
        xml.writeStartElement("toma3");
        tag(xml, "toma", cte.tomador().codigo());
        xml.writeEndElement(); // toma3
        xml.writeEndElement(); // ide
    }

    private void escreverEmitente(XMLStreamWriter xml, Emitente emitente) throws XMLStreamException {
        xml.writeStartElement("emit");
        tag(xml, "CNPJ", emitente.cnpjSemMascara());
        tag(xml, "IE", emitente.inscricaoEstadual());
        tag(xml, "xNome", emitente.razaoSocial());
        if (emitente.nomeFantasia() != null) {
            tag(xml, "xFant", emitente.nomeFantasia());
        }
        escreverEndereco(xml, "enderEmit", emitente.endereco());
        tag(xml, "CRT", emitente.regimeTributario());
        xml.writeEndElement();
    }

    private void escreverParticipante(XMLStreamWriter xml, String tagPai, String tagEndereco, ParticipanteCte participante)
            throws XMLStreamException {
        xml.writeStartElement(tagPai);
        tag(xml, participante.ehPessoaJuridica() ? "CNPJ" : "CPF", participante.documentoSemMascara());
        if (participante.inscricaoEstadual() != null) {
            tag(xml, "IE", participante.inscricaoEstadual());
        }
        tag(xml, "xNome", participante.razaoSocial());
        escreverEndereco(xml, tagEndereco, participante.endereco());
        if (participante.email() != null) {
            tag(xml, "email", participante.email());
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
        if (endereco.cep() != null) {
            tag(xml, "CEP", endereco.cep());
        }
        tag(xml, "UF", endereco.uf());
        if (endereco.telefone() != null) {
            tag(xml, "fone", endereco.telefone());
        }
        xml.writeEndElement();
    }

    private void escreverValoresPrestacao(XMLStreamWriter xml, Cte cte) throws XMLStreamException {
        xml.writeStartElement("vPrest");
        tag(xml, "vTPrest", moeda(cte.valorTotalPrestacao()));
        tag(xml, "vRec", moeda(cte.valorAReceber()));
        xml.writeEndElement();
    }

    private void escreverImposto(XMLStreamWriter xml, ImpostoCte imposto) throws XMLStreamException {
        xml.writeStartElement("imp");
        xml.writeStartElement("ICMS");
        xml.writeStartElement("ICMS00");
        tag(xml, "CST", "00");
        tag(xml, "vBC", moeda(imposto.baseCalculoIcms()));
        tag(xml, "pICMS", aliquota(imposto.aliquotaIcms()));
        tag(xml, "vICMS", moeda(imposto.valorIcms()));
        xml.writeEndElement(); // ICMS00
        xml.writeEndElement(); // ICMS
        xml.writeEndElement(); // imp
    }

    private void escreverInfCteNorm(XMLStreamWriter xml, Cte cte) throws XMLStreamException {
        xml.writeStartElement("infCTeNorm");

        xml.writeStartElement("infCarga");
        tag(xml, "vCarga", moeda(cte.informacaoCarga().valorCarga()));
        tag(xml, "proPred", cte.informacaoCarga().produtoPredominante());
        xml.writeStartElement("infQ");
        tag(xml, "cUnid", "01"); // 01 = KG
        tag(xml, "tpMed", "PESO BRUTO");
        tag(xml, "qCarga", quantidade(cte.informacaoCarga().pesoBrutoKg()));
        xml.writeEndElement(); // infQ
        xml.writeEndElement(); // infCarga

        if (!cte.notasFiscaisTransportadas().isEmpty()) {
            xml.writeStartElement("infDoc");
            for (NotaFiscalTransportada nota : cte.notasFiscaisTransportadas()) {
                xml.writeStartElement("infNFe");
                tag(xml, "chave", nota.chaveAcesso());
                xml.writeEndElement();
            }
            xml.writeEndElement(); // infDoc
        }

        xml.writeStartElement("infModal");
        xml.writeAttribute("versaoModal", VERSAO_MODAL_RODOVIARIO);
        xml.writeStartElement("rodo");
        tag(xml, "RNTRC", cte.rntrc());
        xml.writeEndElement(); // rodo
        xml.writeEndElement(); // infModal

        xml.writeEndElement(); // infCTeNorm
    }

    private String moeda(BigDecimal valor) {
        return comCasas(valor, 2);
    }

    /** TDec_0302: aliquota do ICMS do CT-e, sempre 2 casas decimais (diferente da NFe, que usa TDec_0302a04 com 4 casas). */
    private String aliquota(BigDecimal valor) {
        return comCasas(valor, 2);
    }

    private String quantidade(BigDecimal valor) {
        return comCasas(valor, 4);
    }

    private String comCasas(BigDecimal valor, int casas) {
        if (valor.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        return valor.setScale(casas, RoundingMode.HALF_UP).toPlainString();
    }

    private void tag(XMLStreamWriter xml, String nome, String valor) throws XMLStreamException {
        xml.writeStartElement(nome);
        xml.writeCharacters(valor);
        xml.writeEndElement();
    }
}
