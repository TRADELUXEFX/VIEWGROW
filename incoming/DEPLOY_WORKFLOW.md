# How I deploy code changes to this repo (ViewGrow app)

I work entirely from my phone. Files that GitHub's "Upload files" cannot place
(this file's folder and `.github/workflows/deploy.yml`) were created by hand once.
Everything else arrives as a zip.

## The pieces
1. `.github/workflows/deploy.yml`: the one GitHub Action. Runs on every push to `main`.
   It unpacks any zip in `incoming/`, applies `DELETE_FILES.txt` if present, commits,
   deletes the zip, then builds the APK and publishes a release (`v1.0.<run number>`).
   If `app/build.gradle` or `settings.gradle` is not in the repo yet, it unpacks and
   skips the build (a notice, not a failure).
2. `incoming/`: the drop zone. Any zip name works. Never delete this folder.

## Steps for each change
1. Get a zip whose paths start at the repo root (`app/...`, `supabase/...`, `docs/...`), no wrapper folder.
2. github.com in the phone browser, open `incoming/`, Add file, Upload files, pick the zip.
3. Commit directly to `main`.
4. Actions tab: wait about a minute. Green = unpacked and built. Red = did not land; open the run and read the annotations.
5. Check Profile, APP VERSION in the new APK.

## Deleting files
Put `DELETE_FILES.txt` at the zip root, one repo path per line. Single files only.
Protected paths (`.git`, `.github`, `incoming`, root and app `build.gradle`, `settings.gradle`, `gradle.properties`, anything with `..`) are skipped.

## Rules
- **Every change is sent as the FULL updated zip** (the whole app, not only changed files), with `DELETE_FILES.txt` for any file that was removed. Uploading a zip never deletes files by itself.
- Bump `versionCode` in `app/build.gradle` whenever app code changes.
- `.github/workflows/` inside a zip is always ignored. Edit that file on github.com.
- SQL: pasted in chat AND saved in `supabase/migrations/`; the system guide is updated in the same zip.

## One-time repo settings
- Settings, Actions, General, Workflow permissions: **Read and write permissions**.
- Settings, Secrets and variables, Actions: `SUPABASE_URL`, `SUPABASE_ANON_KEY` (ViewGrow project), `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
