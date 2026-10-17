"""성공한 내부 테스트 배포 태그를 기준으로 다음 Android 버전을 결정한다."""

import configparser
import re
import subprocess
from pathlib import Path


TAG_PATTERN = re.compile(r"android-internal-(\d+)")
NAME_PATTERN = re.compile(r"versionName=(\d+)\.(\d+)\.(\d{4})")
REPOSITORY_DIR = Path(__file__).resolve().parents[1]


def next_release_version(
    major: int, minor: int, first_code: int, tags: dict[str, str]
) -> tuple[int, str]:
    code = first_code
    last_patch = 0
    for tag, subject in tags.items():
        match = TAG_PATTERN.fullmatch(tag)
        if not match:
            continue
        code = max(code, int(match.group(1)) + 1)
        name = NAME_PATTERN.fullmatch(subject)
        if name and (int(name.group(1)), int(name.group(2))) == (major, minor):
            last_patch = max(last_patch, int(name.group(3)))

    patch = last_patch + 1
    if patch > 9999:
        raise ValueError("patch가 9999에 도달했다. version_minor를 올려야 한다")
    return code, f"{major}.{minor}.{patch:04d}"


def read_release_tags(repository_dir: Path) -> dict[str, str]:
    result = subprocess.run(
        ["git", "for-each-ref", "--format=%(refname:short)%09%(contents:subject)", "refs/tags/android-internal-*"],
        cwd=repository_dir,
        check=True,
        capture_output=True,
        text=True,
    )
    return dict(line.split("\t", 1) for line in result.stdout.splitlines())


def main() -> None:
    config = configparser.ConfigParser()
    config.read_string("[version]\n" + (REPOSITORY_DIR / "version.properties").read_text(encoding="utf-8"))
    major = config.getint("version", "version_major")
    minor = config.getint("version", "version_minor")
    first_code = config.getint("version", "first_version_code")
    if major < 1 or minor < 0 or first_code < 1:
        raise ValueError("version.properties: major와 first_version_code는 양수, minor는 0 이상이어야 한다")

    tags = read_release_tags(REPOSITORY_DIR)
    code, name = next_release_version(major, minor, first_code, tags)
    print(f"code={code}\nname={name}")


if __name__ == "__main__":
    main()
