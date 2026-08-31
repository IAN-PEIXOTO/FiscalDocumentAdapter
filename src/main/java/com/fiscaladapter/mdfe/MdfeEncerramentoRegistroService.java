package com.fiscaladapter.mdfe;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/** Ver javadoc de {@link MdfeEncerramentoRegistrado} (FIS-54). */
@Service
public class MdfeEncerramentoRegistroService {

    private final MdfeEncerramentoRegistradoRepository repository;

    public MdfeEncerramentoRegistroService(MdfeEncerramentoRegistradoRepository repository) {
        this.repository = repository;
    }

    /** Idempotente: registrar o encerramento da mesma chave duas vezes nao falha, so ignora a segunda vez. */
    public void registrar(String chaveAcesso, String codigoMunicipioEncerramento, LocalDate dataEncerramento) {
        try {
            repository.save(new MdfeEncerramentoRegistrado(chaveAcesso, codigoMunicipioEncerramento, dataEncerramento, Instant.now()));
        } catch (DataIntegrityViolationException jaRegistrado) {
            // encerramento ja registrado - nada a fazer
        }
    }

    public Optional<MdfeEncerramentoRegistrado> consultar(String chaveAcesso) {
        return repository.findByChaveAcesso(chaveAcesso);
    }

    public boolean estaEncerrado(String chaveAcesso) {
        return repository.findByChaveAcesso(chaveAcesso).isPresent();
    }
}
