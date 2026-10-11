$p = "protocol=https`nhost=github.com`n`n"
$c = $p | git credential fill
$token = ""
foreach ($l in $c) {
    if ($l -match "^password=(.+)$") {
        $token = $matches[1]
    }
}
$headers = @{
    "Authorization" = "Bearer $token"
    "User-Agent" = "PowerShell"
    "Accept" = "application/vnd.github.v3+json"
}
$runs = Invoke-RestMethod -Uri "https://api.github.com/repos/dwaraa/TAMILDHOOL-CS3/actions/runs" -Headers $headers
$latest = $runs.workflow_runs[0]
Write-Output "Run ID: $($latest.id), Status: $($latest.status), Conclusion: $($latest.conclusion), Commit: $($latest.head_sha)"
if ($latest.id) {
    $jobs = Invoke-RestMethod -Uri "https://api.github.com/repos/dwaraa/TAMILDHOOL-CS3/actions/runs/$($latest.id)/jobs" -Headers $headers
    foreach ($j in $jobs.jobs) {
        Write-Output "Job: $($j.name) [$($j.status)]"
        foreach ($s in $j.steps) {
            $state = $s.conclusion
            if (-not $state) { $state = $s.status }
            Write-Output "  Step: $($s.name) -> $state"
        }
    }
}
