#!/usr/bin/env python3
"""Offline structural validator; this cannot prove Android/Firebase runtime correctness."""
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(__file__).resolve().parent.parent
required = [
    'settings.gradle.kts', 'build.gradle.kts', 'app/build.gradle.kts',
    'app/src/main/AndroidManifest.xml',
    'app/src/main/java/com/aaspass/app/MainActivity.kt',
    'app/src/main/java/com/aaspass/app/ui/CallScreen.kt',
    'app/src/main/java/com/aaspass/app/calls/RtcSession.kt',
    'app/src/main/java/com/aaspass/app/data/MediaRepository.kt',
    'firebase/storage.rules', 'firebase/firestore.rules',
    'firebase/functions/calls.js', 'firebase/functions/media.js',
    'firebase/functions/account.js', 'README.md',
    'branding/official-logo-original.png',
    'app/src/main/res/mipmap-xxhdpi/ic_launcher.png',
    'qa/RELEASE_CHECKLIST.md',
    'app/src/main/java/com/aaspass/app/navigation/NotificationDestination.kt',
    'app/src/main/java/com/aaspass/app/data/AttachmentCachePolicy.kt',
    'app/src/main/java/com/aaspass/app/data/ReadReceiptEligibility.kt',
    'firebase/functions/push-utils.js', 'PUBLISH_HINDI.md'
]
issues = []
for path in required:
    if not (root / path).is_file():
        issues.append(f'MISSING: {path}')
if (root / 'app/google-services.json').exists():
    issues.append('SECRET: app/google-services.json must not ship in the archive')
if any(root.rglob('*.jks')) or any(root.rglob('*.keystore')):
    issues.append('SECRET: signing key must not ship in the archive')
xml_files = sorted(root.rglob('*.xml'))
for file in xml_files:
    try:
        ET.parse(file)
    except ET.ParseError as e:
        issues.append(f'XML {file.relative_to(root)}: {e}')
json_files = sorted(root.rglob('*.json'))
for file in json_files:
    try:
        json.loads(file.read_text(encoding='utf-8'))
    except (ValueError, UnicodeDecodeError) as e:
        issues.append(f'JSON {file.relative_to(root)}: {e}')
manifest = (root/'app/src/main/AndroidManifest.xml').read_text()
for permission in ['android.permission.INTERNET', 'android.permission.RECORD_AUDIO',
                   'android.permission.CAMERA', 'android.permission.POST_NOTIFICATIONS']:
    if permission not in manifest:
        issues.append(f'Missing Android permission: {permission}')
storage_rules = (root/'firebase/storage.rules').read_text()
if storage_rules.count("assetId.matches('^[A-Za-z0-9]{20}$')") != 2:
    issues.append('Storage rule is missing one or more upload asset-ID restrictions')
indexes = json.loads((root/'firebase/firestore.indexes.json').read_text())
if not any(idx.get('collectionGroup') == 'conversations' and
           any(field.get('fieldPath') == 'participants' and
               field.get('arrayConfig') == 'CONTAINS' for field in idx.get('fields', [])) and
           any(field.get('fieldPath') == 'lastAt' and
               field.get('order') == 'DESCENDING' for field in idx.get('fields', []))
           for idx in indexes.get('indexes', [])):
    issues.append('Missing recent-conversations composite index')
if not (root/'app/src/main/res/xml/file_paths.xml').exists():
    issues.append('Missing private FileProvider mapping')
print(f'Structural checks: {len(required)} mandatory files, '
      f'{len(xml_files)} XML, {len(json_files)} JSON')
for item in issues:
    print('FAIL:', item)
if issues:
    sys.exit(1)
print('PASS: static source/config sanity checks only')
