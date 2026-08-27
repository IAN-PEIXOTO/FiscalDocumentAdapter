package com.fiscaladapter.documento.nfce;

import com.fiscaladapter.documento.nfe.TipoAmbiente;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Endereco de consulta publica do QR Code da NFC-e por UF/ambiente, carregado
 * de nfce-qrcode.properties (FIS-17). Fonte dos dados: mesma familia de
 * arquivos do projeto nfephp-org/sped-nfe usada para verificar a estrutura
 * do QR Code em si - ver cabecalho do properties para detalhes.
 */
@Component
public class NfceQrCodeUrlRegistry {

    private final Properties propriedades = new Properties();

    public NfceQrCodeUrlRegistry() {
        try (InputStream in = new ClassPathResource("nfce-qrcode.properties").getInputStream()) {
            propriedades.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao carregar nfce-qrcode.properties", e);
        }
    }

    public String obterUrl(String uf, TipoAmbiente ambiente) {
        String chave = uf.toUpperCase() + "." + ambiente.name() + ".QRCODE";
        String url = propriedades.getProperty(chave);
        if (url == null) {
            throw new IllegalArgumentException("Endereco de QR Code nao cadastrado para " + chave);
        }
        return url;
    }
}
