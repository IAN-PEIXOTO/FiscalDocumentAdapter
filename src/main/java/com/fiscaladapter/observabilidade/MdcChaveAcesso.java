package com.fiscaladapter.observabilidade;

import org.slf4j.MDC;

/**
 * Escopo de MDC para a chave de acesso da NFe sendo emitida (FIS-11), usado
 * com try-with-resources em torno da tentativa de emissao para que toda linha
 * de log logada durante o processamento (inclusive por outras classes) fique
 * correlacionada a mesma nota, mesmo em caso de retry/contingencia (onde a
 * chave muda entre tentativas).
 */
public final class MdcChaveAcesso implements AutoCloseable {

    static final String CHAVE_MDC = "chaveAcesso";

    private MdcChaveAcesso() {
    }

    public static MdcChaveAcesso abrir(String chaveAcesso) {
        MDC.put(CHAVE_MDC, chaveAcesso);
        return new MdcChaveAcesso();
    }

    @Override
    public void close() {
        MDC.remove(CHAVE_MDC);
    }
}
