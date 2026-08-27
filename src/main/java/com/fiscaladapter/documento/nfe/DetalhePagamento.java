package com.fiscaladapter.documento.nfe;

import java.math.BigDecimal;

/** codigoFormaPagamento segue a tabela oficial (01=dinheiro, 03=cartao credito, 04=cartao debito, 15=boleto, 99=outros). */
public record DetalhePagamento(String codigoFormaPagamento, BigDecimal valor) {
}
