# English Lyrics

Leitor de letras flutuante para Android, feito para funcionar sobre o YouTube ou outros aplicativos.

## Funcionalidades

- Lupa flutuante arrastável: toque para pesquisar músicas em qualquer idioma.
- Pesquisa no LRCLIB (sem chave API), com resultados por título/artista.
- Leitor translúcido sobreposto à parte inferior da tela, com altura ajustável, rolagem independente, tamanho de fonte e botões para minimizar/fechar.
- Se uma letra não estiver disponível, pesquisa alternativa na web ou importação manual colando o texto.
- Histórico local para reabrir letras mesmo sem internet.
- Interface inicial em Jetpack Compose, com temas claro/escuro e ajuste da opacidade.
- Sem reconhecimento de áudio, sem sincronização e sem acessar dados do YouTube.

## Gerar o APK

No GitHub abra **Actions → Android APK → Run workflow**. Ao terminar, baixe o artefato `english-lyrics-debug-apk`. Também é gerado a cada push na branch `main`.

O projeto requer JDK 17, Android SDK 35, Gradle 8.10.2 e Android Gradle Plugin 8.7.3. No Android Studio, abra a pasta do repositório e sincronize o projeto Gradle.

## Instalação

1. Instale o APK e abra English Lyrics.
2. Pressione **Ativar lupa** e conceda a permissão **Exibir sobre outros apps**.
3. A lupa aparecerá por cima dos outros aplicativos. Toque para buscar.
4. Toque em um resultado e leia a letra na janela inferior. Arraste a pequena barra superior para mudar a altura.
5. Use **Minimizar** para voltar à lupa ou **Fechar** para encerrar.

Um serviço em primeiro plano mantém a sobreposição ativa. Uma notificação permite encerrá-lo, mesmo sem abrir o aplicativo.

## Privacidade e fontes

A pesquisa envia apenas o texto digitado para https://lrclib.net; o histórico e as letras consultadas ficam armazenados localmente. O aplicativo não solicita acesso ao microfone, mídia, notificações de outros aplicativos ou serviços de acessibilidade.

LRCLIB é um catálogo colaborativo e pode não conter todas as músicas. Não há garantia de encontrar a letra de absolutamente todo título. A pesquisa alternativa abre o navegador. O aplicativo não extrai letras de páginas protegidas.

© Os direitos das letras pertencem aos respectivos titulares.
