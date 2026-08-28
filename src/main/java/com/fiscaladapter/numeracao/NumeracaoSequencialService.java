package com.fiscaladapter.numeracao;

import com.fiscaladapter.documento.TipoDocumentoFiscal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Garante que o numero de um documento fiscal (informado pelo cliente, no
 * mesmo formato da API ACBr - o ERP e quem escolhe o numero) nunca e
 * reutilizado por engano para o mesmo emissor/UF/serie/tipo de documento,
 * mesmo sob requisicoes concorrentes (FIS-23). Duplicidade de numeracao e
 * uma violacao legal, entao a garantia de atomicidade e mais importante que
 * performance aqui - mas nao HA geracao automatica de numero: o adapter
 * apenas valida/reserva o que o cliente enviou.
 *
 * A reserva so deve ser chamada quando o documento efetivamente "usou" o
 * numero perante o fisco (autorizado pela SEFAZ, ou liberado via EPEC) - uma
 * submissao rejeitada nao reserva nada, ja que o ERP pode legitimamente
 * corrigir e reenviar o mesmo numero (o numero so precisa ser formalmente
 * inutilizado junto a SEFAZ se o ERP decidir pular para o proximo, ver
 * NfeInutilizacaoClient/FIS-5).
 */
@Service
public class NumeracaoSequencialService {

    private final SequenciaDocumentoRepository repository;

    public NumeracaoSequencialService(SequenciaDocumentoRepository repository) {
        this.repository = repository;
    }

    public void reservar(String cnpjEmissor, String uf, int serie, TipoDocumentoFiscal tipoDocumento, long numero) {
        String cnpjNormalizado = normalizarCnpj(cnpjEmissor);
        String ufNormalizada = normalizarUf(uf);

        try {
            repository.saveAndFlush(new SequenciaDocumento(cnpjNormalizado, ufNormalizada, serie, tipoDocumento, numero));
        } catch (DataIntegrityViolationException e) {
            throw new NumeracaoIndisponivelException(
                    "Numero " + numero + " ja foi utilizado para o emissor " + cnpjNormalizado + ", UF " + ufNormalizada
                            + ", serie " + serie + ", tipo " + tipoDocumento, e);
        }
    }

    private String normalizarCnpj(String cnpj) {
        String digits = cnpj.replaceAll("\\D", "");
        if (digits.length() != 14) {
            throw new IllegalArgumentException("CNPJ invalido: " + cnpj);
        }
        return digits;
    }

    private String normalizarUf(String uf) {
        String normalizada = uf.trim().toUpperCase();
        if (normalizada.length() != 2) {
            throw new IllegalArgumentException("UF invalida: " + uf);
        }
        return normalizada;
    }
}
