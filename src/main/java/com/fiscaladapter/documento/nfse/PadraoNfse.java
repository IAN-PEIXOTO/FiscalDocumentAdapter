package com.fiscaladapter.documento.nfse;

/**
 * Padroes de NFS-e suportados. Nao existe um schema XSD nacional unico (cada
 * municipio pode ter seu proprio); ABRASF e o padrao mais adotado (usado por
 * centenas de prefeituras, ainda que muitas com pequenas variacoes locais).
 * Novos padroes (ex.: GINFES, DSF, ou uma customizacao especifica de um
 * municipio que se desvie do ABRASF puro) sao adicionados aqui e implementados
 * como um novo {@link NfseXmlGenerator} - ver NfseXmlGeneratorRegistry.
 */
public enum PadraoNfse {
    ABRASF_V2_01
}
