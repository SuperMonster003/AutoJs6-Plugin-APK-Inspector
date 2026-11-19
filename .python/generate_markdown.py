# -*- coding: utf-8 -*-
CHECK_MODE = False
CHECK_ERRORS = []
GENERATED_PATHS = set()

import json
import re
from pathlib import Path


LANGUAGE_CODES = [
    "zh-Hans",
    "zh-Hant-HK",
    "zh-Hant-TW",
    "en",
    "fr",
    "es",
    "ja",
    "ko",
    "ru",
    "ar",
]
LANGUAGE_CODE_DEFAULT = "zh-Hans"
ANDROID_CHANGELOG_ALIASES = {
    "zh-Hans": ["zh", "zh-Hans"],
    "zh-Hant-HK": ["zh-rHK", "zh-Hant-HK"],
    "zh-Hant-TW": ["zh-rTW", "zh-Hant-TW"],
}
ANDROID_INSTRUCTION_DIRECTORIES = {
    "zh-Hans": "raw-zh",
    "zh-Hant-HK": "raw-zh-rHK",
    "zh-Hant-TW": "raw-zh-rTW",
    "en": "raw-en",
    "fr": "raw-fr",
    "es": "raw-es",
    "ja": "raw-ja",
    "ko": "raw-ko",
    "ru": "raw-ru",
    "ar": "raw-ar",
}
ANDROID_INSTRUCTION_DEFAULT_LANGUAGE = "en"

# Plugin Center instruction copy lives in its own keys and never borrows README fields
# (D:/idea-projects/AUTOJS6_PLUGIN_INSTRUCTION_AGENTS.md).
INSTRUCTION_KEY_PREFIX = "instruction_"
INSTRUCTION_LIST_KEYS = [
    "instruction_start_steps",
    "instruction_features",
    "instruction_script_notes",
    "instruction_requirements",
]
INSTRUCTION_FAQ_KEY = "instruction_faq"
INSTRUCTION_REQUIRED_KEYS = [
    "instruction_p_what",
    "instruction_h3_start",
    "instruction_start_steps",
    "instruction_h3_requirements",
    "instruction_requirements",
]


ROOT = Path(__file__).resolve().parents[1]
README_DIR = ROOT / ".readme"
CHANGELOG_DIR = ROOT / ".changelog"
ANDROID_CHANGELOG_DIR = ROOT / "app" / "src" / "main" / "assets" / "doc"


def load_json(path: Path):
    with path.open("r", encoding="utf-8") as file:
        return json.load(file)


def render_template(text: str, values: dict) -> str:
    def replace(match):
        key = match.group(1).strip()
        if key not in values:
            raise KeyError(f"Missing template value: {key}")
        return str(values[key])

    return re.sub(r"\{\{\s*([A-Za-z0-9_$.-]+)\s*\}\}", replace, text)


def render_dynamic(value, values: dict):
    if isinstance(value, dict):
        return {key: render_dynamic(item, values) for key, item in value.items()}
    if isinstance(value, list):
        return [render_dynamic(item, values) for item in value]
    if isinstance(value, str):
        return render_template(value, values)
    return value


def bullet_list(items):
    return "\n".join(f"- {item}" for item in items)


def numbered_list(items):
    return "\n".join(f"{index}. {item}" for index, item in enumerate(items, start=1))


def faq_list(items):
    return "\n\n".join(f"#### {item['q']}\n\n{item['a']}" for item in items)


def markdown_link(label, url):
    return f"[{label}]({url})"


def load_languages():
    common = load_json(README_DIR / "common.json")
    languages = {}
    changelogs = {}
    raw_languages = {code: load_json(README_DIR / f"lang_{code}.json") for code in LANGUAGE_CODES}
    validate_instruction_sources(raw_languages)
    for code in LANGUAGE_CODES:
        raw_language = raw_languages[code]
        merged_language = {**common, **raw_language}
        languages[code] = render_dynamic(merged_language, merged_language)

        raw_changelog = load_json(CHANGELOG_DIR / f"lang_{code}.json")
        changelog_values = {key: value for key, value in raw_changelog.items() if key != "$data"}
        changelog_values = render_dynamic(changelog_values, changelog_values)
        changelogs[code] = {
            "values": changelog_values,
            "data": render_dynamic(raw_changelog["$data"], changelog_values),
        }
    return languages, changelogs


def shape_of(value):
    if isinstance(value, dict):
        return ("dict", tuple(sorted(value)))
    if isinstance(value, list):
        return ("list", len(value), tuple(shape_of(item) for item in value))
    return type(value).__name__


def validate_instruction_sources(raw_languages):
    reference = {k: v for k, v in raw_languages[LANGUAGE_CODE_DEFAULT].items() if k.startswith(INSTRUCTION_KEY_PREFIX)}
    for code, content in raw_languages.items():
        instruction = {k: v for k, v in content.items() if k.startswith(INSTRUCTION_KEY_PREFIX)}
        for key in INSTRUCTION_REQUIRED_KEYS:
            if key not in instruction:
                raise ValueError(f"Instruction language {code!r} is missing {key!r}")
        if set(instruction) != set(reference):
            raise ValueError(f"Instruction keys of {code!r} differ from {LANGUAGE_CODE_DEFAULT!r}: {sorted(set(instruction) ^ set(reference))}")
        for key, value in instruction.items():
            if shape_of(value) != shape_of(reference[key]):
                raise ValueError(f"Instruction field {key!r} of {code!r} does not match the {LANGUAGE_CODE_DEFAULT!r} shape")
            if key in INSTRUCTION_LIST_KEYS:
                valid = isinstance(value, list) and value and all(isinstance(item, str) and item for item in value)
            elif key == INSTRUCTION_FAQ_KEY:
                valid = isinstance(value, list) and value and all(
                    isinstance(item, dict) and set(item) == {"q", "a"} and all(isinstance(text, str) and text for text in item.values())
                    for item in value
                )
            else:
                valid = isinstance(value, str) and value
            if not valid:
                raise ValueError(f"Instruction field {key!r} of {code!r} is empty or malformed")
            # UI names are quoted with ASCII double quotes; an odd count means a quote was swallowed on the way in.
            texts = [value] if isinstance(value, str) else [
                text for item in value for text in (item.values() if isinstance(item, dict) else [item])
            ]
            for text in texts:
                if text.count('"') % 2:
                    raise ValueError(f"Instruction field {key!r} of {code!r} has an unpaired double quote: {text[:60]!r}")


def instruction_faq(items):
    return "\n\n".join(f"**{item['q']}**\n\n{item['a']}" for item in items)


def build_instruction_values(code, languages):
    """Only instruction_* keys reach the instruction template, so it cannot borrow README copy."""
    values = {key: value for key, value in languages[code].items() if key.startswith(INSTRUCTION_KEY_PREFIX)}
    for key in INSTRUCTION_LIST_KEYS:
        if key in values:
            render = numbered_list if key == "instruction_start_steps" else bullet_list
            values[f"placeholder_{key}"] = render(values[key])
    if INSTRUCTION_FAQ_KEY in values:
        values[f"placeholder_{INSTRUCTION_FAQ_KEY}"] = instruction_faq(values[INSTRUCTION_FAQ_KEY])
    return values


def format_changelog_items(changelog, limit=None, heading_level=2):
    values = changelog["values"]
    chunks = []
    heading = "#" * heading_level
    for index, (version_name, item) in enumerate(changelog["data"].items()):
        if limit is not None and index >= limit:
            break
        lines = [
            f"{heading} {version_name}",
            "",
            f"###### {item['released_date']}",
            "",
        ]
        for category in ["hint", "feature", "fix", "improvement", "dependency"]:
            for text in item.get(category, []):
                lines.append(f"* `{values[f'changelog_label_{category}']}` {text}")
        chunks.append("\n".join(lines).rstrip())
    return "\n\n".join(chunks).rstrip() + "\n"


def build_language_list(target_code, languages):
    repo_url = languages[target_code]["repo_url"]
    lines = []
    for code in LANGUAGE_CODES:
        content = languages[code]
        label = f"{content['$name']} [{code}]"
        if code == target_code:
            lines.append(f"- {label} # {content['text_current_lowercase']}")
        else:
            lines.append(f"- {markdown_link(label, f'{repo_url}/blob/master/.readme/README-{code}.md')}")
    return "\n".join(lines)


def build_readme_values(code, languages, changelogs):
    content = dict(languages[code])
    repo_url = content["repo_url"]
    content["placeholder_ul_languages_all_supported"] = build_language_list(code, languages)
    content["placeholder_features"] = bullet_list(content["features"])
    content["placeholder_usage_steps"] = numbered_list(content["usage_steps"])
    content["placeholder_security_limits"] = bullet_list(content["security_limits"])
    content["placeholder_faq"] = faq_list(content["faq"])
    content["placeholder_latest_release_history"] = format_changelog_items(
        changelogs[code],
        limit=3,
        heading_level=4,
    ).rstrip()
    content["placeholder_read_more_in_changelog_md"] = markdown_link(
        f"CHANGELOG-{code}.md",
        f"{repo_url}/blob/master/app/src/main/assets/doc/CHANGELOG-{code}.md",
    )
    return content


def write_text(path: Path, text: str):
    GENERATED_PATHS.add(path)
    if CHECK_MODE:
        if not path.is_file() or path.read_bytes().replace(b"\r\n", b"\n") != text.encode("utf-8"):
            CHECK_ERRORS.append(str(path.relative_to(ROOT)))
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8", newline="\n")
    print(f"Generated {path.relative_to(ROOT)}")


def generate_readmes(languages, changelogs):
    template = (README_DIR / "template_readme.md").read_text(encoding="utf-8")
    for code in LANGUAGE_CODES:
        output = render_template(template, build_readme_values(code, languages, changelogs))
        write_text(README_DIR / f"README-{code}.md", output)
        if code == LANGUAGE_CODE_DEFAULT:
            write_text(ROOT / "README.md", output)


def generate_changelogs(languages, changelogs):
    template = (CHANGELOG_DIR / "template_changelog.md").read_text(encoding="utf-8")
    for code in LANGUAGE_CODES:
        values = dict(languages[code])
        values["placeholder_release_history"] = format_changelog_items(
            changelogs[code],
            heading_level=2,
        ).rstrip()
        output = render_template(template, values)
        for name in ANDROID_CHANGELOG_ALIASES.get(code, [code]):
            write_text(ANDROID_CHANGELOG_DIR / f"CHANGELOG-{name}.md", output)
        if code == LANGUAGE_CODE_DEFAULT:
            write_text(ANDROID_CHANGELOG_DIR / "CHANGELOG.md", output)


def generate_instructions(languages):
    template = (README_DIR / "template_plugin_instruction.md").read_text(encoding="utf-8")
    resource_dir = ROOT / "app" / "src" / "main" / "res"
    for code in LANGUAGE_CODES:
        output = render_template(template, build_instruction_values(code, languages))
        write_text(resource_dir / ANDROID_INSTRUCTION_DIRECTORIES[code] / "plugin_instruction.md", output)
        if code == ANDROID_INSTRUCTION_DEFAULT_LANGUAGE:
            write_text(resource_dir / "raw" / "plugin_instruction.md", output)


def main():
    if LANGUAGE_CODE_DEFAULT not in LANGUAGE_CODES:
        raise ValueError(f"Default language code {LANGUAGE_CODE_DEFAULT!r} is not supported")
    languages, changelogs = load_languages()
    generate_changelogs(languages, changelogs)
    generate_readmes(languages, changelogs)
    generate_instructions(languages)



def validate_release_sources():
    import xml.etree.ElementTree as ET
    version_text = (ROOT / "version.properties").read_text(encoding="utf-8-sig")
    version = re.search(r"(?m)^VERSION_NAME=(.+)$", version_text).group(1).strip().split("-")[0]
    shape = None
    for code in LANGUAGE_CODES:
        path = CHANGELOG_DIR / f"lang_{code}.json"
        data = json.loads(path.read_text(encoding="utf-8"))["$data"]
        if next(iter(data)) != f"v{version}":
            raise ValueError(f"Current changelog version mismatch: {path}")
        latest = data[f"v{version}"]
        actual = [(key, len(value) if isinstance(value, list) else value) for key, value in latest.items()]
        if shape is None:
            shape = actual
        elif actual != shape:
            raise ValueError(f"Current changelog language shape mismatch: {path}")
    res = ROOT / "app/src/main/res"
    qualifiers = ["", "en", "ar", "es", "fr", "ja", "ko", "ru", "zh", "zh-rHK", "zh-rTW"]
    reference = None
    for qualifier in qualifiers:
        path = res / ("values" + ("-" + qualifier if qualifier else "")) / "strings.xml"
        entries = {item.attrib["name"]: "".join(item.itertext()) for item in ET.parse(path).getroot() if item.tag == "string"}
        if "plugin_description" not in entries:
            raise ValueError(f"Missing localized plugin description: {path}")
        if list(entries) != sorted(entries):
            raise ValueError(f"Unsorted strings: {path}")
        if reference is None:
            reference = entries
        elif qualifier == "en" and any(value != reference[name] for name, value in entries.items() if name in reference):
            raise ValueError("Default and explicit English strings differ")


def cli():
    import argparse
    global CHECK_MODE
    parser = argparse.ArgumentParser(description="Generate localized documentation or check it without writing")
    parser.add_argument("--check", action="store_true")
    CHECK_MODE = parser.parse_args().check
    validate_release_sources()
    main()
    if CHECK_ERRORS:
        raise SystemExit("Generated documentation differs: " + ", ".join(CHECK_ERRORS))
    print(f"{'Checked' if CHECK_MODE else 'Generated'} {len(GENERATED_PATHS)} documentation artifacts")


if __name__ == "__main__":
    cli()
