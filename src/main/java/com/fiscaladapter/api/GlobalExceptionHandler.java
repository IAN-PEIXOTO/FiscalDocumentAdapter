package com.fiscaladapter.api;

import com.fiscaladapter.assinatura.AssinaturaDigitalException;
import com.fiscaladapter.certificado.CertificadoInvalidoException;
import com.fiscaladapter.documento.nfe.XmlInvalidoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResposta> tratar(MethodArgumentNotValidException e) {
        List<String> detalhes = e.getBindingResult().getFieldErrors().stream()
                .map(erro -> erro.getField() + ": " + erro.getDefaultMessage())
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErroResposta("Dados invalidos no documento enviado", detalhes));
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
}
