package com.fiscaladapter.documento.nfse.impressao;

import com.fiscaladapter.documento.nfse.Nfse;
import com.fiscaladapter.sefaz.nfse.NfseResponse;

/**
 * Ponto de extensao para layouts de representacao impressa da NFS-e por
 * municipio (FIS-50, criterio de aceite 2) - nao existe um DANFE nacional
 * padrao para NFS-e, cada prefeitura pode exigir um layout proprio. Mesmo
 * espirito do {@link com.fiscaladapter.documento.nfse.NfseXmlGenerator}
 * (FIS-20): implementar esta interface, registrar como {@code @Component}
 * (entra automaticamente no {@link RepresentacaoImpressaNfseGeneratorRegistry})
 * e declarar os municipios suportados em {@link #suporta(String)}.
 */
public interface RepresentacaoImpressaNfseGenerator {

    byte[] gerar(Nfse nfse, NfseResponse resposta);

    /** @param codigoMunicipioIbge codigo IBGE do municipio de prestacao do servico (7 digitos). */
    boolean suporta(String codigoMunicipioIbge);
}
