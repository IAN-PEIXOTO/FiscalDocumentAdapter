package com.fiscaladapter.api.nfe;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Espelha NfeSefazPag. */
public record PagRequest(@NotEmpty List<@Valid DetPagRequest> detPag) {
}
