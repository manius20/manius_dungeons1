#!/usr/bin/env python3
"""Dependency-free release checks; fails CI on version or self-test drift."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent
ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
pom = ET.parse(root/'pom.xml').getroot()
version = pom.find('m:version', ns).text
plugin = (root/'src/main/resources/plugin.yml').read_text(encoding='utf-8')
found = re.search(r"(?m)^version:\s*['\"]?([^'\"\n]+)", plugin)
assert found and found.group(1).strip() == version, f'plugin.yml {found.group(1) if found else "MISSING"} != pom.xml {version}'
assert "api-version: '26.2'" in plugin
ex = pom.findall('.//m:execution', ns)
executed = {e.find('m:configuration/m:mainClass', ns).text.rsplit('.', 1)[-1] for e in ex}
tests = {p.stem for p in (root/'src/test/java').rglob('*SelfTest.java')}
assert executed == tests, f'POM/test drift: missing from Maven={tests - executed}; missing source={executed - tests}'
checks = {
    'private admin notice': 'case "testkomunikat"',
    'entrance preview': 'case "podgladwejsc"',
}
admin = (root/'src/main/java/pl/s16/dungeons/AdminCommand.java').read_text()
for description, symbol in checks.items():
    assert symbol in admin, f'Missing {description}'
print(f'PASS project release checks: {version}, {len(tests)} test suites, plugin.yml and Maven match')
