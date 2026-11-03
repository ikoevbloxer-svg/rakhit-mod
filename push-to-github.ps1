$ErrorActionPreference = "Stop"
if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw "Git не установлен: https://git-scm.com/download/win" }
if (-not (Test-Path ".git")) { git init; git branch -M main; git remote add origin "https://github.com/ikoevbloxer-svg/rakhit-mod.git" } else { git remote set-url origin "https://github.com/ikoevbloxer-svg/rakhit-mod.git" }
git add .
git commit -m "Initial import: Kingdom of Rakhit III"
git push -u origin main
