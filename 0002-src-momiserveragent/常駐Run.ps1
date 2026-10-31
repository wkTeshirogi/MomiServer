echo 'building......'
&".\build.bat"
echo 'running......'
$agentTestDir = Join-Path $env:LOCALAPPDATA "MomiServer\agent-test"
.\dist\momiserver-agent.exe run --state-dir $agentTestDir --allow-insecure-localhost --allow-insecure-mqtt-localhost