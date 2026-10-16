#!/usr/bin/env python3
# Aerix Launcher - YouTube upload bot.
#
# Uploads the videos described by the manifests in youtube/queue/ to the channel, sets the title,
# description, tags, category, thumbnail, playlists and captions, and either publishes them right
# away or schedules them for the moment the manifest asks for. It then moves the manifest to
# youtube/published/ with the video id, so the same video can never be uploaded twice.
#
# Two modes, both safe to run any time:
#   check  - validate every manifest and print what it would do. No network, no credentials.
#   upload - do it. Needs YT_CLIENT_ID / YT_CLIENT_SECRET / YT_REFRESH_TOKEN in the environment.
#
# Nothing here talks to YouTube except in `upload`.
#
# This program is free software: you can redistribute it and/or modify it under the terms of the
# GNU General Public License as published by the Free Software Foundation, either version 3 of the
# License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
# even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
# General Public License for more details. See <https://www.gnu.org/licenses/gpl-3.0.txt>.

from __future__ import annotations

import argparse
import dataclasses
import datetime as dt
import json
import os
import pathlib
import re
import shutil
import sys
import time
import urllib.request

# Where manifests wait, and where they go once a video id exists.
QUEUE_DIR = pathlib.Path("youtube/queue")
PUBLISHED_DIR = pathlib.Path("youtube/published")
CONFIG_PATH = pathlib.Path("tools/youtube/config.json")

SLUG_PATTERN = re.compile(r"^[a-z0-9][a-z0-9-]{1,60}$")
VISIBILITIES = ("scheduled", "public", "unlisted", "private")

# YouTube's own limits. Checking them here means a typo is caught in a second instead of after a
# forty minute upload.
MAX_TITLE = 100
MAX_DESCRIPTION = 5000
MAX_TAGS_CHARS = 500
MAX_THUMBNAIL_BYTES = 2 * 1024 * 1024

# Quota, as YouTube counts it: 10,000 units a day by default, and an upload is 1,600 of them.
QUOTA_VIDEOS_INSERT = 1600
QUOTA_THUMBNAILS_SET = 50
QUOTA_PLAYLIST_ITEM = 50
QUOTA_CAPTIONS_INSERT = 400

SCOPES = [
    "https://www.googleapis.com/auth/youtube",
    "https://www.googleapis.com/auth/youtube.force-ssl",
    "https://www.googleapis.com/auth/youtube.upload",
]


class ManifestError(Exception):
    """A manifest is wrong: not a runtime failure, and always worth failing fast on."""


# ----------------------------------------------------------------------------------------------
# Manifests
# ----------------------------------------------------------------------------------------------


@dataclasses.dataclass
class Source:
    """A file in the repository, or a URL (with optional headers) to fetch it from."""

    file: str | None = None
    url: str | None = None
    headers: dict[str, str] = dataclasses.field(default_factory=dict)

    @property
    def label(self) -> str:
        return self.file or (self.url or "?")

    def describe(self, root: pathlib.Path) -> str:
        if self.file:
            path = root / self.file
            return f"{self.file} ({'present' if path.is_file() else 'MISSING'})"
        return self.url or "?"


@dataclasses.dataclass
class Manifest:
    path: pathlib.Path
    slug: str
    title: str
    description: str
    tags: list[str]
    category_id: str
    visibility: str
    publish_at: str | None
    made_for_kids: bool
    playlist_id: str | None
    video: Source
    thumbnail: Source | None
    captions: list[dict]
    raw: dict

    @property
    def quota(self) -> int:
        """What this manifest costs, in YouTube quota units."""
        cost = QUOTA_VIDEOS_INSERT
        if self.thumbnail:
            cost += QUOTA_THUMBNAILS_SET
        if self.playlist_id:
            cost += QUOTA_PLAYLIST_ITEM
        cost += QUOTA_CAPTIONS_INSERT * len(self.captions)
        return cost

    def status(self) -> tuple[str, str | None]:
        """(privacyStatus, publishAt) as the API wants them."""
        if self.visibility == "scheduled":
            # A scheduled video is uploaded private and YouTube makes it public at publishAt.
            return "private", self.publish_at
        return self.visibility, None


def _source(value, field: str) -> Source | None:
    if value is None:
        return None
    if not isinstance(value, dict):
        raise ManifestError(f"{field} must be an object with a file or a url")
    unknown = set(value) - {"file", "url", "headers"}
    if unknown:
        raise ManifestError(f"{field} has unknown keys: {', '.join(sorted(unknown))}")
    file = value.get("file")
    url = value.get("url")
    if bool(file) == bool(url):
        raise ManifestError(f"{field} needs exactly one of file or url")
    headers = value.get("headers") or {}
    if not isinstance(headers, dict):
        raise ManifestError(f"{field}.headers must be an object")
    return Source(file=file, url=url, headers={str(k): str(v) for k, v in headers.items()})


def _iso_time(value: str, field: str) -> dt.datetime:
    text = value.strip().replace("Z", "+00:00")
    try:
        parsed = dt.datetime.fromisoformat(text)
    except ValueError as error:
        raise ManifestError(f"{field} is not an ISO-8601 time: {value}") from error
    if parsed.tzinfo is None:
        raise ManifestError(f"{field} needs a timezone, e.g. 2026-11-02T17:00:00+05:45 or ...Z")
    return parsed


def load_manifest(path: pathlib.Path, config: dict) -> Manifest:
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ManifestError(f"{path} is not valid JSON: {error}") from error
    if not isinstance(raw, dict):
        raise ManifestError(f"{path} must contain a JSON object")

    slug = str(raw.get("slug") or "").strip()
    if not SLUG_PATTERN.match(slug):
        raise ManifestError(f"{path}: slug must be lowercase words joined by dashes, got {slug!r}")

    title = str(raw.get("title") or "").strip()
    if not title:
        raise ManifestError(f"{path}: title is required")
    if len(title) > MAX_TITLE:
        raise ManifestError(f"{path}: title is {len(title)} characters, YouTube allows {MAX_TITLE}")

    description = str(raw.get("description") or "")
    if len(description) > MAX_DESCRIPTION:
        raise ManifestError(
            f"{path}: description is {len(description)} characters, YouTube allows {MAX_DESCRIPTION}"
        )

    tags = raw.get("tags")
    if tags is None:
        tags = list(config.get("defaultTags") or [])
    if not isinstance(tags, list) or not all(isinstance(t, str) for t in tags):
        raise ManifestError(f"{path}: tags must be a list of strings")
    tag_chars = sum(len(t) + 1 for t in tags)
    if tag_chars > MAX_TAGS_CHARS:
        raise ManifestError(
            f"{path}: tags add up to {tag_chars} characters, YouTube allows {MAX_TAGS_CHARS}"
        )

    visibility = str(
        raw.get("visibility") or config.get("defaultVisibility") or "private"
    ).strip()
    if visibility not in VISIBILITIES:
        raise ManifestError(
            f"{path}: visibility must be one of {', '.join(VISIBILITIES)}, got {visibility!r}"
        )

    publish_at = raw.get("publishAt")
    if visibility == "scheduled":
        if not publish_at:
            raise ManifestError(f"{path}: a scheduled video needs publishAt")
        when = _iso_time(str(publish_at), "publishAt")
        if when <= dt.datetime.now(dt.timezone.utc) - dt.timedelta(minutes=5):
            raise ManifestError(f"{path}: publishAt {publish_at} is in the past")
        publish_at = when.astimezone(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    elif publish_at:
        raise ManifestError(f"{path}: publishAt only makes sense with visibility scheduled")

    category_id = str(raw.get("categoryId") or config.get("defaultCategoryId") or "20")
    if not category_id.isdigit():
        raise ManifestError(f"{path}: categoryId must be a number, got {category_id!r}")

    video = _source(raw.get("video"), "video")
    if video is None:
        raise ManifestError(f"{path}: video is required (a file or a url)")

    thumbnail = _source(raw.get("thumbnail"), "thumbnail")
    if thumbnail is not None and thumbnail.file:
        name = thumbnail.file.lower()
        if not (name.endswith(".jpg") or name.endswith(".jpeg") or name.endswith(".png")):
            raise ManifestError(f"{path}: a thumbnail must be a jpg or a png")

    captions = raw.get("captions") or []
    if not isinstance(captions, list):
        raise ManifestError(f"{path}: captions must be a list")
    for index, caption in enumerate(captions):
        if not isinstance(caption, dict) or "file" not in caption:
            raise ManifestError(f"{path}: captions[{index}] needs a file")
        if not caption.get("language"):
            raise ManifestError(f"{path}: captions[{index}] needs a language, e.g. \"en\"")

    return Manifest(
        path=path,
        slug=slug,
        title=title,
        description=description,
        tags=tags,
        category_id=category_id,
        visibility=visibility,
        publish_at=publish_at,
        made_for_kids=bool(raw.get("madeForKids", False)),
        playlist_id=(raw.get("playlistId") or config.get("defaultPlaylistId") or None),
        video=video,
        thumbnail=thumbnail,
        captions=list(captions),
        raw=raw,
    )


def load_config(root: pathlib.Path) -> dict:
    path = root / CONFIG_PATH
    if not path.is_file():
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ManifestError(f"{CONFIG_PATH} is not valid JSON: {error}") from error


def queued_manifests(root: pathlib.Path, config: dict, directory: pathlib.Path, only: str | None):
    """The manifests to work on, in filename order, skipping what is already published."""
    found: list[Manifest or str] = []
    files = sorted(directory.glob("*.json")) if directory.is_dir() else []
    if not files:
        return []
    published = {p.stem for p in (root / PUBLISHED_DIR).glob("*.json")} if (root / PUBLISHED_DIR).is_dir() else set()
    for path in files:
        if only and only not in (path.stem, path.name):
            continue
        manifest = load_manifest(path, config)
        if manifest.slug in published:
            found.append(f"{manifest.slug} (already published, skipping)")
            continue
        found.append(manifest)
    return found


# ----------------------------------------------------------------------------------------------
# check: what would happen, with no credentials and no network
# ----------------------------------------------------------------------------------------------


def run_check(root: pathlib.Path, directory: pathlib.Path, only: str | None) -> int:
    config = load_config(root)
    print(f"manifests in {directory}/")
    items = queued_manifests(root, config, directory, only)
    if not items:
        print("  none. Drop a file in youtube/queue/ to post something.")
        summary_lines.append("No manifests queued.")
        return 0

    problems = 0
    total_quota = 0
    for item in items:
        if isinstance(item, str):
            print(f"  - {item}")
            continue
        status, publish_at = item.status()
        total_quota += item.quota
        print(f"  {item.slug}")
        print(f"    title       {item.title}")
        print(f"    visibility  {item.visibility}" + (f" at {publish_at}" if publish_at else ""))
        print(f"    video       {item.video.describe(root)}")
        if item.thumbnail:
            print(f"    thumbnail   {item.thumbnail.describe(root)}")
        if item.playlist_id:
            print(f"    playlist    {item.playlist_id}")
        for caption in item.captions:
            print(f"    captions    {caption['file']} ({caption.get('language')})")
        print(f"    quota       {item.quota} units")
        for section in ("video", "thumbnail"):
            source = getattr(item, section)
            if source is not None and source.file and not (root / source.file).is_file():
                print(f"    PROBLEM     {section} file {source.file} does not exist")
                problems += 1
        for caption in item.captions:
            if not (root / caption["file"]).is_file():
                print(f"    PROBLEM     caption file {caption['file']} does not exist")
                problems += 1
        if item.video.url and "${" in item.video.url:
            print("    note        the url uses ${SECRET}; it is filled in at upload time")
    print(f"\ntotal quota when uploaded: {total_quota} units of 10,000 a day")
    summary_lines.append(
        f"Checked {sum(1 for i in items if not isinstance(i, str))} manifest(s), "
        f"{total_quota} quota units, {problems} problem(s)."
    )
    return 1 if problems else 0


# ----------------------------------------------------------------------------------------------
# upload: the real thing
# ----------------------------------------------------------------------------------------------


class SetupProblem(Exception):
    """Something the person running this has to fix, explained in plain words."""


def credentials():
    client_id = os.environ.get("YT_CLIENT_ID", "").strip()
    client_secret = os.environ.get("YT_CLIENT_SECRET", "").strip()
    refresh_token = os.environ.get("YT_REFRESH_TOKEN", "").strip()
    if not (client_id and client_secret and refresh_token):
        return None
    try:
        # Imported here on purpose: `check` runs with nothing installed but Python itself.
        from google.oauth2.credentials import Credentials
    except ImportError as error:
        raise SetupProblem(
            "the Google client is not installed: "
            "pip install google-api-python-client google-auth google-auth-oauthlib"
        ) from error

    return Credentials(
        token=None,
        refresh_token=refresh_token,
        token_uri="https://oauth2.googleapis.com/token",
        client_id=client_id,
        client_secret=client_secret,
        scopes=SCOPES,
    )


def service(creds):
    try:
        from googleapiclient.discovery import build
    except ImportError as error:
        raise SetupProblem(
            "the Google client is not installed: "
            "pip install google-api-python-client google-auth google-auth-oauthlib"
        ) from error
    return build("youtube", "v3", credentials=creds, cache_discovery=False)


def expand(value: str) -> str:
    """Fills ${NAME} in from the environment, so a private download can be authorised.

    Only names that exist in the environment are substituted, and only in headers and urls, which
    keeps a manifest from reading anything it should not.
    """

    def replace(match: re.Match) -> str:
        name = match.group(1)
        return os.environ.get(name, match.group(0))

    return re.sub(r"\$\{([A-Z0-9_]+)\}", replace, value)


def local_copy(source: Source, root: pathlib.Path, work: pathlib.Path, name: str) -> pathlib.Path:
    """The bytes to upload: a repo file as it is, a URL downloaded into the work directory."""
    if source.file:
        path = root / source.file
        if not path.is_file():
            raise ManifestError(f"{source.file} does not exist")
        return path
    target = work / name
    request = urllib.request.Request(expand(source.url or ""), method="GET")
    for header, value in source.headers.items():
        request.add_header(header, expand(value))
    with urllib.request.urlopen(request, timeout=120) as response, target.open("wb") as out:
        shutil.copyfileobj(response, out, length=1024 * 1024)
    return target


def upload_one(youtube, manifest: Manifest, root: pathlib.Path, work: pathlib.Path) -> dict:
    try:
        from googleapiclient.http import MediaFileUpload
    except ImportError as error:
        raise SetupProblem(
            "the Google client is not installed: "
            "pip install google-api-python-client google-auth google-auth-oauthlib"
        ) from error

    privacy, publish_at = manifest.status()
    video_path = local_copy(manifest.video, root, work, f"{manifest.slug}.video")
    size = video_path.stat().st_size
    print(f"  uploading {video_path.name} ({size / 1048576:.1f} MB)")

    body = {
        "snippet": {
            "title": manifest.title,
            "description": manifest.description,
            "tags": manifest.tags,
            "categoryId": manifest.category_id,
        },
        "status": {
            "privacyStatus": privacy,
            "selfDeclaredMadeForKids": manifest.made_for_kids,
            "license": "youtube",
            "embeddable": True,
        },
    }
    if publish_at:
        body["status"]["publishAt"] = publish_at

    # A resumable upload survives a dropped connection; the client retries 5xx and rate limits.
    media = MediaFileUpload(str(video_path), mimetype="video/*", resumable=True, chunksize=8 * 1024 * 1024)
    request = youtube.videos().insert(part="snippet,status", body=body, media_body=media)
    response = None
    while response is None:
        _, response = request.next_chunk()
    video_id = response["id"]
    print(f"  uploaded: https://youtu.be/{video_id}" + (f" (scheduled for {publish_at})" if publish_at else ""))

    result = {"videoId": video_id, "uploadedAt": dt.datetime.now(dt.timezone.utc).isoformat()}
    if publish_at:
        result["scheduledFor"] = publish_at

    if manifest.thumbnail:
        image = local_copy(manifest.thumbnail, root, work, f"{manifest.slug}.thumb")
        if image.stat().st_size > MAX_THUMBNAIL_BYTES:
            raise ManifestError(f"thumbnail {image.name} is larger than 2 MB")
        youtube.thumbnails().set(videoId=video_id, media_body=MediaFileUpload(str(image))).execute()
        print("  thumbnail set")
        result["thumbnail"] = True

    if manifest.playlist_id:
        youtube.playlistItems().insert(
            part="snippet",
            body={
                "snippet": {
                    "playlistId": manifest.playlist_id,
                    "resourceId": {"kind": "youtube#video", "videoId": video_id},
                }
            },
        ).execute()
        print(f"  added to playlist {manifest.playlist_id}")
        result["playlistId"] = manifest.playlist_id

    for caption in manifest.captions:
        path = root / caption["file"]
        youtube.captions().insert(
            part="snippet",
            body={
                "snippet": {
                    "videoId": video_id,
                    "language": caption["language"],
                    "name": caption.get("name") or caption["language"],
                }
            },
            media_body=MediaFileUpload(str(path), mimetype="text/vtt"),
        ).execute()
        print(f"  captions added: {caption['language']}")
        result.setdefault("captions", []).append(caption["language"])

    return result


def run_upload(root: pathlib.Path, directory: pathlib.Path, only: str | None, max_uploads: int) -> int:
    config = load_config(root)
    items = [i for i in queued_manifests(root, config, directory, only) if not isinstance(i, str)]
    if not items:
        print("nothing queued: no video to upload")
        summary_lines.append("Nothing queued.")
        return 0

    creds = credentials()
    if creds is None:
        # Not an error: the queue is simply waiting for the one-time setup. Saying so plainly beats
        # a red run every six hours.
        message = (
            "setup incomplete: add YT_CLIENT_ID, YT_CLIENT_SECRET and YT_REFRESH_TOKEN as repository "
            "secrets (tools/youtube/README.md, step 5), then run this workflow again"
        )
        print(f"::notice title=YouTube bot::{message}")
        summary_lines.append(f"**Waiting on setup.** {message}")
        return 0

    youtube = service(creds)
    uploaded = 0
    failures = 0
    for manifest in items:
        if uploaded >= max_uploads:
            print(f"stopping at {max_uploads} uploads this run (quota guard); the rest wait for the next")
            summary_lines.append(f"Stopped at {max_uploads} uploads this run; the rest are still queued.")
            break
        print(f"\n{manifest.slug}")
        try:
            result = upload_one(youtube, manifest, root, root / ".yt-work")
        except Exception as error:  # noqa: BLE001 - one bad manifest must not stop the others
            failures += 1
            print(f"  FAILED: {error}")
            print(f"::error title=upload failed ({manifest.slug})::{error}")
            summary_lines.append(f"**{manifest.slug} failed:** {error}")
            continue
        record = dict(manifest.raw)
        record["youtube"] = result
        PUBLISHED_DIR.mkdir(parents=True, exist_ok=True)
        target = root / PUBLISHED_DIR / f"{manifest.slug}.json"
        target.write_text(json.dumps(record, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        manifest.path.unlink()
        uploaded += 1
        link = f"https://youtu.be/{result['videoId']}"
        when = result.get("scheduledFor")
        summary_lines.append(f"Uploaded **{manifest.title}** - {link}" + (f" (goes live {when})" if when else ""))
        print(f"  recorded in {PUBLISHED_DIR}/{manifest.slug}.json")
        time.sleep(2)

    if failures:
        print(f"\n{failures} manifest(s) failed; they stay in the queue and will be retried")
        return 1
    return 0


# ----------------------------------------------------------------------------------------------

summary_lines: list[str] = []


def write_summary() -> None:
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if not path or not summary_lines:
        return
    with open(path, "a", encoding="utf-8") as handle:
        handle.write("### YouTube\n\n")
        for line in summary_lines:
            handle.write(f"- {line}\n")


def main() -> int:
    parser = argparse.ArgumentParser(description="Upload the queued videos to YouTube")
    parser.add_argument("mode", choices=("check", "upload"))
    parser.add_argument("--dir", default=str(QUEUE_DIR), help="where the manifests are")
    parser.add_argument("--only", default=None, help="one manifest, by slug or file name")
    parser.add_argument("--max", type=int, default=5, help="uploads per run (quota guard)")
    args = parser.parse_args()

    root = pathlib.Path.cwd()
    directory = pathlib.Path(args.dir)
    try:
        if args.mode == "check":
            code = run_check(root, directory, args.only)
        else:
            code = run_upload(root, directory, args.only, args.max)
    except ManifestError as error:
        print(f"::error title=manifest::{error}")
        summary_lines.append(f"**A manifest is wrong:** {error}")
        code = 1
    except SetupProblem as error:
        print(f"::error title=setup::{error}")
        summary_lines.append(f"**Setup problem:** {error}")
        code = 2
    write_summary()
    return code


if __name__ == "__main__":
    sys.exit(main())
