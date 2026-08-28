package com.fiscaladapter.retencao;

import com.fiscaladapter.documento.TipoDocumentoFiscal;
import com.fiscaladapter.seguranca.CriptografiaEmRepousoService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/** Arquiva e recupera o XML assinado de documentos fiscais autorizados, para retencao legal (FIS-26/34). */
@Service
public class RetencaoDocumentoFiscalService {

    private final DocumentoFiscalArquivadoRepository repository;
    private final CriptografiaEmRepousoService criptografiaEmRepousoService;

    public RetencaoDocumentoFiscalService(DocumentoFiscalArquivadoRepository repository,
                                           CriptografiaEmRepousoService criptografiaEmRepousoService) {
        this.repository = repository;
        this.criptografiaEmRepousoService = criptografiaEmRepousoService;
    }

    /** Idempotente: arquivar a mesma chave de acesso duas vezes (ex.: reprocessamento) nao falha, so ignora a segunda vez. */
    public void arquivar(String chaveAcesso, String cnpjEmissor, TipoDocumentoFiscal tipoDocumento,
                          String numeroProtocolo, String xmlAssinado, LocalDate dataEmissao) {
        String xmlCriptografado = criptografiaEmRepousoService.criptografar(xmlAssinado);
        DocumentoFiscalArquivado documento = new DocumentoFiscalArquivado(chaveAcesso, cnpjEmissor, tipoDocumento,
                numeroProtocolo, xmlCriptografado, dataEmissao, Instant.now());
        try {
            repository.save(documento);
        } catch (DataIntegrityViolationException jaArquivado) {
            // chave de acesso ja arquivada - nada a fazer
        }
    }

    public Optional<DocumentoRecuperado> recuperar(String chaveAcesso) {
        return repository.findByChaveAcesso(chaveAcesso).map(documento -> new DocumentoRecuperado(
                documento.getChaveAcesso(),
                documento.getCnpjEmissor(),
                documento.getTipoDocumento(),
                documento.getNumeroProtocolo(),
                criptografiaEmRepousoService.descriptografar(documento.getXmlAssinadoCriptografado()),
                documento.getDataEmissao()));
    }

    public record DocumentoRecuperado(String chaveAcesso, String cnpjEmissor, TipoDocumentoFiscal tipoDocumento,
                                       String numeroProtocolo, String xmlAssinado, LocalDate dataEmissao) {
    }
}
