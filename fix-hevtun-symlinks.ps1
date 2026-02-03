# Fix Git symlinks (Windows stores them as path-only files) in hev-socks5-tunnel submodules
$hevtun = "$PSScriptRoot\hev-socks5-tunnel"

# hev-task-system
$base = "$hevtun\third-part\hev-task-system"
$pairs = @(
  @("include\hev-object-atomic.h", "src\lib\object\hev-object-atomic.h"),
  @("include\hev-object.h", "src\lib\object\hev-object.h"),
  @("include\hev-task.h", "src\kern\task\hev-task.h"),
  @("include\hev-task-system.h", "src\kern\core\hev-task-system.h"),
  @("include\hev-task-mutex.h", "src\kern\sync\hev-task-mutex.h"),
  @("include\hev-task-io.h", "src\lib\io\basic\hev-task-io.h"),
  @("include\hev-task-io-poll.h", "src\lib\io\poll\hev-task-io-poll.h"),
  @("include\hev-task-io-socket.h", "src\lib\io\socket\hev-task-io-socket.h"),
  @("include\hev-task-io-pipe.h", "src\lib\io\pipe\hev-task-io-pipe.h"),
  @("include\hev-task-dns.h", "src\lib\dns\hev-task-dns.h"),
  @("include\hev-task-cond.h", "src\kern\sync\hev-task-cond.h"),
  @("include\hev-task-channel.h", "src\kern\itc\hev-task-channel.h"),
  @("include\hev-task-channel-select.h", "src\kern\itc\hev-task-channel-select.h"),
  @("include\hev-task-call.h", "src\kern\task\hev-task-call.h"),
  @("include\hev-memory-allocator.h", "src\mem\api\hev-memory-allocator-api.h"),
  @("include\hev-circular-buffer.h", "src\lib\io\buffer\hev-circular-buffer.h")
)
foreach ($pair in $pairs) {
  $dst = Join-Path $base $pair[0]
  $src = Join-Path $base $pair[1]
  if (Test-Path $src) { Get-Content $src -Raw | Set-Content $dst -NoNewline }
}

# yaml
$yamlBase = "$hevtun\third-part\yaml"
$yamlSrc = Join-Path $yamlBase "src\yaml.h"
$yamlDst = Join-Path $yamlBase "include\yaml.h"
if (Test-Path $yamlSrc) { Get-Content $yamlSrc -Raw | Set-Content $yamlDst -NoNewline }

# hev-socks5-tunnel src/core (hev-socks5-core submodule)
$coreBase = "$hevtun\src\core"
$coreHeaders = @(
  "hev-rbtree.h", "hev-socks5.h", "hev-socks5-authenticator.h", "hev-socks5-client.h",
  "hev-socks5-client-tcp.h", "hev-socks5-client-udp.h", "hev-socks5-logger.h",
  "hev-socks5-misc.h", "hev-socks5-proto.h", "hev-socks5-server.h", "hev-socks5-tcp.h",
  "hev-socks5-udp.h", "hev-socks5-user.h"
)
foreach ($h in $coreHeaders) {
  $src = Join-Path $coreBase "src\$h"
  $dst = Join-Path $coreBase "include\$h"
  if (Test-Path $src) { Get-Content $src -Raw | Set-Content $dst -NoNewline }
}
