package com.fiscaladapter.api.cte;

import com.fiscaladapter.api.nfe.EmitRequest;
import com.fiscaladapter.documento.cte.TipoTomadorServico;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

public record InfCteRequest(
        @NotNull @Valid IdeCteRequest ide,
        @NotNull @Valid EmitRequest emit,
        @Valid ParticipanteCteRequest remetente,
        @Valid ParticipanteCteRequest destinatario,
        @NotNull TipoTomadorServico tomador,
        @NotNull @PositiveOrZero BigDecimal vTPrest,
        @NotNull @PositiveOrZero BigDecimal vRec,
        @NotNull @Valid ImpostoCteRequest imp,
        @NotNull @Valid InformacaoCargaRequest infCarga,
        List<@Valid NotaFiscalTransportadaRequest> infNFe,
        @NotBlank String rntrc
) {
}
