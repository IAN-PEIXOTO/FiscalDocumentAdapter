package com.fiscaladapter.documento.nfse.abrasf;

import com.fiscaladapter.documento.nfse.DadosServicoNfse;
import com.fiscaladapter.documento.nfse.EnderecoNfse;
import com.fiscaladapter.documento.nfse.Nfse;
import com.fiscaladapter.documento.nfse.NfseXmlGenerator;
import com.fiscaladapter.documento.nfse.PadraoNfse;
import com.fiscaladapter.documento.nfse.PrestadorServicoNfse;
import com.fiscaladapter.documento.nfse.TomadorServicoNfse;
import com.fiscaladapter.documento.nfse.ValoresServicoNfse;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Gera o XML do RPS (Recibo Provisorio de Servicos) no padrao ABRASF v2.01
 * (schema oficial obtido de flexait/nfse, mesmo modelo publicado pela
 * ABRASF - Associacao Brasileira das Secretarias de Financas das Capitais).
 * Gera o envelope GerarNfseEnvio/Rps, a operacao de envio individual e
 * sincrono - a maioria dos municipios tambem aceita EnviarLoteRpsEnvio para
 * lotes, que fica para uma evolucao futura desta capacidade (FIS-21 cuida da
 * comunicacao com os webservices municipais, nao deste gerador de XML).
 */
@Component
public class AbrasfNfseXmlGenerator implements NfseXmlGenerator {

    @Override
    public PadraoNfse padraoSuportado() {
        return PadraoNfse.ABRASF_V2_01;
    }

    @Override
    public String gerar(Nfse nfse) {
        try {
            StringWriter destino = new StringWriter();
            XMLStreamWriter xml = XMLOutputFactory.newInstance().createXMLStreamWriter(destino);

            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeStartElement("GerarNfseEnvio");
            xml.writeDefaultNamespace("http://www.abrasf.org.br/nfse.xsd");

            xml.writeStartElement("Rps");
            xml.writeStartElement("InfDeclaracaoPrestacaoServico");

            escreverInfRps(xml, nfse);
            tagData(xml, "Competencia", nfse.competencia());
            escreverServico(xml, nfse.servico());
            escreverPrestador(xml, nfse.prestador());
            if (nfse.tomador() != null) {
                escreverTomador(xml, nfse.tomador());
            }
            tag(xml, "OptanteSimplesNacional", simNao(nfse.optanteSimplesNacional()));
            tag(xml, "IncentivoFiscal", simNao(nfse.incentivoFiscal()));

            xml.writeEndElement(); // InfDeclaracaoPrestacaoServico
            xml.writeEndElement(); // Rps
            xml.writeEndElement(); // GerarNfseEnvio
            xml.writeEndDocument();
            xml.flush();

            return destino.toString();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("Falha ao gerar XML do RPS (ABRASF)", e);
        }
    }

    private void escreverInfRps(XMLStreamWriter xml, Nfse nfse) throws XMLStreamException {
        xml.writeStartElement("Rps");
        xml.writeStartElement("IdentificacaoRps");
        tag(xml, "Numero", String.valueOf(nfse.rps().identificacao().numero()));
        tag(xml, "Serie", nfse.rps().identificacao().serie());
        tag(xml, "Tipo", String.valueOf(nfse.rps().identificacao().tipo().codigo()));
        xml.writeEndElement(); // IdentificacaoRps
        tagData(xml, "DataEmissao", nfse.rps().dataEmissao());
        tag(xml, "Status", String.valueOf(nfse.rps().status()));
        xml.writeEndElement(); // Rps (tcInfRps)
    }

    private void escreverServico(XMLStreamWriter xml, DadosServicoNfse servico) throws XMLStreamException {
        xml.writeStartElement("Servico");
        escreverValores(xml, servico.valores());
        tag(xml, "IssRetido", simNao(servico.issRetido()));
        tag(xml, "ItemListaServico", servico.itemListaServico());
        tag(xml, "Discriminacao", servico.discriminacao());
        tag(xml, "CodigoMunicipio", servico.codigoMunicipioPrestacao());
        tag(xml, "ExigibilidadeISS", String.valueOf(servico.exigibilidadeIss()));
        xml.writeEndElement(); // Servico
    }

    private void escreverValores(XMLStreamWriter xml, ValoresServicoNfse valores) throws XMLStreamException {
        xml.writeStartElement("Valores");
        tag(xml, "ValorServicos", moeda(valores.valorServicos()));
        if (valores.valorDeducoes() != null) {
            tag(xml, "ValorDeducoes", moeda(valores.valorDeducoes()));
        }
        if (valores.valorIss() != null) {
            tag(xml, "ValorIss", moeda(valores.valorIss()));
        }
        if (valores.aliquota() != null) {
            tag(xml, "Aliquota", aliquota(valores.aliquota()));
        }
        xml.writeEndElement(); // Valores
    }

    private void escreverPrestador(XMLStreamWriter xml, PrestadorServicoNfse prestador) throws XMLStreamException {
        xml.writeStartElement("Prestador");
        escreverCpfCnpj(xml, prestador.ehPessoaJuridica(), prestador.documentoSemMascara());
        if (prestador.inscricaoMunicipal() != null) {
            tag(xml, "InscricaoMunicipal", prestador.inscricaoMunicipal());
        }
        xml.writeEndElement(); // Prestador
    }

    private void escreverTomador(XMLStreamWriter xml, TomadorServicoNfse tomador) throws XMLStreamException {
        xml.writeStartElement("Tomador");
        xml.writeStartElement("IdentificacaoTomador");
        escreverCpfCnpj(xml, tomador.ehPessoaJuridica(), tomador.documentoSemMascara());
        if (tomador.inscricaoMunicipal() != null) {
            tag(xml, "InscricaoMunicipal", tomador.inscricaoMunicipal());
        }
        xml.writeEndElement(); // IdentificacaoTomador
        tag(xml, "RazaoSocial", tomador.razaoSocial());
        if (tomador.endereco() != null) {
            escreverEndereco(xml, tomador.endereco());
        }
        if (tomador.telefone() != null || tomador.email() != null) {
            xml.writeStartElement("Contato");
            if (tomador.telefone() != null) {
                tag(xml, "Telefone", tomador.telefone());
            }
            if (tomador.email() != null) {
                tag(xml, "Email", tomador.email());
            }
            xml.writeEndElement(); // Contato
        }
        xml.writeEndElement(); // Tomador
    }

    private void escreverEndereco(XMLStreamWriter xml, EnderecoNfse endereco) throws XMLStreamException {
        xml.writeStartElement("Endereco");
        tag(xml, "Endereco", endereco.logradouro());
        tag(xml, "Numero", endereco.numero());
        if (endereco.complemento() != null) {
            tag(xml, "Complemento", endereco.complemento());
        }
        tag(xml, "Bairro", endereco.bairro());
        tag(xml, "CodigoMunicipio", endereco.codigoMunicipio());
        tag(xml, "Uf", endereco.uf());
        if (endereco.cep() != null) {
            tag(xml, "Cep", endereco.cep());
        }
        xml.writeEndElement(); // Endereco
    }

    private void escreverCpfCnpj(XMLStreamWriter xml, boolean pessoaJuridica, String documento) throws XMLStreamException {
        xml.writeStartElement("CpfCnpj");
        tag(xml, pessoaJuridica ? "Cnpj" : "Cpf", documento);
        xml.writeEndElement(); // CpfCnpj
    }

    private String simNao(boolean valor) {
        return valor ? "1" : "2";
    }

    /** tsValor: xsd:decimal com fractionDigits fixo em 2 - sempre exatamente 2 casas, mesmo para zero. */
    private String moeda(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** tsAliquota: xsd:decimal com ate 4 casas. */
    private String aliquota(BigDecimal valor) {
        return valor.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private void tagData(XMLStreamWriter xml, String nome, java.time.LocalDate data) throws XMLStreamException {
        tag(xml, nome, data.toString()); // xsd:date - formato AAAA-MM-DD, igual ao LocalDate.toString()
    }

    private void tag(XMLStreamWriter xml, String nome, String valor) throws XMLStreamException {
        xml.writeStartElement(nome);
        xml.writeCharacters(valor);
        xml.writeEndElement();
    }
}
