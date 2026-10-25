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
Write-Host "1. Logged in successfully. Access token received."

$headers = @{
    "Authorization" = "Bearer $token"
}

# 2. Create POI
$poiResp = Send-JsonPost "http://localhost:8080/api/v1/pois" $headers @{ name = "Chua Mot Cot"; description = "Di tich lich su van hoa Ha Noi" }
$poiId = $poiResp.data.poiId
Write-Host "2. Created POI: $poiId"

# 3. Create Content under POI
$contentResp = Send-JsonPost "http://localhost:8080/api/v1/pois/$poiId/contents" $headers @{
    title = "Gioi thieu Chua Mot Cot"
    sourceLang = "vi"
    textContent = "Chua Mot Cot la cong trinh kien truc doc dao thoi Ly tai Ha Noi Viet Nam."
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
Write-Host "4. Created Narration Job: $jobId (Status: $($jobResp.data.status))"

# 5. Wait for worker processing
Write-Host "Waiting for LocalJobWorker to process targets..."
for ($i = 0; $i -lt 15; $i++) {
    Start-Sleep -Seconds 1
    $getJob = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/jobs/$jobId" -Method GET -Headers $headers
    if ($getJob.data.status -eq 'COMPLETED') {
        break
    }
}

Write-Host "5. Job status after worker processing: $($getJob.data.status)"
$getJob.data.targets | ForEach-Object {
    Write-Host "   Target lang: $($_.lang), status: $($_.status), voice: $($_.voiceId)"
}

# 6. Check list narrations endpoint
$narrListResp = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/client/pois/$poiId/narrations" -Method GET
Write-Host "6. Published narrations count: $($narrListResp.data.narrations.Count)"
$narrListResp.data.narrations | ForEach-Object {
    Write-Host "   Published lang: $($_.lang), version: $($_.version)"
}

# 7. Check single narration endpoint for vi
$viNarr = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/client/pois/$poiId/narrations/vi" -Method GET
Write-Host "7. Narration detail (vi):"
Write-Host "   Subtitle: $($viNarr.data.subtitle)"
Write-Host "   AudioUrl: $($viNarr.data.audioUrl)"

# 8. Check single narration endpoint for en
$enNarr = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/client/pois/$poiId/narrations/en" -Method GET
Write-Host "8. Narration detail (en):"
Write-Host "   Subtitle: $($enNarr.data.subtitle)"
Write-Host "   AudioUrl: $($enNarr.data.audioUrl)"

# 9. Test audioUrl playback with curl
$curlOut = curl.exe -s -I $viNarr.data.audioUrl
Write-Host "9. Audio URL HTTP Response headers:"
Write-Host ($curlOut -join "`n")
