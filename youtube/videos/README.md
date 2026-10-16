# Videos

Small clips can live here and be referenced as `"video": { "file": "youtube/videos/name.mp4" }`.

Keep this folder out of git history for anything large: a phone recording is hundreds of megabytes,
and snapshots of this repository are meant to stay small. The pattern that works is a private
staging repository whose release carries the video, referenced by URL in the manifest — see
`tools/youtube/README.md`, "Big videos".
