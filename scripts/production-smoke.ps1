[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$ApiBaseUrl,
    [string]$AdminApiKey
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$base = $ApiBaseUrl.TrimEnd('/')
$uri = [Uri]$base
if (-not $uri.IsAbsoluteUri -or $uri.Scheme -notin @('http', 'https') -or $uri.UserInfo -or $uri.Query -or $uri.Fragment) {
    throw 'ApiBaseUrl deve ser uma URL HTTP(S) sem credenciais, query ou fragmento.'
}
$client = [System.Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromSeconds(30)
function Test-ReadEndpoint([string]$Path, [int[]]$Expected, [string]$Key = '') {
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Get, "$base$Path")
    try {
        if ($Key) { [void]$request.Headers.TryAddWithoutValidation('X-Admin-Key', $Key) }
        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        try {
            $code = [int]$response.StatusCode
            if ($code -notin $Expected) { throw "FAIL $Path status=$code" }
            if ($Path -eq '/actuator/health') {
                $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
                if ($body.status -ne 'UP') { throw 'Healthcheck não está UP.' }
            }
            Write-Output "PASS GET $Path status=$code"
        } finally { $response.Dispose() }
    } finally { $request.Dispose() }
}
try {
    foreach ($path in @('/actuator/health', '/api/candidates', '/api/pollsters', '/api/polls/latest', '/api/aggregates', '/api/election/source-status', '/api/election/president', '/api/election/president/projection')) {
        Test-ReadEndpoint $path @(200)
    }
    Test-ReadEndpoint '/api/pre-election-freeze' @(200, 204)
    Test-ReadEndpoint '/api/admin/data-quality' @(401)
    if ($AdminApiKey) { Test-ReadEndpoint '/api/admin/data-quality' @(200) $AdminApiKey }
    else { Write-Output 'SKIP chave correta: AdminApiKey não informada.' }
} finally { $client.Dispose() }
