package com.fiscaladapter.documento.mdfe;

import java.math.BigDecimal;

/** veicTracao: dados do veiculo com a tracao. tpRod/tpCar seguem a tabela do XSD (ex.: "03"=Cavalo Mecanico, "02"=Fechada/Bau). */
public record VeiculoTracao(
        String placa,
        BigDecimal taraKg,
        String tipoRodado,
        String tipoCarroceria,
        String ufLicenciamento
) {
}
