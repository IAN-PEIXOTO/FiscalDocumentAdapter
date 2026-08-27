package com.fiscaladapter.api;

import com.fiscaladapter.api.idempotencia.RequisicaoEmProcessamentoException;
import com.fiscaladapter.assinatura.AssinaturaDigitalException;
import com.fiscaladapter.certificado.CertificadoInvalidoException;
import com.fiscaladapter.documento.nfe.XmlInvalidoException;
import com.fiscaladapter.documento.nfe.rvn.RegraNegocioVioladaException;
import com.fiscaladapter.sefaz.SefazComunicacaoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Alem de traduzir excecoes em respostas HTTP, loga as que representam falha
 * de infraestrutura ou bug (comunicacao com SEFAZ, erro nao mapeado) - FIS-11.
 * Excecoes que sao apenas "requisicao invalida do cliente" (validacao, regra
 * de negocio, certificado) nao precisam de log de erro: nao indicam problema
 * do nosso lado.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResposta> tratar(MethodArgumentNotValidException e) {
        List<String> detalhes = e.getBindingResult().getFieldErrors().stream()
                .map(erro -> erro.getField() + ": " + erro.getDefaultMessage())
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErroResposta("Dados invalidos no documento enviado", detalhes));
    }

    @ExceptionHandler(RegraNegocioVioladaException.class)
    public ResponseEntity<ErroResposta> tratar(RegraNegocioVioladaException e) {
        List<String> detalhes = e.getViolacoes().stream()
                .map(v -> v.codigo() + ": " + v.mensagem())
                .toList();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErroResposta("Documento viola regras de negocio e seria rejeitado pela SEFAZ", detalhes));
    }

    @ExceptionHandler(XmlInvalidoException.class)
    public ResponseEntity<ErroResposta> tratar(XmlInvalidoException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErroResposta("XML gerado nao esta conforme o schema oficial da NFe", e.getErros()));
    }

    @ExceptionHandler(CertificadoInvalidoException.class)
    public ResponseEntity<ErroResposta> tratar(CertificadoInvalidoException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErroResposta(e.getMessage()));
    }

    @ExceptionHandler(AssinaturaDigitalException.class)
    public ResponseEntity<ErroResposta> tratar(AssinaturaDigitalException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErroResposta(e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErroResposta> tratar(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErroResposta(e.getMessage()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErroResposta> tratar(MissingRequestHeaderException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErroResposta("Cabecalho obrigatorio ausente: " + e.getHeaderName()));
    }

    @ExceptionHandler(RequisicaoEmProcessamentoException.class)
    public ResponseEntity<ErroResposta> tratar(RequisicaoEmProcessamentoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErroResposta(e.getMessage()));
    }

    @ExceptionHandler(SefazComunicacaoException.class)
    public ResponseEntity<ErroResposta> tratar(SefazComunicacaoException e) {
        log.error("Falha de comunicacao com a SEFAZ (endpoint normal e contingencia esgotados): {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErroResposta("Falha de comunicacao com a SEFAZ: " + e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResposta> tratar(Exception e) {
        log.error("Erro nao mapeado ao processar requisicao", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErroResposta("Erro interno ao processar a requisicao"));
    }
}
