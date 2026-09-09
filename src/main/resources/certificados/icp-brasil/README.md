# Raizes da ICP-Brasil

Certificados das Autoridades Certificadoras Raiz da ICP-Brasil (v2, v5, v6, v7, v10, v12 - as
gerações atualmente válidas que o parser X.509 padrão do JDK consegue ler; v1 expirou em 2021,
v3/v8/v9 foram revogadas, v11 é exclusiva para assinatura de código, e v4/v13 usam chave EC com
parâmetros explícitos - formato que o provider padrão do JDK rejeita com "Only named ECParameters
supported", nenhuma delas é a raiz usada por certificados SSL/TLS de servidor de qualquer forma),
baixadas da fonte oficial do ITI (Instituto Nacional de Tecnologia da Informação):

https://www.gov.br/iti/pt-br/assuntos/repositorio/repositorio-ac-raiz

Cada arquivo foi baixado diretamente de `https://acraiz.icpbrasil.gov.br/credenciadas/RAIZ/ICP-Brasilv<N>.crt`
e o fingerprint SHA-256 conferido contra o que o próprio ITI publica.

Usado por `SefazHttpClientFactory` para confiar nos certificados TLS dos webservices da SEFAZ, que
usam certificados emitidos sob a cadeia ICP-Brasil (confirmado por teste real contra a SEFAZ-PR de
homologação: `sun.security.provider.certpath.SunCertPathBuilderException` antes desta correção,
porque o cacerts padrão do JDK não inclui a cadeia raiz brasileira - só CAs comerciais/internacionais).

Atualizar quando o ITI emitir uma nova geração de raiz e alguma SEFAZ passar a usá-la (o ITI marca
cada certificado com a data de emissão/expiração/revogação na página do repositório).
