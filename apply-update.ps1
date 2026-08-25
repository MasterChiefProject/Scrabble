$ErrorActionPreference = "Stop"

Write-Host "Preparing ScrabbleGame repository layout..."

New-Item -ItemType Directory -Force -Path "coursework" | Out-Null

Get-ChildItem -Path "src" -File -Filter "*.pdf" -ErrorAction SilentlyContinue | ForEach-Object {
    Move-Item -Force $_.FullName (Join-Path "coursework" $_.Name)
}

# Remove only the legacy Java files that lived directly under src/test.
# Keep the new Maven test tree at src/test/java.
Get-ChildItem -Path "src/test" -File -Filter "*.java" -ErrorAction SilentlyContinue | Remove-Item -Force

Write-Host "Done. Review the changes, then run: mvn test"
