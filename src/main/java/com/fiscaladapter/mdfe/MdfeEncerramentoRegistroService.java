package com.fiscaladapter.mdfe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/** Ver javadoc de {@link MdfeEncerramentoRegistrado} (FIS-54). */
@Service
public class MdfeEncerramentoRegistroService {

    private static final Logger log = LoggerFactory.getLogger(MdfeEncerramentoRegistroService.class);

    private final MdfeEncerramentoRegistradoRepository repository;

    public MdfeEncerramentoRegistroService(MdfeEncerramentoRegistradoRepository repository) {
        this.repository = repository;
    }

    /**
     * Idempotente: registrar o encerramento da mesma chave duas vezes nao falha, so ignora a
     * segunda vez. FIS-86: loga antes de ignorar - DataIntegrityViolationException tambem cobre
     * outras violacoes (NOT NULL/CHECK/tamanho), nao so a constraint unica esperada.
     */
    public void registrar(String chaveAcesso, String codigoMunicipioEncerramento, LocalDate dataEncerramento) {
        try {
            repository.save(new MdfeEncerramentoRegistrado(chaveAcesso, codigoMunicipioEncerramento, dataEncerramento, Instant.now()));
        } catch (DataIntegrityViolationException jaRegistrado) {
            log.warn("Falha ao registrar encerramento de MDF-e (tratado como ja registrado, mas pode ser outra "
                            + "violacao de integridade) - chaveAcesso={}: {}",
                    chaveAcesso, jaRegistrado.getMessage());
        }
    }

    public Optional<MdfeEncerramentoRegistrado> consultar(String chaveAcesso) {
        return repository.findByChaveAcesso(chaveAcesso);
    }

    public boolean estaEncerrado(String chaveAcesso) {
        return repository.findByChaveAcesso(chaveAcesso).isPresent();
    }
}
