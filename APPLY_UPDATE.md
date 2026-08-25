# Apply this refactor to the existing repository

This package is designed to be extracted over the existing `ScrabbleGame` repository.

## Windows

1. Make sure your current work is committed or backed up.
2. Extract this package into the repository root and allow files to be replaced.
3. Open PowerShell in the repository root.
4. Run:

```powershell
.\apply-update.ps1
```

The script moves the original milestone PDFs from `src` to `coursework` and removes only the legacy Java files that lived directly under `src/test`. It keeps the new `src/test/java` Maven test tree.

Then run:

```powershell
mvn test
node --check docs\app.js
git add -A
git commit -m "Refactor ScrabbleGame engine and browser demo"
git push
```

If PowerShell blocks the local script, run it for this process with:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\apply-update.ps1
```

## GitHub Pages

Keep GitHub Pages configured as:

- Source: Deploy from a branch
- Branch: main
- Folder: /docs

The browser game will be available at:

https://masterchiefproject.github.io/ScrabbleGame/
