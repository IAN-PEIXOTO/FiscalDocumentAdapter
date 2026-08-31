package com.fiscaladapter.api.mdfe;

import com.fiscaladapter.api.nfe.EnderecoNfeRequest;
import com.fiscaladapter.documento.mdfe.Condutor;
import com.fiscaladapter.documento.mdfe.EmitenteMdfe;
import com.fiscaladapter.documento.mdfe.IdentificacaoMdfe;
import com.fiscaladapter.documento.mdfe.Mdfe;
import com.fiscaladapter.documento.mdfe.VeiculoTracao;
import com.fiscaladapter.documento.nfe.Endereco;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/** Traduz o payload de emissao de MDF-e (FIS-45) para o dominio interno - schema proprio, nao compartilhado com a NFe/CT-e. */
@Component
public class MdfeRequestMapper {

    public Mdfe paraDominio(MdfePedidoEmissaoRequest pedido) {
        InfMdfeRequest infMDFe = pedido.infMDFe();
        TipoAmbiente ambiente = TipoAmbiente.valueOf(pedido.ambiente().toUpperCase(Locale.ROOT));

        return new Mdfe(
                identificacao(infMDFe.ide(), ambiente),
                emitente(infMDFe.emit()),
                infMDFe.rntrc(),
                veiculoTracao(infMDFe.veicTracao()),
                condutores(infMDFe.condutores()),
                infMDFe.cMunDescarga(),
                infMDFe.xMunDescarga(),
                infMDFe.infCte() != null ? infMDFe.infCte() : List.of(),
                infMDFe.infNFe() != null ? infMDFe.infNFe() : List.of(),
                infMDFe.vCarga(),
                infMDFe.pesoBrutoKg()
        );
    }

    private IdentificacaoMdfe identificacao(IdeMdfeRequest ide, TipoAmbiente ambiente) {
        return new IdentificacaoMdfe(ide.uf(), ide.serie(), ide.nMDF(), ide.dhEmi(), ambiente,
                ide.UFIni(), ide.UFFim(), ide.cMunCarrega(), ide.xMunCarrega());
    }

    private EmitenteMdfe emitente(EmitMdfeRequest emit) {
        return new EmitenteMdfe(emit.CNPJ(), emit.xNome(), emit.xFant(), emit.IE(), endereco(emit.enderEmit()));
    }

    private Endereco endereco(EnderecoNfeRequest r) {
        return new Endereco(r.xLgr(), r.nro(), r.xBairro(), r.cMun(), r.xMun(), r.UF(), r.CEP(), r.fone());
    }

    private VeiculoTracao veiculoTracao(VeiculoTracaoRequest v) {
        return new VeiculoTracao(v.placa(), v.tara(), v.tpRod(), v.tpCar(), v.UF());
    }

    private List<Condutor> condutores(List<CondutorRequest> condutores) {
        return condutores.stream().map(c -> new Condutor(c.xNome(), c.CPF())).toList();
    }
}
