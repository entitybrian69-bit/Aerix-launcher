"""Live metadata smoke check. Never prints credentials, response bodies or private headers."""
import json
import os
import urllib.error
import urllib.parse
import urllib.request


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def probe(base, key, query):
    headers = {"Accept": "application/json", "User-Agent":
               "AerixLauncher/1.3.4 (+https://github.com/entitybrian69-bit/Aerix-launcher)"}
    if key:
        headers["x-api-key"] = key
    request = urllib.request.Request(base + "/mods/search?" + urllib.parse.urlencode(query), headers=headers)
    try:
        # Do not forward authenticated headers via redirects.
        with urllib.request.build_opener(NoRedirect).open(request, timeout=20) as response:
            status = response.status
            content_type = response.headers.get_content_type()
            try:
                body = json.load(response)
            except (ValueError, UnicodeError):
                return {"status": status, "content_type": content_type, "result": "invalid_json"}
            data = body.get("data") if isinstance(body, dict) else body
            return {"status": status, "content_type": content_type,
                    "result": "valid_list" if isinstance(data, list) else "invalid_shape",
                    "count": len(data) if isinstance(data, list) else None}
    except urllib.error.HTTPError as failure:
        return {"status": failure.code, "result": "http_error"}
    except Exception as failure:
        return {"result": "transport_error", "kind": type(failure).__name__}


def main():
    key = os.environ.get("CURSEFORGE_API_KEY", "").strip()
    if key.upper() == "DUMMY":
        key = ""
    full = {"gameId": 432, "classId": 6, "searchFilter": "", "sortField": 1,
            "sortOrder": "desc", "pageSize": 20, "index": 0}
    minimal = {"gameId": 432, "classId": 6, "pageSize": 1}
    report = {"build_key_present": bool(key)}
    report["official"] = probe("https://api.curseforge.com/v1", key, full) if key else {"result": "not_configured"}
    report["mirror_exact_search"] = probe("https://mod.mcimirror.top/curseforge/v1", None, full)
    report["mirror_minimal_search"] = probe("https://mod.mcimirror.top/curseforge/v1", None, minimal)
    text = json.dumps(report, sort_keys=True)
    print(text)
    # Annotations remain available even when downloading Actions logs is restricted.
    print("::notice title=CurseForge live metadata check::" + text)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as out:
            out.write("## CurseForge live metadata check\n```json\n" + json.dumps(report, indent=2) + "\n```\n")
    if not any(report[name]["result"] == "valid_list"
               for name in ("official", "mirror_exact_search")):
        print("::error title=CurseForge unavailable::Neither configured official access nor the exact mirror search succeeded. Do not treat fixture tests as live verification.")
        raise SystemExit(1)


if __name__ == "__main__":
    main()
