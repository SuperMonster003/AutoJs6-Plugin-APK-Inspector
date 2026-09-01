#!/usr/bin/env python3
"""Capture and audit the APK Inspector UI review matrix on an Android emulator."""

from __future__ import annotations

import argparse
import html
import itertools
import json
import os
import re
import shlex
import struct
import subprocess
import sys
import time
import xml.etree.ElementTree as ElementTree
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MATRIX = ROOT / "app/src/test/resources/ui-review-matrix/matrix.json"
BOUNDS_PATTERN = re.compile(r"\[(?P<left>-?\d+),(?P<top>-?\d+)]\[(?P<right>-?\d+),(?P<bottom>-?\d+)]")
OVERRIDE_SIZE_PATTERN = re.compile(r"Override size:\s*(\d+x\d+)")
PHYSICAL_SIZE_PATTERN = re.compile(r"Physical size:\s*(\d+x\d+)")
OVERRIDE_DENSITY_PATTERN = re.compile(r"Override density:\s*(\d+)")
PHYSICAL_DENSITY_PATTERN = re.compile(r"Physical density:\s*(\d+)")
MEDIA_ID_PATTERN = re.compile(r"\b_id=(\d+)\b")
APP_LOCALES_PATTERN = re.compile(r"\[(.*)]")
NIGHT_MODE_PATTERN = re.compile(r"Night mode:\s*([^\s]+)")
WINDOW_ROTATION_PATTERN = re.compile(r"\bmRotation=([0-3])\b")
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


class MatrixError(RuntimeError):
    """Raised when the matrix or a device interaction violates its contract."""


def log(message: str) -> None:
    print(message, flush=True)


def slug(value: str) -> str:
    return re.sub(r"[^a-z0-9._-]+", "-", value.lower()).strip("-")


def relative_to_output(path: Path, output: Path) -> str:
    return path.relative_to(output).as_posix()


def run_process(
    arguments: list[str],
    *,
    timeout: float = 60,
    text: bool = True,
    check: bool = True,
) -> subprocess.CompletedProcess[Any]:
    decoding: dict[str, Any] = {}
    if text:
        decoding = {"encoding": "utf-8", "errors": "replace"}
    try:
        completed = subprocess.run(
            arguments,
            cwd=ROOT,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=text,
            **decoding,
            timeout=timeout,
            check=False,
        )
    except FileNotFoundError as error:
        raise MatrixError(f"Command not found: {arguments[0]}") from error
    except subprocess.TimeoutExpired as error:
        raise MatrixError(f"Command timed out after {timeout:g}s: {shlex.join(arguments)}") from error

    if check and completed.returncode != 0:
        stdout = completed.stdout if text else completed.stdout.decode("utf-8", errors="replace")
        stderr = completed.stderr if text else completed.stderr.decode("utf-8", errors="replace")
        detail = "\n".join(item.strip() for item in (stdout, stderr) if item and item.strip())
        raise MatrixError(
            f"Command failed ({completed.returncode}): {shlex.join(arguments)}"
            + (f"\n{detail}" if detail else "")
        )
    return completed


class Adb:
    def __init__(self, executable: str, serial: str) -> None:
        self.executable = executable
        self.prefix = [executable, "-s", serial]

    def command(
        self,
        *arguments: str,
        timeout: float = 60,
        text: bool = True,
        check: bool = True,
    ) -> subprocess.CompletedProcess[Any]:
        command = [*self.prefix, *map(str, arguments)]
        for attempt in range(3):
            try:
                return run_process(
                    command,
                    timeout=timeout,
                    text=text,
                    check=check,
                )
            except MatrixError as error:
                transient = any(
                    marker in str(error).lower()
                    for marker in (
                        "daemon not running",
                        "protocol fault",
                        "connection reset",
                        "device offline",
                        "cannot connect",
                        "transport error",
                    )
                )
                if not transient or attempt == 2:
                    raise
                run_process(
                    [self.executable, "start-server"],
                    timeout=30,
                    check=False,
                )
                time.sleep(1.0)
        raise AssertionError("unreachable")

    def output(self, *arguments: str, timeout: float = 60) -> str:
        return self.command(*arguments, timeout=timeout).stdout.strip()

    def shell(
        self,
        *arguments: str,
        timeout: float = 60,
        check: bool = True,
    ) -> str:
        remote_command = " ".join(shlex.quote(str(argument)) for argument in arguments)
        return self.output("shell", remote_command, timeout=timeout) if check else self.command(
            "shell",
            remote_command,
            timeout=timeout,
            check=False,
        ).stdout.strip()

    def exec_out(self, *arguments: str, timeout: float = 60) -> bytes:
        return self.command("exec-out", *arguments, timeout=timeout, text=False).stdout


@dataclass(frozen=True)
class Bounds:
    left: int
    top: int
    right: int
    bottom: int

    @property
    def width(self) -> int:
        return max(0, self.right - self.left)

    @property
    def height(self) -> int:
        return max(0, self.bottom - self.top)

    @property
    def center(self) -> tuple[int, int]:
        return ((self.left + self.right) // 2, (self.top + self.bottom) // 2)

    def intersects(self, other: "Bounds") -> bool:
        return (
            self.width > 0
            and self.height > 0
            and self.right > other.left
            and self.left < other.right
            and self.bottom > other.top
            and self.top < other.bottom
        )


def parse_bounds(raw: str) -> Bounds:
    match = BOUNDS_PATTERN.fullmatch(raw)
    if not match:
        return Bounds(0, 0, 0, 0)
    return Bounds(*(int(match.group(name)) for name in ("left", "top", "right", "bottom")))


@dataclass
class UiNode:
    element: ElementTree.Element

    @property
    def resource_id(self) -> str:
        raw = self.element.attrib.get("resource-id", "")
        return raw.rsplit("/", 1)[-1] if raw else ""

    @property
    def package(self) -> str:
        return self.element.attrib.get("package", "")

    @property
    def text(self) -> str:
        return self.element.attrib.get("text", "").strip()

    @property
    def description(self) -> str:
        return self.element.attrib.get("content-desc", "").strip()

    @property
    def class_name(self) -> str:
        return self.element.attrib.get("class", "")

    @property
    def bounds(self) -> Bounds:
        return parse_bounds(self.element.attrib.get("bounds", ""))

    @property
    def enabled(self) -> bool:
        return self.element.attrib.get("enabled") == "true"

    @property
    def clickable(self) -> bool:
        return self.element.attrib.get("clickable") == "true"

    @property
    def long_clickable(self) -> bool:
        return self.element.attrib.get("long-clickable") == "true"

    @property
    def focusable(self) -> bool:
        return self.element.attrib.get("focusable") == "true"

    def accessible_label(self) -> str:
        direct = self.description or self.text
        if direct:
            return direct
        labels: list[str] = []
        for descendant in self.element.iter("node"):
            if descendant is self.element:
                continue
            label = (
                descendant.attrib.get("content-desc", "").strip()
                or descendant.attrib.get("text", "").strip()
            )
            if label and label not in labels:
                labels.append(label)
        return " | ".join(labels)


class Hierarchy:
    def __init__(self, xml: str, application_id: str) -> None:
        self.xml = xml
        try:
            self.tree = ElementTree.fromstring(xml)
        except ElementTree.ParseError as error:
            raise MatrixError(f"Unable to parse UI hierarchy: {error}") from error
        root_element = self.tree.find("node")
        if root_element is None:
            raise MatrixError("UI hierarchy did not contain a root node")
        self.display_bounds = parse_bounds(root_element.attrib.get("bounds", ""))
        self._display_size = (self.display_bounds.right, self.display_bounds.bottom)
        self.nodes = [UiNode(element) for element in self.tree.iter("node")]
        self.application_id = application_id

    @property
    def display_size(self) -> tuple[int, int]:
        # In landscape Android may reserve a navigation strip on the logical start side, so the
        # active root can begin at x > 0 while its right/bottom edges still identify the display.
        return self._display_size

    def use_physical_display_size(self, size: tuple[int, int]) -> None:
        self._display_size = size

    def visible(self, node: UiNode) -> bool:
        return node.bounds.intersects(self.display_bounds)

    def app_nodes(self, *, visible_only: bool = False) -> list[UiNode]:
        nodes = [node for node in self.nodes if node.package == self.application_id]
        return [node for node in nodes if self.visible(node)] if visible_only else nodes

    def visible_resource(self, resource_id: str, *, enabled: bool | None = None) -> UiNode | None:
        for node in self.app_nodes(visible_only=True):
            if node.resource_id != resource_id:
                continue
            if enabled is not None and node.enabled != enabled:
                continue
            return node
        return None

    def visible_checkpoint(self, checkpoint: dict[str, Any], density_dpi: int) -> UiNode | None:
        return self.checkpoint_match(checkpoint, density_dpi, require_minimum_height=True)

    def checkpoint_match(
        self,
        checkpoint: dict[str, Any],
        density_dpi: int,
        *,
        require_minimum_height: bool,
    ) -> UiNode | None:
        resource_id = checkpoint.get("targetResourceId")
        description_prefix = checkpoint.get("targetContentDescriptionPrefix")
        minimum_height_px = round(checkpoint.get("minimumVisibleHeightDp", 0) * density_dpi / 160)
        for node in self.app_nodes(visible_only=True):
            if resource_id and node.resource_id != resource_id:
                continue
            if description_prefix and not node.description.startswith(description_prefix):
                continue
            if not resource_id and not description_prefix:
                continue
            if not node.enabled:
                continue
            if require_minimum_height and node.bounds.height < minimum_height_px:
                continue
            return node
        return None

    def packages(self) -> set[str]:
        return {node.package for node in self.nodes if node.package}


@dataclass
class Audit:
    application_id: str
    density_dpi: int
    minimum_touch_target_dp: int
    seen_by_phase: dict[str, set[str]] = field(default_factory=lambda: {"report": set(), "manifest": set()})
    heading_first_seen: dict[str, int] = field(default_factory=dict)
    semantic_order: dict[str, list[str]] = field(default_factory=lambda: {"report": [], "manifest": []})
    semantic_keys: dict[str, set[str]] = field(default_factory=lambda: {"report": set(), "manifest": set()})
    control_sizes: dict[str, tuple[int, int]] = field(default_factory=dict)
    unlabeled_controls: set[str] = field(default_factory=set)
    ellipsized_report_text: set[str] = field(default_factory=set)
    observation_index: int = 0

    def ingest(self, hierarchy: Hierarchy, phase: str) -> None:
        for node in hierarchy.app_nodes(visible_only=True):
            resource_id = node.resource_id
            label = node.accessible_label()
            if resource_id:
                self.seen_by_phase[phase].add(resource_id)
                if resource_id.endswith("_heading") and resource_id not in self.heading_first_seen:
                    self.heading_first_seen[resource_id] = self.observation_index

            if label and (node.focusable or node.clickable or node.long_clickable or resource_id.endswith("_heading")):
                semantic_key = f"{resource_id}|{node.class_name}|{label}"
                if semantic_key not in self.semantic_keys[phase]:
                    self.semantic_keys[phase].add(semantic_key)
                    identity = resource_id or node.class_name.rsplit(".", 1)[-1]
                    self.semantic_order[phase].append(f"{identity}: {label}")

            if node.clickable or node.long_clickable:
                identity = resource_id or node.description or node.class_name
                if not label:
                    self.unlabeled_controls.add(identity)
                previous = self.control_sizes.get(identity, (0, 0))
                self.control_sizes[identity] = (
                    max(previous[0], node.bounds.width),
                    max(previous[1], node.bounds.height),
                )

            if phase == "report" and node.text:
                if "…" in node.text or node.text.rstrip().endswith("..."):
                    identity = resource_id or node.text[:80]
                    self.ellipsized_report_text.add(identity)
        self.observation_index += 1

    def failures(self, matrix: dict[str, Any]) -> list[str]:
        failures: list[str] = []
        report_required = set(matrix["report"]["requiredResourceIds"])
        manifest_required = set(matrix["manifest"]["requiredResourceIds"])
        missing_report = sorted(report_required - self.seen_by_phase["report"])
        missing_manifest = sorted(manifest_required - self.seen_by_phase["manifest"])
        if missing_report:
            failures.append("Unreachable report resources: " + ", ".join(missing_report))
        if missing_manifest:
            failures.append("Unreachable manifest resources: " + ", ".join(missing_manifest))

        required_headings = matrix["report"]["requiredHeadingOrder"]
        heading_positions = [self.heading_first_seen.get(heading) for heading in required_headings]
        if any(position is None for position in heading_positions):
            missing = [
                heading for heading, position in zip(required_headings, heading_positions) if position is None
            ]
            failures.append("Missing headings in accessibility traversal: " + ", ".join(missing))
        elif heading_positions != sorted(heading_positions):
            failures.append("Report headings were not observed in linear document order")

        if self.unlabeled_controls:
            failures.append("Unlabeled actionable controls: " + ", ".join(sorted(self.unlabeled_controls)))

        minimum_px = round(self.minimum_touch_target_dp * self.density_dpi / 160)
        undersized = [
            f"{identity}={width}x{height}px"
            for identity, (width, height) in sorted(self.control_sizes.items())
            if width < minimum_px or height < minimum_px
        ]
        if undersized:
            failures.append(
                f"Touch targets below {self.minimum_touch_target_dp}dp ({minimum_px}px): "
                + ", ".join(undersized)
            )
        if self.ellipsized_report_text:
            failures.append(
                "Visible report text contained an ellipsis: "
                + ", ".join(sorted(self.ellipsized_report_text))
            )
        return failures


class DeviceState:
    def __init__(self, adb: Adb, package_name: str) -> None:
        self.adb = adb
        self.package_name = package_name

    def capture(self) -> dict[str, Any]:
        size_output = self.adb.shell("wm", "size")
        density_output = self.adb.shell("wm", "density")
        actual_rotation = self.read_actual_rotation()
        night_output = self.adb.shell("cmd", "uimode", "night")
        locales_output = self.adb.shell(
            "cmd", "locale", "get-app-locales", self.package_name, "--user", "0"
        )
        size_override = OVERRIDE_SIZE_PATTERN.search(size_output)
        size_physical = PHYSICAL_SIZE_PATTERN.search(size_output)
        density_override = OVERRIDE_DENSITY_PATTERN.search(density_output)
        density_physical = PHYSICAL_DENSITY_PATTERN.search(density_output)
        night = NIGHT_MODE_PATTERN.search(night_output)
        locales = APP_LOCALES_PATTERN.search(locales_output)
        return {
            "physicalSize": size_physical.group(1) if size_physical else None,
            "overrideSize": size_override.group(1) if size_override else None,
            "physicalDensity": int(density_physical.group(1)) if density_physical else None,
            "overrideDensity": int(density_override.group(1)) if density_override else None,
            "effectiveSize": (
                size_override.group(1)
                if size_override
                else size_physical.group(1) if size_physical else None
            ),
            "effectiveDensity": (
                int(density_override.group(1))
                if density_override
                else int(density_physical.group(1)) if density_physical else None
            ),
            "fontScale": self.adb.shell("settings", "get", "system", "font_scale"),
            "accelerometerRotation": self.adb.shell(
                "settings", "get", "system", "accelerometer_rotation"
            ),
            "userRotation": self.adb.shell("settings", "get", "system", "user_rotation"),
            "windowRotationMode": self.adb.shell("wm", "user-rotation"),
            "fixedToUserRotation": self.adb.shell("wm", "fixed-to-user-rotation"),
            "actualRotation": actual_rotation,
            "nightMode": night.group(1) if night else night_output,
            "appLocales": locales.group(1).strip() if locales else "",
        }

    def read_actual_rotation(self) -> int | None:
        output = self.adb.shell("dumpsys", "window", "displays", timeout=30)
        match = WINDOW_ROTATION_PATTERN.search(output)
        return int(match.group(1)) if match else None

    def configure(self, case: dict[str, Any]) -> None:
        screen = case["screen"]
        self.adb.shell("am", "force-stop", self.package_name)
        self.adb.shell("settings", "put", "system", "accelerometer_rotation", "0")
        self.adb.shell("wm", "size", screen["size"])
        self.adb.shell("wm", "density", str(screen["densityDpi"]))
        self.adb.shell("settings", "put", "system", "font_scale", str(case["font"]["scale"]))
        self.adb.shell("cmd", "uimode", "night", case["theme"]["nightMode"])
        self.set_app_locales(case["direction"]["locale"])
        expected_mode = f"lock {screen['rotation']}"
        for _ in range(5):
            # Locale/theme/display changes dispatch asynchronously, so reassert until the
            # window-manager policy, actual rotation, and SettingsProvider value all agree.
            self.adb.shell("wm", "fixed-to-user-rotation", "enabled")
            self.adb.shell("wm", "user-rotation", "lock", str(screen["rotation"]))
            self.adb.shell("settings", "put", "system", "accelerometer_rotation", "0")
            self.adb.shell("settings", "put", "system", "user_rotation", str(screen["rotation"]))
            time.sleep(1.0)
            if (
                self.adb.shell("wm", "user-rotation") == expected_mode
                and self.adb.shell("wm", "fixed-to-user-rotation") == "enabled"
                and self.adb.shell("settings", "get", "system", "user_rotation")
                == str(screen["rotation"])
                and self.read_actual_rotation() == int(screen["rotation"])
            ):
                break
        else:
            raise MatrixError(
                f"Display rotation did not settle at {screen['rotation']} after five attempts"
            )
        self.adb.shell("am", "force-stop", self.package_name)

    def restore(self, state: dict[str, Any]) -> list[str]:
        failures: list[str] = []
        operations: list[tuple[str, tuple[str, ...]]] = [
            ("force stop", ("am", "force-stop", self.package_name)),
            ("app locales", ()),
            ("night mode", ("cmd", "uimode", "night", str(state["nightMode"]))),
            ("display size", ("wm", "size", str(state["overrideSize"] or "reset"))),
            (
                "display density",
                ("wm", "density", str(state["overrideDensity"] or "reset")),
            ),
            ("rotation policy", ()),
        ]
        for label, arguments in operations:
            try:
                if label == "app locales":
                    self.set_app_locales(str(state["appLocales"]))
                elif label == "rotation policy":
                    self.restore_rotation(state)
                else:
                    self.adb.shell(*arguments)
            except Exception as error:  # Cleanup must attempt every remaining operation.
                failures.append(f"Unable to restore {label}: {error}")
        # Display and rotation changes settle asynchronously and can rewrite the default font row.
        # Restore and verify font scale only after those configuration changes have completed.
        time.sleep(2.0)
        expected_font_scale = str(state["fontScale"])
        try:
            for _ in range(3):
                self.restore_setting("font_scale", expected_font_scale)
                time.sleep(1.0)
                actual = self.adb.shell("settings", "get", "system", "font_scale")
                if actual == expected_font_scale:
                    break
            else:
                failures.append(
                    f"Unable to restore font scale: expected {expected_font_scale!r}, got {actual!r}"
                )
        except Exception as error:
            failures.append(f"Unable to restore font scale: {error}")
        return failures

    def restore_setting(self, name: str, value: str) -> None:
        if value in ("", "null", "None"):
            self.adb.shell("settings", "delete", "system", name)
        else:
            self.adb.shell("settings", "put", "system", name, value)

    def restore_rotation(self, state: dict[str, Any]) -> None:
        user_rotation = str(state["userRotation"])
        rotation_mode = str(state.get("windowRotationMode", "free"))
        if rotation_mode == "free":
            self.adb.shell("wm", "user-rotation", "free")
        else:
            match = re.fullmatch(r"lock\s+([0-3])", rotation_mode)
            locked_rotation = match.group(1) if match else user_rotation
            self.adb.shell("wm", "user-rotation", "lock", locked_rotation)
        self.restore_setting("accelerometer_rotation", str(state["accelerometerRotation"]))
        self.restore_setting("user_rotation", user_rotation)
        self.adb.shell(
            "wm",
            "fixed-to-user-rotation",
            str(state.get("fixedToUserRotation", "default")),
        )

    def set_app_locales(self, locales: str) -> None:
        self.adb.shell(
            "cmd",
            "locale",
            "set-app-locales",
            self.package_name,
            "--user",
            "0",
            "--locales",
            locales,
        )


def parse_png_size(png: bytes) -> tuple[int, int]:
    if not png.startswith(PNG_SIGNATURE) or len(png) < 24:
        raise MatrixError("ADB screencap did not return a valid PNG")
    return struct.unpack(">II", png[16:24])


def expand_cases(matrix: dict[str, Any]) -> list[dict[str, Any]]:
    axes = matrix["axes"]
    cases: list[dict[str, Any]] = []
    for screen, font, theme, direction in itertools.product(
        axes["screens"], axes["fonts"], axes["themes"], axes["directions"]
    ):
        case_id = "__".join(item["id"] for item in (screen, font, theme, direction))
        cases.append(
            {
                "id": case_id,
                "screen": screen,
                "font": font,
                "theme": theme,
                "direction": direction,
            }
        )
    return cases


def smoke_case_ids(matrix: dict[str, Any]) -> set[str]:
    axes = matrix["axes"]
    combinations = [
        (axes["screens"][0], axes["fonts"][0], axes["themes"][0], axes["directions"][0]),
        (axes["screens"][0], axes["fonts"][-1], axes["themes"][-1], axes["directions"][-1]),
        (axes["screens"][1], axes["fonts"][-1], axes["themes"][0], axes["directions"][-1]),
        (axes["screens"][2], axes["fonts"][-1], axes["themes"][-1], axes["directions"][-1]),
    ]
    return {"__".join(item["id"] for item in combination) for combination in combinations}


class MatrixRunner:
    def __init__(
        self,
        matrix: dict[str, Any],
        adb: Adb,
        output: Path,
        apk_path: Path,
        skip_install: bool,
        fail_fast: bool,
    ) -> None:
        self.matrix = matrix
        self.adb = adb
        self.output = output
        self.apk_path = apk_path
        self.skip_install = skip_install
        self.fail_fast = fail_fast
        self.application_id = matrix["application"]["packageName"]
        self.component = self.application_id + "/" + matrix["application"]["externalViewerActivity"]
        self.driver = matrix["driver"]
        self.device_state = DeviceState(adb, self.application_id)
        timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        self.run_id = f"{timestamp}-{os.getpid()}"
        self.remote_fixture = f"/sdcard/Download/codex-ui-review-matrix-{self.run_id}.apks"
        self.remote_hierarchy = f"/sdcard/Download/.codex-ui-review-matrix-{self.run_id}.xml"
        self.fixture_display_name = f"ui-review-matrix-{self.run_id}.apks"
        self.media_ids: list[str] = []
        self.content_uri = ""
        self.results: dict[str, Any] = {
            "schemaVersion": 1,
            "matrixSchemaVersion": matrix["schemaVersion"],
            "runId": self.run_id,
            "startedAtUtc": datetime.now(timezone.utc).isoformat(),
            "outputDirectory": str(output),
            "cases": [],
            "cleanupFailures": [],
        }

    def run(self, cases: list[dict[str, Any]]) -> int:
        self.output.mkdir(parents=True, exist_ok=False)
        before: dict[str, Any] | None = None
        try:
            self.ensure_device()
            if not self.skip_install:
                self.install_apk()
            else:
                self.ensure_installed()
            before = self.device_state.capture()
            self.results["deviceBefore"] = before
            self.stage_fixture()
            log(f"Running {len(cases)} UI matrix case(s); artifacts: {self.output}")
            for index, case in enumerate(cases, start=1):
                started = time.monotonic()
                log(f"[{index:02d}/{len(cases):02d}] {case['id']} ...")
                result = self.run_case(case)
                result["durationSeconds"] = round(time.monotonic() - started, 2)
                self.results["cases"].append(result)
                status = "PASS" if result["status"] == "passed" else "FAIL"
                log(
                    f"[{index:02d}/{len(cases):02d}] {status} "
                    f"({result['durationSeconds']:.2f}s, {len(result['failures'])} failure(s))"
                )
                self.write_results()
                if result["status"] != "passed" and self.fail_fast:
                    break
        except KeyboardInterrupt:
            self.results["aborted"] = "Interrupted by user"
            log("Matrix interrupted; restoring device state ...")
        except Exception as error:
            self.results["fatalError"] = str(error)
            log(f"FATAL: {error}")
        finally:
            self.cleanup_fixture()
            if before is not None:
                restoration_failures = self.device_state.restore(before)
                self.results["cleanupFailures"].extend(restoration_failures)
                try:
                    after = self.device_state.capture()
                    self.results["deviceAfter"] = after
                    self.results["restorationDifferences"] = self.state_differences(before, after)
                except Exception as error:
                    self.results["cleanupFailures"].append(
                        f"Unable to verify restored device state: {error}"
                    )
            self.results["finishedAtUtc"] = datetime.now(timezone.utc).isoformat()
            self.write_results()
            self.write_index()

        failed_cases = sum(case["status"] != "passed" for case in self.results["cases"])
        fatal = bool(self.results.get("fatalError") or self.results.get("aborted"))
        cleanup_failed = bool(
            self.results.get("cleanupFailures") or self.results.get("restorationDifferences")
        )
        log(
            f"Completed: {len(self.results['cases']) - failed_cases} passed, "
            f"{failed_cases} failed; review {self.output / 'index.html'}"
        )
        return 1 if failed_cases or fatal or cleanup_failed else 0

    def ensure_device(self) -> None:
        state = self.adb.output("get-state")
        if state != "device":
            raise MatrixError(f"ADB target is not ready: {state}")
        sdk_text = self.adb.shell("getprop", "ro.build.version.sdk")
        try:
            sdk = int(sdk_text)
        except ValueError as error:
            raise MatrixError(f"Unable to read the target API level: {sdk_text!r}") from error
        if sdk < 33:
            raise MatrixError(
                "UI matrix automation requires Android API 33 or newer for reversible "
                "per-app locale switching"
            )
        self.adb.shell("input", "keyevent", "KEYCODE_WAKEUP", check=False)
        self.adb.shell("wm", "dismiss-keyguard", check=False)

    def install_apk(self) -> None:
        if not self.apk_path.is_file():
            raise MatrixError(
                f"Debug APK not found: {self.apk_path}. Run ./gradlew.bat :app:assembleDebug first."
            )
        output = self.adb.output("install", "-r", "-t", str(self.apk_path), timeout=240)
        if "Success" not in output:
            raise MatrixError(f"ADB did not confirm APK installation:\n{output}")

    def ensure_installed(self) -> None:
        output = self.adb.shell("pm", "path", self.application_id, check=False)
        if not output.startswith("package:"):
            raise MatrixError(f"{self.application_id} is not installed on the selected device")

    def stage_fixture(self) -> None:
        fixture = (ROOT / self.matrix["fixture"]["path"]).resolve()
        if not fixture.is_file():
            raise MatrixError(f"UI matrix fixture not found: {fixture}")
        self.adb.command("push", str(fixture), self.remote_fixture, timeout=60)
        self.adb.shell(
            "content",
            "insert",
            "--uri",
            "content://media/external/file",
            "--bind",
            f"_data:s:{self.remote_fixture.replace('/sdcard/', '/storage/emulated/0/')}",
            "--bind",
            f"mime_type:s:{self.matrix['fixture']['mimeType']}",
            "--bind",
            f"_display_name:s:{self.fixture_display_name}",
        )
        rows = self.adb.shell(
            "content",
            "query",
            "--uri",
            "content://media/external/file",
            "--projection",
            "_id:_display_name:mime_type",
            timeout=90,
        )
        self.media_ids = [
            match.group(1)
            for line in rows.splitlines()
            if self.fixture_display_name in line
            for match in [MEDIA_ID_PATTERN.search(line)]
            if match
        ]
        if not self.media_ids:
            raise MatrixError("Unable to resolve the staged fixture's MediaStore URI")
        self.content_uri = f"content://media/external/file/{self.media_ids[-1]}"

    def cleanup_fixture(self) -> None:
        for media_id in self.media_ids:
            try:
                self.adb.shell(
                    "content",
                    "delete",
                    "--uri",
                    f"content://media/external/file/{media_id}",
                )
            except Exception as error:
                self.results["cleanupFailures"].append(
                    f"Unable to delete MediaStore row {media_id}: {error}"
                )
        for remote_path in (self.remote_fixture, self.remote_hierarchy):
            if not remote_path.startswith("/sdcard/Download/") or "codex-ui-review-matrix-" not in remote_path:
                self.results["cleanupFailures"].append(
                    f"Refused to clean unexpected remote path: {remote_path}"
                )
                continue
            try:
                self.adb.shell("rm", "-f", remote_path)
            except Exception as error:
                self.results["cleanupFailures"].append(
                    f"Unable to delete temporary device file {remote_path}: {error}"
                )

    def run_case(self, case: dict[str, Any]) -> dict[str, Any]:
        case_directory = self.output / case["id"]
        case_directory.mkdir(parents=True, exist_ok=False)
        result: dict[str, Any] = {
            "id": case["id"],
            "screen": case["screen"],
            "font": case["font"],
            "theme": case["theme"],
            "direction": case["direction"],
            "status": "failed",
            "failures": [],
            "checkpoints": [],
        }
        audit = Audit(
            application_id=self.application_id,
            density_dpi=case["screen"]["densityDpi"],
            minimum_touch_target_dp=self.driver["minimumTouchTargetDp"],
        )
        checkpoint_index = 1
        try:
            self.device_state.configure(case)
            state = self.device_state.capture()
            result["configuredState"] = state
            result["failures"].extend(self.configuration_failures(case, state))

            hierarchy = self.open_report(case)
            audit.ingest(hierarchy, "report")
            result["failures"].extend(self.toolbar_direction_failures(case, hierarchy, "action_share_report"))

            for checkpoint in self.matrix["report"]["checkpoints"]:
                hierarchy = self.scroll_until(
                    hierarchy,
                    checkpoint,
                    audit,
                    phase="report",
                    direction="forward",
                )
                artifact = self.capture_checkpoint(
                    case_directory,
                    checkpoint_index,
                    checkpoint["id"],
                    hierarchy,
                    case,
                )
                result["checkpoints"].append(artifact)
                checkpoint_index += 1

                if checkpoint["id"] == "simulation-actions":
                    apply_checkpoint = {
                        "id": "apply-simulation",
                        "targetResourceId": "apply_device_simulation",
                        "minimumVisibleHeightDp": 48,
                        "scrollFraction": 0.18,
                    }
                    hierarchy = self.scroll_until(
                        hierarchy,
                        apply_checkpoint,
                        audit,
                        phase="report",
                        direction="backward",
                    )
                    target = hierarchy.visible_checkpoint(
                        apply_checkpoint,
                        case["screen"]["densityDpi"],
                    )
                    if target is None:
                        raise MatrixError("Device simulation action was visible but not enabled")
                    self.tap(target)
                    time.sleep(0.8)
                    result_checkpoint = self.matrix["report"]["simulationResultCheckpoint"]
                    hierarchy = self.scroll_until(
                        self.dump_ui(case),
                        result_checkpoint,
                        audit,
                        phase="report",
                        direction="forward",
                    )
                    result_node = hierarchy.visible_resource(
                        result_checkpoint["targetResourceId"], enabled=True
                    )
                    if result_node is None or not result_node.text:
                        raise MatrixError("Device simulation result did not become readable")
                    artifact = self.capture_checkpoint(
                        case_directory,
                        checkpoint_index,
                        result_checkpoint["id"],
                        hierarchy,
                        case,
                    )
                    result["checkpoints"].append(artifact)
                    checkpoint_index += 1

            hierarchy = self.open_report(case)
            audit.ingest(hierarchy, "report")
            report_top = self.matrix["report"]["checkpoints"][0]
            hierarchy = self.scroll_until(
                hierarchy,
                report_top,
                audit,
                phase="report",
                direction="forward",
            )
            view_manifest = hierarchy.visible_checkpoint(
                report_top,
                case["screen"]["densityDpi"],
            )
            if view_manifest is None:
                raise MatrixError("Manifest action was not available after reopening the report")
            self.tap(view_manifest)
            hierarchy = self.wait_for_resource(case, "action_find_manifest", phase="manifest")
            audit.ingest(hierarchy, "manifest")
            result["failures"].extend(
                self.toolbar_direction_failures(case, hierarchy, "action_find_manifest")
            )
            find_action = hierarchy.visible_resource("action_find_manifest", enabled=True)
            if find_action is None:
                raise MatrixError("Manifest search action was not enabled")
            self.tap(find_action)
            hierarchy = self.wait_for_resource(case, "manifest_search_input", phase="manifest")
            audit.ingest(hierarchy, "manifest")
            search_input = hierarchy.visible_resource("manifest_search_input", enabled=True)
            if search_input is None:
                raise MatrixError("Manifest search input did not become available")
            search_query = self.matrix["manifest"]["query"]
            if not search_query.isascii() or not search_query.isalpha():
                raise MatrixError("Manifest matrix query must contain ASCII letters only")
            # Keep all key events in one input process so compact-window remeasurement cannot
            # invalidate an IME connection between separate adb shell commands.
            self.adb.shell(
                "input",
                "keyevent",
                *(f"KEYCODE_{character.upper()}" for character in search_query),
            )
            time.sleep(0.3)
            keyboard_probe = self.dump_ui(case)
            audit.ingest(keyboard_probe, "manifest")
            populated_input = keyboard_probe.visible_resource(
                "manifest_search_input",
                enabled=True,
            )
            if populated_input is None or populated_input.text != search_query:
                actual_query = populated_input.text if populated_input else "<missing>"
                raise MatrixError(
                    f"Manifest search input contains {actual_query!r}, expected {search_query!r}"
                )
            manifest_scroll = keyboard_probe.visible_resource("manifest_scroll", enabled=True)
            if (
                manifest_scroll is None
                or manifest_scroll.bounds.bottom < keyboard_probe.display_size[1] * 0.8
            ):
                self.adb.shell("input", "keyevent", "KEYCODE_BACK")
            hierarchy = self.wait_for_manifest_search(case, audit)
            checkpoint = self.matrix["manifest"]["checkpoint"]
            artifact = self.capture_checkpoint(
                case_directory,
                checkpoint_index,
                checkpoint["id"],
                hierarchy,
                case,
            )
            result["checkpoints"].append(artifact)

            result["failures"].extend(audit.failures(self.matrix))
        except Exception as error:
            result["failures"].append(str(error))
        finally:
            self.adb.shell("am", "force-stop", self.application_id, check=False)
            result["seenResources"] = {
                phase: sorted(values) for phase, values in audit.seen_by_phase.items()
            }
            result["accessibilityOrder"] = audit.semantic_order
            accessibility_path = case_directory / "accessibility-order.txt"
            accessibility_path.write_text(
                self.render_accessibility_order(audit.semantic_order),
                encoding="utf-8",
                newline="\n",
            )
            result["accessibilityOrderFile"] = relative_to_output(
                accessibility_path, self.output
            )
            result["failures"] = list(dict.fromkeys(result["failures"]))
            result["status"] = "passed" if not result["failures"] else "failed"
        return result

    def open_report(self, case: dict[str, Any]) -> Hierarchy:
        last_error: Exception | None = None
        for _ in range(2):
            try:
                self.adb.shell("am", "force-stop", self.application_id)
                self.adb.shell(
                    "am",
                    "start",
                    "-W",
                    "-a",
                    "android.intent.action.VIEW",
                    "-d",
                    self.content_uri,
                    "-t",
                    self.matrix["fixture"]["mimeType"],
                    "--grant-read-uri-permission",
                    "-n",
                    self.component,
                    timeout=70,
                )
                return self.wait_for_resource(case, "action_share_report", phase="report")
            except Exception as error:
                last_error = error
                time.sleep(0.8)
        raise MatrixError(f"Unable to open a ready inspection report: {last_error}")

    def wait_for_resource(self, case: dict[str, Any], resource_id: str, phase: str) -> Hierarchy:
        deadline = time.monotonic() + self.driver["readyTimeoutSeconds"]
        last_detail = "no hierarchy captured"
        while time.monotonic() < deadline:
            try:
                hierarchy = self.dump_ui(case)
                node = hierarchy.visible_resource(resource_id, enabled=True)
                error = hierarchy.visible_resource("error", enabled=True)
                if error is not None and error.text:
                    raise MatrixError(f"Application reported an inspection error: {error.text}")
                if node is not None:
                    return hierarchy
                visible_resources = [
                    f"{item.resource_id}@{item.bounds.left},{item.bounds.top},"
                    f"{item.bounds.right},{item.bounds.bottom}"
                    for item in hierarchy.app_nodes(visible_only=True)
                    if item.resource_id
                ]
                last_detail = "visible resources=" + (
                    ", ".join(visible_resources) if visible_resources else "<none>"
                )
            except MatrixError as error:
                last_detail = str(error)
            time.sleep(0.35)
        raise MatrixError(f"Timed out waiting for {phase} resource {resource_id}: {last_detail}")

    def wait_for_manifest_search(self, case: dict[str, Any], audit: Audit) -> Hierarchy:
        deadline = time.monotonic() + self.driver["interactionTimeoutSeconds"]
        last_detail = "search controls did not settle"
        while time.monotonic() < deadline:
            hierarchy = self.dump_ui(case)
            audit.ingest(hierarchy, "manifest")
            status = hierarchy.visible_resource("manifest_search_status", enabled=True)
            next_match = hierarchy.visible_resource("next_manifest_match", enabled=True)
            manifest_scroll = hierarchy.visible_resource("manifest_scroll", enabled=True)
            keyboard_visible = (
                manifest_scroll is None
                or manifest_scroll.bounds.bottom < hierarchy.display_size[1] * 0.8
            )
            if status is not None and status.text and next_match is not None and not keyboard_visible:
                return hierarchy
            last_detail = (
                f"status={status.text if status else '<missing>'}, "
                f"nextEnabled={next_match is not None}, keyboardVisible={keyboard_visible}"
            )
            time.sleep(0.35)
        raise MatrixError(f"Manifest search did not yield a keyboard-free navigable match: {last_detail}")

    def scroll_until(
        self,
        hierarchy: Hierarchy,
        checkpoint: dict[str, Any],
        audit: Audit,
        *,
        phase: str,
        direction: str,
    ) -> Hierarchy:
        for step in range(self.driver["maxScrollSteps"] + 1):
            audit.ingest(hierarchy, phase)
            target = hierarchy.visible_checkpoint(checkpoint, self.density_for_hierarchy(hierarchy))
            if target is not None:
                return hierarchy
            if step == self.driver["maxScrollSteps"]:
                break
            width, height = hierarchy.display_size
            if width <= 0 or height <= 0:
                raise MatrixError("UI hierarchy reported an invalid display size")
            density_dpi = self.density_for_hierarchy(hierarchy)
            # Report text is selectable and can consume a centered drag as text selection. Start
            # inside the layout's 20dp padding band so the ScrollView owns the vertical gesture.
            x = min(
                hierarchy.display_bounds.right - 1,
                hierarchy.display_bounds.left + round(10 * density_dpi / 160),
            )
            partial_target = hierarchy.checkpoint_match(
                checkpoint,
                density_dpi,
                require_minimum_height=False,
            )
            if partial_target is not None:
                _, target_y = partial_target.bounds.center
                if target_y >= height // 2:
                    start_y, end_y = round(height * 0.68), round(height * 0.53)
                else:
                    start_y, end_y = round(height * 0.32), round(height * 0.47)
                duration = self.driver["swipeDurationMilliseconds"]
            elif checkpoint.get("scrollFraction"):
                fraction = float(checkpoint["scrollFraction"])
                if direction == "forward":
                    start_y, end_y = round(height * 0.68), round(height * (0.68 - fraction))
                else:
                    start_y, end_y = round(height * 0.32), round(height * (0.32 + fraction))
                duration = self.driver["swipeDurationMilliseconds"]
            elif direction == "forward":
                start_y, end_y = round(height * 0.82), round(height * 0.28)
                duration = self.driver["swipeDurationMilliseconds"]
            else:
                start_y, end_y = round(height * 0.28), round(height * 0.82)
                duration = self.driver["swipeDurationMilliseconds"]
            self.adb.shell(
                "input",
                "swipe",
                str(x),
                str(start_y),
                str(x),
                str(end_y),
                str(duration),
            )
            time.sleep(self.driver["settleMilliseconds"] / 1000)
            hierarchy = self.dump_ui_from_expected_size(case_size=self.expected_display_size_from_hierarchy(hierarchy))
        raise MatrixError(
            f"Checkpoint {checkpoint['id']} was not reachable after "
            f"{self.driver['maxScrollSteps']} scrolls"
        )

    def density_for_hierarchy(self, hierarchy: Hierarchy) -> int:
        for screen in self.matrix["axes"]["screens"]:
            width, height = (int(value) for value in screen["size"].split("x", 1))
            expected = (height, width) if screen["rotation"] % 2 else (width, height)
            if hierarchy.display_size == expected:
                return int(screen["densityDpi"])
        raise MatrixError(f"No density profile matches display size {hierarchy.display_size}")

    def dump_ui(self, case: dict[str, Any]) -> Hierarchy:
        return self.dump_ui_from_expected_size(self.expected_display_size(case))

    def dump_ui_from_expected_size(self, case_size: tuple[int, int]) -> Hierarchy:
        last_error: Exception | None = None
        for _ in range(5):
            try:
                self.adb.shell("rm", "-f", self.remote_hierarchy, check=False)
                self.adb.shell(
                    "uiautomator",
                    "dump",
                    "--compressed",
                    self.remote_hierarchy,
                    timeout=25,
                )
                xml_bytes = self.adb.exec_out("cat", self.remote_hierarchy, timeout=20)
                hierarchy = Hierarchy(xml_bytes.decode("utf-8"), self.application_id)
                packages = hierarchy.packages()
                if self.application_id not in packages:
                    visible_packages = ", ".join(sorted(packages)) or "<none>"
                    raise MatrixError(
                        "Foreground hierarchy does not contain APK Inspector; "
                        f"visible packages: {visible_packages}"
                    )
                actual_width, actual_height = hierarchy.display_size
                expected_width, expected_height = case_size
                same_orientation = (actual_width >= actual_height) == (
                    expected_width >= expected_height
                )
                sufficient_coverage = (
                    actual_width >= expected_width * 0.8
                    and actual_height >= expected_height * 0.8
                    and actual_width <= expected_width
                    and actual_height <= expected_height
                )
                if not same_orientation or not sufficient_coverage:
                    raise MatrixError(
                        f"Display hierarchy is {actual_width}x{actual_height}, "
                        f"expected {expected_width}x{expected_height}"
                    )
                hierarchy.use_physical_display_size(case_size)
                return hierarchy
            except Exception as error:
                last_error = error
                time.sleep(0.3)
        raise MatrixError(f"Unable to capture a stable UI hierarchy: {last_error}")

    def capture_checkpoint(
        self,
        case_directory: Path,
        index: int,
        checkpoint_id: str,
        hierarchy: Hierarchy,
        case: dict[str, Any],
    ) -> dict[str, Any]:
        stem = f"{index:02d}-{slug(checkpoint_id)}"
        xml_path = case_directory / f"{stem}.xml"
        png_path = case_directory / f"{stem}.png"
        xml_path.write_text(hierarchy.xml, encoding="utf-8", newline="\n")
        png = self.adb.exec_out("screencap", "-p", timeout=30)
        actual_size = parse_png_size(png)
        expected_size = self.expected_display_size(case)
        if actual_size != expected_size:
            raise MatrixError(
                f"Screenshot is {actual_size[0]}x{actual_size[1]}, "
                f"expected {expected_size[0]}x{expected_size[1]}"
            )
        png_path.write_bytes(png)
        return {
            "id": checkpoint_id,
            "png": relative_to_output(png_path, self.output),
            "xml": relative_to_output(xml_path, self.output),
            "size": f"{actual_size[0]}x{actual_size[1]}",
        }

    def tap(self, node: UiNode) -> None:
        x, y = node.bounds.center
        self.adb.shell("input", "tap", str(x), str(y))
        time.sleep(self.driver["settleMilliseconds"] / 1000)

    def expected_display_size(self, case: dict[str, Any]) -> tuple[int, int]:
        width, height = (int(value) for value in case["screen"]["size"].split("x", 1))
        return (height, width) if case["screen"]["rotation"] % 2 else (width, height)

    @staticmethod
    def expected_display_size_from_hierarchy(hierarchy: Hierarchy) -> tuple[int, int]:
        return hierarchy.display_size

    def configuration_failures(
        self, case: dict[str, Any], state: dict[str, Any]
    ) -> list[str]:
        failures: list[str] = []
        screen = case["screen"]
        if state["effectiveSize"] != screen["size"]:
            failures.append(
                f"Effective display size is {state['effectiveSize']}, expected {screen['size']}"
            )
        if state["effectiveDensity"] != screen["densityDpi"]:
            failures.append(
                f"Effective display density is {state['effectiveDensity']}, "
                f"expected {screen['densityDpi']}"
            )
        try:
            if abs(float(state["fontScale"]) - float(case["font"]["scale"])) > 0.001:
                failures.append(
                    f"Font scale is {state['fontScale']}, expected {case['font']['scale']}"
                )
        except (TypeError, ValueError):
            failures.append(f"Unable to read configured font scale: {state['fontScale']}")
        if state["nightMode"] != case["theme"]["nightMode"]:
            failures.append(
                f"Night mode is {state['nightMode']}, expected {case['theme']['nightMode']}"
            )
        if state["appLocales"] != case["direction"]["locale"]:
            failures.append(
                f"App locale is {state['appLocales']!r}, expected {case['direction']['locale']!r}"
            )
        if state["accelerometerRotation"] != "0":
            failures.append("Rotation policy was not frozen for deterministic capture")
        if state["userRotation"] != str(screen["rotation"]):
            failures.append(
                f"User rotation is {state['userRotation']}, expected {screen['rotation']}"
            )
        expected_rotation_mode = f"lock {screen['rotation']}"
        if state["windowRotationMode"] != expected_rotation_mode:
            failures.append(
                f"Window rotation mode is {state['windowRotationMode']!r}, "
                f"expected {expected_rotation_mode!r}"
            )
        if state["fixedToUserRotation"] != "enabled":
            failures.append(
                "Window manager did not force activities to follow the frozen user rotation"
            )
        if state["actualRotation"] != int(screen["rotation"]):
            failures.append(
                f"Actual display rotation is {state['actualRotation']}, "
                f"expected {screen['rotation']}"
            )
        calculated_width_dp = round(self.expected_display_size(case)[0] / screen["densityDpi"] * 160)
        if calculated_width_dp != screen["expectedWidthDp"]:
            failures.append(
                f"Configured width is {calculated_width_dp}dp, expected {screen['expectedWidthDp']}dp"
            )
        return failures

    @staticmethod
    def toolbar_direction_failures(
        case: dict[str, Any], hierarchy: Hierarchy, action_resource: str
    ) -> list[str]:
        action = hierarchy.visible_resource(action_resource, enabled=True)
        if action is None:
            return [f"Toolbar action {action_resource} was not visible"]
        center_x, _ = action.bounds.center
        midpoint = hierarchy.display_size[0] / 2
        direction = case["direction"]["layoutDirection"]
        if direction == "ltr" and center_x <= midpoint:
            return [f"LTR toolbar action {action_resource} was not on the end/right side"]
        if direction == "rtl" and center_x >= midpoint:
            return [f"RTL toolbar action {action_resource} was not on the end/left side"]
        return []

    @staticmethod
    def render_accessibility_order(order: dict[str, list[str]]) -> str:
        lines = ["Observed semantic traversal (UI Automator)", ""]
        for phase in ("report", "manifest"):
            lines.extend([phase.upper(), "-" * len(phase)])
            lines.extend(f"{index}. {item}" for index, item in enumerate(order[phase], start=1))
            lines.append("")
        return "\n".join(lines).rstrip() + "\n"

    @staticmethod
    def state_differences(before: dict[str, Any], after: dict[str, Any]) -> dict[str, Any]:
        keys = (
            "overrideSize",
            "overrideDensity",
            "fontScale",
            "accelerometerRotation",
            "userRotation",
            "windowRotationMode",
            "fixedToUserRotation",
            "actualRotation",
            "nightMode",
            "appLocales",
        )
        return {
            key: {"before": before.get(key), "after": after.get(key)}
            for key in keys
            if before.get(key) != after.get(key)
        }

    def write_results(self) -> None:
        if not self.output.exists():
            return
        path = self.output / "results.json"
        path.write_text(
            json.dumps(self.results, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
            newline="\n",
        )

    def write_index(self) -> None:
        if not self.output.exists():
            return
        cases_html: list[str] = []
        for case in self.results["cases"]:
            failures = "".join(f"<li>{html.escape(item)}</li>" for item in case["failures"])
            images = "".join(
                "<figure>"
                f"<a href='{html.escape(checkpoint['png'])}'>"
                f"<img loading='lazy' src='{html.escape(checkpoint['png'])}' "
                f"alt='{html.escape(case['id'] + ' ' + checkpoint['id'])}'></a>"
                f"<figcaption>{html.escape(checkpoint['id'])} · "
                f"<a href='{html.escape(checkpoint['xml'])}'>XML</a></figcaption>"
                "</figure>"
                for checkpoint in case["checkpoints"]
            )
            cases_html.append(
                f"<section class='case {case['status']}'>"
                f"<h2>{html.escape(case['id'])} <span>{case['status'].upper()}</span></h2>"
                f"<p>{case['screen']['orientation']}, {case['screen']['expectedWidthDp']} dp · "
                f"font {case['font']['scale']}x · {case['theme']['id']} · "
                f"{case['direction']['layoutDirection'].upper()}</p>"
                + (f"<ul class='failures'>{failures}</ul>" if failures else "")
                + f"<p><a href='{html.escape(case['accessibilityOrderFile'])}'>Accessibility order</a></p>"
                + f"<div class='shots'>{images}</div></section>"
            )
        fatal = self.results.get("fatalError") or self.results.get("aborted") or ""
        cleanup = [
            *self.results.get("cleanupFailures", []),
            *(
                ["Device restoration differed from the initial state: " + json.dumps(
                    self.results.get("restorationDifferences"), ensure_ascii=False
                )]
                if self.results.get("restorationDifferences")
                else []
            ),
        ]
        alerts = "".join(f"<li>{html.escape(item)}</li>" for item in ([fatal] if fatal else []) + cleanup)
        document = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>APK Inspector UI review matrix</title>
<style>
body {{ font: 15px/1.5 system-ui, sans-serif; margin: 0 auto; max-width: 1800px; padding: 24px; background: #f4f5f7; color: #1d1b20; }}
h1 {{ margin-bottom: 4px; }}
.case {{ background: white; border-left: 6px solid #2e7d32; border-radius: 8px; margin: 24px 0; padding: 16px; box-shadow: 0 1px 5px #0002; }}
.case.failed {{ border-left-color: #b3261e; }}
.case h2 {{ overflow-wrap: anywhere; }}
.case h2 span {{ font-size: .65em; color: #2e7d32; }}
.case.failed h2 span, .failures {{ color: #b3261e; }}
.shots {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 14px; align-items: start; }}
figure {{ margin: 0; }}
img {{ display: block; width: 100%; max-height: 620px; object-fit: contain; object-position: top; background: #222; border: 1px solid #aaa; }}
figcaption {{ margin-top: 5px; }}
.notice {{ background: #fff3cd; border-radius: 8px; padding: 12px 16px; }}
code {{ overflow-wrap: anywhere; }}
</style>
</head>
<body>
<h1>APK Inspector UI review matrix</h1>
<p>Run <code>{html.escape(self.run_id)}</code>. Automated checks are necessary but do not replace visual and TalkBack review.</p>
<div class="notice"><strong>Review every screenshot:</strong> no clipping or ellipsis; controls remain reachable; light/dark contrast is readable; LTR/RTL actions mirror; manifest XML remains LTR. Then compare TalkBack focus order with each accessibility-order file.</div>
{f'<ul class="failures">{alerts}</ul>' if alerts else ''}
{''.join(cases_html)}
</body>
</html>
"""
        (self.output / "index.html").write_text(document, encoding="utf-8", newline="\n")


def validate_matrix(matrix: dict[str, Any]) -> None:
    if matrix.get("schemaVersion") != 1:
        raise MatrixError(f"Unsupported matrix schema: {matrix.get('schemaVersion')}")
    axes = matrix.get("axes", {})
    expected_counts = {"screens": 3, "fonts": 3, "themes": 2, "directions": 2}
    for axis, expected in expected_counts.items():
        values = axes.get(axis, [])
        if len(values) != expected:
            raise MatrixError(f"Axis {axis} must contain {expected} values, found {len(values)}")
        identifiers = [value.get("id") for value in values]
        if None in identifiers or len(identifiers) != len(set(identifiers)):
            raise MatrixError(f"Axis {axis} contains a missing or duplicate ID")
    cases = expand_cases(matrix)
    if len(cases) != 36 or len({case["id"] for case in cases}) != 36:
        raise MatrixError("UI review matrix must expand to 36 unique cases")
    if not matrix.get("report", {}).get("checkpoints"):
        raise MatrixError("UI review matrix has no report checkpoints")


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Capture the 36-case APK Inspector screen/font/theme/direction review matrix."
    )
    parser.add_argument("--matrix", type=Path, default=DEFAULT_MATRIX)
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"))
    parser.add_argument("--adb", default="adb", help="ADB executable (default: adb)")
    parser.add_argument("--apk", type=Path, help="Debug APK to install")
    parser.add_argument("--output", type=Path, help="Fresh artifact directory")
    parser.add_argument("--skip-install", action="store_true")
    parser.add_argument("--case", action="append", dest="case_ids", help="Exact case ID; repeatable")
    parser.add_argument("--smoke", action="store_true", help="Run four boundary cases")
    parser.add_argument("--list", action="store_true", help="List case IDs without using ADB")
    parser.add_argument("--fail-fast", action="store_true")
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    matrix_path = arguments.matrix.resolve()
    try:
        matrix = json.loads(matrix_path.read_text(encoding="utf-8"))
        validate_matrix(matrix)
        cases = expand_cases(matrix)
        if arguments.list:
            for case in cases:
                print(case["id"])
            print(f"{len(cases)} cases")
            return 0

        if not arguments.serial:
            raise MatrixError("Select exactly one target with --serial or ANDROID_SERIAL")
        if arguments.case_ids and arguments.smoke:
            raise MatrixError("--case and --smoke cannot be used together")
        if arguments.case_ids:
            requested = set(arguments.case_ids)
            known = {case["id"] for case in cases}
            unknown = sorted(requested - known)
            if unknown:
                raise MatrixError("Unknown case ID(s): " + ", ".join(unknown))
            cases = [case for case in cases if case["id"] in requested]
        elif arguments.smoke:
            selected = smoke_case_ids(matrix)
            cases = [case for case in cases if case["id"] in selected]

        apk_path = (
            arguments.apk.resolve()
            if arguments.apk
            else (ROOT / matrix["driver"]["defaultApkPath"]).resolve()
        )
        if arguments.output:
            output = arguments.output.resolve()
        else:
            timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
            output = (ROOT / matrix["driver"]["defaultOutputDirectory"] / timestamp).resolve()
        if output.exists():
            raise MatrixError(f"Output directory already exists; choose a fresh path: {output}")
        runner = MatrixRunner(
            matrix=matrix,
            adb=Adb(arguments.adb, arguments.serial),
            output=output,
            apk_path=apk_path,
            skip_install=arguments.skip_install,
            fail_fast=arguments.fail_fast,
        )
        return runner.run(cases)
    except (OSError, json.JSONDecodeError, KeyError, TypeError, MatrixError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
