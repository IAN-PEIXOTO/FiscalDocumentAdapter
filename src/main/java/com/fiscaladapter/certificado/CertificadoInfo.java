package com.fiscaladapter.certificado;

import java.time.Instant;

/**
 * Dados extraidos de um certificado digital A1 apos carregamento e validacao.
 * O CNPJ e obtido do OID 2.16.76.1.3.3 (padrao ICP-Brasil) presente no Subject DN.
 */
public record CertificadoInfo(
        String alias,
        String subjectDn,
        String cnpj,
        Instant validoDe,
        Instant validoAte
) {

    public boolean expirado(Instant referencia) {
        return referencia.isAfter(validoAte);
    }
}
