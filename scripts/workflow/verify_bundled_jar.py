"""Check the installable worker/native bundle using Python 3.11+ standard library."""
import hashlib
import io
import json
from pathlib import Path
import sys
import tomllib
from zipfile import ZipFile


def require(condition, message):
    if not condition:
        raise ValueError(message)


def check_archive(archive, mod_id, version, required):
    names = archive.namelist()
    require(len(names) == len(set(names)), f"{mod_id}: duplicate ZIP entries")
    require(archive.testzip() is None, f"{mod_id}: corrupt ZIP entry")
    require(set(required) <= set(names), f"{mod_id}: missing production resources/classes")
    forbidden = ("com/github/lunatrius/", "fi/dy/masa/litematica/", "org/junit/",
                 "net/minecraft/", "net/neoforged/", "io/netty/")
    require(not any(n.startswith(forbidden) or "/gametest/" in n.lower()
                    or n.endswith("Test.class") or n in ("fabric.mod.json", "quilt.mod.json")
                    for n in names), f"{mod_id}: unexpected test/runtime/placeholder content")
    metadata = tomllib.loads(archive.read("META-INF/neoforge.mods.toml").decode("utf-8"))
    require([(m["modId"], m["version"]) for m in metadata["mods"]] == [(mod_id, version)],
            f"{mod_id}: unexpected identity/version")
    for dependency in metadata["dependencies"][mod_id]:
        require(dependency["side"] == "BOTH", f"{mod_id}: dependency excludes a side")
    for name in names:
        if name.endswith(".json"):
            json.loads(archive.read(name).decode("utf-8"))
    return metadata


def verify(bundle, native, version):
    with ZipFile(bundle) as outer:
        worker = check_archive(outer, "automatone_worker", version, [
            "automatone/worker/WorkerMod.class", "automatone/worker/client/WorkerScreen.class",
            "automatone/worker/client/WorkerRenderer.class", "automatone-worker.mixins.json",
            "automatone-worker-client.mixins.json", "assets/automatone_worker/lang/en_us.json",
            "assets/automatone_worker/textures/entity/worker.png",
        ])
        dependencies = worker["dependencies"]["automatone_worker"]
        require(any(d["modId"] == "automatone" and d["versionRange"] == f"[{version}]"
                    and d["type"] == "required" and d["ordering"] == "AFTER" for d in dependencies),
                "Worker must require exactly the embedded native version")
        require({m["config"] for m in worker["mixins"]} == {
            "automatone-worker.mixins.json", "automatone-worker-client.mixins.json"}, "Missing mixin registration")
        client_mixin = json.loads(outer.read("automatone-worker-client.mixins.json"))
        require(client_mixin.get("client") == ["WorkerToastMixin"] and not client_mixin.get("mixins"),
                "Client mixin must remain client-only")
        embedded = json.loads(outer.read("META-INF/jarjar/metadata.json"))["jars"]
        require(len(embedded) == 1, "Bundle must embed exactly one dependency")
        entry = embedded[0]
        require(entry["identifier"] == {"group": "io.github.ladysnake", "artifact": "automatone"}
                and entry["version"]["artifactVersion"] == version, "Wrong embedded dependency")
        require([n for n in outer.namelist() if n.endswith(".jar")] == [entry["path"]],
                "Unexpected embedded JAR")
        content = outer.read(entry["path"])
        require(content == native.read_bytes(), "Embedded native bytes differ from the build")
        with ZipFile(io.BytesIO(content)) as inner:
            check_archive(inner, "automatone", version, [
                "baritone/Automatone.class", "baritone/api/process/IMineProcess$TerminationReason.class",
                "baritone/utils/schematic/schematica/SchematicaHelper.class",
                "baritone/utils/schematic/litematica/LitematicaHelper.class", "assets/automatone/icon.png",
            ])
            require(not any(n.endswith(".jar") for n in inner.namelist()), "Native JAR bundles dependencies")
    print(json.dumps({"status": "PASS", "bundle": str(bundle), "version": version,
                      "sha256": hashlib.sha256(bundle.read_bytes()).hexdigest(),
                      "embedded_native_sha256": hashlib.sha256(content).hexdigest()}, indent=2))


if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit("Usage: verify_bundled_jar.py BUNDLE.jar NATIVE.jar VERSION")
    verify(Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3])
