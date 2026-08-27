package com.fiscaladapter.documento.nfe;

import com.fiscaladapter.documento.CodigoUfSefaz;
import com.fiscaladapter.documento.TipoDocumentoFiscal;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;

/**
 * Calcula a chave de acesso de 44 digitos dos documentos fiscais eletronicos
 * (NFe, NFCe, CTe, MDFe seguem o mesmo layout de chave, mudando apenas o
 * codigo de modelo). Layout: cUF(2) AAMM(4) CNPJ(14) mod(2) serie(3) nNF(9)
 * tpEmis(1) cNF(8) cDV(1) = 44 digitos.
 */
@Service
public class ChaveAcessoService {

    private static final SecureRandom RANDOM = new SecureRandom();

    public String gerar(String uf, LocalDate dataEmissao, String cnpjEmitente, String modelo,
                         int serie, long numeroDocumento, int tipoEmissao) {
        String codigoNumerico = codigoNumericoAleatorio();
        return gerar(uf, dataEmissao, cnpjEmitente, modelo, serie, numeroDocumento, tipoEmissao, codigoNumerico);
    }

    public String gerar(String uf, LocalDate dataEmissao, String cnpjEmitente, String modelo,
                         int serie, long numeroDocumento, int tipoEmissao, String codigoNumerico) {
        if (codigoNumerico.length() != 8) {
            throw new IllegalArgumentException("Codigo numerico (cNF) deve ter 8 digitos");
        }
        String cnpj = cnpjEmitente.replaceAll("\\D", "");
        if (cnpj.length() != 14) {
            throw new IllegalArgumentException("CNPJ do emitente invalido: " + cnpjEmitente);
        }

        String corpo = CodigoUfSefaz.codigo(uf)
                + aamm(dataEmissao)
                + cnpj
                + modelo
                + String.format("%03d", serie)
                + String.format("%09d", numeroDocumento)
                + tipoEmissao
                + codigoNumerico;

        return corpo + calcularDigitoVerificador(corpo);
    }

    public String modeloPara(TipoDocumentoFiscal tipo) {
        return switch (tipo) {
            case NFE -> "55";
            case NFCE -> "65";
            case CTE -> "57";
            case MDFE -> "58";
            case NFSE -> throw new IllegalArgumentException("NFSe nao usa chave de acesso padrao SEFAZ estadual");
        };
    }

    private String aamm(LocalDate data) {
        return String.format("%02d%02d", data.getYear() % 100, data.getMonthValue());
    }

    private String codigoNumericoAleatorio() {
        int valor = 10_000_000 + RANDOM.nextInt(90_000_000);
        return String.valueOf(valor);
    }

    int calcularDigitoVerificador(String corpo43Digitos) {
        int[] pesos = {2, 3, 4, 5, 6, 7, 8, 9};
        int soma = 0;
        int indicePeso = 0;
        for (int i = corpo43Digitos.length() - 1; i >= 0; i--) {
            int digito = Character.getNumericValue(corpo43Digitos.charAt(i));
            soma += digito * pesos[indicePeso % pesos.length];
            indicePeso++;
        }
        int resto = soma % 11;
        return (resto == 0 || resto == 1) ? 0 : 11 - resto;
    }
}
