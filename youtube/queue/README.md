# The queue

Every `.json` in this folder is a video waiting to go out. Push one and the **YouTube** workflow
uploads it, sets the title, description, tags, thumbnail, playlist and captions, and either posts it
or schedules it for the `publishAt` in the file. The file then moves to `youtube/published/` with
the video id, so it can never be posted twice.

Start from a copy of `youtube/examples/scheduled-from-repo-file.json`, and read
`tools/youtube/README.md` first: it has the one-time setup and what every field means.

```bash
cp youtube/examples/scheduled-from-repo-file.json youtube/queue/aerix-1-3-devlog.json
# edit the copy, then:
git add youtube/queue/aerix-1-3-devlog.json && git commit -m "Queue the devlog" && git push
```

Want to see what would happen without posting anything? Actions → **YouTube** → *Run workflow* →
`check`.
