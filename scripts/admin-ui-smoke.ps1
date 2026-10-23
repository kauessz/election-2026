[CmdletBinding()]
param([Parameter(Mandatory)][string]$WebBaseUrl, [switch]$Enabled)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$client = [System.Net.Http.HttpClient]::new()
try {
    foreach ($path in @('/admin/pesquisas', '/admin/institutos', '/admin/importacao', '/admin/freeze')) {
        foreach ($rsc in @($false, $true)) {
            $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Get, "$($WebBaseUrl.TrimEnd('/'))$path")
            if ($rsc) { [void]$request.Headers.TryAddWithoutValidation('RSC', '1') }
            try {
                $response = $client.SendAsync($request).GetAwaiter().GetResult()
                try {
                    $expected = if ($Enabled) { 200 } else { 404 }
                    if ([int]$response.StatusCode -ne $expected) { throw "FAIL $path rsc=$rsc status=$([int]$response.StatusCode)" }
                    Write-Output "PASS $path rsc=$rsc status=$expected"
                } finally { $response.Dispose() }
            } finally { $request.Dispose() }
        }
    }
} finally { $client.Dispose() }
