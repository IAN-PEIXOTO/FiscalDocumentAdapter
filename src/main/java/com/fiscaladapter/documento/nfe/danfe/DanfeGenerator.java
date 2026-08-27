package com.fiscaladapter.documento.nfe.danfe;

import com.fiscaladapter.documento.nfe.Destinatario;
import com.fiscaladapter.documento.nfe.Emitente;
import com.fiscaladapter.documento.nfe.Endereco;
import com.fiscaladapter.documento.nfe.ItemNota;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.Barcode128;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

/**
 * Gera o DANFE (Documento Auxiliar da NFe) em PDF a partir do modelo de
 * dominio e da chave de acesso ja calculada.
 *
 * Layout simplificado: cobre os elementos exigidos pelo criterio de aceite
 * (orientacao retrato/paisagem, codigo de barras da chave, indicacao de
 * contingencia) mas nao e uma replica pixel-a-pixel do layout oficial do
 * manual do contribuinte - isso ficaria para uma iteracao futura caso a
 * fidelidade visual exata seja necessaria.
 */
@Component
public class DanfeGenerator {

    private static final DateTimeFormatter DATA_HORA_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final Font FONTE_TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font FONTE_SUBTITULO = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font FONTE_LABEL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7);
    private static final Font FONTE_TEXTO = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font FONTE_CONTINGENCIA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Font.NORMAL, Color.RED);

    public byte[] gerar(NotaFiscalEletronica nfe, String chaveAcesso, DadosImpressaoDanfe dados) {
        try {
            Document documento = new Document(
                    dados.orientacao() == OrientacaoDanfe.PAISAGEM ? PageSize.A4.rotate() : PageSize.A4,
                    24, 24, 24, 24);
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(documento, saida);
            documento.open();

            escreverCabecalho(documento, dados);
            escreverIdentificacaoEChave(documento, writer, nfe, chaveAcesso, dados);
            escreverEmitente(documento, nfe.emitente());
            if (nfe.destinatario() != null) {
                escreverDestinatario(documento, nfe.destinatario());
            }
            escreverItens(documento, nfe);
            escreverTotais(documento, nfe);

            documento.close();
            return saida.toByteArray();
        } catch (DocumentException e) {
            throw new DanfeGenerationException("Falha ao gerar o DANFE em PDF", e);
        }
    }

    private void escreverCabecalho(Document documento, DadosImpressaoDanfe dados) throws DocumentException {
        Paragraph titulo = new Paragraph("DANFE", FONTE_TITULO);
        titulo.setAlignment(Element.ALIGN_CENTER);
        documento.add(titulo);

        Paragraph subtitulo = new Paragraph("Documento Auxiliar da Nota Fiscal Eletronica", FONTE_SUBTITULO);
        subtitulo.setAlignment(Element.ALIGN_CENTER);
        subtitulo.setSpacingAfter(6);
        documento.add(subtitulo);

        if (dados.contingencia()) {
            Paragraph aviso = new Paragraph(
                    "EMITIDA EM CONTINGENCIA - PENDENTE DE TRANSMISSAO/AUTORIZACAO PELA SEFAZ", FONTE_CONTINGENCIA);
            aviso.setAlignment(Element.ALIGN_CENTER);
            aviso.setSpacingAfter(8);
            documento.add(aviso);
        }
    }

    private void escreverIdentificacaoEChave(Document documento, PdfWriter writer, NotaFiscalEletronica nfe,
                                              String chaveAcesso, DadosImpressaoDanfe dados) throws DocumentException {
        PdfPTable tabela = new PdfPTable(2);
        tabela.setWidthPercentage(100);
        tabela.setWidths(new float[]{1.3f, 1f});
        tabela.setSpacingAfter(10);

        StringBuilder infoEsquerda = new StringBuilder();
        infoEsquerda.append("NF-e numero ").append(nfe.identificacao().numero())
                .append(" serie ").append(nfe.identificacao().serie()).append('\n');
        infoEsquerda.append("Natureza da operacao: ").append(nfe.identificacao().naturezaOperacao()).append('\n');
        infoEsquerda.append("Chave de acesso: ").append(formatarChave(chaveAcesso)).append('\n');
        if (dados.autorizada()) {
            infoEsquerda.append("Protocolo de autorizacao: ").append(dados.protocoloAutorizacao())
                    .append(" em ").append(dados.dataHoraAutorizacao().format(DATA_HORA_FORMAT));
        } else {
            infoEsquerda.append("Documento ainda nao transmitido/autorizado pela SEFAZ");
        }
        tabela.addCell(celulaTexto(infoEsquerda.toString(), FONTE_TEXTO));

        PdfContentByte conteudo = writer.getDirectContent();
        Barcode128 barcode = new Barcode128();
        barcode.setCode(chaveAcesso);
        barcode.setFont(null); // nao repete o texto da chave embaixo das barras, ja escrito ao lado
        Image imagemBarcode = barcode.createImageWithBarcode(conteudo, null, null);

        PdfPCell celulaBarcode = new PdfPCell(imagemBarcode, false);
        celulaBarcode.setHorizontalAlignment(Element.ALIGN_CENTER);
        celulaBarcode.setVerticalAlignment(Element.ALIGN_MIDDLE);
        celulaBarcode.setBorder(Rectangle.BOX);
        celulaBarcode.setPadding(6);
        tabela.addCell(celulaBarcode);

        documento.add(tabela);
    }

    private void escreverEmitente(Document documento, Emitente emitente) throws DocumentException {
        StringBuilder texto = new StringBuilder();
        texto.append(emitente.razaoSocial());
        if (emitente.nomeFantasia() != null) {
            texto.append(" (").append(emitente.nomeFantasia()).append(')');
        }
        texto.append('\n').append("CNPJ: ").append(formatarCnpj(emitente.cnpjSemMascara()))
                .append("  IE: ").append(emitente.inscricaoEstadual()).append('\n');
        texto.append(formatarEndereco(emitente.endereco()));

        documento.add(bloco("EMITENTE", texto.toString()));
    }

    private void escreverDestinatario(Document documento, Destinatario destinatario) throws DocumentException {
        StringBuilder texto = new StringBuilder();
        texto.append(destinatario.razaoSocial()).append('\n');
        texto.append(destinatario.ehPessoaJuridica() ? "CNPJ: " : "CPF: ")
                .append(destinatario.documentoSemMascara()).append('\n');
        texto.append(formatarEndereco(destinatario.endereco()));

        documento.add(bloco("DESTINATARIO", texto.toString()));
    }

    private void escreverItens(Document documento, NotaFiscalEletronica nfe) throws DocumentException {
        PdfPTable tabela = new PdfPTable(7);
        tabela.setWidthPercentage(100);
        tabela.setWidths(new float[]{2.5f, 4f, 1.3f, 1f, 1f, 1.3f, 1.3f});
        tabela.setSpacingBefore(10);

        for (String cabecalho : new String[]{"Codigo", "Descricao", "NCM", "CFOP", "Qtd", "Vl. Unit.", "Vl. Total"}) {
            tabela.addCell(celulaCabecalho(cabecalho));
        }

        for (ItemNota item : nfe.itens()) {
            tabela.addCell(celulaTexto(item.codigoProduto(), FONTE_TEXTO));
            tabela.addCell(celulaTexto(item.descricao(), FONTE_TEXTO));
            tabela.addCell(celulaTexto(item.ncm(), FONTE_TEXTO));
            tabela.addCell(celulaTexto(item.cfop(), FONTE_TEXTO));
            tabela.addCell(celulaTexto(item.quantidade().toPlainString(), FONTE_TEXTO));
            tabela.addCell(celulaTexto(item.valorUnitario().toPlainString(), FONTE_TEXTO));
            tabela.addCell(celulaTexto(item.valorTotal().toPlainString(), FONTE_TEXTO));
        }

        documento.add(tabela);
    }

    private void escreverTotais(Document documento, NotaFiscalEletronica nfe) throws DocumentException {
        String texto = "Valor dos produtos: " + nfe.valorTotalProdutos()
                + "   Valor do ICMS: " + nfe.valorTotalIcms()
                + "   Valor do IPI: " + nfe.valorTotalIpi()
                + "   Valor total da NF-e: " + nfe.valorTotalNota();

        documento.add(bloco("TOTAIS", texto));
    }

    private Paragraph bloco(String titulo, String texto) {
        Paragraph paragrafo = new Paragraph();
        paragrafo.setSpacingBefore(8);
        paragrafo.add(new Chunk(titulo + "\n", FONTE_LABEL));
        paragrafo.add(new Chunk(texto, FONTE_TEXTO));
        return paragrafo;
    }

    private PdfPCell celulaTexto(String texto, Font fonte) {
        PdfPCell celula = new PdfPCell(new com.lowagie.text.Phrase(texto, fonte));
        celula.setPadding(4);
        return celula;
    }

    private PdfPCell celulaCabecalho(String texto) {
        PdfPCell celula = celulaTexto(texto, FONTE_LABEL);
        celula.setBackgroundColor(new Color(230, 230, 230));
        return celula;
    }

    private String formatarChave(String chave) {
        StringBuilder formatada = new StringBuilder();
        for (int i = 0; i < chave.length(); i += 4) {
            if (i > 0) {
                formatada.append(' ');
            }
            formatada.append(chave, i, Math.min(i + 4, chave.length()));
        }
        return formatada.toString();
    }

    private String formatarCnpj(String cnpj) {
        if (cnpj == null || cnpj.length() != 14) {
            return cnpj;
        }
        return cnpj.substring(0, 2) + "." + cnpj.substring(2, 5) + "." + cnpj.substring(5, 8)
                + "/" + cnpj.substring(8, 12) + "-" + cnpj.substring(12);
    }

    private String formatarEndereco(Endereco endereco) {
        return endereco.logradouro() + ", " + endereco.numero() + " - " + endereco.bairro() + '\n'
                + endereco.municipio() + "/" + endereco.uf() + " - CEP: " + endereco.cep();
    }
}
