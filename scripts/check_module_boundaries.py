"""Check the stage-one module graph without resolving Gradle or Android SDKs."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
ALLOWED = {
    'app': {'journey/domain', 'journey/data', 'feature/journey', 'experience/api', 'experience/train', 'platform/notifications', 'platform/billing', 'platform/telemetry'},
    'journey/domain': set(),
    'journey/data': {'journey/domain'},
    'experience/api': set(),
    'experience/train': {'experience/api', 'platform/telemetry'},
    # Transitional dependency: the train-specific ViewModel is separated in stage two.
    'feature/journey': {'journey/domain', 'experience/api', 'experience/train', 'platform/billing', 'platform/notifications', 'platform/telemetry'},
    'platform/notifications': {'platform/telemetry'},
    'platform/billing': {'platform/telemetry'},
    'platform/telemetry': set(),
}

def main():
    failures = []
    graph = {}
    settings = (ROOT / 'settings.gradle.kts').read_text()
    included = {p.replace(':', '/') for p in re.findall(r'"\:([\w:]+)"', settings)}
    if included != set(ALLOWED):
        failures.append(f'Update the checked module graph: {included ^ set(ALLOWED)}')
    for module, allowed in ALLOWED.items():
        folder = ROOT / module
        build = (folder / 'build.gradle.kts').read_text()
        dependencies = {p.replace(':', '/') for p in re.findall(r'project\("\:([\w:]+)"\)', build)}
        graph[module] = dependencies
        for dependency in dependencies - allowed:
            failures.append(f'{module}: forbidden dependency on {dependency}')
        for source in (folder / 'src/main').rglob('*.kt'):
            text = source.read_text()
            relative = source.relative_to(ROOT)
            if module != 'app' and re.search(r'\bcom\.kero\.passingby\.(?:R|BuildConfig)\b', text):
                failures.append(f'{relative}: library references app R or BuildConfig')
            if module == 'journey/domain' and re.search(r'^import (?:android\.|androidx\.|com\.google\.)', text, re.M):
                failures.append(f'{relative}: JVM domain references an Android/SDK dependency')
            if module == 'journey/data' and re.search(r'^import com\.kero\.passingby\.(?:scene|content|ui)\.', text, re.M):
                failures.append(f'{relative}: storage references experience/UI types')
            if module != 'platform/telemetry' and re.search(r'^import com\.google\.firebase\.', text, re.M):
                failures.append(f'{relative}: Firebase SDK escaped its adapter')
            if module != 'platform/billing' and re.search(r'^import com\.android\.billingclient\.', text, re.M):
                failures.append(f'{relative}: Play Billing SDK escaped its adapter')
    def visit(module, path):
        if module in path:
            failures.append('Dependency cycle: ' + ' -> '.join([*path, module]))
            return
        for child in graph[module]:
            visit(child, [*path, module])
    for module in graph:
        visit(module, [])
    if failures:
        print('\n'.join(failures), file=sys.stderr)
        return 1
    print(f'Module boundaries OK ({len(graph)} modules; domain is JVM-only; no dependency on :app).')
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
