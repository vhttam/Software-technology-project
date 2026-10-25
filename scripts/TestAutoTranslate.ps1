$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Send-JsonPost($uri, $headers, $obj) {
    $json = $obj | ConvertTo-Json -Compress
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    return Invoke-RestMethod -Uri $uri -Method POST -Headers $headers -Body $bytes -ContentType "application/json; charset=utf-8"
}

# 1. Login
$loginResp = Send-JsonPost "http://localhost:8080/api/v1/auth/login" @{} @{ username = "admin"; password = "Admin12345!" }
$token = $loginResp.data.accessToken
Write-Host "1. Logged in successfully."

$headers = @{
    "Authorization" = "Bearer $token"
}

# 2. Create POI
$poiResp = Send-JsonPost "http://localhost:8080/api/v1/pois" $headers @{ 
    name = "Ho Guom"
    description = "Trai tim cua Thu do Ha Noi voi Thap Rua co kinh" 
}
$poiId = $poiResp.data.poiId
Write-Host "2. Created POI: $poiId (Ho Guom)"

# 3. Create Content under POI
$contentResp = Send-JsonPost "http://localhost:8080/api/v1/pois/$poiId/contents" $headers @{
    title = "Truyen thuyet Ho Guom"
    sourceLang = "vi"
    textContent = "Ho Guom la danh thang noi tieng gan lien voi truyen thuyet vua Le Loi tra guom than cho Rua Vang."
}
$contentId = $contentResp.data.contentId
Write-Host "3. Created Content: $contentId"

# 4. Create Narration Job
$idempKey = [System.Guid]::NewGuid().ToString()
$jobHeaders = @{
    "Authorization" = "Bearer $token"
    "Idempotency-Key" = $idempKey
}
$jobResp = Send-JsonPost "http://localhost:8080/api/v1/jobs" $jobHeaders @{
    contentId = $contentId
    targets = @(
        @{ lang = "vi"; voiceId = "vi-VN-Standard-A" },
        @{ lang = "en"; voiceId = "en-US-Journey-F" }
    )
}
$jobId = $jobResp.data.jobId
Write-Host "4. Created Narration Job: $jobId"

# 5. Wait for worker processing
Write-Host "Waiting for LocalJobWorker to auto-translate and synthesize speech..."
for ($i = 0; $i -lt 15; $i++) {
    Start-Sleep -Seconds 1
    $getJob = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/jobs/$jobId" -Method GET -Headers $headers
    if ($getJob.data.status -eq 'COMPLETED') {
        break
    }
}

Write-Host "5. Job status: $($getJob.data.status)"

# 6. Check single narration endpoint for vi
$viNarr = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/client/pois/$poiId/narrations/vi" -Method GET
Write-Host "6. Vietnamese Narration:"
Write-Host "   Subtitle: $($viNarr.data.subtitle)"
Write-Host "   AudioUrl: $($viNarr.data.audioUrl)"

# 7. Check single narration endpoint for en (verify auto-translation!)
$enNarr = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/client/pois/$poiId/narrations/en" -Method GET
Write-Host "7. English Narration (Auto-translated):"
Write-Host "   Subtitle: $($enNarr.data.subtitle)"
Write-Host "   AudioUrl: $($enNarr.data.audioUrl)"

# 8. Check audio file size
$curlVi = curl.exe -s -I $viNarr.data.audioUrl
Write-Host "8. Audio vi response:"
Write-Host ($curlVi -join "`n")
