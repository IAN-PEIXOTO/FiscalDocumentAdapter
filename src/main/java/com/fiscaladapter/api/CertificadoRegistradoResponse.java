package com.fiscaladapter.api;

import java.time.Instant;

public record CertificadoRegistradoResponse(String cnpj, String subjectDn, Instant validoDe, Instant validoAte) {
}
