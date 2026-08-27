package com.fiscaladapter.documento.nfse;

/** Ponto de extensao: cada padrao municipal de NFS-e suportado implementa esta interface (ver {@link PadraoNfse}). */
public interface NfseXmlGenerator {

    String gerar(Nfse nfse);

    PadraoNfse padraoSuportado();
}
