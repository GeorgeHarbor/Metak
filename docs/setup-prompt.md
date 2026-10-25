# Android TV setup with Metak and optional apps

Paste the prompt below into a coding assistant with shell access on a computer connected to the same network as your TV or set-top box.

Before starting, enable Developer options on the device by selecting **Build** seven times under Settings → About, then enable USB debugging and Network/ADB debugging if available. Menu names vary by device. Keep the TV on so you can accept the debugging authorization prompt.

This prompt targets Android TV and Google TV devices. Do not assume it applies to Samsung Tizen, LG webOS or Fire OS.

---

## The prompt

I want to debloat my Android TV or Google TV device and use **Metak** as its home screen. Offer **SmartTube Harbor**, **Nuvio TV** and **Spotify TV Mod** as separate optional installations. Wait for my explicit choice for each app; declining one must not affect the others. Do not download, install, update or configure an optional app unless I accept its offer. Preserve existing installations when I decline.

**Start by asking me to type my TV or set-top box model name.** If it is a separate box, ask for the box model and the connected TV model. Then work on the real device, verify your changes, and complete the installation.

Install Metak and any accepted optional apps from their specified GitHub release sources, preferring the **latest published stable release**. Explain any beta-only source and ask before using it. **Do not build apps from source.**

### Ground rules

- Inspect applicable workspace instructions and device capabilities before changing anything.
- Ask about preferences together in a short checklist: apps to keep, voice assistant, casting, firmware updates, dock apps and order, and weather location.
- Complete routine inspection, downloads, installation and verification without repeatedly asking permission.
- Make reversible changes. Disable unwanted packages with `pm disable-user --user <current-user>` rather than uninstalling system apps.
- Keep a record of every change and its undo command.
- Do not factory-reset, root, unlock the bootloader or flash firmware.
- If the device runs an unsupported operating system or cannot install the required apps, explain the limitation and complete whatever supported work remains.
- Do not assume helper scripts, configuration keys or documentation from another launcher repository exist here. Verify instructions against the actual apps and device.

### Connect and identify the device

- Ask for the model first, then find the device using existing ADB connections, local discovery or an IP address I provide.
- If multiple devices are connected, identify the intended device and use its explicit ADB serial for every command.
- If I provide a remote or Tailscale address, use it instead of scanning the local network.
- Tell me when to accept the “Allow USB debugging?” prompt.
- Use the device’s supported connection method. If port 5555 is unavailable, check for Wireless debugging and use pairing if offered. Do not assume every device supports `adb tcpip 5555`.
- Record manufacturer, model, Android version, SDK level, CPU architecture, current user, screen resolution and density.
- If your execution environment blocks local network access, explain the actual connection problem and provide a concrete workaround. Do not treat a failed connection as proof of a particular cause without checking.
- Ask whether any USB devices are connected, including whether Ethernet uses a USB adapter. Some devices interrupt USB accessories when debugging is enabled; confirm the working network connection before proceeding.

### Remotes without a Home button

- Ask: **“Does your TV/STB remote have a working Home button?”** If it does not, include setting up a replacement Home shortcut in this session.
- Ask me to pick a spare physical button and choose single press or long press. Explain that its existing action will be replaced for that press type. Preserve power, volume, D-pad, OK and Back for normal use.
- Do not require me to know the button's technical name. Start listening for input on the selected TV/STB, then tell me: **“Press the button you want to use as Home once.”** Capture its press and release with `getevent -lt`, stop listening after identification, and record the input device, scancode and Android keycode where available. Use the active device keylayout to interpret the event; Linux key names and Android keycodes are different.
- **Wait for my physical button press before proceeding with any dependent configuration.** Start the input listener before asking me to press the button. Keep the task pending until a real press and release are captured. If a bounded capture window expires, tell me you are still waiting and restart capture; do not treat a timeout, silence or an injected event as my selection. Do not choose a default button. Stop waiting only when I press a button, explicitly cancel, or a confirmed device/connection limitation prevents capture.
- Identify the button before remapping it. Do not hardcode F7, scancode 65, a device descriptor or a mapping from another TV. If the captured event is ambiguous, ask me to press the same button again and confirm its identity before changing the mapping.
- **Install and configure Key Mapper as part of the setup when a replacement Home button is needed.** After the state backup, Metak installation and button identification, install the latest compatible official release APK from [Key Mapper](https://github.com/keymapperorg/KeyMapper/releases). Reuse or compatibly update an existing installation while preserving its rules and data. Explain and enable the required accessibility service, preserving existing enabled services, then create and save the chosen button/press → **Open Metak** rule. Do not stop at recommending the app or giving me manual setup instructions when you can perform the supported steps. Metak itself currently has no global button-remapping service. Do not root the device or modify system keylayout files.
- Back up existing mappings, enabled accessibility services and relevant settings before changing them. Preserve unrelated services. If temporary file access is needed to import a mapping, restore its original permission state after import. Save the rule and an undo script.
- Prefer launching `app.metak/.HomeActivity` directly or the remapper's **Open Metak** action. Android's generic Home action may still open the manufacturer's launcher. Do not disable a stock launcher or TV input service simply to make this shortcut work.
- Verify the remapper is enabled and its accessibility service is actually bound. Match the input device using the remapper's supported device classification; a raw descriptor may not match an internal remote. If broadening the match to any input device is necessary, explain that it also affects the same key on other connected controllers/keyboards.
- Open Settings, listen again, and ask me to press the physical button. Check the focused activity to confirm that the press opens Metak. Repeat from another app and after reboot; reconnect and handle any renewed ADB authorization. Saved settings, a bound service or an injected key alone do not prove the physical shortcut works.
- Check volume-key repeat and other essential remote controls after enabling a key-filtering service. If a check fails, investigate or restore the original mapping and settings. Report firmware limitations and incomplete tests honestly.
- Include the chosen button, press type, mapping tool/version, physical test results, reboot result and undo instructions in the handover. A replacement button opens Metak on demand; it does not by itself change the firmware's default launcher or power-on behavior.

### Back up before making changes

Create a dated backup folder containing:

- Installed packages, package paths and disabled packages.
- Device properties and global, secure and system settings.
- Current HOME handlers and default launcher.
- Relevant package versions and installation details.
- Screenshots of the original home screen.
- A change log and restore script.

Record the original enabled state of any package you change. The restore script must undo this session’s changes without enabling packages that were already disabled. Save original values before changing settings and handle originally absent values correctly when restoring them.

### Debloat carefully

- Inspect installed packages and propose a concise list of removable clutter: manufacturer advertising, telemetry, promotional content, unwanted preloads and duplicate services.
- Ask which streaming apps, voice assistant, casting features and update services I want to keep before disabling anything in those categories.
- Investigate unfamiliar packages with package metadata and `dumpsys package`; do not infer their purpose solely from their names.
- Never launch factory, production, calibration or service-menu apps to investigate them.
- Preserve Android core services, System UI, Settings, permission management, package installation, providers needed by the system, Google Play services and Play Store.
- Preserve remote control, Bluetooth pairing, keyboard, HDMI inputs, tuner, source selection, audio and networking services.
- Execute package changes individually and verify the resulting package state, rather than relying only on command exit codes.
- Disable in small batches. Check Home, Settings, app launching, remote navigation and inputs between batches.
- If firmware rejects package disabling, stop retrying and explain the limitation. Do not escalate to uninstalling system packages.
- Reboot after the final batch and check whether packages return automatically. Document that behavior.
- Offer ad-blocking DNS only as an optional extra. Ask whether the router already provides domain filtering or domain-based routing before proposing Private DNS, since it can bypass those rules. Explain that DNS filtering does not reliably block YouTube video ads. Record original settings and undo commands if I choose it.

### Download the latest stable releases

- Install **Metak** from its latest published stable GitHub release. Apply these release checks to accepted optional app installations only; do not download optional apps before I accept. Do not build apps from source.
- Use **https://github.com/GeorgeHarbor/Metak/releases/latest** for Metak.
- If I accept SmartTube Harbor, use **https://github.com/GeorgeHarbor/SmartTube/releases**. If I accept Nuvio TV, use **https://github.com/NuvioMedia/NuvioTV/releases**. Check the current release channel and metadata rather than assuming the newest visible release is stable.
- Query release metadata at installation time; do not hardcode versions or guess APK asset URLs.
- Exclude drafts and prereleases unless I explicitly request them.
- Select an actual release APK compatible with the device’s Android version and CPU architecture. Do not mistake GitHub source archives for installable packages.
- Record each release version, release page, APK download URL, local file checksum and installed package name. Compare published checksums if supplied.
- Inspect downloaded APK metadata before installation, including package name, version and minimum SDK.
- Preserve existing app data. Prefer a compatible update. If installation fails because of a signing mismatch or would require a downgrade, explain the conflict and ask before uninstalling or resetting anything.
- If the latest stable release is incompatible with the device, explain why and ask before using an older release. Do not fall back to building from source.
- If no suitable release APK exists, report the limitation and complete the remaining supported work.

### Install Metak

- Use **Metak**, rather than building or substituting another launcher.
- Read its current README and release notes for supported installation and configuration behavior.
- Check for an existing installation and preserve its data and signing compatibility.
- Install the compatible release APK and launch Metak while the stock launcher remains available.
- Configure supported preferences through Metak’s existing settings or documented mechanisms. Ask for favorite apps and their order, dock size within its supported range, and weather city.
- Do not edit source or rebuild the app to change preferences. If a requested preference is unavailable in the release, explain the limitation.
- Avoid unrelated visual redesigns or new features.
- Verify app discovery, D-pad navigation, focus visibility, app launching, returning Home and Settings access.
- List HOME handlers and set Metak as the default using the device’s supported method.
- Press Home and inspect the focused activity to confirm that Metak actually opens. A successful command alone is insufficient.
- Prefer keeping the stock launcher enabled if Metak works reliably as the default.
- If firmware intercepts Home, investigate the exact device behavior before disabling its launcher or setup wizard. Check dependencies and potential loss of saved Wi-Fi networks first. Do not disable either unless a working recovery path is recorded.
- Verify Home again after a reboot.

### Optional: install SmartTube Harbor

- Ask me: **“Do you want to install SmartTube Harbor?”** Wait for my answer; silence or a timeout is not consent. If I decline, skip its download, installation, update and configuration, leaving any existing version untouched.
- If I agree, install **SmartTube Harbor**, the Harbor fork, rather than substituting upstream SmartTube. Its official repository currently describes its rolling builds as beta, even when GitHub marks them non-prerelease. Explain that distinction and obtain explicit agreement to use a beta before downloading one. If I want stable only and none is available, leave Harbor unchanged and continue the rest of setup.
- Follow the verified Harbor repository’s release instructions and the release-download rules above. Do not use APK mirrors.
- Inspect existing SmartTube installations, package names, versions and signing compatibility.
- Prefer a compatible update that preserves accounts, settings and history. Ask before any uninstall or data reset.
- Record the repository, release version and package name installed.
- Launch it and verify that browsing and video playback work.
- Let me complete account sign-in on the TV; do not collect credentials.
- Ask whether I want SmartTube Harbor in Metak’s dock and at which position; configure it only as chosen using the installed release’s supported controls.

### Optional: install Nuvio TV

- Ask me: **“Do you want to install Nuvio TV from its official GitHub releases?”** Wait for my answer; silence or a timeout is not consent. If I decline, skip its download, installation, update and configuration, leaving any existing version untouched.
- If I agree, use **https://github.com/NuvioMedia/NuvioTV/releases** and resolve the latest published stable release from its metadata. Do not substitute the mobile or desktop app. Ignore drafts and prereleases unless I explicitly choose a beta after you explain it.
- Select the released Android TV APK compatible with the device's SDK and architecture. Inspect its actual package name and version, record its source and SHA-256, and verify any published digest before installing. Do not build from source or use APK mirrors.
- Inspect any existing Nuvio installation and preserve accounts, settings and library data. Use a compatible update; ask before any uninstall, data reset, signing-conflict workaround or downgrade.
- Open Nuvio TV and verify remote navigation. Let me complete sign-in and connect my own media sources directly on the TV. Verify playback if a source is available; otherwise record that playback is untested. Ask whether I want it in Metak's dock and at which position.

### Optional: install Spotify TV Mod

- Ask me: **“Do you want to install Spotify TV Mod from GeorgeHarbor/tv-apks?”** Explain that this is a user-provided modified APK, not an official Spotify release. Wait for my answer; silence or a timeout is not consent. If I decline, skip this step and preserve my existing Spotify installation.
- If I agree, download the APK from `https://github.com/GeorgeHarbor/tv-apks/releases/latest/download/spotify-tv.apk` and its metadata and checksum from the same release: `spotify-tv.json` and `SHA256SUMS`. Record the resolved release and verify the APK's SHA-256 against both files before installation.
- Inspect the APK's actual package name, version, minimum SDK and architecture compatibility, and compare them with the metadata and device. Check any existing Spotify installation and its version and signing compatibility first.
- Install a compatible update over ADB using the selected device's explicit serial and `install -r`. If a signing conflict or downgrade would require uninstalling or clearing data, stop and ask me before doing so. Do not remove my existing Spotify app automatically.
- Open Spotify, let me sign in directly on the TV, and verify navigation and playback. Ask whether I want it in Metak's dock and at which position. Record the installed version, source, checksum and verification results in the handover.

### Check Continue Watching honestly

- Inspect the current Metak documentation and, if necessary, read its source to understand how it obtains Continue Watching entries. Reading source is allowed; building from source is not.
- Test Continue Watching only for supported apps I accepted for setup, or existing apps I explicitly agree to test. If neither Nuvio TV nor SmartTube Harbor is selected, skip their playback tests and record that Continue Watching was not tested. Never install an app solely to run this check.
- Check TV listings permissions, the actual installed package names and the Android TV Watch Next provider.
- For each selected supported app (SmartTube Harbor or Nuvio TV), play part of an available video, leave playback, return to Metak and check whether a usable entry appears. If no configured source is available, record the test as incomplete.
- Verify the card’s title, artwork and resume behavior if an entry is published.
- Do not assume SmartTube Harbor publishes Watch Next entries simply because Metak accepts its package name.
- If no SmartTube entries appear, distinguish missing publication from permission errors, provider query failures, filtering and hidden cards. An empty or failed shell query alone is not sufficient to prove which app is responsible.
- Report unsupported integration plainly. Do not claim Continue Watching works without testing it.
- If support requires app changes, explain the missing behavior and proposed scope. Do not expand this release-only installation task into source changes or custom builds.

### Verify on the real device

- Capture and inspect screenshots after meaningful changes.
- Use explicit device targeting when injecting remote keys or capturing the screen.
- If protected video produces a blank capture, use activity state and my confirmation to verify playback.
- Check display size and density overrides before attributing clipped layouts to Metak. Record original values before changing overrides.
- Verify all applicable functions:
  - Metak opens with Home and after reboot.
  - Dock apps and the app grid launch correctly.
  - Each accepted optional app browses and plays content where sign-in and media sources are available; record missing prerequisites instead of claiming playback was verified.
  - Settings, Wi-Fi/Ethernet and Bluetooth still work.
  - HDMI, Live TV and source selection still work.
  - Volume, power, input and dedicated app buttons retain their expected behavior.
- Ask me to test physical remote buttons where injected keys cannot prove the behavior.
- If a change breaks a required function, restore the relevant change and verify recovery before proceeding.

### Finish and hand over

Provide:

- A concise report of what was installed, configured and disabled.
- Exact installed versions and links to their official release pages.
- The backup location and a working restore script.
- A repeatable download-and-install script for Metak and only the optional apps I selected, using their specified release sources. Require explicit opt-in for optional apps and beta builds on future runs too. Check compatibility, target the intended device explicitly, and stop on installation errors without automatically uninstalling apps or clearing data.
- Instructions for changing dock apps, weather location and returning to the original launcher, using only features supported by the installed Metak release.
- Update instructions for each optional app installed, and a record of apps I declined.
- Any verified limitations, especially Continue Watching support.

Ask whether I want debugging left enabled for development or turned off. If I choose to turn it off, complete all ADB-dependent work first, guide me through disabling network and USB debugging, and confirm that normal networking and USB accessories still work. Verify that the network ADB endpoint is closed where practical; do not interpret an unrelated network error as proof.

Do not describe the job as complete until the installed apps and launcher behavior have been verified. Clearly identify anything that could not be tested or completed.
