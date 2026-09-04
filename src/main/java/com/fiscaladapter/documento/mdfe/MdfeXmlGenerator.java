package com.fiscaladapter.documento.mdfe;

import com.fiscaladapter.documento.FusoHorarioFiscal;
import com.fiscaladapter.documento.TipoDocumentoFiscal;
import com.fiscaladapter.documento.nfe.ChaveAcessoService;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

/**
 * Gera o XML do MDF-e (layout 3.00, modelo 58), modal rodoviario, emissao Normal.
 * Mesmo estilo de geracao por XMLStreamWriter e mesmo layout de chave de
 * acesso ja usados em NFe/NFC-e/CT-e.
 */
@Component
public class MdfeXmlGenerator {

    private static final String VERSAO_LAYOUT = "3.00";
    private static final String VERSAO_MODAL_RODOVIARIO = "3.00";
    private static final DateTimeFormatter DATA_EMISSAO_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");

    private final ChaveAcessoService chaveAcessoService;

    public MdfeXmlGenerator(ChaveAcessoService chaveAcessoService) {
        this.chaveAcessoService = chaveAcessoService;
    }

    /** Versao do layout MDF-e suportada por este adapter (FIS-27) - hoje uma unica versao fixa. */
    public String versaoLayout() {
        return VERSAO_LAYOUT;
    }

    public String gerar(Mdfe mdfe) {
        String chaveAcesso = chaveAcessoService.gerar(
                mdfe.identificacao().uf(),
                mdfe.identificacao().dataEmissao(),
                mdfe.emitente().cnpjSemMascara(),
                chaveAcessoService.modeloPara(TipoDocumentoFiscal.MDFE),
                mdfe.identificacao().serie(),
                mdfe.identificacao().numero(),
                1
        );
        return gerar(mdfe, chaveAcesso);
    }

    public String gerar(Mdfe mdfe, String chaveAcesso) {
        try {
            StringWriter destino = new StringWriter();
            XMLStreamWriter xml = XMLOutputFactory.newInstance().createXMLStreamWriter(destino);

            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeStartElement("MDFe");
            xml.writeDefaultNamespace("http://www.portalfiscal.inf.br/mdfe");

            xml.writeStartElement("infMDFe");
            xml.writeAttribute("versao", VERSAO_LAYOUT);
            xml.writeAttribute("Id", "MDFe" + chaveAcesso);

            escreverIde(xml, mdfe, chaveAcesso);
            escreverEmitente(xml, mdfe.emitente());
            escreverInfModal(xml, mdfe);
            escreverInfDoc(xml, mdfe);
            escreverTotais(xml, mdfe);

            xml.writeEndElement(); // infMDFe
            xml.writeEndElement(); // MDFe
            xml.writeEndDocument();
            xml.flush();

            return destino.toString();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("Falha ao gerar XML do MDF-e", e);
        }
    }

    private void escreverIde(XMLStreamWriter xml, Mdfe mdfe, String chaveAcesso) throws XMLStreamException {
        IdentificacaoMdfe ide = mdfe.identificacao();
        xml.writeStartElement("ide");
        tag(xml, "cUF", chaveAcesso.substring(0, 2));
        tag(xml, "tpAmb", String.valueOf(ide.ambiente().codigo()));
        tag(xml, "tpEmit", "1"); // 1 = prestador de servico de transporte
        tag(xml, "mod", chaveAcessoService.modeloPara(TipoDocumentoFiscal.MDFE));
        tag(xml, "serie", String.valueOf(ide.serie()));
        tag(xml, "nMDF", String.valueOf(ide.numero()));
        tag(xml, "cMDF", chaveAcesso.substring(35, 43));
        tag(xml, "cDV", chaveAcesso.substring(43));
        tag(xml, "modal", "1"); // 1 = rodoviario (unico modal suportado nesta fase - ver Mdfe.java)
        tag(xml, "dhEmi", ide.dataEmissao().atStartOfDay(FusoHorarioFiscal.BRASIL).format(DATA_EMISSAO_FORMAT));
        tag(xml, "tpEmis", chaveAcesso.substring(34, 35));
        tag(xml, "procEmi", "0"); // emissao por aplicativo do contribuinte
        tag(xml, "verProc", "1.0.0");
        tag(xml, "UFIni", ide.ufInicio());
        tag(xml, "UFFim", ide.ufFim());
        xml.writeStartElement("infMunCarrega");
        tag(xml, "cMunCarrega", ide.codigoMunicipioCarregamento());
        tag(xml, "xMunCarrega", ide.municipioCarregamento());
        xml.writeEndElement(); // infMunCarrega
        xml.writeEndElement(); // ide
    }

    private void escreverEmitente(XMLStreamWriter xml, EmitenteMdfe emitente) throws XMLStreamException {
        xml.writeStartElement("emit");
        tag(xml, "CNPJ", emitente.cnpjSemMascara());
        if (emitente.inscricaoEstadual() != null) {
            tag(xml, "IE", emitente.inscricaoEstadual());
        }
        tag(xml, "xNome", emitente.razaoSocial());
        if (emitente.nomeFantasia() != null) {
            tag(xml, "xFant", emitente.nomeFantasia());
        }
        xml.writeStartElement("enderEmit");
        tag(xml, "xLgr", emitente.endereco().logradouro());
        tag(xml, "nro", emitente.endereco().numero());
        tag(xml, "xBairro", emitente.endereco().bairro());
        tag(xml, "cMun", emitente.endereco().codigoMunicipio());
        tag(xml, "xMun", emitente.endereco().municipio());
        if (emitente.endereco().cep() != null) {
            tag(xml, "CEP", emitente.endereco().cep());
        }
        tag(xml, "UF", emitente.endereco().uf());
        if (emitente.endereco().telefone() != null) {
            tag(xml, "fone", emitente.endereco().telefone());
        }
        xml.writeEndElement(); // enderEmit
        xml.writeEndElement(); // emit
    }

    private void escreverInfModal(XMLStreamWriter xml, Mdfe mdfe) throws XMLStreamException {
        xml.writeStartElement("infModal");
        xml.writeAttribute("versaoModal", VERSAO_MODAL_RODOVIARIO);

        xml.writeStartElement("rodo");
        if (mdfe.rntrc() != null) {
            xml.writeStartElement("infANTT");
            tag(xml, "RNTRC", mdfe.rntrc());
            xml.writeEndElement(); // infANTT
        }
        xml.writeStartElement("veicTracao");
        tag(xml, "placa", mdfe.veiculoTracao().placa());
        tag(xml, "tara", inteiro(mdfe.veiculoTracao().taraKg()));
        for (Condutor condutor : mdfe.condutores()) {
            xml.writeStartElement("condutor");
            tag(xml, "xNome", condutor.nome());
            tag(xml, "CPF", condutor.cpfSemMascara());
            xml.writeEndElement(); // condutor
        }
        tag(xml, "tpRod", mdfe.veiculoTracao().tipoRodado());
        tag(xml, "tpCar", mdfe.veiculoTracao().tipoCarroceria());
        if (mdfe.veiculoTracao().ufLicenciamento() != null) {
            tag(xml, "UF", mdfe.veiculoTracao().ufLicenciamento());
        }
        xml.writeEndElement(); // veicTracao
        xml.writeEndElement(); // rodo

        xml.writeEndElement(); // infModal
    }

    private void escreverInfDoc(XMLStreamWriter xml, Mdfe mdfe) throws XMLStreamException {
        xml.writeStartElement("infDoc");
        xml.writeStartElement("infMunDescarga");
        tag(xml, "cMunDescarga", mdfe.codigoMunicipioDescarga());
        tag(xml, "xMunDescarga", mdfe.municipioDescarga());
        for (String chaveCte : mdfe.chavesCteTransportados()) {
            xml.writeStartElement("infCTe");
            tag(xml, "chCTe", chaveCte);
            xml.writeEndElement();
        }
        for (String chaveNfe : mdfe.chavesNfeTransportadas()) {
            xml.writeStartElement("infNFe");
            tag(xml, "chNFe", chaveNfe);
            xml.writeEndElement();
        }
        xml.writeEndElement(); // infMunDescarga
        xml.writeEndElement(); // infDoc
    }

    private void escreverTotais(XMLStreamWriter xml, Mdfe mdfe) throws XMLStreamException {
        xml.writeStartElement("tot");
        if (!mdfe.chavesCteTransportados().isEmpty()) {
            tag(xml, "qCTe", String.valueOf(mdfe.chavesCteTransportados().size()));
        }
        if (!mdfe.chavesNfeTransportadas().isEmpty()) {
            tag(xml, "qNFe", String.valueOf(mdfe.chavesNfeTransportadas().size()));
        }
        tag(xml, "vCarga", moeda(mdfe.valorCarga()));
        tag(xml, "cUnid", "01"); // 01 = KG
        tag(xml, "qCarga", quantidade(mdfe.pesoBrutoKg()));
        xml.writeEndElement(); // tot
    }

    private String moeda(BigDecimal valor) {
        return comCasas(valor, 2);
    }

    private String quantidade(BigDecimal valor) {
        return comCasas(valor, 4);
    }

    /** tara/capKG do MDF-e sao inteiros (sem casas decimais), diferente dos demais campos monetarios/quantidade. */
    private String inteiro(BigDecimal valor) {
        return valor.setScale(0, RoundingMode.HALF_UP).toPlainString();
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
