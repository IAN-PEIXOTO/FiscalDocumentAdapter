package com.fiscaladapter.documento.nfse.impressao;

import com.fiscaladapter.documento.nfse.DadosServicoNfse;
import com.fiscaladapter.documento.nfse.EnderecoNfse;
import com.fiscaladapter.documento.nfse.Nfse;
import com.fiscaladapter.documento.nfse.PrestadorServicoNfse;
import com.fiscaladapter.documento.nfse.TomadorServicoNfse;
import com.fiscaladapter.sefaz.nfse.NfseResponse;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

/**
 * Layout generico da representacao impressa da NFS-e (FIS-50, criterio de
 * aceite 1): cobre os dados minimos exigidos - prestador, tomador, valor e
 * ISS - com os campos que o RPS de fato carrega (ver {@link Nfse}). Usado
 * como fallback por {@link RepresentacaoImpressaNfseGeneratorRegistry} para
 * qualquer municipio sem layout customizado (todos, nesta fase - ver README).
 *
 * O RPS nao carrega razao social/endereco do PRESTADOR (so documento e
 * inscricao municipal - a prefeitura ja tem esses dados cadastrados e nao
 * exige repeti-los no envio do RPS, diferente do TOMADOR, que o XSD ABRASF
 * exige por extenso). Por isso o bloco do prestador imprime so o que o
 * dominio tem; preencher razao social/endereco do prestador exigiria uma
 * fonte de dados adicional (cadastro do emissor), fora do escopo deste card.
 */
@Component
public class RepresentacaoImpressaNfseGenericaGenerator implements RepresentacaoImpressaNfseGenerator {

    private static final DateTimeFormatter DATA_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Font FONTE_TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font FONTE_SUBTITULO = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font FONTE_LABEL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7);
    private static final Font FONTE_TEXTO = FontFactory.getFont(FontFactory.HELVETICA, 9);

    @Override
    public byte[] gerar(Nfse nfse, NfseResponse resposta) {
        try {
            Document documento = new Document(PageSize.A4, 24, 24, 24, 24);
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            PdfWriter.getInstance(documento, saida);
            documento.open();

            escreverCabecalho(documento, resposta);
            escreverIdentificacao(documento, nfse, resposta);
            escreverPrestador(documento, nfse.prestador());
            escreverTomador(documento, nfse.tomador());
            escreverServico(documento, nfse.servico());
            escreverValoresEIss(documento, nfse.servico());

            documento.close();
            return saida.toByteArray();
        } catch (DocumentException e) {
            throw new RepresentacaoImpressaNfseGenerationException("Falha ao gerar a representacao impressa da NFS-e em PDF", e);
        }
    }

    /** Layout generico: nao ha restricao por municipio, so usado como fallback pelo registry. */
    @Override
    public boolean suporta(String codigoMunicipioIbge) {
        return true;
    }

    private void escreverCabecalho(Document documento, NfseResponse resposta) throws DocumentException {
        Paragraph titulo = new Paragraph("NFS-e", FONTE_TITULO);
        titulo.setAlignment(Element.ALIGN_CENTER);
        documento.add(titulo);

        Paragraph subtitulo = new Paragraph(
                "Nota Fiscal de Servicos Eletronica - Representacao Impressa (layout generico)", FONTE_SUBTITULO);
        subtitulo.setAlignment(Element.ALIGN_CENTER);
        subtitulo.setSpacingAfter(8);
        documento.add(subtitulo);

        if (!resposta.autorizada()) {
            Paragraph aviso = new Paragraph(
                    "NFS-e nao autorizada pela prefeitura - " + resposta.mensagemErro(), FONTE_TEXTO);
            aviso.setAlignment(Element.ALIGN_CENTER);
            aviso.setSpacingAfter(8);
            documento.add(aviso);
        }
    }

    private void escreverIdentificacao(Document documento, Nfse nfse, NfseResponse resposta) throws DocumentException {
        StringBuilder texto = new StringBuilder();
        if (resposta.autorizada()) {
            texto.append("Numero da NFS-e: ").append(resposta.numeroNfse())
                    .append("   Codigo de verificacao: ").append(resposta.codigoVerificacao()).append('\n');
        }
        texto.append("RPS numero ").append(nfse.rps().identificacao().numero())
                .append(" serie ").append(nfse.rps().identificacao().serie()).append('\n');
        texto.append("Competencia: ").append(nfse.competencia().format(DATA_FORMAT))
                .append("   Emissao do RPS: ").append(nfse.rps().dataEmissao().format(DATA_FORMAT));

        documento.add(bloco("IDENTIFICACAO", texto.toString()));
    }

    private void escreverPrestador(Document documento, PrestadorServicoNfse prestador) throws DocumentException {
        String texto = (prestador.ehPessoaJuridica() ? "CNPJ: " : "CPF: ") + prestador.documentoSemMascara()
                + (prestador.inscricaoMunicipal() != null ? "  Inscricao Municipal: " + prestador.inscricaoMunicipal() : "");

        documento.add(bloco("PRESTADOR DO SERVICO", texto));
    }

    private void escreverTomador(Document documento, TomadorServicoNfse tomador) throws DocumentException {
        if (tomador == null) {
            documento.add(bloco("TOMADOR DO SERVICO", "Consumidor nao identificado"));
            return;
        }

        StringBuilder texto = new StringBuilder();
        texto.append(tomador.razaoSocial()).append('\n');
        texto.append(tomador.ehPessoaJuridica() ? "CNPJ: " : "CPF: ").append(tomador.documentoSemMascara());
        if (tomador.inscricaoMunicipal() != null) {
            texto.append("  Inscricao Municipal: ").append(tomador.inscricaoMunicipal());
        }
        if (tomador.endereco() != null) {
            texto.append('\n').append(formatarEndereco(tomador.endereco()));
        }

        documento.add(bloco("TOMADOR DO SERVICO", texto.toString()));
    }

    private void escreverServico(Document documento, DadosServicoNfse servico) throws DocumentException {
        String texto = "Item da lista de servicos: " + servico.itemListaServico()
                + "   Municipio de prestacao: " + servico.codigoMunicipioPrestacao()
                + "\nDiscriminacao: " + servico.discriminacao();

        documento.add(bloco("DISCRIMINACAO DO SERVICO", texto));
    }

    private void escreverValoresEIss(Document documento, DadosServicoNfse servico) throws DocumentException {
        StringBuilder texto = new StringBuilder();
        texto.append("Valor dos servicos: ").append(servico.valores().valorServicos());
        if (servico.valores().valorDeducoes() != null) {
            texto.append("   Deducoes: ").append(servico.valores().valorDeducoes());
        }
        texto.append("\nISS - aliquota: ").append(servico.valores().aliquota() != null ? servico.valores().aliquota() : "-")
                .append("   valor: ").append(servico.valores().valorIss() != null ? servico.valores().valorIss() : "-")
                .append("   retido na fonte: ").append(servico.issRetido() ? "sim" : "nao")
                .append("\nExigibilidade do ISS: ").append(descricaoExigibilidade(servico.exigibilidadeIss()));

        documento.add(bloco("VALORES E ISS", texto.toString()));
    }

    private String descricaoExigibilidade(int codigo) {
        return switch (codigo) {
            case 1 -> "Exigivel";
            case 2 -> "Nao incidencia";
            case 3 -> "Isencao";
            case 4 -> "Exportacao";
            case 5 -> "Imunidade";
            case 6 -> "Exigibilidade suspensa por decisao judicial";
            case 7 -> "Exigibilidade suspensa por decisao administrativa";
            default -> "Codigo " + codigo;
        };
    }

    private Paragraph bloco(String titulo, String texto) {
        Paragraph paragrafo = new Paragraph();
        paragrafo.setSpacingBefore(8);
        paragrafo.add(new Chunk(titulo + "\n", FONTE_LABEL));
        paragrafo.add(new Chunk(texto, FONTE_TEXTO));
        return paragrafo;
    }

    private String formatarEndereco(EnderecoNfse endereco) {
        return endereco.logradouro() + ", " + endereco.numero()
                + (endereco.complemento() != null ? " - " + endereco.complemento() : "")
                + " - " + endereco.bairro() + '\n'
                + "UF: " + endereco.uf() + " - CEP: " + endereco.cep();
    }
}
