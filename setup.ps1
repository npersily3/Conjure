<#
.SYNOPSIS
    Conjure mod setup script -- installs external runtime dependencies and pre-builds the project.

.DESCRIPTION
    Installs the external runtime dependencies that Gradle cannot fetch (JDK 21, Ollama or
    Anthropic API key, ComfyUI optional image backend), writes an initial config if one does not
    exist, then pre-compiles the mod so the first runClient launch does not have to download
    NeoForge and Minecraft.

    Gradle auto-fetches MC 1.21.1, NeoForge 21.1.93, GeckoLib 4.7.6, and Rhino 1.7.15 -- this
    script does NOT touch those.

.PARAMETER TextProvider
    Which text/logic model backend to use: "local" (Ollama, default) or "anthropic" (cloud).

.PARAMETER AnthropicKey
    Your Anthropic API key -- required when TextProvider=anthropic.

.PARAMETER SkipComfyUI
    Omit ComfyUI installation. ComfyUI installs BY DEFAULT; pass this flag to opt out.
    Texture generation falls back to LLM pixel-art without it -- everything still works.

.PARAMETER SkipBuild
    Skip the initial gradlew compileJava pre-build step.

.PARAMETER DryRun
    Print every action the script WOULD take; make no changes at all.
    Use this to verify the plan before committing.

.EXAMPLE
    # Default: local Ollama + ComfyUI, then pre-build
    .\setup.ps1

.EXAMPLE
    # Use Anthropic, skip ComfyUI, dry-run first
    .\setup.ps1 -TextProvider anthropic -AnthropicKey sk-ant-... -SkipComfyUI -DryRun

.EXAMPLE
    # Local Ollama, skip image backend and build
    .\setup.ps1 -SkipComfyUI -SkipBuild

.NOTES
    Requires PowerShell 5.1 (Windows built-in). winget must be available for automatic installs.
    Python 3.10+ must be on PATH for ComfyUI setup.
    Run from the Conjure repo root.
#>

param(
    [ValidateSet('local','anthropic')]
    [string]$TextProvider = 'local',

    [string]$AnthropicKey = '',

    [switch]$SkipComfyUI,

    [switch]$SkipBuild,

    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

function Write-Banner {
    param([string]$Text)
    Write-Host ''
    Write-Host ('=' * 70) -ForegroundColor Cyan
    Write-Host "  $Text" -ForegroundColor Cyan
    Write-Host ('=' * 70) -ForegroundColor Cyan
}

function Write-Ok    { param([string]$m) Write-Host "[OK]      $m" -ForegroundColor Green  }
function Write-Info  { param([string]$m) Write-Host "[INFO]    $m" -ForegroundColor Yellow }
function Write-Warn  { param([string]$m) Write-Host "[WARN]    $m" -ForegroundColor DarkYellow }
function Write-Err   { param([string]$m) Write-Host "[ERROR]   $m" -ForegroundColor Red    }
function Write-Dry   { param([string]$m) Write-Host "[DryRun]  $m" -ForegroundColor Magenta }
function Write-Skip  { param([string]$m) Write-Host "[SKIP]    $m" -ForegroundColor DarkGray }

# Summary table accumulator: each entry is a hashtable with Name, Status
$script:Summary = @()

function Add-Summary {
    param([string]$Name, [string]$Status)
    $script:Summary += @{ Name = $Name; Status = $Status }
}

# Resolve the repo root as the directory containing this script
$RepoRoot = $PSScriptRoot
if (-not $RepoRoot) {
    $RepoRoot = (Get-Location).Path
}
$RepoParent = Split-Path -Parent $RepoRoot

if ($DryRun) {
    Write-Host ''
    Write-Host '*** DRY RUN MODE -- no changes will be made ***' -ForegroundColor Magenta
}

# ---------------------------------------------------------------------------
# Step 1 -- JDK 21
# ---------------------------------------------------------------------------

Write-Banner 'Step 1: JDK 21'

$jdkPresent = $false
$jdkVersion = ''

# Check JAVA_HOME first
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    try {
        $v = & "$env:JAVA_HOME\bin\java.exe" -version 2>&1 | Select-String 'version' | Select-Object -First 1
        if ($v -match '"21\.') {
            $jdkPresent = $true
            $jdkVersion = $v.ToString().Trim()
        }
    } catch {}
}

# Fallback: java on PATH
if (-not $jdkPresent) {
    try {
        $v = java -version 2>&1 | Select-String 'version' | Select-Object -First 1
        if ($v -match '"21\.') {
            $jdkPresent = $true
            $jdkVersion = $v.ToString().Trim()
        }
    } catch {}
}

if ($jdkPresent) {
    Write-Ok "JDK 21 already present: $jdkVersion"
    Add-Summary -Name 'JDK 21' -Status 'already present'
} else {
    Write-Info 'JDK 21 not found -- attempting install via winget...'

    # Verify winget is available
    $wingetCmd = $null
    try { $wingetCmd = Get-Command winget -ErrorAction Stop } catch {}

    if ($null -eq $wingetCmd) {
        Write-Err 'winget is not available on this machine.'
        Write-Err 'Please install JDK 21 manually:'
        Write-Err '  https://learn.microsoft.com/en-us/java/openjdk/download#openjdk-21'
        Write-Err 'After installing, set JAVA_HOME and add %JAVA_HOME%\bin to your PATH,'
        Write-Err 'then re-run this script.'
        exit 1
    }

    if ($DryRun) {
        Write-Dry 'would run: winget install -e --id Microsoft.OpenJDK.21'
        Add-Summary -Name 'JDK 21' -Status '[DryRun] would install'
    } else {
        Write-Info 'Installing JDK 21 via winget (this may take a minute)...'
        try {
            winget install -e --id Microsoft.OpenJDK.21 --accept-package-agreements --accept-source-agreements
            Write-Ok 'JDK 21 installed. You may need to restart your terminal for JAVA_HOME to take effect.'
            Add-Summary -Name 'JDK 21' -Status 'installed'
        } catch {
            Write-Err "winget install failed: $_"
            Write-Warn 'Please install JDK 21 manually from https://learn.microsoft.com/en-us/java/openjdk/download#openjdk-21'
            exit 1
        }
    }
}

# ---------------------------------------------------------------------------
# Step 2 -- Text provider: Ollama (local) or Anthropic key
# ---------------------------------------------------------------------------

Write-Banner "Step 2: Text provider ($TextProvider)"

if ($TextProvider -eq 'local') {

    # 2a. Ollama binary
    $ollamaCmd = $null
    try { $ollamaCmd = Get-Command ollama -ErrorAction Stop } catch {}

    if ($null -ne $ollamaCmd) {
        Write-Ok "ollama already on PATH: $($ollamaCmd.Source)"
        Add-Summary -Name 'Ollama binary' -Status 'already present'
    } else {
        Write-Info 'ollama not found -- installing via winget...'

        $wingetCmd2 = $null
        try { $wingetCmd2 = Get-Command winget -ErrorAction Stop } catch {}

        if ($null -eq $wingetCmd2) {
            Write-Warn 'winget is not available. Install Ollama manually from https://ollama.com/download/windows'
            Add-Summary -Name 'Ollama binary' -Status 'FAILED -- manual install required'
        } elseif ($DryRun) {
            Write-Dry 'would run: winget install -e --id Ollama.Ollama'
            Add-Summary -Name 'Ollama binary' -Status '[DryRun] would install'
        } else {
            try {
                winget install -e --id Ollama.Ollama --accept-package-agreements --accept-source-agreements
                Write-Ok 'Ollama installed.'
                Add-Summary -Name 'Ollama binary' -Status 'installed'
            } catch {
                Write-Warn "Ollama install via winget failed: $_"
                Write-Warn 'Install manually from https://ollama.com/download/windows'
                Add-Summary -Name 'Ollama binary' -Status 'FAILED -- manual install required'
            }
        }
    }

    # 2b. Ensure Ollama service is running
    $ollamaRunning = $false
    try {
        Invoke-WebRequest -Uri 'http://127.0.0.1:11434' -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop | Out-Null
        $ollamaRunning = $true
    } catch {}

    if ($ollamaRunning) {
        Write-Ok 'Ollama service is already running on port 11434.'
        Add-Summary -Name 'Ollama service' -Status 'already running'
    } elseif ($DryRun) {
        Write-Dry 'would start Ollama service: Start-Process ollama -ArgumentList serve'
        Add-Summary -Name 'Ollama service' -Status '[DryRun] would start'
    } else {
        Write-Info 'Starting Ollama service...'
        try {
            Start-Process -FilePath 'ollama' -ArgumentList 'serve' -WindowStyle Hidden -ErrorAction Stop
            # Give it a moment to bind
            $waited = 0
            $started = $false
            while ($waited -lt 15) {
                Start-Sleep -Seconds 2
                $waited += 2
                try {
                    Invoke-WebRequest -Uri 'http://127.0.0.1:11434' -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop | Out-Null
                    $started = $true
                    break
                } catch {}
            }
            if ($started) {
                Write-Ok 'Ollama service started.'
                Add-Summary -Name 'Ollama service' -Status 'started'
            } else {
                Write-Warn 'Ollama service did not respond within 15 s. It may still be starting -- check manually.'
                Add-Summary -Name 'Ollama service' -Status 'started (unconfirmed)'
            }
        } catch {
            Write-Warn "Could not start Ollama automatically: $_"
            Write-Warn "Run 'ollama serve' in a separate terminal before launching the mod."
            Add-Summary -Name 'Ollama service' -Status 'start failed -- manual required'
        }
    }

    # 2c. Pull the default model
    $modelNeeded = 'gemma4:latest'
    $modelPresent = $false
    try {
        $listOut = ollama list 2>&1
        if ($listOut -match 'gemma4') {
            $modelPresent = $true
        }
    } catch {}

    if ($modelPresent) {
        Write-Ok "$modelNeeded already pulled."
        Add-Summary -Name "Ollama model ($modelNeeded)" -Status 'already present'
    } elseif ($DryRun) {
        Write-Dry "would run: ollama pull $modelNeeded  (WARNING: large download -- several GB)"
        Add-Summary -Name "Ollama model ($modelNeeded)" -Status '[DryRun] would pull'
    } else {
        Write-Warn "About to pull '$modelNeeded' -- this is a large download (several GB). Press Ctrl+C to cancel."
        Write-Info "Pulling $modelNeeded..."
        try {
            ollama pull $modelNeeded
            Write-Ok "$modelNeeded pulled."
            Add-Summary -Name "Ollama model ($modelNeeded)" -Status 'pulled'
        } catch {
            Write-Warn "ollama pull failed: $_"
            Write-Warn "Run 'ollama pull $modelNeeded' manually before launching the mod."
            Add-Summary -Name "Ollama model ($modelNeeded)" -Status 'pull failed -- manual required'
        }
    }

} else {
    # TextProvider = anthropic

    if ($AnthropicKey -eq '') {
        Write-Err '-AnthropicKey is required when -TextProvider anthropic is specified.'
        Write-Err 'Example: .\setup.ps1 -TextProvider anthropic -AnthropicKey "sk-ant-..."'
        exit 1
    }

    $currentKey = [Environment]::GetEnvironmentVariable('ANTHROPIC_API_KEY', 'User')
    if ($currentKey -eq $AnthropicKey) {
        Write-Ok 'ANTHROPIC_API_KEY user env var already set to the provided value.'
        Add-Summary -Name 'ANTHROPIC_API_KEY env var' -Status 'already set'
    } elseif ($DryRun) {
        Write-Dry 'would set user env var ANTHROPIC_API_KEY = <provided key>'
        Add-Summary -Name 'ANTHROPIC_API_KEY env var' -Status '[DryRun] would set'
    } else {
        [Environment]::SetEnvironmentVariable('ANTHROPIC_API_KEY', $AnthropicKey, 'User')
        # Also set in current session so gradlew picks it up if SkipBuild is not passed
        $env:ANTHROPIC_API_KEY = $AnthropicKey
        Write-Ok 'ANTHROPIC_API_KEY set as persistent user environment variable.'
        Add-Summary -Name 'ANTHROPIC_API_KEY env var' -Status 'set'
    }

    Write-Skip 'Ollama: skipped (TextProvider=anthropic)'
    Add-Summary -Name 'Ollama' -Status 'skipped (using Anthropic)'
}

# ---------------------------------------------------------------------------
# Step 3 -- ComfyUI (optional image backend)
# ---------------------------------------------------------------------------

Write-Banner 'Step 3: ComfyUI (optional image backend)'

if ($SkipComfyUI) {
    Write-Skip 'ComfyUI: skipped via -SkipComfyUI flag.'
    Write-Info 'Texture generation will use the LLM pixel-art fallback -- everything still works.'
    Add-Summary -Name 'ComfyUI' -Status 'skipped (-SkipComfyUI)'
} else {
    $ComfyUIPath = Join-Path $RepoParent 'ComfyUI'

    # 3a. Clone repo
    if (Test-Path (Join-Path $ComfyUIPath '.git')) {
        Write-Ok "ComfyUI repo already cloned at $ComfyUIPath"
        Add-Summary -Name 'ComfyUI repo' -Status 'already present'
    } elseif ($DryRun) {
        Write-Dry "would clone https://github.com/comfyanonymous/ComfyUI into $ComfyUIPath"
        Add-Summary -Name 'ComfyUI repo' -Status '[DryRun] would clone'
    } else {
        Write-Info "Cloning ComfyUI into $ComfyUIPath ..."
        try {
            git clone https://github.com/comfyanonymous/ComfyUI $ComfyUIPath
            Write-Ok 'ComfyUI cloned.'
            Add-Summary -Name 'ComfyUI repo' -Status 'cloned'
        } catch {
            Write-Warn "git clone failed: $_"
            Write-Warn 'ComfyUI setup skipped -- texture generation will use LLM pixel-art fallback.'
            Add-Summary -Name 'ComfyUI repo' -Status 'clone FAILED (non-fatal)'
            $SkipComfyUI = $true
        }
    }

    # 3b. Detect Python 3.10+
    if (-not $SkipComfyUI) {
        $pythonExe = $null
        foreach ($candidate in @('py', 'python', 'python3')) {
            try {
                $cmd = Get-Command $candidate -ErrorAction Stop
                $ver = & $cmd.Source --version 2>&1
                if ($ver -match 'Python 3\.(1[0-9]|[2-9]\d)') {
                    $pythonExe = $cmd.Source
                    break
                }
            } catch {}
        }

        if ($null -eq $pythonExe) {
            Write-Warn 'Python 3.10+ not found on PATH.'
            Write-Warn 'ComfyUI requires Python 3.10 or newer.'
            Write-Warn 'Download from https://www.python.org/downloads/ then re-run without -SkipComfyUI.'
            Write-Warn 'ComfyUI setup will be skipped this run -- texture generation falls back to LLM pixel-art.'
            Add-Summary -Name 'ComfyUI venv' -Status 'skipped (Python 3.10+ missing)'
            $SkipComfyUI = $true
        }
    }

    # 3c. Create venv + pip install
    if (-not $SkipComfyUI) {
        $venvPath = Join-Path $ComfyUIPath 'venv'
        if (Test-Path (Join-Path $venvPath 'Scripts\python.exe')) {
            Write-Ok "ComfyUI venv already exists at $venvPath"
            Add-Summary -Name 'ComfyUI venv' -Status 'already present'
        } elseif ($DryRun) {
            Write-Dry "would run: $pythonExe -m venv $venvPath"
            Write-Dry "would run: pip install -r $ComfyUIPath\requirements.txt"
            Add-Summary -Name 'ComfyUI venv' -Status '[DryRun] would create'
        } else {
            Write-Info 'Creating Python venv for ComfyUI...'
            try {
                & $pythonExe -m venv $venvPath
                $pipExe = Join-Path $venvPath 'Scripts\pip.exe'
                Write-Info 'Installing ComfyUI requirements (this may take several minutes)...'
                & $pipExe install -r (Join-Path $ComfyUIPath 'requirements.txt')
                Write-Ok 'ComfyUI venv ready.'
                Add-Summary -Name 'ComfyUI venv' -Status 'created'
            } catch {
                Write-Warn "ComfyUI venv/install failed: $_"
                Write-Warn 'ComfyUI setup incomplete -- texture generation will use LLM pixel-art fallback.'
                Add-Summary -Name 'ComfyUI venv' -Status 'FAILED (non-fatal)'
                $SkipComfyUI = $true
            }
        }
    }

    # 3d. Download default SD 1.5 checkpoint (~4 GB)
    if (-not $SkipComfyUI) {
        $checkpointsDir = Join-Path $ComfyUIPath 'models\checkpoints'
        $checkpointFile = Join-Path $checkpointsDir 'v1-5-pruned-emaonly.safetensors'

        if (Test-Path $checkpointFile) {
            Write-Ok "SD 1.5 checkpoint already present at $checkpointFile"
            Add-Summary -Name 'SD 1.5 checkpoint' -Status 'already present'
        } elseif ($DryRun) {
            Write-Dry "would create dir: $checkpointsDir"
            Write-Dry 'would download v1-5-pruned-emaonly.safetensors (~4 GB) into checkpoints dir'
            Add-Summary -Name 'SD 1.5 checkpoint' -Status '[DryRun] would download'
        } else {
            if (-not (Test-Path $checkpointsDir)) {
                New-Item -ItemType Directory -Force -Path $checkpointsDir | Out-Null
            }
            Write-Warn 'Downloading Stable Diffusion 1.5 checkpoint (~4 GB). This will take a while on slow connections.'
            Write-Info 'You can press Ctrl+C and download separately; place the file at:'
            Write-Info "  $checkpointFile"
            $sdUrl = 'https://huggingface.co/runwayml/stable-diffusion-v1-5/resolve/main/v1-5-pruned-emaonly.safetensors'
            try {
                Invoke-WebRequest -Uri $sdUrl -OutFile $checkpointFile -UseBasicParsing
                Write-Ok 'SD 1.5 checkpoint downloaded.'
                Add-Summary -Name 'SD 1.5 checkpoint' -Status 'downloaded'
            } catch {
                Write-Warn "Checkpoint download failed: $_"
                Write-Warn "Download manually from: $sdUrl"
                Write-Warn "Place the file at: $checkpointFile"
                Add-Summary -Name 'SD 1.5 checkpoint' -Status 'download FAILED (non-fatal)'
            }
        }
    }
}

# ---------------------------------------------------------------------------
# Step 4 -- Write initial config (never overwrites)
# ---------------------------------------------------------------------------

Write-Banner 'Step 4: Conjure config (run\config\conjure-common.toml)'

$configDir  = Join-Path $RepoRoot 'run\config'
$configFile = Join-Path $configDir 'conjure-common.toml'

if (Test-Path $configFile) {
    Write-Ok "Config already exists at $configFile -- not overwriting."
    Add-Summary -Name 'conjure-common.toml' -Status 'already exists (not touched)'
} elseif ($DryRun) {
    Write-Dry "would create directory: $configDir"
    if ($TextProvider -eq 'local') {
        Write-Dry 'would write config: [text] provider="LOCAL", localModel="gemma4:latest", localEndpoint="http://127.0.0.1:11434"'
    } else {
        Write-Dry 'would write config: [text] provider="ANTHROPIC", anthropicModel="claude-sonnet-4-6", anthropicKeyEnv="ANTHROPIC_API_KEY"'
    }
    Write-Dry 'would write config: [image] provider="LOCAL", localEndpoint="http://127.0.0.1:8188", quality="FAST"'
    Write-Dry 'would write config: [features] entityAnimations=true, interactivity=true'
    Add-Summary -Name 'conjure-common.toml' -Status '[DryRun] would create'
} else {
    if (-not (Test-Path $configDir)) {
        New-Item -ItemType Directory -Force -Path $configDir | Out-Null
    }

    if ($TextProvider -eq 'local') {
        $providerLine = '    provider = "LOCAL"'
    } else {
        $providerLine = '    provider = "ANTHROPIC"'
    }

    $configLines = @(
        '[text]',
        '    # ANTHROPIC (cloud) or LOCAL (Ollama/LM Studio/llama.cpp)',
        $providerLine,
        '    localEndpoint = "http://127.0.0.1:11434"',
        '    localModel = "gemma4:latest"',
        '    anthropicModel = "claude-sonnet-4-6"',
        '    # Name of the environment variable holding the API key',
        '    anthropicKeyEnv = "ANTHROPIC_API_KEY"',
        '',
        '[image]',
        '    # LOCAL (ComfyUI) or ANTHROPIC (falls back to LLM pixel-art)',
        '    provider = "LOCAL"',
        '    # ComfyUI server, e.g. http://127.0.0.1:8188',
        '    localEndpoint = "http://127.0.0.1:8188"',
        '    # FAST (fewer steps, 512px, ~seconds) or HIGH (more steps, 768px, slower)',
        '    quality = "FAST"',
        '    # ComfyUI checkpoint filename for FAST mode (must exist in ComfyUI/models/checkpoints)',
        '    fastModel = "v1-5-pruned-emaonly.safetensors"',
        '    # ComfyUI checkpoint filename for HIGH mode (must exist in ComfyUI/models/checkpoints)',
        '    highModel = "v1-5-pruned-emaonly.safetensors"',
        '    # Generated texture edge length (px) in FAST mode',
        '    fastSize = 64',
        '    # Generated texture edge length (px) in HIGH mode',
        '    highSize = 128',
        '',
        '[features]',
        '    # Play GeckoLib idle/walk animations on generated mobs (off = static pose)',
        '    entityAnimations = true',
        '    # Allow generated blocks to be interactive (machines / scripted)',
        '    interactivity = true'
    )

    Set-Content -Path $configFile -Value $configLines -Encoding utf8
    Write-Ok "Config written to $configFile"
    Add-Summary -Name 'conjure-common.toml' -Status 'created'
}

# ---------------------------------------------------------------------------
# Step 5 -- Pre-build
# ---------------------------------------------------------------------------

Write-Banner 'Step 5: Pre-build (gradlew compileJava)'

if ($SkipBuild) {
    Write-Skip 'Build skipped via -SkipBuild flag.'
    Write-Info 'Run .\gradlew.bat runClient when ready -- the first launch will download MC/NeoForge.'
    Add-Summary -Name 'Pre-build (compileJava)' -Status 'skipped (-SkipBuild)'
} elseif ($DryRun) {
    Write-Dry 'would run: .\gradlew.bat compileJava --no-daemon'
    Write-Dry '(first run downloads MC 1.21.1 + NeoForge 21.1.93 -- may take several minutes)'
    Add-Summary -Name 'Pre-build (compileJava)' -Status '[DryRun] would run'
} else {
    $gradlew = Join-Path $RepoRoot 'gradlew.bat'
    if (-not (Test-Path $gradlew)) {
        Write-Warn "gradlew.bat not found at $gradlew -- skipping build step."
        Add-Summary -Name 'Pre-build (compileJava)' -Status 'skipped (gradlew.bat not found)'
    } else {
        Write-Info 'Running gradlew compileJava --no-daemon ...'
        Write-Info '(First run downloads MC 1.21.1 + NeoForge 21.1.93 -- this may take several minutes)'
        try {
            & $gradlew compileJava --no-daemon
            if ($LASTEXITCODE -eq 0) {
                Write-Ok 'Build succeeded (BUILD SUCCESSFUL).'
                Add-Summary -Name 'Pre-build (compileJava)' -Status 'succeeded'
            } else {
                Write-Err "Build exited with code $LASTEXITCODE. Check output above."
                Add-Summary -Name 'Pre-build (compileJava)' -Status "FAILED (exit $LASTEXITCODE)"
            }
        } catch {
            Write-Warn "Build step failed: $_"
            Add-Summary -Name 'Pre-build (compileJava)' -Status 'FAILED (exception)'
        }
    }
}

# ---------------------------------------------------------------------------
# Summary table
# ---------------------------------------------------------------------------

Write-Banner 'Setup Summary'

$colW = 36
Write-Host ('+' + ('-' * ($colW + 2)) + '+' + ('-' * 32) + '+') -ForegroundColor Cyan
Write-Host ('| ' + 'Dependency'.PadRight($colW) + '| ' + 'Status'.PadRight(30) + ' |') -ForegroundColor Cyan
Write-Host ('+' + ('-' * ($colW + 2)) + '+' + ('-' * 32) + '+') -ForegroundColor Cyan

foreach ($row in $script:Summary) {
    $name   = $row.Name.PadRight($colW)
    $status = $row.Status
    if ($status -match 'FAILED') {
        $color = 'Red'
    } elseif ($status -match 'DryRun|would') {
        $color = 'Magenta'
    } elseif ($status -match 'skipped|Skipped') {
        $color = 'DarkGray'
    } elseif ($status -match 'already') {
        $color = 'Green'
    } else {
        $color = 'Yellow'
    }
    Write-Host ('| ' + $name + '| ') -NoNewline -ForegroundColor Cyan
    Write-Host $status.PadRight(30) -NoNewline -ForegroundColor $color
    Write-Host ' |' -ForegroundColor Cyan
}

Write-Host ('+' + ('-' * ($colW + 2)) + '+' + ('-' * 32) + '+') -ForegroundColor Cyan

Write-Host ''
Write-Host 'Next step:' -ForegroundColor White
Write-Host '  .\gradlew.bat runClient' -ForegroundColor Green
Write-Host ''
Write-Host 'In-game:  /conjure new a glowing ember dagger' -ForegroundColor White
Write-Host ''
