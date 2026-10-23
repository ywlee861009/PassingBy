import contextlib
import io
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import check_module_boundaries as boundaries


class ModuleBoundariesTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.root.joinpath('settings.gradle.kts').write_text('include(' + ','.join(
            '":' + module.replace('/', ':') + '"' for module in boundaries.ALLOWED) + ')')
        for module, dependencies in boundaries.ALLOWED.items():
            folder = self.root / module
            folder.mkdir(parents=True)
            folder.joinpath('build.gradle.kts').write_text('\n'.join(
                'implementation(project(":' + dep.replace('/', ':') + '"))' for dep in dependencies))

    def result(self):
        output = io.StringIO()
        with patch.object(boundaries, 'ROOT', self.root), contextlib.redirect_stdout(output), contextlib.redirect_stderr(output):
            code = boundaries.main()
        return code, output.getvalue()

    def source(self, module, text):
        path = self.root / module / 'src/main/kotlin/Example.kt'
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)

    def test_current_graph_is_allowed(self):
        self.assertEqual(0, self.result()[0])

    def test_feature_cannot_restore_the_train_dependency(self):
        path = self.root / 'feature/journey/build.gradle.kts'
        path.write_text(path.read_text() + '\nimplementation(project(":experience:train"))')
        code, output = self.result()
        self.assertEqual(1, code)
        self.assertIn('forbidden dependency on experience/train', output)

    def test_domain_cannot_import_android(self):
        self.source('journey/domain', 'import android.os.SystemClock')
        self.assertIn('JVM domain references', self.result()[1])

    def test_feature_cannot_import_a_train_implementation(self):
        self.source('feature/journey', 'import com.kero.passingby.scene.SceneRenderer')
        self.assertIn('feature references train', self.result()[1])

    def test_feature_cannot_write_completion_effects(self):
        self.source('feature/journey', 'fun frame() { store.saveSession(active) }')
        self.assertIn('outside the runtime', self.result()[1])

    def test_libraries_cannot_reverse_reference_app_resources(self):
        self.source('journey/data', 'import com.kero.passingby.R')
        self.assertIn('library references app', self.result()[1])

    def test_cycles_are_reported(self):
        path = self.root / 'journey/domain/build.gradle.kts'
        path.write_text('implementation(project(":app"))')
        self.assertIn('Dependency cycle:', self.result()[1])

    def test_unknown_dependency_fails_with_a_diagnostic(self):
        path = self.root / 'journey/domain/build.gradle.kts'
        path.write_text('implementation(project(":unknown"))')
        self.assertIn('forbidden dependency on unknown', self.result()[1])


if __name__ == '__main__':
    unittest.main()
