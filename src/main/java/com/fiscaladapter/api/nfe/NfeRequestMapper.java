package com.fiscaladapter.api.nfe;

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

import java.util.List;

@Component
public class NfeRequestMapper {

    public NotaFiscalEletronica paraDominio(NfeRequest request) {
        return new NotaFiscalEletronica(
                identificacao(request.identificacao()),
                emitente(request.emitente()),
                destinatario(request.destinatario()),
                itens(request.itens()),
                pagamentos(request.pagamentos())
        );
    }

    private IdentificacaoNfe identificacao(IdentificacaoRequest r) {
        return new IdentificacaoNfe(
                r.uf(), r.naturezaOperacao(), r.serie(), r.numero(), r.dataEmissao(),
                TipoAmbiente.valueOf(r.ambiente()), r.finalidadeEmissao(), r.consumidorFinal(),
                r.codigoMunicipioFatoGerador()
        );
    }

    private Emitente emitente(EmitenteRequest r) {
        return new Emitente(r.cnpj(), r.razaoSocial(), r.nomeFantasia(), r.inscricaoEstadual(),
                r.regimeTributario(), endereco(r.endereco()));
    }

    private Destinatario destinatario(DestinatarioRequest r) {
        return new Destinatario(r.cpfOuCnpj(), r.razaoSocial(), r.indicadorInscricaoEstadual(),
                r.inscricaoEstadual(), r.email(), endereco(r.endereco()));
    }

    private Endereco endereco(EnderecoRequest r) {
        return new Endereco(r.logradouro(), r.numero(), r.bairro(), r.codigoMunicipio(),
                r.municipio(), r.uf(), r.cep(), r.telefone());
    }

    private List<ItemNota> itens(List<ItemRequest> itens) {
        return itens.stream().map(this::item).toList();
    }

    private ItemNota item(ItemRequest r) {
        return new ItemNota(r.numero(), r.codigoProduto(), r.descricao(), r.ncm(), r.cfop(),
                r.unidadeComercial(), r.quantidade(), r.valorUnitario(), r.valorTotal(), imposto(r.imposto()));
    }

    private ImpostoItem imposto(ImpostoItemRequest r) {
        return new ImpostoItem(r.origemIcms(), r.cstIcms(), r.baseCalculoIcms(), r.aliquotaIcms(),
                r.valorIcms(), r.valorIpi(), r.valorPis(), r.valorCofins());
    }

    private List<DetalhePagamento> pagamentos(List<PagamentoRequest> pagamentos) {
        return pagamentos.stream()
                .map(p -> new DetalhePagamento(p.codigoFormaPagamento(), p.valor()))
                .toList();
    }
}
