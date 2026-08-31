package com.fiscaladapter.documento.mdfe.damdfe;

import com.fiscaladapter.documento.mdfe.Condutor;
import com.fiscaladapter.documento.mdfe.EmitenteMdfe;
import com.fiscaladapter.documento.mdfe.Mdfe;
import com.fiscaladapter.documento.nfe.Endereco;
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
import java.util.stream.Collectors;

/**
 * Gera o DAMDFE (Documento Auxiliar do MDF-e) em PDF a partir do modelo de
 * dominio e da chave de acesso ja calculada (FIS-49). Mesmo padrao do
 * DanfeGenerator/DacteGenerator: A4 retrato, blocos de texto por secao,
 * codigo de barras Code128 da chave de acesso (AC2).
 *
 * "Suporte a diferentes modais" nao e criterio de aceite deste card (ao
 * contrario do FIS-48/DACTE) - o MDF-e, assim como o CT-e, so tem emissao
 * implementada no modal rodoviario (ver Mdfe.java).
 */
@Component
public class DamdfeGenerator {

    private static final DateTimeFormatter DATA_HORA_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter DATA_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Font FONTE_TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font FONTE_SUBTITULO = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font FONTE_LABEL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7);
    private static final Font FONTE_TEXTO = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font FONTE_ENCERRADO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Font.NORMAL, new Color(0, 128, 0));

    public byte[] gerar(Mdfe mdfe, String chaveAcesso, DadosImpressaoDamdfe dados) {
        try {
            Document documento = new Document(PageSize.A4, 24, 24, 24, 24);
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(documento, saida);
            documento.open();

            escreverCabecalho(documento, dados);
            escreverIdentificacaoEChave(documento, writer, mdfe, chaveAcesso, dados);
            escreverEmitente(documento, mdfe.emitente());
            escreverVeiculoEMotoristas(documento, mdfe);
            escreverPercurso(documento, mdfe, dados);
            escreverDocumentosVinculados(documento, mdfe);

            documento.close();
            return saida.toByteArray();
        } catch (DocumentException e) {
            throw new DamdfeGenerationException("Falha ao gerar o DAMDFE em PDF", e);
        }
    }

    private void escreverCabecalho(Document documento, DadosImpressaoDamdfe dados) throws DocumentException {
        Paragraph titulo = new Paragraph("DAMDFE", FONTE_TITULO);
        titulo.setAlignment(Element.ALIGN_CENTER);
        documento.add(titulo);

        Paragraph subtitulo = new Paragraph(
                "Documento Auxiliar do Manifesto Eletronico de Documentos Fiscais\nModal: Rodoviario", FONTE_SUBTITULO);
        subtitulo.setAlignment(Element.ALIGN_CENTER);
        subtitulo.setSpacingAfter(6);
        documento.add(subtitulo);

        if (dados.encerrado()) {
            Paragraph aviso = new Paragraph("MDF-e ENCERRADO", FONTE_ENCERRADO);
            aviso.setAlignment(Element.ALIGN_CENTER);
            aviso.setSpacingAfter(8);
            documento.add(aviso);
        }
    }

    private void escreverIdentificacaoEChave(Document documento, PdfWriter writer, Mdfe mdfe, String chaveAcesso,
                                              DadosImpressaoDamdfe dados) throws DocumentException {
        PdfPTable tabela = new PdfPTable(2);
        tabela.setWidthPercentage(100);
        tabela.setWidths(new float[]{1.3f, 1f});
        tabela.setSpacingAfter(10);

        StringBuilder infoEsquerda = new StringBuilder();
        infoEsquerda.append("MDF-e numero ").append(mdfe.identificacao().numero())
                .append(" serie ").append(mdfe.identificacao().serie()).append('\n');
        infoEsquerda.append("Chave de acesso: ").append(formatarChave(chaveAcesso)).append('\n');
        if (dados.autorizado()) {
            infoEsquerda.append("Protocolo de autorizacao: ").append(dados.protocoloAutorizacao())
                    .append(" em ").append(dados.dataHoraAutorizacao().format(DATA_HORA_FORMAT));
        } else {
            infoEsquerda.append("Documento ainda nao transmitido/autorizado pela SEFAZ");
        }
        if (dados.encerrado()) {
            infoEsquerda.append("\nEncerrado em ").append(dados.dataEncerramento().format(DATA_FORMAT))
                    .append(" - municipio: ").append(dados.municipioEncerramento());
        }
        tabela.addCell(celulaTexto(infoEsquerda.toString(), FONTE_TEXTO));

        PdfContentByte conteudo = writer.getDirectContent();
        Barcode128 barcode = new Barcode128();
        barcode.setCode(chaveAcesso);
        barcode.setFont(null);
        Image imagemBarcode = barcode.createImageWithBarcode(conteudo, null, null);

        PdfPCell celulaBarcode = new PdfPCell(imagemBarcode, false);
        celulaBarcode.setHorizontalAlignment(Element.ALIGN_CENTER);
        celulaBarcode.setVerticalAlignment(Element.ALIGN_MIDDLE);
        celulaBarcode.setBorder(Rectangle.BOX);
        celulaBarcode.setPadding(6);
        tabela.addCell(celulaBarcode);

        documento.add(tabela);
    }

    private void escreverEmitente(Document documento, EmitenteMdfe emitente) throws DocumentException {
        StringBuilder texto = new StringBuilder();
        texto.append(emitente.razaoSocial());
        if (emitente.nomeFantasia() != null) {
            texto.append(" (").append(emitente.nomeFantasia()).append(')');
        }
        texto.append('\n').append("CNPJ: ").append(formatarCnpj(emitente.cnpjSemMascara()));
        if (emitente.inscricaoEstadual() != null) {
            texto.append("  IE: ").append(emitente.inscricaoEstadual());
        }
        texto.append('\n').append(formatarEndereco(emitente.endereco()));

        documento.add(bloco("EMITENTE", texto.toString()));
    }

    private void escreverVeiculoEMotoristas(Document documento, Mdfe mdfe) throws DocumentException {
        StringBuilder texto = new StringBuilder();
        texto.append("Placa: ").append(mdfe.veiculoTracao().placa())
                .append("  Tara: ").append(mdfe.veiculoTracao().taraKg()).append(" kg\n");
        texto.append("Tipo rodado: ").append(mdfe.veiculoTracao().tipoRodado())
                .append("  Tipo carroceria: ").append(mdfe.veiculoTracao().tipoCarroceria());
        if (mdfe.rntrc() != null) {
            texto.append("\nRNTRC: ").append(mdfe.rntrc());
        }
        texto.append("\nMotorista(s): ").append(mdfe.condutores().stream()
                .map(Condutor::nome).collect(Collectors.joining(", ")));

        documento.add(bloco("VEICULO E MOTORISTA", texto.toString()));
    }

    private void escreverPercurso(Document documento, Mdfe mdfe, DadosImpressaoDamdfe dados) throws DocumentException {
        String texto = "Carregamento: " + mdfe.identificacao().municipioCarregamento()
                + "   Percurso: " + mdfe.identificacao().ufInicio() + " -> " + mdfe.identificacao().ufFim()
                + "\nDescarregamento previsto: " + mdfe.municipioDescarga();

        documento.add(bloco("PERCURSO", texto));
    }

    private void escreverDocumentosVinculados(Document documento, Mdfe mdfe) throws DocumentException {
        StringBuilder texto = new StringBuilder();
        texto.append("CT-e: ").append(mdfe.chavesCteTransportados().isEmpty()
                ? "nenhum" : String.join(", ", mdfe.chavesCteTransportados()));
        texto.append("\nNF-e: ").append(mdfe.chavesNfeTransportadas().isEmpty()
                ? "nenhuma" : String.join(", ", mdfe.chavesNfeTransportadas()));
        texto.append("\nValor da carga: ").append(mdfe.valorCarga())
                .append("   Peso bruto: ").append(mdfe.pesoBrutoKg()).append(" kg");

        documento.add(bloco("DOCUMENTOS FISCAIS VINCULADOS", texto.toString()));
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
