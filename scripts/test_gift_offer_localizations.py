#!/usr/bin/env python3
"""Check gift upsell and paywall resources without allowing English fallback."""

from pathlib import Path
import xml.etree.ElementTree as ET

res = Path(__file__).resolve().parents[1] / "app/src/main/res"
languages = [item.attrib["{http://schemas.android.com/apk/res/android}name"]
             for item in ET.parse(res / "xml/locales_config.xml").getroot()]
keys = {
    "gift_club_offer_title", "gift_club_offer_body",
    "gift_club_offer_cta_trial", "gift_club_offer_cta_standard",
    "club_subtitle_gift", "club_feature_gifts", "gift_club_slots",
}


def strings(directory):
    return {item.attrib["name"]: "".join(item.itertext()).strip()
            for path in directory.glob("*.xml")
            for item in ET.parse(path).getroot().findall("string")}


english = strings(res / "values")
errors = []
for language in languages:
    if language == "en":
        continue
    qualifier = {"pt-BR": "pt-rBR", "zh-Hans": "b+zh+Hans"}.get(language, language)
    translated = strings(res / f"values-{qualifier}")
    for key in sorted(keys):
        value = translated.get(key, "")
        if not value or value == english[key]:
            errors.append(f"{language}: {key}")
        elif "3" in english[key] and "3" not in value:
            errors.append(f"{language}: missing three-gift allowance in {key}")

assert not errors, "Missing gift offer translations:\n" + "\n".join(errors)
print(f"PASS: {len(keys)} gift offer strings across {len(languages) - 1} translated Android languages")
