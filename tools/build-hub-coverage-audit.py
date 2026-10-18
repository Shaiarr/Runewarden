#!/usr/bin/env python3
"""Build the read-only Hub capability audit from preserved primary-source inputs.

Usage: python3 tools/build-hub-coverage-audit.py --source-dir INPUT_DIRECTORY

Inputs are the official signed 1.13.2 manifest, current official install-count
response, pinned official plugin-hub markers and fetched pinned READMEs. This
tool does not add plugin code, fetch executable code, or change runtime config.
Runewarden evidence records file hashes, not a claim that referenced tests ran.
"""
import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
from hub_coverage_assessments import ASSESSMENTS

ROOT = Path(__file__).resolve().parents[1]
PRODUCTION = ROOT / 'src/main/java/com/runewarden'
TESTS = ROOT / 'src/test/java/com/runewarden'
INTEGRATION_TESTS = ROOT / 'integration-tests/java/com/runewarden'
TEST_EVIDENCE_SCOPE = {
    'repository': 'Plugin-only source export; no development launcher, native client or companion source/binary is included.',
    'defaultTests': 'src/test runs without a real Shortest Path implementation. Its identity-only fixture covers plugin discovery and message ownership, not pathfinding or transport behavior.',
    'optionalIntegrationTests': {
        'directory': 'integration-tests/java',
        'suites': ['GuideShortestPathContractTest', 'PohTravelCapabilitiesTest'],
        'testMethods': 11,
        'requirement': 'An externally supplied, unmodified Shortest Path 1.21.3 JAR with resources; upstream commit 37abff3a3345185fc11fccecae04d8170de25a11.',
        'executionClaim': 'Retained unchanged contracts from the earlier integration validation; not executed by the default plugin-only test task or by this audit generator.'},
    'excludedLauncherTests': {
        'suites': ['RoutingCompanionStartupTest', 'QuestHelperLauncherBridgeTest'],
        'testMethods': 6,
        'coverageClaim': 'Historical development-launcher coverage only; excluded from this export and from plugin-only test coverage.'},
    'interpretation': 'A source/test hash identifies retained bytes, not a successful test execution. Consult the actual JUnit output for execution evidence.'}
DOMAIN_FILES = {
    'quests': ('RunewardenGuides GuideCatalog QuestOrder', 'QuestHelperHandoffTest QuestOrderTest GuideCatalogTest ReleaseGuideBoundaryTest'),
    'bank': ('LoadoutPlan LoadoutReadiness LoadoutReadinessTool GuideBankFilter BankedExperience BankedExperienceCalculator OwnershipLedger BankMemoryTool BankMemorySnapshot BankMemoryDiff', 'LoadoutDoseAndLayoutTest LoadoutReadinessTest GuideBankFilterTest BankedExperienceCalculatorTest OwnershipLedgerTest BankMemoryTest'),
    'clues': ('ClueDataService ClueCoachTool ClueAccessIndex ClueJugglingTool ClueJugglingTracker ClueJugglingOverlay', 'ClueTravelTest ClueJugglingTrackerTest ClueJugglingToolTest'),
    'items': ('ItemKnowledgeTool ItemSourceService ItemSourceHtmlParser MobKnowledgeCatalog', 'ItemSourceHtmlParserTest ItemKnowledgeLifecycleTest'),
    'travel': ('GuideNavigation ShortestPathRouteReading GuideTravelCatalog RunewardenGuides PohTravelCapabilities TravelNotebook FarmTravelPlan FarmPatchDestinations FarmRunNavigation FarmPatchPlanner FarmRunsTool', 'ClueTravelTest TravelNotebookTest PohTravelCapabilitiesTest FarmTravelPlanTest FarmTravelRecommendationTest GuideNavigationReceiverTest GuideNavigationPlanningTest GuideShortestPathContractTest GuideTravelCatalogTest GuideLifecycleTest FarmRunWorkflowTest FarmRunNavigationTest'),
    'containers': ('OwnershipLedger SupplyAwarenessTool ContainerHudTool ContainerHudRules ContainerHudOverlay FishBarrelReading', 'OwnershipLedgerTest SupplyAwarenessToolTest ContainerHudTest'),
    'xp': ('GoalPlannerTool TrainingProgressTool TrainingProgressOverlay TrainingGuideProgress GuideLiveProgress BankedExperience ActionProgressTool ActionProgressOverlay ActionProgressRules ActionBatchProgress ActionRecipeDetector SkillProgressTool SkillProgressSession SkillProgressOverlay SkillTabProgressOverlay', 'TrainingMethodIntegrationTest TrainingProgressLayoutTest TrainingGuideProgressTest GuideObjectiveLifecycleTest BankedExperienceTest ActionProgressTest SkillProgressSessionTest SkillProgressToolTest'),
    'ui': ('HudSettingsTool HudPreferences HudStyle HudResizeController InventoryPreviewOverlay GuideCoachOverlay', 'SidebarLayoutTest HudCustomizationTest HudSettingsToolTest HudResizeControllerTest GuideCloseControlTest'),
    'graphics': ('HudStyle HudSettingsTool RunewardenPlugin', 'HudCustomizationTest'),
    'network': ('RunewardenPlugin ItemSourceService ItemKnowledgeTool', 'ItemSourceConsentTest'),
    'social': ('CommandDashboardTool CommandDashboardOverlay EquipmentInspectorTool EquipmentInspectorOverlay RunewardenPlugin', 'CommandDashboardToolTest EquipmentInspectorTest'),
    'pets': ('RunewardenPlugin', ''), 'camera': ('RunewardenPlugin', ''),
    'menus': ('RunewardenPlugin GuideBankFilter', 'RunewardenPluginIntegrationTest'),
    'rewards': ('RewardNotifications RewardNotification RewardPopupOverlay RewardSoundboard', 'RewardNotificationsTest RewardPopupOverlayTest RewardSoundboardTest'),
    'collection': ('RarityCatalog RewardNotifications', 'RarityCatalogTest RewardNotificationsTest'),
    'puzzles': ('ClueDataService ClueCoachTool', 'ClueTravelTest'),
    'economy': ('RunLootTool ItemKnowledgeTool', 'RunLootToolTest'),
    'loot': ('RunLootTool SharedActivitySession EncounterPracticeSession', 'RunLootToolTest ActivitySessionTest'),
    'alerts': ('SupplyAwarenessTool TrainingProgressTool HarvestClockTool RewardNotifications', 'SupplyAwarenessToolTest TrainingAfkAlertTest'),
    'tasks': ('DiaryPlannerTool CombatAchievementsTool GoalPlannerTool', 'DiaryCatalogTest CombatAchievementsToolTest'),
    'cosmetics': ('RunewardenPlugin HudSettingsTool', 'RunewardenPluginIntegrationTest'),
    'diagnostics': ('ModuleRuntime SkillingCoachOverlay', 'ModuleRuntimeTest'),
    'sailing': ('SailingXpScenarios TrainingProgressTool ActivityXpTool SailingCoach SailingCoachRules SailingCoachTasks', 'SailingXpScenariosTest SailingCoachTest SailingCoachRulesTest'),
    'markers': ('TileMarkersTool GuideMarkerOverlay RunewardenSightOverlay', 'GuideMarkerOverlayTest TileMarkersToolTest'),
    'minigames': ('MinigameActivityCatalog MinigameNativeObjectives MinigameFoundry MinigameHomes MinigameTelekinetic MinigameCoach MinigameRunProgress MinigameGuideProgress MinigameActivityExit MinigameGuideSession GuideLiveProgress RunewardenGuides RunewardenPlugin GuideBankFilter MinigameFieldEvents MinigameCountdowns MinigameHudOverlay MinigameSceneOverlay', 'MinigameCoachTest MinigameSpecialistsTest MinigameNativeObjectivesTest MinigameFieldEventsTest MinigameCountdownsTest MinigameNativeGuideProgressTest MinigameActivityExitTest MinigameGuideSessionTest GuideObjectiveLifecycleTest GuideBankFilterTest FoundryNativeProgressTest FoundryGuideLifecycleTest'),
    'raids': ('RunewardenGuides BossGuideLifecycle EncounterPracticeTool SharedActivitySession', 'BossGuideLifecycleTest EncounterPracticeToolTest ActivitySessionTest'),
    'npc': ('RunewardenSightTool RunewardenSightOverlay MinigameSceneOverlay', 'RunewardenSightToolTest MinigameCoachTest'),
    'bosses': ('RunewardenGuides BossGuideLifecycle EncounterPracticeTool TileMarkersTool', 'BossGuideLifecycleTest EncounterPracticeToolTest GuidePanelTest'),
    'pvp': ('RunewardenPlugin', 'RunewardenPluginIntegrationTest'),
    'hunter': ('TrainingProgressTool ActivityXpTool HunterRumourCoach HunterRumourCatalog HunterRumourOverlay HunterRumourSceneOverlay', 'TrainingMethodIntegrationTest HunterRumourTest'),
    'waves': ('WaveEntryCoach WaveEntryOverlay RunewardenGuides WaveExportTool', 'WaveEntryCoachTest GuidePanelTest'),
    'skilling': ('TrainingProgressTool VarlamoreThievingCoach VarlamoreThievingState VarlamoreThievingHudOverlay VarlamoreThievingSceneOverlay AgilityCoach AgilityCourseCatalog AgilityObstacles AgilityTrainingSession AgilityHudOverlay AgilitySceneOverlay', 'TrainingMethodIntegrationTest VarlamoreThievingStateTest VarlamoreThievingCoachTest AgilityCatalogTest AgilityCoachTest AgilityTrainingSessionTest'),
    'ticks': ('SkillingCoach SkillingCoachOverlay HudSettingsTool', 'SkillingCoachTest HudCustomizationTest'),
    'effects': ('FieldCoach FieldStatusOverlay LoadoutReadiness', 'FieldCoachTest LoadoutReadinessTest'),
    'charges': ('SupplyAwarenessTool LoadoutReadiness LoadoutItemVariants', 'SupplyAwarenessToolTest LoadoutReadinessTest'),
    'combat': ('DpsCalculator CombatRollProbability DpsSpecialAttack DpsTargetScenario CurrentMaxHit CombatCalculatorTool ConsumablePreviewOverlay', 'DpsCalculatorTest CombatRollProbabilityTest DpsSpecialAttackTest CurrentMaxHitTest'),
    'accessibility': ('HudSettingsTool RunewardenSightOverlay', 'HudCustomizationTest'),
    'gathering': ('NodeTimerRules NodeTimerOverlay SkillingCoach RadialTimer ContextActions', 'ContextHudTest GatheringRadialTimerTest ContextActionsTest'),
    'farming': ('HarvestClockTool HarvestClockOverlay HarvestTimerWindows HarvestSelections FarmRunsTool FarmTravelPlan FarmPatchCatalog FarmGrowthTiming FarmPatchPlanner FarmGuideProgress TrainingGuideProgress GuideLiveProgress FarmPatchDestinations FarmRunNavigation FarmPatchScene FarmCompostEvidence FarmPatchOverlay', 'FarmRunsToolTest FarmTravelPlanTest FarmTravelRecommendationTest FarmPatchHudTest FarmCropPreparationTest HarvestRadialHudTest FarmPatchPlannerTest FarmGrowthTimingTest FarmGuideProgressTest TrainingGuideProgressTest GuideObjectiveLifecycleTest FarmRunWorkflowTest FarmRunNavigationTest'),
    'audio': ('RewardSoundboard RunewardenConfig', 'RewardSoundboardTest'),
    'slayer': ('SlayerCoachTool SlayerPlanningTool SlayerTaskCatalog MobKnowledgeCatalog', 'SlayerCoachToolTest SlayerPlanEngineTest'),
}


PLUGIN_FILES = {
    'easy-giantsfoundry': ('MinigameFoundry MinigameActivityCatalog MinigameNativeObjectives GuideLiveProgress RunewardenGuides', 'FoundryNativeProgressTest FoundryGuideLifecycleTest MinigameFoundryTest GuideObjectiveLifecycleTest GuideLifecycleTest'),
    'guardians-of-the-rift-helper': ('MinigameFieldEvents MinigameActivityCatalog MinigameNativeObjectives MinigameCoach MinigameSceneOverlay MinigameHudOverlay', 'MinigameFieldEventsTest MinigameActivityCatalogTest MinigameNativeObjectivesTest MinigameCoachTest'),
    'tempoross': ('MinigameFieldEvents MinigameActivityCatalog MinigameCoach MinigameSceneOverlay MinigameHudOverlay', 'MinigameFieldEventsTest MinigameActivityCatalogTest MinigameCoachTest'),
    'equipment-inspector': ('EquipmentInspectorTool EquipmentInspectorOverlay', 'EquipmentInspectorTest'),
    'clue-scroll-juggling': ('ClueJugglingTracker ClueJugglingTool ClueJugglingOverlay', 'ClueJugglingTrackerTest ClueJugglingToolTest'),
    'bank-memory': ('BankMemoryTool BankMemorySnapshot BankMemoryDiff OwnershipLedger', 'BankMemoryTest OwnershipLedgerTest'),
    'fish-barrel': ('ContainerHudTool ContainerHudRules FishBarrelReading ContainerHudOverlay', 'ContainerHudTest'),
    'plank-sack': ('ContainerHudTool ContainerHudRules ContainerHudOverlay', 'ContainerHudTest'),
    'action-progress': ('ActionProgressTool ActionProgressOverlay ActionProgressRules ActionBatchProgress ActionRecipeDetector', 'ActionProgressTest'),
    'skills-tab-progress-bars': ('SkillProgressTool SkillProgressSession SkillProgressOverlay SkillTabProgressOverlay', 'SkillProgressToolTest SkillProgressSessionTest'),
    'varlamore-house-thieving': ('VarlamoreThievingCoach VarlamoreThievingState VarlamoreThievingHudOverlay VarlamoreThievingSceneOverlay', 'VarlamoreThievingStateTest VarlamoreThievingCoachTest'),
    'hunter-rumours': ('HunterRumourCoach HunterRumourCatalog HunterRumourOverlay HunterRumourSceneOverlay', 'HunterRumourTest'),
    'sailing': ('SailingCoach SailingCoachRules SailingCoachTasks', 'SailingCoachTest SailingCoachRulesTest SailingCoachTasksTest'),
    'port-tasks': ('SailingCoach SailingCoachRules SailingCoachTasks', 'SailingCoachTest SailingCoachRulesTest SailingCoachTasksTest'),
}


NATIVE_EXTENSIONS = [
    ('native-world-hopper', 'Integrated World Hopper',
        ('WorldHopperTool WorldHopperPanel WorldHopperPingOverlay RunewardenPlugin RunewardenConfig HudPreferences HudSettingsTool', 'WorldHopperToolTest WorldHopperPanelTest WorldHopperWorkerLifecycleTest RunewardenPluginIntegrationTest HudCustomizationTest'),
        ['Official world list and native population updates; country and US-coast flags, search, membership/region/type filters, character favourites and six reversible sorts', 'Explicit row/return/previous/next actions, configurable quick-hop keys, namespaced command and friends/chat/clan Hop-to entries with fresh entry checks and native confirmations', 'Special-world approval invalidation on changed world types, profile/connection-generation guards and a bounded worker for TCP latency plus optional native retransmission HUD'],
        ['Manual hops only; native entry restrictions and confirmations remain authoritative, and headless cases do not establish live world switching', 'Uses ::rwhop, unbound default keys, all saved quick-hop filters and character favourites instead of identical native preferences', 'TCP-connect probes differ from native ICMP-first measurements; three-second probe spacing/current priority after six seconds is not a response-time guarantee, OS DNS can block the single worker, and network history/lag analytics remain absent'],
        'https://github.com/runelite/runelite/tree/runelite-parent-1.13.2/runelite-client/src/main/java/net/runelite/client/plugins/worldhopper'),
    ('native-agility', 'Native Agility coaching',
        ('AgilityCoach AgilityCourseCatalog AgilityObstacles AgilityTrainingSession AgilityHudOverlay AgilitySceneOverlay', 'AgilityCatalogTest AgilityCoachTest AgilityTrainingSessionTest'),
        ['Native course, obstacle and shortcut observations with exact-XP/end-position lap confirmation', 'Measured course sessions, level goals, mark/stick observations and Brimhaven target-change estimates'],
        ['No weighted Brimhaven route solver, full Sepulchre trap/runner engine or Dorgesh-Kaan/Underwater task routes', 'Broad native region recognition is not an exact course-boundary or universal traversal engine'],
        'https://github.com/runelite/runelite/tree/runelite-parent-1.13.2/runelite-client/src/main/java/net/runelite/client/plugins/agility'),
    ('native-ground-loot', 'Contextual ground loot and native loot beams',
        ('GroundLootTool GroundLootOverlay GroundLootBeams GroundLootTier', 'GroundLootToolTest GroundLootBeamsTest'),
        ['Native identity/ownership/deadline-aware floor-item highlights and guide/manual targets', 'Five configurable value-band beams using native models, bounded object caches and actual despawn/public rings'],
        ['Floor-item eligibility is not path reachability and does not add earned loot', 'This is not every native Ground Items menu, rule or display feature'],
        'https://github.com/runelite/runelite/tree/runelite-parent-1.13.2/runelite-client/src/main/java/net/runelite/client/plugins/grounditems'),
    ('native-gathering-farming', 'Gathering, harvest and observed farming HUDs',
        ('SkillingCoach NodeTimerRules NodeTimerOverlay RadialTimer HarvestClockTool HarvestClockOverlay HarvestTimerWindows FarmPatchCatalog FarmGrowthTiming FarmPatchPlanner FarmPatchScene FarmCompostEvidence FarmPatchOverlay', 'GatheringRadialTimerTest HarvestRadialHudTest FarmPatchHudTest FarmPatchPlannerTest FarmGrowthTimingTest'),
        ['Observed respawn windows, supported estimated tree lifetimes and text/radial choices', 'Native Time Tracking harvest summaries, crop-specific icons, cycle-aligned patch growth estimates and confirmed compost evidence'],
        ['Unknown/random resource lifetimes and unobserved crop states remain unknown; disease and growth skips can alter estimates', 'Native Time Tracking integration is retained; full original tracker replacement is not claimed'],
        'https://github.com/runelite/runelite/tree/runelite-parent-1.13.2/runelite-client/src/main/java/net/runelite/client/plugins/timetracking'),
]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def evidence(domain, slug=None):
    production, tests = PLUGIN_FILES.get(slug, DOMAIN_FILES[domain])
    found = []
    missing = []
    for prefix, names, kind in [(PRODUCTION, production, 'production'), (TESTS, tests, 'test-reference')]:
        for name in names.split():
            path = prefix / (name + '.java')
            source_kind = kind
            if kind == 'test-reference' and not path.exists():
                optional = INTEGRATION_TESTS / (name + '.java')
                if optional.exists():
                    path = optional
                    source_kind = 'optional-integration-test-reference'
            if path.exists(): found.append({'file': path.relative_to(ROOT).as_posix(), 'sha256': sha(path), 'kind': source_kind})
            else: missing.append(name)
    return found, missing


def verify(source):
    raw = (source / 'manifest-signed.bin').read_bytes()
    size = struct.unpack('>I', raw[:4])[0]
    if not 128 <= size <= 4096 or size + 4 >= len(raw): raise ValueError('Invalid manifest signature envelope')
    data = raw[4 + size:]
    if data != (source / 'manifest-data.json').read_bytes(): raise ValueError('Unsigned manifest bytes differ')
    if raw[4:4 + size] != (source / 'manifest.sig').read_bytes(): raise ValueError('Manifest signature bytes differ')
    if sha(source / 'externalplugins.crt') != '284416508f83a69e9b5c34520ccf7a8874b948bd170908ab253fd688fcf28507':
        raise ValueError('Certificate differs from the verified RuneLite 1.13.2 certificate')
    public = subprocess.run(['openssl', 'x509', '-in', str(source / 'externalplugins.crt'), '-pubkey', '-noout'], capture_output=True)
    if public.returncode != 0 or public.stdout != (source / 'manifest-public.pem').read_bytes():
        raise ValueError('Manifest public key does not match the pinned certificate')
    process = subprocess.run(['openssl', 'dgst', '-sha256', '-verify', str(source / 'manifest-public.pem'),
        '-signature', str(source / 'manifest.sig'), str(source / 'manifest-data.json')], capture_output=True, text=True)
    if process.returncode != 0: raise ValueError('Official manifest RSA signature did not verify: ' + process.stderr)
    return json.loads(data), sha(source / 'manifest-signed.bin'), sha(source / 'externalplugins.crt')


def check_evidence():
    """Offline release check: the retained report must describe this source tree."""
    audit = json.loads((ROOT / 'docs/hub-utility-audit.json').read_text())
    inventory = json.loads((ROOT / 'docs/hub-inventory.json').read_text())
    version = re.search(r"^version = '([^']+)'", (ROOT / 'build.gradle').read_text(), re.M).group(1)
    if audit['auditedRunewardenVersion'] != version + '-working-tree': raise ValueError('Audit version is stale')
    if audit['summary'] != inventory['summary']: raise ValueError('Inventory/audit summaries differ')
    baseline = {p['id'] for p in inventory['plugins'] if p['availableJar'] and p['activeInstalls'] is not None and p['activeInstalls'] >= audit['summary']['comparisonMinimumActiveInstalls']}
    if baseline != {c['pluginId'] for c in audit['comparisons']}: raise ValueError('Popular baseline coverage differs')
    files = {}
    for comparison in audit['comparisons'] + audit.get('supplementalNativeExtensions', []):
        if comparison.get('unresolvedEvidenceNames'): raise ValueError('Unresolved evidence: ' + comparison['pluginId'])
        for source in comparison['sourceInspection']:
            before = files.setdefault(source['file'], source['sha256'])
            if before != source['sha256']: raise ValueError('Inconsistent source evidence hash')
        for feature in comparison.get('features', []):
            if feature['status'] not in {'implemented','partial','missing','unknown'}: raise ValueError('Unknown capability status')
    stale = [name for name, expected in files.items() if not (ROOT / name).is_file() or sha(ROOT / name) != expected]
    if stale: raise ValueError('Audit source evidence is stale: ' + ', '.join(stale))
    import gzip
    retained = ROOT / 'docs/hub-source-snapshot'
    envelope = gzip.decompress((retained / 'manifest-signed.bin.gz').read_bytes())
    counts = gzip.decompress((retained / 'api-latest-counts.json.gz').read_bytes())
    if hashlib.sha256(envelope).hexdigest() != audit['sources']['manifestEnvelopeSha256']: raise ValueError('Retained manifest differs from report')
    if hashlib.sha256(counts).hexdigest() != audit['sources']['countsSha256']: raise ValueError('Retained install-count response differs from report')
    print('Audit evidence is current: ' + str(len(files)) + ' source/test files, ' + str(len(baseline)) + ' popular plugins.')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--source-dir', type=Path)
    parser.add_argument('--check-evidence', action='store_true', help='Offline check that retained report hashes match current source/test files')
    parser.add_argument('--minimum-installs', type=int, default=50000)
    args = parser.parse_args()
    if args.check_evidence:
        check_evidence()
        return
    if args.source_dir is None: parser.error('--source-dir is required for report generation')
    source = args.source_dir.resolve()
    manifest, manifest_sha, certificate_sha = verify(source)
    counts = json.loads((source / 'api-latest-counts.json').read_text())
    markers = json.loads((source / 'hub-markers.json').read_text())
    readmes = json.loads((source / 'readme-index.json').read_text())
    java = json.loads((source / 'java-index.json').read_text()) if (source / 'java-index.json').exists() else {}
    jars = {row['internalName']: row for row in manifest['jars']}
    records = []
    for row in manifest['display']:
        slug = row['internalName']
        marker = markers.get(slug, {})
        count = counts.get(slug)
        if count is not None and (isinstance(count, bool) or not isinstance(count, int) or count < 0): raise ValueError('Invalid install count')
        record = {'id': slug, 'name': row['displayName'], 'author': row['author'], 'pluginVersion': row['version'],
            'activeInstalls': count, 'availableJar': slug in jars,
            'hubUrl': 'https://runelite.net/plugin-hub/show/' + slug,
            'repository': marker.get('repository'), 'sourceCommit': marker.get('commit'),
            'createdAt': row['createdAt'], 'lastUpdatedAt': row['lastUpdatedAt'],
            'hasWarning': bool(row.get('warning')), 'warning': row.get('warning'),
            'hasBuildFailure': 'buildFailAt' in row, 'buildFailAt': row.get('buildFailAt'),
            'unavailableReason': row.get('unavailableReason'),
            'auditState': 'outside-popularity-tier-not-reviewed'}
        if slug in jars:
            record['jarSha256UrlSafe'] = jars[slug]['jarHash']
            record['jarBytes'] = jars[slug]['jarSize']
        records.append(record)
    records.sort(key=lambda row: (-(row['activeInstalls'] if row['activeInstalls'] is not None else -1), row['name'].lower()))
    rank = 0
    for row in records:
        if row['availableJar']:
            rank += 1
            row['availableRank'] = rank
    popular = [row for row in records if row['availableJar'] and row['activeInstalls'] is not None and row['activeInstalls'] >= args.minimum_installs]
    comparisons = []
    for row in popular:
        slug = row['id']
        if slug not in ASSESSMENTS: raise ValueError('Popular plugin not assessed: ' + slug)
        finding = ASSESSMENTS[slug]
        reference = readmes.get(slug)
        if not reference or 'source' not in reference: raise ValueError('Missing pinned README: ' + slug)
        if reference['commit'] != row['sourceCommit']: raise ValueError('README is not at the manifest-marker pin: ' + slug)
        local = Path(reference['local'])
        if not local.is_absolute(): local = source / 'readmes' / (slug + '.md')
        if sha(local) != reference['sha256']: raise ValueError('README content changed: ' + slug)
        for reference_file in java.get(slug, {}).get('files', []):
            local_java = source / 'java' / Path(reference_file['local']).name
            if sha(local_java) != reference_file['sha256']: raise ValueError('Pinned Java text changed: ' + slug)
        files, absent = evidence(finding['domain'], slug)
        features = []
        for feature in finding['features']:
            key = re.sub('[^a-z0-9]+', '-', feature['capability'].lower()).strip('-')[:115]
            features.append(dict(feature, id=slug + ':' + key,
                evidence=[value for value in files if value['kind'] == 'production'] if feature['status'] != 'missing' else [],
                testReferences=[value for value in files if value['kind'] in {'test-reference', 'optional-integration-test-reference'}] if feature['status'] != 'missing' else [],
                primarySource=reference['source']))
        row['auditState'] = 'readme-baseline-reviewed'
        comparisons.append({'pluginId': slug, 'name': row['name'], 'availableRank': row['availableRank'], 'activeInstalls': row['activeInstalls'],
            'domain': finding['domain'], 'reviewDepth': 'Pinned README capability baseline plus current Runewarden source inspection; not every configuration option certified',
            'primaryReadme': {key: reference[key] for key in ['source', 'commit', 'sha256', 'bytes']},
            'additionalPrimarySource': [{key: item[key] for key in ['source', 'sha256', 'bytes']} for item in java.get(slug, {}).get('files', [])],
            'features': features, 'qualification': finding['note'], 'sourceInspection': files,
            'unresolvedEvidenceNames': absent})
    supplemental = []
    for identifier, title, mapping, implemented, limitations, primary in NATIVE_EXTENSIONS:
        DOMAIN_FILES[identifier] = mapping
        files, absent = evidence(identifier)
        if absent: raise ValueError('Missing native-extension evidence: ' + ', '.join(absent))
        supplemental.append({'id': identifier, 'title': title, 'reviewScope': 'Selected owned native extension; not a complete built-in plugin comparison and excluded from Hub finding counts',
            'implementedMechanisms': implemented, 'remainingLimits': limitations, 'primarySource': primary, 'sourceInspection': files})
    status = Counter(feature['status'] for comparison in comparisons for feature in comparison['features'])
    domains = Counter(comparison['domain'] for comparison in comparisons)
    timestamp = datetime.now(timezone.utc).isoformat()
    provenance = {
        'retrievedDate': '2026-10-17', 'generatedAtUtc': timestamp,
        'manifestUrl': 'https://repo.runelite.net/plugins/manifest/1.13.2_full.js',
        'manifestResponseDateUtc': '2026-10-17T13:50:00Z', 'manifestLastModifiedUtc': '2026-10-17T12:58:00Z',
        'manifestEnvelopeSha256': manifest_sha, 'manifestRsaVerified': True,
        'verificationCertificateSha256': certificate_sha,
        'verificationCertificateOrigin': 'net/runelite/client/externalplugins/externalplugins.crt inside verified RuneLite client-1.13.2.jar',
        'countsUrl': 'https://api.runelite.net/runelite-1.13.2/pluginhub',
        'countsResponseDateUtc': '2026-10-17T13:50:01Z', 'countsSha256': sha(source / 'api-latest-counts.json'),
        'countsCacheSeconds': 1800,
        'officialPluginHubRepository': 'https://github.com/runelite/plugin-hub',
        'officialMarkerTreeCommit': '3e8ace3bbfe9aca406b9d874cb162a334f86ac0b',
        'availabilityFilter': {'available': 'display.internalName has a matching signed manifest jars.internalName entry',
            'popularBaseline': 'availableJar && activeInstalls >= minimumInstalls',
            'displaySchemaFields': sorted({key for row in manifest['display'] for key in row}),
            'hiddenOrDisabledFlags': 'No hidden or disabled fields occur in this signed manifest display schema. No such flags are invented.',
            'warnings': 'Retained verbatim as metadata; warning presence does not imply JAR unavailability.',
            'buildFailuresAndUnavailableReasons': 'Retained independently; JAR matching remains the exact availability rule.'},
        'websiteCrossCheck': {'url': 'https://runelite.net/plugin-hub/', 'displayedPluginCount': 2636,
            'displayedActiveInstalls': 28801065, 'usedForRanking': False,
            'reason': 'The server-rendered website is an older cached snapshot than the signed manifest/current count endpoint. Metrics are not mixed.'},
    }
    summary = {'displayRecords': len(records), 'availableJarRecords': sum(row['availableJar'] for row in records),
        'unavailableRecords': sum(not row['availableJar'] for row in records),
        'recordsWithoutCount': sum(row['activeInstalls'] is None for row in records),
        'warningRecords': sum(row['hasWarning'] for row in records),
        'buildFailureRecords': sum(row['hasBuildFailure'] for row in records),
        'recordsWithUnavailableReason': sum(bool(row['unavailableReason']) for row in records),
        'allCountEndpointIdsIncludingLegacy': len(counts),
        'comparisonMinimumActiveInstalls': args.minimum_installs, 'comparisonPlugins': len(comparisons),
        'featureFindings': sum(status.values()), 'featureStatuses': dict(status), 'comparisonDomains': dict(domains),
        'outsideTierRecordsNotReviewed': len(records) - len(comparisons)}
    inventory = {'schema': 1, 'metric': 'official-active-installs', 'sources': provenance, 'summary': summary, 'plugins': records}
    version = re.search(r"^version = '([^']+)'", (ROOT / 'build.gradle').read_text(), re.M).group(1)
    audit = {'schema': 1, 'auditedRunewardenVersion': version + '-working-tree', 'sources': provenance, 'summary': summary,
        'statusSemantics': {'implemented': 'The named, bounded capability exists in inspected Runewarden code; this is not complete upstream-plugin parity.',
            'partial': 'A limited subset or public native-plugin integration exists; remaining breadth is explicit.',
            'missing': 'No corresponding owned implementation was identified in the inspected source snapshot.',
            'unknown': 'Primary documentation is insufficient for a specific capability comparison; no behaviour or parity is invented.'},
        'testEvidenceScope': TEST_EVIDENCE_SCOPE,
        'limitations': ['This is a source/documentation comparison, not in-game validation or RuneLite approval.',
            f'Source/test paths and hashes are indexed for Runewarden {version} under com.runewarden; this namespace reindex is not new test-execution evidence or Hub approval.',
            'The 151-plugin popularity tier is complete for the recorded threshold and snapshot; every other inventory record remains explicitly unreviewed.',
            'A README capability baseline does not certify parity with every upstream source option or undocumented edge case.',
            'A guide, item catalogue, static reference, generic tick, self HUD or native-plugin adapter is not counted as a full specialist implementation.',
            'Referenced tests are evidence pointers; the audit generator does not execute those tests.',
            'Real-companion contracts are optional integration evidence, not default plugin-only test coverage; development-launcher tests are excluded.',
            'Current and future changes require re-review of capability statuses; source hashes identify the inspected snapshot.',
            'The GitHub marker source pin is independently recorded. The manifest does not expose a source commit that cryptographically binds a README to its built JAR.',
            'A Hub-listed feature is precedent for investigation, not an automatic approval of a new implementation. No unsupported blanket prohibition is inferred.'],
        'comparisons': comparisons,
        'supplementalNativeExtensions': supplemental,
        'largestArchitectureGaps': ['Independent high-detail renderer and base-client resource-pack reskinning',
            'Multiplayer party transport and synchronized peer-state panels', 'Optional WikiSync, public profile, clan/competition and webhook transports',
            'Native virtual bank layouts and full Inventory Setups interoperability',
            'Dedicated raid, Gauntlet and specialized puzzle engines; remaining full clue-helper breadth',
            'General menu-rule editing, custom alert-rule engine, network history/lag analytics and broader container observation']}
    docs = ROOT / 'docs'
    (docs / 'hub-inventory.json').write_text(json.dumps(inventory, indent=2, ensure_ascii=False) + '\n')
    (docs / 'hub-utility-audit.json').write_text(json.dumps(audit, indent=2, ensure_ascii=False) + '\n')
    markdown = ['# RuneLite Hub capability baseline', '',
        'The signed RuneLite 1.13.2 Hub manifest was verified against the certificate bundled in the verified native client. Its metadata, the current official active-install endpoint and the pinned official marker tree are retained separately. The website’s older rendered counts are not mixed into this ranking.', '',
        f'The inventory contains **{summary["displayRecords"]:,} listed records**, **{summary["availableJarRecords"]:,} with an available JAR** and **{summary["unavailableRecords"]} without one**. The comparison includes **every available plugin with at least {args.minimum_installs:,} active installs: {len(comparisons)} plugins**. The remaining {summary["outsideTierRecordsNotReviewed"]:,} inventory records are explicitly unreviewed.', '',
        'Availability means that the signed manifest has a matching JAR entry; it is not inferred from repository presence, warnings or install counts. The manifest schema contains no hidden/disabled flags. Warning text, build-failure timestamps and unavailability metadata remain visible. The install endpoint also contains legacy IDs, which are excluded unless present in this manifest.', '',
        f'The source review records **{summary["featureFindings"]} bounded capability findings**: ' + ', '.join(f'{amount} {name}' for name, amount in sorted(status.items())) + '. These are findings, not a percentage of all features in existence.', '',
        '## Limits', '', *['- ' + item for item in audit['limitations']], '',
        '## Plugin-only test evidence', '',
        TEST_EVIDENCE_SCOPE['repository'], '', TEST_EVIDENCE_SCOPE['defaultTests'], '',
        'The two unchanged real-companion suites under `integration-tests/java` contain 11 tests and require an externally supplied Shortest Path 1.21.3 JAR at the recorded upstream pin. They are optional and are not run by the default test task. Their earlier integration results do not establish a pass for this exported checkout. See `integration-tests/README.md`.', '',
        'The six tests from `RoutingCompanionStartupTest` and `QuestHelperLauncherBridgeTest` belonged to the separate development launcher. Those suites are absent and are not claimed as plugin-only coverage. The retained popularity snapshot and total of 447 capability findings are unchanged. The combined quest-metadata/Quest Helper planning-link finding is partial because this export supplies no handoff bridge. Neither a retained hash nor this generator establishes a test pass.', '',
        '## Architecture gaps that must remain visible', '', *['- ' + item for item in audit['largestArchitectureGaps']], '',
        '## Complete high-usage tier', '',
        '| Rank | Plugin | Active installs | Capability findings |', '|---:|---|---:|---|']
    for comparison in comparisons:
        count = Counter(feature['status'] for feature in comparison['features'])
        markdown.append(f'| {comparison["availableRank"]} | [{comparison["name"]}](https://runelite.net/plugin-hub/show/{comparison["pluginId"]}) | {comparison["activeInstalls"]:,} | ' + ', '.join(f'{n} {kind}' for kind, n in sorted(count.items())) + ' |')
    markdown += ['', '## Selected owned native extensions', '',
        'These separate source checks keep the owned World Hopper, Agility, floor-loot and farming/gathering mechanisms visible. They are excluded from Hub feature counts and are not an exhaustive built-in RuneLite audit.', '']
    for extension in supplemental:
        markdown += ['### ' + extension['title'], '', '[Pinned native source](' + extension['primarySource'] + ').', '']
        markdown += ['- ' + item + '.' for item in extension['implementedMechanisms']]
        markdown += ['', 'Remaining boundaries: ' + '; '.join(extension['remainingLimits']) + '.', '']
        markdown += ['Inspected source/tests: ' + ', '.join('`' + Path(item['file']).name + '`' for item in extension['sourceInspection']) + '.', '']
    markdown += ['', '## Feature-by-feature findings', '']
    for comparison in comparisons:
        markdown += [f'### {comparison["availableRank"]}. {comparison["name"]}', '',
            f'Active installs: {comparison["activeInstalls"]:,}. [Pinned upstream documentation]({comparison["primaryReadme"]["source"]}).', '']
        markdown += [f'- **{feature["status"]}** — {feature["capability"]}.' for feature in comparison['features']]
        if comparison['qualification']: markdown += ['', comparison['qualification']]
        code = [Path(row['file']).name for row in comparison['sourceInspection'] if row['kind'] == 'production']
        if code: markdown += ['', 'Inspected Runewarden files: ' + ', '.join('`' + name + '`' for name in code) + '.']
        markdown += ['']
    markdown += ['## Rebuild', '', '```sh', 'python3 tools/fetch-hub-audit-sources.py --output-dir /tmp/runewarden-hub-evidence', 'python3 tools/build-hub-coverage-audit.py --source-dir /tmp/runewarden-hub-evidence', '```', '',
        'The repository retains the signed metadata, pinned native certificate and text-source indices in `docs/hub-source-snapshot`. The fetcher reconstructs the metadata and downloads only hash-verified pinned text at no more than eight workers. The generator verifies the signed manifest, pinned README/Java hashes, threshold coverage, source file hashes and evidence references. It writes `hub-inventory.json` and this report together with the machine-readable `hub-utility-audit.json`. Original third-party READMEs are not bundled into the plugin. `python3 tools/build-hub-coverage-audit.py --check-evidence` verifies the retained source/test hashes and complete baseline coverage offline.', '']
    (docs / 'hub-utility-audit.md').write_text('\n'.join(markdown))
    print(json.dumps(summary, indent=2))


if __name__ == '__main__':
    main()
