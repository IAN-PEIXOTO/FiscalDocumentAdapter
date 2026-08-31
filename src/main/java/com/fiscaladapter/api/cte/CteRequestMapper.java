package com.fiscaladapter.api.cte;

import com.fiscaladapter.api.nfe.EmitRequest;
import com.fiscaladapter.api.nfe.EnderecoNfeRequest;
import com.fiscaladapter.documento.cte.Cte;
import com.fiscaladapter.documento.cte.IdentificacaoCte;
import com.fiscaladapter.documento.cte.ImpostoCte;
import com.fiscaladapter.documento.cte.InformacaoCarga;
import com.fiscaladapter.documento.cte.NotaFiscalTransportada;
import com.fiscaladapter.documento.cte.ParticipanteCte;
import com.fiscaladapter.documento.nfe.Emitente;
import com.fiscaladapter.documento.nfe.Endereco;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/** Traduz o payload de emissao de CT-e (FIS-44) para o dominio interno - schema proprio, nao compartilhado com a NFe. */
@Component
public class CteRequestMapper {

    public Cte paraDominio(CtePedidoEmissaoRequest pedido) {
        InfCteRequest infCte = pedido.infCte();
        TipoAmbiente ambiente = TipoAmbiente.valueOf(pedido.ambiente().toUpperCase(Locale.ROOT));

        return new Cte(
                identificacao(infCte.ide(), ambiente),
                emitente(infCte.emit()),
                participante(infCte.remetente()),
                participante(infCte.destinatario()),
                infCte.tomador(),
                infCte.vTPrest(),
                infCte.vRec(),
                imposto(infCte.imp()),
                informacaoCarga(infCte.infCarga()),
                notasFiscaisTransportadas(infCte.infNFe()),
                infCte.rntrc()
        );
    }

    private IdentificacaoCte identificacao(IdeCteRequest ide, TipoAmbiente ambiente) {
        return new IdentificacaoCte(
                ide.uf(), ide.cfop(), ide.natOp(), ide.serie(), ide.nCT(), ide.dhEmi(), ambiente,
                ide.cMunEnv(), ide.xMunEnv(), ide.UFEnv(),
                ide.cMunFim(), ide.xMunFim(), ide.UFFim()
        );
    }

    private Emitente emitente(EmitRequest emit) {
        return new Emitente(emit.CNPJ(), emit.xNome(), emit.xFant(), emit.IE(), emit.CRT(), endereco(emit.enderEmit()));
    }

    private ParticipanteCte participante(ParticipanteCteRequest p) {
        if (p == null) {
            return null;
        }
        String documento = p.CNPJ() != null ? p.CNPJ() : p.CPF();
        return new ParticipanteCte(documento, p.IE(), p.xNome(), endereco(p.endereco()), p.email());
    }

    private Endereco endereco(EnderecoNfeRequest r) {
        return new Endereco(r.xLgr(), r.nro(), r.xBairro(), r.cMun(), r.xMun(), r.UF(), r.CEP(), r.fone());
    }

    private ImpostoCte imposto(ImpostoCteRequest imp) {
        return new ImpostoCte(imp.vBC(), imp.pICMS(), imp.vICMS());
    }

    private InformacaoCarga informacaoCarga(InformacaoCargaRequest infCarga) {
        return new InformacaoCarga(infCarga.vCarga(), infCarga.proPred(), infCarga.pesoBrutoKg());
    }

    private List<NotaFiscalTransportada> notasFiscaisTransportadas(List<NotaFiscalTransportadaRequest> notas) {
        if (notas == null) {
            return List.of();
        }
        return notas.stream().map(n -> new NotaFiscalTransportada(n.chave())).toList();
    }
}
