# Changelog

All notable changes to this project will be documented in this file.

## [v1.2.0]

### Chore
- Update paqet submodule to v1.0.0-alpha.21 (wire framing, UDP decoupled writes, socket & buffer optimizations, native SOCKS5 server, binary wire protocol)
- Update hev-socks5-tunnel submodule to v2.19.3+ (ICMP Echo, tunnel offloads, idle memory release, JNI status APIs, stability fixes)
- Update build tooling and dependencies
- Target Android API 37

### Fix
- Fix Save button text color in config editor

## [v1.1.0]

### Chore
- Update build tooling and dependencies
- Update paqet submodule to v1.0.0-alpha.19 (configurable smux keepalive settings, iterator off-by-one fix)
- Update hev-socks5-tunnel submodule (TCP fastopen support, HevConfig 2.15.0, LwIP and HevSocks5Core updates)

### Refactor
- Refine VPN foreground service manifest
- Settings UI polish and code cleanups

## [v1.0.4]

### Chore
- Update paqet submodule to v1.0.0-alpha.18 (UDP DialContext timeout, UDP strm read/write error handling fixes, MIPS softfloat ABI for CI builds)

## [v1.0.3]

### Feat
- Auto-select newly created or imported profiles as active profile
- Add confirmation dialog when deleting profiles to prevent accidental deletion
- Add KCP configuration options: `conn`, `mode`, `mtu`, and manual mode parameters (`nodelay`, `interval`, `resend`, `nocongestion`, `wdelay`, `acknodelay`)

### Fix
- Fix API level 26 compatibility issues for Android 24+
- Add 16 KB page size alignment flags to paqet and tcpdump binaries for Android 15+ compatibility

## [v1.0.2]

### Feat
- Add Quick Settings tile to turn VPN on/off from the notification center

### Fix
- Add missing and remove unintended paqet and tcpdump build

## [v1.0.1]

### Feat
- Add tcpdump viewer

### Fix
- Update application state based on VPN events
- Restart paqet while user switch the profile

### Refactor
- Improve the log viewer
- Improve the asset stripping based on abi

### Chore
- Changed paqet submodule URL to actual repository
