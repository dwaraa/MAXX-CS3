#!/usr/bin/env python3
"""
Daily Health & Status Checker for MAXX CloudStream 3 Plugins
Runs daily via GitHub Actions.
Checks:
 1. Plugin build integrity & plugins.json manifest
 2. Provider API/site reachability
 3. Published release artifacts on the builds branch
"""

import sys
import json
import urllib.request
import urllib.error
import ssl

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
}

TMDB_API_KEY = "e6333b32409e02a4a6eba6fb7ff866bb"

def check_endpoint(name, url, method="GET", timeout=12):
    req = urllib.request.Request(url, headers=HEADERS, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=ctx) as resp:
            status = resp.getcode()
            if status in (200, 301, 302, 307):
                print(f" [PASS] {name}: Status {status}")
                return True
            print(f" [WARN] {name}: Status {status}")
            return False
    except urllib.error.HTTPError as e:
        if e.code in (403, 401) and "cloudflare" in str(e.headers).lower():
            print(f" [PASS] {name}: Protected by Cloudflare (HTTP {e.code}) - Host Online")
            return True
        print(f" [FAIL] {name}: HTTP {e.code}")
        return False
    except Exception as e:
        print(f" [WARN] {name}: {type(e).__name__} - {e}")
        return False

def check_tamildhool():
    print("\n--- [1] Checking TamilDhool Provider ---")
    api_url = "https://tamildhool.tech/wp-json/wp/v2/posts?per_page=3&_embed=1"
    req = urllib.request.Request(api_url, headers=HEADERS)
    try:
        with urllib.request.urlopen(req, timeout=12, context=ctx) as resp:
            data = json.loads(resp.read().decode())
            if isinstance(data, list) and len(data) > 0:
                print(f" [PASS] TamilDhool REST API: {len(data)} posts fetched successfully")
                return True
    except Exception as e:
        print(f" [WARN] TamilDhool API check note: {e}")
    # Backup domain check
    return check_endpoint("TamilDhool Main Site", "https://tamildhool.tech")

def check_tamilmv():
    print("\n--- [2] Checking 1TamilMV Provider ---")
    domains = [
        "https://www.1tamilmv.capital",
        "https://1tamilmv.fi",
        "https://www.1tamilmv.wtf"
    ]
    for d in domains:
        if check_endpoint(f"1TamilMV ({d})", d):
            return True
    return False

def check_einthusan():
    print("\n--- [3] Checking Einthusan Provider ---")
    return check_endpoint("Einthusan (Browse)", "https://einthusan.tv/movie/browse/?lang=tamil")

def check_tamilultra():
    print("\n--- [4] Checking TamilUltra Provider ---")
    return check_endpoint("TamilUltra Site", "https://tamilultra.co.uk/")

def check_specialitystream():
    print("\n--- [5] Checking SpecialityStream & TMDB API ---")
    tmdb_ok = check_endpoint("TMDB Popular API", f"https://api.themoviedb.org/3/movie/popular?api_key={TMDB_API_KEY}")
    return tmdb_ok

def check_repository_builds():
    print("\n--- [6] Checking Published CloudStream Repository ---")
    repo_ok = check_endpoint("repo.json", "https://raw.githubusercontent.com/dwaraa/MAXX-CS3/builds/repo.json")
    plugins_ok = check_endpoint("plugins.json", "https://raw.githubusercontent.com/dwaraa/MAXX-CS3/builds/plugins.json")
    
    # Check that plugins.json contains all active providers
    if plugins_ok:
        try:
            req = urllib.request.Request("https://raw.githubusercontent.com/dwaraa/MAXX-CS3/builds/plugins.json", headers=HEADERS)
            with urllib.request.urlopen(req, timeout=10, context=ctx) as resp:
                plugins = json.loads(resp.read().decode())
                names = [p.get("name") for p in plugins]
                print(f" [INFO] Registered plugins in repo: {names}")
                expected = ["TamilDhool", "TamilMV", "Einthusan", "TamilUltra", "SpecialityStream"]
                missing = [p for p in expected if not any(p.lower() in n.lower() for n in names)]
                if missing:
                    print(f" [WARN] Some plugins missing from manifest: {missing}")
                else:
                    print(f" [PASS] All 5 plugins present in repository manifest!")
        except Exception as e:
            print(f" [WARN] Could not parse plugins.json details: {e}")
            
    return repo_ok and plugins_ok

def main():
    print("=" * 65)
    print("  MAXX-CS3 Plugins Daily Health & Status Checker")
    print("=" * 65)

    checks = {
        "Repository & Manifest": check_repository_builds(),
        "SpecialityStream (TMDB)": check_specialitystream(),
        "1TamilMV": check_tamilmv(),
        "TamilDhool": check_tamildhool(),
        "Einthusan": check_einthusan(),
        "TamilUltra": check_tamilultra(),
    }

    print("\n" + "=" * 65)
    print("  STATUS SUMMARY:")
    for name, ok in checks.items():
        tag = "[OK]  " if ok else "[WARN]"
        status_text = "OPERATIONAL" if ok else "CHECK REQUIRED / BLOCKED BY HOST"
        print(f"  {tag} {name:<26} -> {status_text}")
    print("=" * 65)

    # Core repository assets must always pass
    if not checks["Repository & Manifest"]:
        print(" [CRITICAL] Repository manifest or artifacts not reachable!")
        sys.exit(1)
        
    print(" Health check completed successfully.")

if __name__ == "__main__":
    main()
