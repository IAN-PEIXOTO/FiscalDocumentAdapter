package com.fiscaladapter.documento.mdfe;

import com.fiscaladapter.documento.FusoHorarioFiscal;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Gera o XML do evento de Encerramento do MDF-e (tpEvento 110112 - evEncMDFe),
 * conforme evEncMDFe_v3.00.xsd. Geracao apenas: o envio do evento assinado
 * para a SEFAZ (webservice de recepcao de evento) fica para o FIS-45, assim
 * como a autorizacao/consulta/cancelamento do proprio MDF-e.
 */
@Component
public class MdfeEncerramentoXmlGenerator {

    private static final DateTimeFormatter DATA_EVENTO_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");
    private static final String TP_EVENTO_ENCERRAMENTO = "110112";

    /**
     * @param nProtocoloAutorizacao protocolo de 15 digitos devolvido pela SEFAZ quando o MDF-e foi
     *                              autorizado - nao e gerado por este adapter, pois a autorizacao do
     *                              MDF-e junto a SEFAZ ainda nao esta implementada (fica para o FIS-45).
     */
    public String gerar(String chaveAcessoMdfe, String cnpjAutorEvento, String cUf, String codigoMunicipioEncerramento,
                         LocalDate dataEncerramento, String nProtocoloAutorizacao, TipoAmbiente ambiente) {
        String nSeqEvento = "01";
        String id = "ID" + TP_EVENTO_ENCERRAMENTO + chaveAcessoMdfe + nSeqEvento;

        return "<eventoMDFe versao=\"3.00\" xmlns=\"http://www.portalfiscal.inf.br/mdfe\">"
                + "<infEvento Id=\"" + id + "\">"
                + "<cOrgao>" + cUf + "</cOrgao>"
                + "<tpAmb>" + ambiente.codigo() + "</tpAmb>"
                + "<CNPJ>" + cnpjAutorEvento + "</CNPJ>"
                + "<chMDFe>" + chaveAcessoMdfe + "</chMDFe>"
                + "<dhEvento>" + OffsetDateTime.now(FusoHorarioFiscal.BRASIL).format(DATA_EVENTO_FORMAT) + "</dhEvento>"
                + "<tpEvento>" + TP_EVENTO_ENCERRAMENTO + "</tpEvento>"
                + "<nSeqEvento>" + nSeqEvento + "</nSeqEvento>"
                + "<detEvento versaoEvento=\"3.00\">"
                + "<evEncMDFe>"
                + "<descEvento>Encerramento</descEvento>"
                + "<nProt>" + escaparXml(nProtocoloAutorizacao) + "</nProt>"
                + "<dtEnc>" + dataEncerramento + "</dtEnc>"
                + "<cUF>" + cUf + "</cUF>"
                + "<cMun>" + escaparXml(codigoMunicipioEncerramento) + "</cMun>"
                + "</evEncMDFe>"
                + "</detEvento>"
                + "</infEvento>"
                + "</eventoMDFe>";
    }

    /**
     * Escapa os caracteres especiais de XML (FIS-58) - nProtocoloAutorizacao e
     * codigoMunicipioEncerramento chegam como @RequestParam livre e sao concatenados direto no
     * XML do evento (esta classe monta o XML na mao, sem um writer que escape automaticamente);
     * sem isso, um valor como "</nProt><Malicioso>" quebraria/adulteraria a estrutura do evento
     * assinado digitalmente logo em seguida.
     */
    private String escaparXml(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
