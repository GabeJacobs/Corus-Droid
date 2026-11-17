"""Check an Android manifest produced by Gradle's processReleaseManifest task."""

import argparse
from pathlib import Path
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path, help="Path to the merged AndroidManifest.xml")
    manifest = ET.parse(parser.parse_args().manifest).getroot()
    android = "{http://schemas.android.com/apk/res/android}"
    permissions = {entry.get(android + "name") for entry in manifest.findall("uses-permission")}
    assert "com.google.android.gms.permission.AD_ID" in permissions, (
        "Google Play requires the AD_ID permission under the current declaration"
    )
    assert "android.permission.ACCESS_ADSERVICES_AD_ID" not in permissions, (
        "The separate Android AdServices advertising-ID permission must be excluded"
    )

    settings = {
        entry.get(android + "name"): entry.get(android + "value")
        for entry in manifest.findall("application/meta-data")
    }
    assert settings.get("google_analytics_adid_collection_enabled") == "false", (
        "Firebase Analytics advertising-ID collection must be disabled"
    )


if __name__ == "__main__":
    main()
