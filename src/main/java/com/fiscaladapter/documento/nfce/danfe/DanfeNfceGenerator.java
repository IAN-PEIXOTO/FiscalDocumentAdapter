package com.fiscaladapter.documento.nfce.danfe;

import com.fiscaladapter.documento.nfe.Emitente;
import com.fiscaladapter.documento.nfe.ItemNota;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import javax.imageio.ImageIO;

/**
 * Gera o DANFE NFC-e (modelo 65) em PDF: layout compacto tipo cupom, pensado
 * para impressoras termicas de 80mm (FIS-47).
 *
 * Diferente do DANFE da NFe (com tabelas e retrato/paisagem A4), aqui a
 * pagina tem largura fixa de 80mm (226.772pt) e altura generosa e fixa
 * (bobina continua nao tem "fim de pagina" real, mas o OpenPDF exige uma
 * dimensao numerica) - documentado como decisao de escopo, nao como
 * fidelidade total a uma impressora fisica especifica.
 */
@Component
public class DanfeNfceGenerator {

    private static final float LARGURA_80MM = 226.772f;
    private static final float ALTURA_PAGINA = 3000f;
    private static final int TAMANHO_QR_PIXELS = 300;

    private static final DateTimeFormatter DATA_HORA_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final Font FONTE_TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Font FONTE_LABEL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 6);
    private static final Font FONTE_TEXTO = FontFactory.getFont(FontFactory.HELVETICA, 7);
    private static final Font FONTE_CONTINGENCIA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Font.NORMAL, Color.RED);

    public byte[] gerar(NotaFiscalEletronica nfce, String chaveAcesso, DadosImpressaoDanfeNfce dados) {
        try {
            Document documento = new Document(
                    new Rectangle(LARGURA_80MM, ALTURA_PAGINA), 6, 6, 6, 6);
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            com.lowagie.text.pdf.PdfWriter.getInstance(documento, saida);
            documento.open();

            escreverCabecalho(documento, nfce, dados);
            escreverEmitente(documento, nfce.emitente());
            escreverItens(documento, nfce);
            escreverTotais(documento, nfce);
            escreverChaveEQrCode(documento, chaveAcesso, dados);

            documento.close();
            return saida.toByteArray();
        } catch (DocumentException e) {
            throw new DanfeNfceGenerationException("Falha ao gerar o DANFE NFC-e em PDF", e);
        }
    }

    private void escreverCabecalho(Document documento, NotaFiscalEletronica nfce, DadosImpressaoDanfeNfce dados) throws DocumentException {
        Paragraph titulo = new Paragraph("DANFE NFC-e", FONTE_TITULO);
        titulo.setAlignment(Element.ALIGN_CENTER);
        documento.add(titulo);

        Paragraph subtitulo = new Paragraph(
                "Documento Auxiliar da Nota Fiscal de Consumidor Eletronica\nNao permite aproveitamento de credito de ICMS",
                FONTE_TEXTO);
        subtitulo.setAlignment(Element.ALIGN_CENTER);
        subtitulo.setSpacingAfter(4);
        documento.add(subtitulo);

        if (dados.contingencia()) {
            Paragraph aviso = new Paragraph(
                    "EMITIDA EM CONTINGENCIA - PENDENTE DE TRANSMISSAO/AUTORIZACAO PELA SEFAZ", FONTE_CONTINGENCIA);
            aviso.setAlignment(Element.ALIGN_CENTER);
            aviso.setSpacingAfter(4);
            documento.add(aviso);
        }

        Paragraph identificacao = new Paragraph();
        identificacao.setSpacingAfter(4);
        identificacao.add(new Chunk("NFC-e numero " + nfce.identificacao().numero()
                + " serie " + nfce.identificacao().serie() + "\n", FONTE_TEXTO));
        if (dados.autorizada()) {
            identificacao.add(new Chunk("Protocolo de autorizacao: " + dados.protocoloAutorizacao()
                    + " em " + dados.dataHoraAutorizacao().format(DATA_HORA_FORMAT), FONTE_TEXTO));
        } else {
            identificacao.add(new Chunk("Documento ainda nao transmitido/autorizado pela SEFAZ", FONTE_TEXTO));
        }
        documento.add(identificacao);
    }

    private void escreverEmitente(Document documento, Emitente emitente) throws DocumentException {
        StringBuilder texto = new StringBuilder();
        texto.append(emitente.razaoSocial()).append('\n');
        texto.append("CNPJ: ").append(formatarCnpj(emitente.cnpjSemMascara()));
        if (emitente.inscricaoEstadual() != null) {
            texto.append("  IE: ").append(emitente.inscricaoEstadual());
        }
        documento.add(bloco(null, texto.toString()));
    }

    private void escreverItens(Document documento, NotaFiscalEletronica nfce) throws DocumentException {
        Paragraph itens = new Paragraph();
        itens.setSpacingBefore(6);
        itens.add(new Chunk("ITENS\n", FONTE_LABEL));

        for (ItemNota item : nfce.itens()) {
            itens.add(new Chunk(item.numero() + " " + item.descricao() + "\n", FONTE_TEXTO));
            String linhaQuantidade = item.quantidade().toPlainString() + " " + item.unidadeComercial()
                    + " x " + item.valorUnitario().toPlainString()
                    + " = " + item.valorTotal().toPlainString() + "\n";
            itens.add(new Chunk(linhaQuantidade, FONTE_TEXTO));
        }
        documento.add(itens);
    }

    private void escreverTotais(Document documento, NotaFiscalEletronica nfce) throws DocumentException {
        String texto = "Valor total: " + nfce.valorTotalNota() + "\n"
                + "Valor dos tributos aproximados nao calculado nesta iteracao";
        documento.add(bloco("TOTAIS", texto));
    }

    private void escreverChaveEQrCode(Document documento, String chaveAcesso, DadosImpressaoDanfeNfce dados) throws DocumentException {
        Paragraph chave = new Paragraph();
        chave.setSpacingBefore(6);
        chave.setAlignment(Element.ALIGN_CENTER);
        chave.add(new Chunk("Consulte pela chave de acesso em\n" + dados.urlConsultaPublica() + "\n", FONTE_TEXTO));
        chave.add(new Chunk(formatarChave(chaveAcesso), FONTE_TEXTO));
        documento.add(chave);

        Image imagemQrCode = gerarImagemQrCode(dados.conteudoQrCode());
        imagemQrCode.setAlignment(Element.ALIGN_CENTER);
        imagemQrCode.setSpacingBefore(6);
        documento.add(imagemQrCode);
    }

    private Image gerarImagemQrCode(String conteudo) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix matriz = qrCodeWriter.encode(conteudo, BarcodeFormat.QR_CODE, TAMANHO_QR_PIXELS, TAMANHO_QR_PIXELS);
            BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(matriz);

            ByteArrayOutputStream pngBytes = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "png", pngBytes);
            return Image.getInstance(pngBytes.toByteArray());
        } catch (WriterException | IOException | com.lowagie.text.BadElementException e) {
            throw new DanfeNfceGenerationException("Falha ao gerar a imagem do QR Code do DANFE NFC-e", e);
        }
    }

    private Paragraph bloco(String titulo, String texto) {
        Paragraph paragrafo = new Paragraph();
        paragrafo.setSpacingBefore(6);
        if (titulo != null) {
            paragrafo.add(new Chunk(titulo + "\n", FONTE_LABEL));
        }
        paragrafo.add(new Chunk(texto, FONTE_TEXTO));
        return paragrafo;
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
}
