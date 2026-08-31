package com.fiscaladapter.documento.cte.dacte;

import com.fiscaladapter.documento.cte.Cte;
import com.fiscaladapter.documento.cte.ParticipanteCte;
import com.fiscaladapter.documento.nfe.Emitente;
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

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

/**
 * Gera o DACTE (Documento Auxiliar do CT-e) em PDF a partir do modelo de
 * dominio e da chave de acesso ja calculada (FIS-48). Mesmo padrao do
 * DanfeGenerator da NFe (FIS-8): cobre os elementos exigidos pelo criterio
 * de aceite, sem ser uma replica pixel-a-pixel do manual do contribuinte.
 *
 * "Suporte aos diferentes modais de transporte" (AC3): o dominio `Cte` (FIS-18)
 * so implementa emissao no modal RODOVIARIO ate aqui - os demais modais
 * (aereo, aquaviario, ferroviario, dutoviario, multimodal) exigem grupos XML
 * proprios (cteModalAereo/Aquaviario/etc.) que ainda nao existem no dominio,
 * mesma limitacao ja documentada em Cte.java. O layout abaixo, portanto,
 * imprime o modal como informacao textual fixa ("Rodoviario") e o RNTRC
 * (obrigatorio so nesse modal); dar suporte real aos outros modais e debito
 * tecnico condicionado a evoluir o dominio (fora do escopo deste card).
 */
@Component
public class DacteGenerator {

    private static final DateTimeFormatter DATA_HORA_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final Font FONTE_TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font FONTE_SUBTITULO = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font FONTE_LABEL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7);
    private static final Font FONTE_TEXTO = FontFactory.getFont(FontFactory.HELVETICA, 9);

    public byte[] gerar(Cte cte, String chaveAcesso, DadosImpressaoDacte dados) {
        try {
            Document documento = new Document(PageSize.A4, 24, 24, 24, 24);
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(documento, saida);
            documento.open();

            escreverCabecalho(documento);
            escreverIdentificacaoEChave(documento, writer, cte, chaveAcesso, dados);
            escreverEmitente(documento, cte.emitente());
            escreverRemetenteEDestinatario(documento, cte);
            escreverPercurso(documento, cte);
            escreverInformacaoDaCarga(documento, cte);
            escreverValoresDaPrestacao(documento, cte);

            documento.close();
            return saida.toByteArray();
        } catch (DocumentException e) {
            throw new DacteGenerationException("Falha ao gerar o DACTE em PDF", e);
        }
    }

    private void escreverCabecalho(Document documento) throws DocumentException {
        Paragraph titulo = new Paragraph("DACTE", FONTE_TITULO);
        titulo.setAlignment(Element.ALIGN_CENTER);
        documento.add(titulo);

        Paragraph subtitulo = new Paragraph(
                "Documento Auxiliar do Conhecimento de Transporte Eletronico\nModal: Rodoviario", FONTE_SUBTITULO);
        subtitulo.setAlignment(Element.ALIGN_CENTER);
        subtitulo.setSpacingAfter(6);
        documento.add(subtitulo);
    }

    private void escreverIdentificacaoEChave(Document documento, PdfWriter writer, Cte cte, String chaveAcesso,
                                              DadosImpressaoDacte dados) throws DocumentException {
        PdfPTable tabela = new PdfPTable(2);
        tabela.setWidthPercentage(100);
        tabela.setWidths(new float[]{1.3f, 1f});
        tabela.setSpacingAfter(10);

        StringBuilder infoEsquerda = new StringBuilder();
        infoEsquerda.append("CT-e numero ").append(cte.identificacao().numero())
                .append(" serie ").append(cte.identificacao().serie()).append('\n');
        infoEsquerda.append("Natureza da operacao: ").append(cte.identificacao().naturezaOperacao()).append('\n');
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

    private void escreverEmitente(Document documento, Emitente emitente) throws DocumentException {
        StringBuilder texto = new StringBuilder();
        texto.append(emitente.razaoSocial());
        if (emitente.nomeFantasia() != null) {
            texto.append(" (").append(emitente.nomeFantasia()).append(')');
        }
        texto.append('\n').append("CNPJ: ").append(formatarCnpj(emitente.cnpjSemMascara()))
                .append("  IE: ").append(emitente.inscricaoEstadual()).append('\n');
        texto.append(formatarEndereco(emitente.endereco()));

        documento.add(bloco("EMITENTE / TRANSPORTADOR", texto.toString()));
    }

    private void escreverRemetenteEDestinatario(Document documento, Cte cte) throws DocumentException {
        documento.add(bloco("REMETENTE", formatarParticipante(cte.remetente())));
        documento.add(bloco("DESTINATARIO", formatarParticipante(cte.destinatario())));
    }

    private void escreverPercurso(Document documento, Cte cte) throws DocumentException {
        String texto = "Origem: " + cte.identificacao().municipioInicio() + "/" + cte.identificacao().ufInicio()
                + "   Destino: " + cte.identificacao().municipioFim() + "/" + cte.identificacao().ufFim()
                + "\nRNTRC: " + (cte.rntrc() != null ? cte.rntrc() : "-");

        documento.add(bloco("PERCURSO", texto));
    }

    private void escreverInformacaoDaCarga(Document documento, Cte cte) throws DocumentException {
        String texto = "Produto predominante: " + cte.informacaoCarga().produtoPredominante()
                + "\nValor da carga: " + cte.informacaoCarga().valorCarga()
                + "   Peso bruto: " + cte.informacaoCarga().pesoBrutoKg() + " kg";

        documento.add(bloco("INFORMACAO DA CARGA", texto));
    }

    private void escreverValoresDaPrestacao(Document documento, Cte cte) throws DocumentException {
        String texto = "Valor total da prestacao: " + cte.valorTotalPrestacao()
                + "   Valor a receber: " + cte.valorAReceber()
                + "\nICMS - base de calculo: " + cte.imposto().baseCalculoIcms()
                + "   aliquota: " + cte.imposto().aliquotaIcms()
                + "   valor: " + cte.imposto().valorIcms();

        documento.add(bloco("VALORES DA PRESTACAO DO SERVICO", texto));
    }

    private String formatarParticipante(ParticipanteCte participante) {
        StringBuilder texto = new StringBuilder();
        texto.append(participante.razaoSocial()).append('\n');
        texto.append(participante.ehPessoaJuridica() ? "CNPJ: " : "CPF: ")
                .append(participante.documentoSemMascara());
        if (participante.inscricaoEstadual() != null) {
            texto.append("  IE: ").append(participante.inscricaoEstadual());
        }
        texto.append('\n').append(formatarEndereco(participante.endereco()));
        return texto.toString();
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
