# GitFlow Mobile

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

GitHub, direto do Android. O GitFlow Mobile é um cliente Android em Kotlin + Jetpack Compose, com foco em acessibilidade e nas tarefas mais comuns de manutenção de repositórios.

> **Status:** primeira release pública (`v1.0.0`).

## O que dá para fazer

- Entrar usando um Personal Access Token do GitHub, armazenado criptografado no aparelho
- Listar, criar e excluir repositórios (a exclusão exige digitar o nome do repositório)
- Pesquisar repositórios, usuários/organizações e código no GitHub
- Baixar e instalar APKs publicados nas releases de qualquer repositório
- Enviar um `.zip` como um único commit, com opção de remover a pasta-raiz
- Acionar builds de APK no GitHub Actions e salvar o resultado em Downloads, incluindo o log completo em caso de falha
- Usar a interface com suporte a TalkBack

## Download

Baixe a [release v1.0.0](https://github.com/miolanatan5-commits/gitflowmobile/releases/tag/v1.0.0). O APK **universal** é a opção recomendada para a maioria dos aparelhos; também estão disponíveis builds para `arm64-v8a`, `armeabi-v7a`, `x86` e `x86_64`.

## Build local

Requisitos: Android Studio, JDK 17 e Android SDK 36.

```bash
./gradlew assembleDebug
```

O APK de debug será gerado em `app/build/outputs/apk/debug/`.

## Token do GitHub

Crie um Personal Access Token clássico com os escopos `repo`, `workflow` e `delete_repo`. O aplicativo precisa desses acessos para operar repositórios e acionar builds; use um token dedicado e revogue-o quando não precisar mais.

O token é armazenado localmente usando o Android Keystore. Nunca compartilhe seu token nem o inclua no código-fonte, em screenshots ou em issues.

## Localização

Os textos da interface ficam em `app/src/main/res/values*/strings.xml`. Inglês é o idioma padrão (`values/`) e português brasileiro está em `values-pt-rBR/`.

## Estrutura

- `app/src/main/java/com/natam/gitflowmobile/data` — cliente da API do GitHub, upload de ZIP, serviço de build e armazenamento seguro do token
- `app/src/main/java/com/natam/gitflowmobile/ui` — ViewModel, componentes compartilhados e telas

## Licença

Copyright (C) 2026 GitFlow Mobile contributors.

Este projeto é distribuído sob a **GNU General Public License v3.0**. Consulte o arquivo [LICENSE](LICENSE) para os termos completos.
