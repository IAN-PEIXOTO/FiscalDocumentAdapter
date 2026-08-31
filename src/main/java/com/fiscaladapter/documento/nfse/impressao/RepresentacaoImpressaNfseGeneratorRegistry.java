package com.fiscaladapter.documento.nfse.impressao;

import com.fiscaladapter.documento.nfse.Nfse;
import com.fiscaladapter.sefaz.nfse.NfseResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolve, a partir do codigo IBGE do municipio de prestacao, qual
 * {@link RepresentacaoImpressaNfseGenerator} usar para gerar a representacao
 * impressa da NFS-e (FIS-50). Municipios sem layout customizado registrado
 * caem no layout generico ({@link RepresentacaoImpressaNfseGenericaGenerator},
 * criterio de aceite 1) - hoje o UNICO layout implementado (ver
 * "Representacao impressa da NFS-e" no README para a lista de municipios com
 * layout customizado, criterio de aceite 3).
 */
@Component
public class RepresentacaoImpressaNfseGeneratorRegistry {

    private final List<RepresentacaoImpressaNfseGenerator> geradoresCustomizados;
    private final RepresentacaoImpressaNfseGenericaGenerator geradorGenerico;

    public RepresentacaoImpressaNfseGeneratorRegistry(List<RepresentacaoImpressaNfseGenerator> geradores,
                                                        RepresentacaoImpressaNfseGenericaGenerator geradorGenerico) {
        this.geradoresCustomizados = geradores.stream().filter(g -> g != geradorGenerico).toList();
        this.geradorGenerico = geradorGenerico;
    }

    public byte[] gerar(String codigoMunicipioIbge, Nfse nfse, NfseResponse resposta) {
        return resolver(codigoMunicipioIbge).gerar(nfse, resposta);
    }

    public RepresentacaoImpressaNfseGenerator resolver(String codigoMunicipioIbge) {
        return geradoresCustomizados.stream()
                .filter(gerador -> gerador.suporta(codigoMunicipioIbge))
                .findFirst()
                .orElse(geradorGenerico);
    }
}
