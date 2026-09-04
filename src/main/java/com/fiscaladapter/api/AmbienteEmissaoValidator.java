package com.fiscaladapter.api;

import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.retencao.RetencaoDocumentoFiscalService;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Valida que o ambiente informado numa operacao pos-emissao (consulta, cancelamento, evento)
 * bate com o tpAmb gravado no XML arquivado na emissao original (FIS-64) - evita transmitir um
 * evento de producao para o endpoint de homologacao (ou vice-versa) por engano do integrador,
 * que so seria pego pela SEFAZ depois de gasta a chamada.
 *
 * So valida quando o documento foi emitido por este adapter (esta arquivado em
 * RetencaoDocumentoFiscalService) - uma chave de terceiro nao tem XML arquivado aqui para
 * comparar, entao nesse caso a validacao e pulada silenciosamente (limitacao aceita: o
 * cenario do FIS-64 e sobre os proprios documentos emitidos por este adapter).
 */
@Component
public class AmbienteEmissaoValidator {

    private static final Pattern TAG_TP_AMB = Pattern.compile("<tpAmb>(\\d)</tpAmb>");

    private final RetencaoDocumentoFiscalService retencaoDocumentoFiscalService;

    public AmbienteEmissaoValidator(RetencaoDocumentoFiscalService retencaoDocumentoFiscalService) {
        this.retencaoDocumentoFiscalService = retencaoDocumentoFiscalService;
    }

    public void validar(String chaveAcesso, TipoAmbiente ambienteInformado) {
        retencaoDocumentoFiscalService.recuperar(chaveAcesso).ifPresent(documento -> {
            Matcher matcher = TAG_TP_AMB.matcher(documento.xmlAssinado());
            if (!matcher.find()) {
                return;
            }

            String tpAmbOriginal = matcher.group(1);
            if (!tpAmbOriginal.equals(String.valueOf(ambienteInformado.codigo()))) {
                throw new IllegalArgumentException("ambiente informado (" + ambienteInformado
                        + ") diverge do ambiente em que a chave " + chaveAcesso + " foi emitida");
            }
        });
    }
}
