package com.fiscaladapter.api.nfe;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

/** Espelha NfeSefazPag. vTroco (FIS-72) e opcional - null/ausente significa sem troco. */
public record PagRequest(@NotEmpty List<@Valid DetPagRequest> detPag, @PositiveOrZero BigDecimal vTroco) {

    public PagRequest(List<DetPagRequest> detPag) {
        this(detPag, null);
    }
}
