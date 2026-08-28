package com.fiscaladapter.api;

import com.fiscaladapter.documento.cte.CteXmlGenerator;
import com.fiscaladapter.documento.mdfe.MdfeXmlGenerator;
import com.fiscaladapter.documento.nfe.NfeXmlGenerator;
import com.fiscaladapter.documento.nfse.NfseXmlGenerator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Descoberta de versao (FIS-27): a versao da API em si e versionada pelo
 * proprio path (/api/v1/... - ver "Versionamento da API" no README para a
 * estrategia completa de quando/como um /api/v2 conviveria com o v1). Este
 * endpoint expoe qual versao de cada layout de documento fiscal esta
 * suportada nesta implantacao, para integradores confirmarem
 * programaticamente antes de gerar um documento (util quando a SEFAZ ou uma
 * prefeitura mudar de versao de schema).
 */
@RestController
public class VersaoController {

    private final NfeXmlGenerator nfeXmlGenerator;
    private final CteXmlGenerator cteXmlGenerator;
    private final MdfeXmlGenerator mdfeXmlGenerator;
    private final List<NfseXmlGenerator> nfseXmlGenerators;

    public VersaoController(NfeXmlGenerator nfeXmlGenerator, CteXmlGenerator cteXmlGenerator,
                             MdfeXmlGenerator mdfeXmlGenerator, List<NfseXmlGenerator> nfseXmlGenerators) {
        this.nfeXmlGenerator = nfeXmlGenerator;
        this.cteXmlGenerator = cteXmlGenerator;
        this.mdfeXmlGenerator = mdfeXmlGenerator;
        this.nfseXmlGenerators = nfseXmlGenerators;
    }

    @GetMapping("/api/versao")
    public VersaoResponse versao() {
        Map<String, String> layoutsPorDocumento = new LinkedHashMap<>();
        layoutsPorDocumento.put("NFE", nfeXmlGenerator.versaoLayout());
        layoutsPorDocumento.put("NFCE", nfeXmlGenerator.versaoLayout()); // NFC-e compartilha o mesmo layout/schema da NFe (FIS-17)
        layoutsPorDocumento.put("CTE", cteXmlGenerator.versaoLayout());
        layoutsPorDocumento.put("MDFE", mdfeXmlGenerator.versaoLayout());

        List<String> padroesNfse = nfseXmlGenerators.stream()
                .map(gerador -> gerador.padraoSuportado().descricao())
                .toList();

        return new VersaoResponse("v1", layoutsPorDocumento, padroesNfse);
    }

    public record VersaoResponse(String versaoApi, Map<String, String> layoutsDocumentosFiscais, List<String> padroesNfseSuportados) {
    }
}
