package com.fiscaladapter.sefaz.mdfe;

/**
 * MDF-e ja encerrado (fim de percurso) nao pode ser cancelado (FIS-54) - o
 * cancelamento so faz sentido enquanto a viagem ainda esta em curso; depois
 * do encerramento, o documento ja cumpriu seu proposito perante o fisco.
 */
public class MdfeJaEncerradoException extends RuntimeException {

    public MdfeJaEncerradoException() {
        super("MDF-e ja encerrado - cancelamento so e permitido antes do encerramento do manifesto");
    }
}
