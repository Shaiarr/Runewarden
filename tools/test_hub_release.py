"""Keep direct Java Sound out while permitting RuneLite's injected AudioPlayer."""
import pathlib
import struct
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import check_hub_jar
from validate_hub_release import validate


def class_bytes(name, references=(), strings=(), descriptor=None):
    """Small Java 11 class fixtures with real constant-pool entry types."""
    entries = []

    def utf8(value):
        encoded = value.encode()
        entries.append(b'\x01' + struct.pack('>H', len(encoded)) + encoded)
        return len(entries)

    def class_entry(value):
        name_index = utf8(value)
        entries.append(b'\x07' + struct.pack('>H', name_index))
        return len(entries)

    this_class = class_entry(name)
    superclass = class_entry('java/lang/Object')
    for reference in references:
        class_entry(reference)
    for value in strings:
        value_index = utf8(value)
        entries.append(b'\x08' + struct.pack('>H', value_index))
    if descriptor:
        owner = class_entry('net/runelite/client/audio/AudioPlayer')
        method_name = utf8('play')
        method_descriptor = utf8(descriptor)
        entries.append(b'\x0c' + struct.pack('>HH', method_name, method_descriptor))
        name_and_type = len(entries)
        entries.append(b'\x0a' + struct.pack('>HH', owner, name_and_type))
    return (
        b'\xca\xfe\xba\xbe' + struct.pack('>HHH', 0, 55, len(entries) + 1)
        + b''.join(entries)
        + struct.pack('>HHHHHHH', 0x0021, this_class, superclass, 0, 0, 0, 0)
    )


class HubReleaseSourceTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = pathlib.Path(self.temporary.name)
        self.production = self.root / 'src/main/java/com/runewarden'
        self.production.mkdir(parents=True)
        for name in ('HudLayoutPositions',):
            (self.production / (name + '.java')).write_text('class ' + name + ' {}')

    def scan(self, source):
        (self.production / 'Soundboard.java').write_text(source)
        return validate(self.root)

    def test_rejects_direct_import_even_without_a_playback_call(self):
        failures = self.scan('import javax.sound.sampled.Clip; class Soundboard {}')
        self.assertEqual(failures, ['src/main/java/com/runewarden/Soundboard.java: direct Java Sound API'])

    def test_rejects_fully_qualified_use_with_qualified_name_whitespace(self):
        failures = self.scan('class Soundboard { Object open() { return javax . sound . sampled . AudioSystem.getClip(); } }')
        self.assertTrue(any('direct Java Sound API' in failure for failure in failures))

    def test_allows_injected_runelite_audio_and_ignores_explanatory_comments_and_strings(self):
        self.assertEqual(self.scan('''
            import net.runelite.client.audio.AudioPlayer;
            class Soundboard {
                @Inject private AudioPlayer audioPlayer;
                // Replaces javax.sound.sampled.AudioSystem.
                /* Do not call javax.sound.sampled.Clip directly. */
                String explanation = "No javax.sound.sampled access";
                void play(java.io.InputStream input) { audioPlayer.play(input); }
            }
        '''), [])

    def test_does_not_scan_test_only_sound_references(self):
        tests = self.root / 'src/test/java'
        tests.mkdir(parents=True)
        (tests / 'AudioTest.java').write_text('import javax.sound.sampled.Clip;')
        self.assertEqual(self.scan('class Soundboard {}'), [])

    def test_existing_forbidden_process_guard_still_runs(self):
        failures = self.scan('class Soundboard { ProcessBuilder process; }')
        self.assertTrue(any('forbidden Java/API type' in failure for failure in failures))


class HubReleaseJarTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = pathlib.Path(self.temporary.name)
        (self.root / 'build.gradle').write_text("version = '0.9.6'\n")
        (self.root / 'runelite-plugin.properties').write_text(
            'build=standard\nversion=0.9.6\nplugins=com.runewarden.RunewardenPlugin\n'
        )

    def verify(self, **plugin_options):
        jar = self.root / 'runewarden-0.9.6.jar'
        with zipfile.ZipFile(jar, 'w') as archive:
            archive.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\n')
            for name, options in (
                ('RunewardenPlugin', plugin_options),
                ('RunewardenConfig', {'strings': ['runewarden']}),
                ('RunewardenVersion', {'strings': ['0.9.6']}),
            ):
                class_name = 'com/runewarden/' + name
                archive.writestr(class_name + '.class', class_bytes(class_name, **options))
        with patch.object(check_hub_jar, 'ROOT', self.root):
            return check_hub_jar.verify(jar)

    def test_rejects_direct_audio_system_class_reference(self):
        with self.assertRaisesRegex(AssertionError, 'Direct Java Sound API reference'):
            self.verify(references=['javax/sound/sampled/AudioSystem'])

    def test_rejects_direct_clip_reference(self):
        with self.assertRaisesRegex(AssertionError, 'Direct Java Sound API reference'):
            self.verify(references=['javax/sound/sampled/Clip'])

    def test_rejects_sound_array_class_reference(self):
        with self.assertRaisesRegex(AssertionError, 'Direct Java Sound API reference'):
            self.verify(references=['[Ljavax/sound/sampled/Clip;'])

    def test_allows_runelite_audio_player_method_descriptor_returning_clip(self):
        report = self.verify(descriptor='(Ljava/io/InputStream;)Ljavax/sound/sampled/Clip;')
        self.assertEqual(report['classes'], 3)
        self.assertFalse(report['hubApproved'])

    def test_allows_ordinary_string_mention_of_java_sound(self):
        report = self.verify(strings=['javax/sound/sampled/Clip', 'javax.sound.sampled.AudioSystem'])
        self.assertTrue(report['resourcesMatch'])


if __name__ == '__main__':
    unittest.main()
