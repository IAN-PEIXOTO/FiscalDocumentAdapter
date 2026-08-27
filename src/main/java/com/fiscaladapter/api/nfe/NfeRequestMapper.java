package com.fiscaladapter.api.nfe;

import com.fiscaladapter.documento.CodigoUfSefaz;
import com.fiscaladapter.documento.nfe.DetalhePagamento;
import com.fiscaladapter.documento.nfe.Destinatario;
import com.fiscaladapter.documento.nfe.Emitente;
import com.fiscaladapter.documento.nfe.Endereco;
import com.fiscaladapter.documento.nfe.IdentificacaoNfe;
import com.fiscaladapter.documento.nfe.ImpostoItem;
import com.fiscaladapter.documento.nfe.ItemNota;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/** Traduz o payload no formato da API ACBr (NfePedidoEmissaoRequest) para o dominio interno. */
@Component
public class NfeRequestMapper {

    public NotaFiscalEletronica paraDominio(NfePedidoEmissaoRequest pedido) {
        InfNfeRequest infNFe = pedido.infNFe();
        return new NotaFiscalEletronica(
                identificacao(pedido, infNFe.ide()),
                emitente(infNFe.emit()),
                destinatario(infNFe.dest()),
                itens(infNFe.det()),
                pagamentos(infNFe.pag())
        );
    }

    private IdentificacaoNfe identificacao(NfePedidoEmissaoRequest pedido, IdeRequest ide) {
        return new IdentificacaoNfe(
                CodigoUfSefaz.uf(ide.cUF()),
                ide.natOp(),
                ide.serie(),
                ide.nNF(),
                ide.dhEmi(),
                TipoAmbiente.valueOf(pedido.ambiente().toUpperCase(Locale.ROOT)),
                ide.finNFe(),
                ide.indFinal() == 1,
                ide.cMunFG()
        );
    }

    private Emitente emitente(EmitRequest emit) {
        return new Emitente(emit.CNPJ(), emit.xNome(), emit.xFant(), emit.IE(), emit.CRT(), endereco(emit.enderEmit()));
    }

    private Destinatario destinatario(DestRequest dest) {
        String documento = dest.CNPJ() != null ? dest.CNPJ() : dest.CPF();
        return new Destinatario(documento, dest.xNome(), String.valueOf(dest.indIEDest()),
                dest.IE(), dest.email(), endereco(dest.enderDest()));
    }

    private Endereco endereco(EnderecoNfeRequest r) {
        return new Endereco(r.xLgr(), r.nro(), r.xBairro(), r.cMun(), r.xMun(), r.UF(), r.CEP(), r.fone());
    }

    private List<ItemNota> itens(List<DetRequest> det) {
        return det.stream().map(this::item).toList();
    }

    private ItemNota item(DetRequest d) {
        ProdRequest prod = d.prod();
        return new ItemNota(d.nItem(), prod.cProd(), prod.xProd(), prod.NCM(), prod.CFOP(), prod.uCom(),
                prod.qCom(), prod.vUnCom(), prod.vProd(), imposto(d.imposto()));
    }

    private ImpostoItem imposto(ImpostoRequest imposto) {
        Icms00Request icms00 = imposto.ICMS().ICMS00();
        BigDecimal valorIpi = imposto.IPI() != null ? imposto.IPI().IPITrib().vIPI() : BigDecimal.ZERO;
        return new ImpostoItem(
                icms00.orig(), icms00.CST(), icms00.vBC(), icms00.pICMS(), icms00.vICMS(),
                valorIpi, imposto.PIS().PISAliq().vPIS(), imposto.COFINS().COFINSAliq().vCOFINS()
        );
    }

    private List<DetalhePagamento> pagamentos(PagRequest pag) {
        return pag.detPag().stream()
                .map(p -> new DetalhePagamento(p.tPag(), p.vPag()))
                .toList();
    }
}
