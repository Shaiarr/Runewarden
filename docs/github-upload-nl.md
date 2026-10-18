# Runewarden HUD testen en indienen

Versie **0.10.5** staat op de ontwikkelingstak **hud-core**. Deze versie beperkt zich tot de HUD, timers en compacte sessietracking in de README. Uitgever: **Shai-arr**. Oorspronkelijke code is **BSD-2-Clause**; bronvermeldingen en licenties van derden blijven behouden.

## De Windows-testdownload starten

1. Pak de volledige Windows x64-ZIP uit in een nieuwe map.
2. Open **Runewarden** en dubbelklik **Start-Runewarden.bat**. Laat `lib`, de argumentbestanden en `runtime` bij elkaar.
3. Kies Runewarden en eventueel Shortest Path in de normale plugininstellingen van RuneLite. De starter registreert de testplugins, maar zet geen andere plugins automatisch aan en verandert hun instellingen niet.
4. Gebruik **Verify-Runewarden.bat** als je het pakket wilt controleren. Een geslaagde controle bewijst bestandshashes en aanwezige klassen/resources, geen ingelogde gameplay of Hub-goedkeuring.

RuneLite en de meegeleverde Temurin 17-runtime blijven ongewijzigd. Bij de Windows-runtime hoort de afzonderlijke Java-broncode-ZIP met dezelfde versie. De portable testdownload bevat geen Java-runtime en gebruikt een geïnstalleerde Java 17. Jagex-accounts volgen de normale [RuneLite-ontwikkelclientinstructies](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

## Broncode en build

Open de repository in GitHub Desktop en kies **hud-core**. Controleer dat `build.gradle`, `runelite-plugin.properties`, `README.md` en `src` direct in de projectmap staan. Voor een bronarchief zonder Git-geschiedenis kun je via **File → Add local repository** de bestaande map toevoegen en daarin een repository aanmaken. Vervang de meegeleverde README, LICENSE en gitignore niet.

Met JDK 17 en Python 3:

```sh
python -m pip install -r tools/requirements.txt
python tools/validate_bundle.py
python tools/validate_hub_release.py
python -m unittest discover -s tools -p 'test_*.py'
./gradlew clean check jar
python tools/check_hub_jar.py
```

Op Windows gebruik je `gradlew.bat` en desgewenst `python -X utf8`. `devTestZip` bouwt het aparte testpakket; de optionele runtime- en routingparameters staan in `CONTRIBUTING.md`. De echte Hub-plugin is `build/libs/runewarden-0.10.5.jar`, zonder de starter, client of runtime.

## Een mogelijke latere HUD-inzending

Deze handleiding voert geen publicatie uit. De eerdere brede inzending staat los van deze kleinere HUD-versie. Controleer eerst de actuele broncode-/bytecodebegroting met de geldende Hub-tools en leg de resultaten vast. Pas na een besluit om deze HUD in te dienen, push je de gecontroleerde broncode en gebruik je de exacte nieuwe commit. Maak bij die latere inzending het bestand `plugins/runewarden` in je Plugin Hub-fork met de volgende inhoud:

```text
repository=https://github.com/Shaiarr/Runewarden.git
commit=DE_EXACTE_40_TEKENS_LANGE_COMMIT_SHA
```

Volg voor een eventuele nieuwe HUD-inzending de actuele instructies van [runelite/plugin-hub](https://github.com/runelite/plugin-hub): een fork, de marker **plugins/runewarden** en een review-PR. Ga er niet van uit dat de eerdere brede PR actief blijft of deze versie al dekt. De pluginbroncode blijft in je eigen repository. Verwerk concrete CI/scanner/reviewfeedback voor de nieuwe scope.

Openbare broncode, een groen buildresultaat en toelating tot de live Plugin Hub zijn verschillende stappen. Oudere prototypechecks gelden niet automatisch voor deze HUD-versie. Bijdragen en testen zijn welkom; er is geen gegarandeerd onderhoudsschema of vaste reactietijd voor ondersteuning.
