package com.fiscaladapter.api.nfe;

import jakarta.validation.Valid;

/**
 * Espelha NfeSefazICMS. Exatamente um dos grupos deve ser enviado, conforme o
 * regime tributario do emitente (CRT) e a situacao do item: ICMS00 (tributada
 * integralmente), ICMS40 (isenta/nao tributada/suspensao) ou ICMSSN102
 * (Simples Nacional, CRT=1). Outros CST/CSOSN ficam para expansao futura.
 */
public record IcmsRequest(@Valid Icms00Request ICMS00, @Valid Icms40Request ICMS40, @Valid IcmsSN102Request ICMSSN102) {

    public IcmsRequest {
        long informados = java.util.stream.Stream.of(ICMS00, ICMS40, ICMSSN102).filter(java.util.Objects::nonNull).count();
        if (informados != 1) {
            throw new IllegalArgumentException(
                    "Informe exatamente um grupo de ICMS por item (ICMS00, ICMS40 ou ICMSSN102), recebido: " + informados);
        }
    }
}
