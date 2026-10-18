# Runewarden op GitHub zetten

Dit pakket bevat de broncode voor de **plugin**. Er wordt geen aangepaste RuneLite-client of Java-runtime gepubliceerd. Het pakket heeft geen Git-geschiedenis of ingestelde remote; je maakt je eigen openbare repository. De bestaande `LICENSE`, auteursvermeldingen en bronvermeldingen blijven behouden. Versie **0.9.6** gebruikt de configuratienaam **runewarden**. Eerdere testinstellingen worden niet automatisch overgenomen; bewaar een geëxporteerde back-up voor een eventuele handmatige migratie. Import van oudere back-ups is niet gegarandeerd.

Uitgever: **Shai-arr**. De oorspronkelijke Runewarden-code valt onder **GPL-3.0-only**; code en gegevens van derden behouden hun eigen licenties, auteursvermeldingen en bronvermeldingen. Runewarden wordt door de gemeenschap onderhouden. Bijdragen en forks zijn welkom; er is geen gegarandeerd onderhoudsschema of vaste reactietijd voor ondersteuning.

## Aanbevolen: GitHub Desktop

1. Pak de ZIP volledig uit. Open de map **runewarden** waarin `build.gradle`, `runelite-plugin.properties`, `README.md` en `src` direct staan. Dit is de repository-root; kies niet de bovenliggende pakketmap.
2. Installeer [GitHub Desktop](https://desktop.github.com/) en meld je aan. Kies **File → Add local repository → Choose** en selecteer die map.
3. Omdat het pakket nog geen Git-repository is, kies je **create a repository here**. Gebruik **Name: runewarden**. Bij een instelbare **Local path** kies je de bovenliggende map: Desktop voegt de naam eraan toe. Het resultaat moet de bestaande `runewarden` zijn, zonder extra `runewarden/runewarden`. Laat README-initialisatie uit en kies geen nieuwe license of gitignore. Die bestanden zitten al in het pakket.
4. Maak de repository. Controleer of je de bestaande bronbestanden ziet. Commit eventuele wijzigingen met bijvoorbeeld **Initial Runewarden 0.9.6 source release**. Er mogen daarna geen niet-gecommitte bronbestanden overblijven.
5. Klik **Publish repository**, gebruik **runewarden** als naam en zet **Keep this code private** uit. Kies je eigen account en klik **Publish repository**. Desktop maakt de openbare GitHub-repository aan; vooraf een lege website-repository maken is bij deze route niet nodig.
6. Kies **Repository → View on GitHub**. Controleer of `README.md`, `LICENSE`, `src` en de buildbestanden rechtstreeks in de repository staan.

Kom je door het padvenster in een lege submap terecht? Gebruik dan onderstaande terminalroute op de oorspronkelijke bronmap. De officiële uitleg staat bij [een lokale repository toevoegen](https://docs.github.com/en/desktop/adding-and-cloning-repositories/adding-a-repository-from-your-local-computer-to-github-desktop) en [aanmaken/publiceren met Desktop](https://docs.github.com/en/desktop/overview/creating-your-first-repository-using-github-desktop).

## Alternatief: Git in een terminal

Open een terminal in de uitgepakte bronmap. Maak op [github.com/new](https://github.com/new) een **public** repository `runewarden`, zonder automatisch README, license of `.gitignore` toe te voegen. Vervang `JOUWACCOUNT` hieronder door je GitHub-account. Gebruik je eigen ingestelde Git-identiteit.

```sh
git init -b main
git add .
git commit -m "Initial Runewarden 0.9.6 source release"
git remote add origin https://github.com/JOUWACCOUNT/runewarden.git
git remote -v
git push -u origin main
```

Dit alternatief is bedoeld voor de bronmap zonder bestaande Git-repository. De officiële [importhandleiding](https://docs.github.com/en/migrations/importing-source-code/using-the-command-line-to-import-source-code/adding-locally-hosted-code-to-github) beschrijft ook authenticatie.

De webknop **Upload files** is voor dit project onhandig: GitHub accepteert daar maximaal **100 bestanden per upload** en **25 MiB per bestand**. Deze bronboom vereist dus meerdere uploads, inclusief bestanden in puntmappen. Upload de ZIP niet als vervanging voor uitgepakte broncode. Zie de [officiële uploadlimieten](https://docs.github.com/en/repositories/working-with-files/managing-files/adding-a-file-to-a-repository).

## Later: indienen bij de RuneLite Plugin Hub

De code openbaar zetten installeert de plugin nog niet in RuneLite. **Runewarden is nog niet door de Plugin Hub goedgekeurd.** De huidige exportchecks staan in `docs/plugin-only-evidence.json`; de procedure staat in `docs/release-process.md`. De oudere `docs/release-readiness-0.9.5.md` en het resultaat van 1.620 tests horen bij de historische ontwikkelomgeving. Ze bewijzen geen livegoedkeuring of dezelfde testdekking van deze pluginexport.

Na de checks, vanuit een volledig gecommitte en gepushte repository, voer je met Python 3 uit:

```sh
python tools/prepare_hub_submission.py https://github.com/JOUWACCOUNT/runewarden
```

Dit maakt lokaal `build/hub-submission/plugins/runewarden` en `build/hub-submission/pull-request.md`. Het publiceert of verstuurt niets. De marker bevat de echte repository-URL en de **exacte 40 tekens lange commit-SHA** van je huidige commit:

```text
repository=https://github.com/JOUWACCOUNT/runewarden.git
commit=DE_EXACTE_40_TEKENS_LANGE_COMMIT_SHA
```

Controleer beide gegenereerde bestanden. Dien vervolgens via een fork en pull request bij [runelite/plugin-hub](https://github.com/runelite/plugin-hub) het markerbestand in als **plugins/runewarden**. De broncode blijft in je eigen repository. Gebruik voor de beschrijving de gecontroleerde reviewtekst. Nieuwe bronwijzigingen vereisen een nieuwe gepushte commit en bijgewerkte marker. RuneLite beoordeelt de inzending daarna volgens zijn eigen procedure.
