# MinisTermux — Monolithic Single-APK Termux Fork for OpenMinis

> **One APK, everything included.** Terminal + API + X11 + Boot + Tasker + Widget + Float + Styling — all in one signed package.

## 🎯 What is this?

A **fork of Termux** repackaged as a **single monolithic APK** (`com.ministermux`) with:
- All 7 official addons merged in-process (no IPC, no sharedUserId)
- Pre-installed bootstrap with 3000+ packages + custom Minis tooling
- 72 MCP servers, 40+ skills ready for agents
- Native Minis bridge: WebSocket + Intent API + ContentProvider + File sync

## 📦 What's Inside the APK

| Component | Source | Status |
|-----------|--------|--------|
| **Terminal Core** | `termux/termux-app` | ✅ Merged |
| **Termux:API** + **MinisAgentApi** | `termux/termux-api` | ✅ Extended |
| **Termux:Boot** | `termux/termux-boot` | ✅ Merged |
| **Termux:X11** | `termux/termux-x11` | ✅ Merged |
| **Termux:Tasker** | `termux/termux-tasker` | ✅ Merged |
| **Termux:Widget** | `termux/termux-widget` | ✅ Merged |
| **Termux:Float** | `termux/termux-float` | ✅ Merged |
| **Termux:Styling** | `termux/termux-styling` | ✅ Merged |

### Pre-installed Packages (Bootstrap)
```
Core: bash, zsh, fish, git, curl, wget, rsync, openssh, python3.11, nodejs-20, golang, rust, clang, cmake, ninja
MCP: jina-reader, fetch, playwright, browser-use, github-repo-mcp, openalex, semantic-scholar, tavily, firecrawl, perplexity, graphify-mcp, filesystem, git
Skills: web-research-master, research-engine, exa-search, web-search, github-actions-codespaces, android-dev, subagent-research-toolkit, vibe-coding, planning-with-files, context-mode, omnitool-workstation, research-master, mcp-manager, generative-ui-minis, acode-ide-integration, android-integration, minis-native, mesh-coordinator
AI/ML: llama.cpp, whisper.cpp, onnxruntime, torch-cpu, tflite-runtime, numpy, scipy, pandas, scikit-learn
Network: cloudflared, tailscale, frpc, nginx, sshd, pm2
```

## 🔌 Minis Integration

### 1. WebSocket Bridge (Real-time)
```
ws://localhost:8765
```
Messages: `exec`, `api_call`, `mcp_call`, `skill_run`, `fs_read`, `fs_write`, `create_session`

### 2. Intent API (Fire-and-forget)
```kotlin
Intent("com.ministermux.RUN_COMMAND").apply {
    putExtra("command", "python3 script.py")
    putExtra("args", arrayListOf("arg1", "arg2"))
    putExtra("session_id", "agent-123")
    putExtra("callback_action", "com.openminis.AGENT_RESULT")
}
```

### 3. ContentProvider (File Bridge)
```
content://com.ministermux.minisagent/files/<path>
content://com.ministermux.minisagent/list?path=<dir>
content://com.ministermux.minisagent/read?path=<file>
```

### 4. File System Sync
```
Minis /var/minis/workspace  ←→  Termux /data/data/com.ministermux/files/home/workspace
Minis /var/minis/shared     ←→  Termux /data/data/com.ministermux/files/home/shared
Minis /var/minis/memory     ←→  Termux /data/data/com.ministermux/files/home/memory
```

## 🚀 Quick Start

### For Users
1. Download `ministermux-<arch>-release.apk` from [Releases](https://github.com/ministermux/ministermux/releases)
2. Install (enable "Install unknown apps" if prompted)
3. Open app → Grant all permissions → Wait for bootstrap extraction
4. Open OpenMinis app → Settings → "Connect MinisTermux Runtime"

### For Developers
```bash
# Clone
git clone https://github.com/ministermux/ministermux.git
cd ministermux

# Build locally (requires Android SDK + NDK)
./gradlew assembleRelease

# APKs in core/build/outputs/apk/release/
```

## 🛠️ Build Requirements

| Tool | Version |
|------|---------|
| JDK | 21 (Temurin) |
| Android SDK | API 34 |
| Android NDK | r26c (26.1.10909125) |
| Gradle | 8.4 (wrapper provided) |

## 🔐 Signing (Release)

Set these GitHub Secrets:
- `SIGNING_KEYSTORE_BASE64` — base64 encoded `.keystore` file
- `SIGNING_KEY_ALIAS` — key alias (default: `ministermux`)
- `SIGNING_KEY_PASSWORD` — key password
- `SIGNING_STORE_PASSWORD` — keystore password
- `GPG_PRIVATE_KEY` — for APT repo signing (optional)
- `GPG_PASSPHRASE` — GPG passphrase (optional)

Generate keystore:
```bash
keytool -genkeypair -alias ministermux -keyalg RSA -keysize 2048 -validity 10000 \
  -keystore ministermux.keystore -storepass <password> -keypass <password> \
  -dname "CN=MinisTermux, OU=MinisTermux, O=MinisTermux, L=City, ST=State, C=TR"
```

## 📁 Project Structure

```
ministermux/
├── .github/workflows/ministermux-build.yml  # CI/CD pipeline
├── core/                                     # Main app module (application)
│   ├── src/main/
│   │   ├── java/com/ministermux/            # Kotlin source
│   │   ├── AndroidManifest.xml              # Merged manifest
│   │   ├── res/xml/                         # network_security, file_paths, widget
│   │   └── assets/bootstrap/                # Bootstrap ZIPs (from CI)
├── api/                                      # Termux:API + MinisAgentApi
├── boot/                                     # Termux:Boot
├── x11/                                      # Termux:X11
├── tasker/                                   # Termux:Tasker
├── widget/                                   # Termux:Widget
├── float/                                    # Termux:Float
├── styling/                                  # Termux:Styling
├── bootstrap/                                # Bootstrap build scripts
├── termux-packages/                          # Custom package repo (submodule)
├── settings.gradle.kts                       # Monorepo config
├── build.gradle.kts                          # Root config
├── gradle.properties                         # Build properties
└── gradlew                                   # Gradle wrapper
```

## 🔄 CI/CD Pipeline

On tag push (`v*`):
1. **build-packages** → Build 3000+ packages + bootstrap (Docker)
2. **build-apk** → Compile monolithic APK (universal + 4 arch)
3. **release** → Create GitHub Release with all APKs
4. **deploy-apt-repo** → Deploy `packages.ministermux.dev` to GitHub Pages
5. **security-scan** → MobSF static analysis
6. **test-emulator** → Install & test on Android emulator

## 📱 Supported Architectures

| Flavor | ABI | APK Size |
|--------|-----|----------|
| universal | arm64-v8a, armeabi-v7a, x86_64, x86 | ~180 MB |
| aarch64 | arm64-v8a | ~120 MB |
| arm | armeabi-v7a | ~110 MB |
| x86_64 | x86_64 | ~115 MB |
| x86 | x86 | ~110 MB |

## 🌐 Custom APT Repository

After first release, add to Termux:
```bash
echo "deb https://ministermux.github.io/ministermux/apt/ stable main" > $PREFIX/etc/apt/sources.list.d/ministermux.list
pkg update
```

## 🤝 Contributing

1. Fork the monorepo
2. Create feature branch
3. Make changes to relevant module
4. Test locally with `./gradlew assembleDebug`
5. Submit PR

## 📄 License

GPL-3.0-only (same as Termux upstream)

## 🙏 Credits

- **Termux Team** — Original terminal emulator and packages
- **PRoot** — User-space chroot implementation
- **OpenMinis** — Agent framework this integrates with

---

**Built for agents, by agents.** 🤖