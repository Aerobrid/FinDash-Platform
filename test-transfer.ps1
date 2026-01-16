#!/usr/bin/env pwsh
# Final gRPC test - uses /me endpoint to get user ID

$ErrorActionPreference = "Stop"
$timestamp = [DateTimeOffset]::Now.ToUnixTimeSeconds()

Write-Host "`n=== Creating and logging in Alice ===`n"
$aliceSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession

$alice = @{
    fullName = "Alice$timestamp"
    email = "alice$timestamp@test.com"
    password = "TestPass!123"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/wallet/users" `
    -Method POST -ContentType "application/json" -Body $alice -WebSession $aliceSession | Out-Null

$aliceLogin = @{
    email = "alice$timestamp@test.com"
    password = "TestPass!123"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/wallet/login" `
    -Method POST -ContentType "application/json" -Body $aliceLogin -WebSession $aliceSession | Out-Null

$aliceInfo = Invoke-RestMethod -Uri "http://localhost:8080/api/wallet/me" `
    -Method GET -WebSession $aliceSession

Write-Host "Alice ID: $($aliceInfo.id)"
Write-Host "Alice Balance: $($aliceInfo.balance)"

Write-Host "`n=== Creating and logging in Bob ===`n"
$bobSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession

$bob = @{
    fullName = "Bob$timestamp"
    email = "bob$timestamp@test.com"
    password = "TestPass!123"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/wallet/users" `
    -Method POST -ContentType "application/json" -Body $bob -WebSession $bobSession | Out-Null

$bobLogin = @{
    email = "bob$timestamp@test.com"
    password = "TestPass!123"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/wallet/login" `
    -Method POST -ContentType "application/json" -Body $bobLogin -WebSession $bobSession | Out-Null

$bobInfo = Invoke-RestMethod -Uri "http://localhost:8080/api/wallet/me" `
    -Method GET -WebSession $bobSession

Write-Host "Bob ID: $($bobInfo.id)"
Write-Host "Bob Balance: $($bobInfo.balance)"

# Test 1: Try to send more than Alice has (should fail with gRPC validation)
Write-Host "`n=== TEST 1: Alice tries to send `$1500 (only has `$$($aliceInfo.balance)) ===`n"

$transfer1 = @{
    senderId = [string]$aliceInfo.id
    receiverId = [string]$bobInfo.id
    amount = 1500
} | ConvertTo-Json

try {
    Invoke-RestMethod -Uri "http://localhost:8080/api/transaction/transfer" `
        -Method POST -ContentType "application/json" -Body $transfer1 -WebSession $aliceSession
    Write-Host "❌ FAIL: Transaction should have been rejected!" -ForegroundColor Red
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 400) {
        Write-Host "✅ PASS: gRPC validation rejected with 400 Bad Request" -ForegroundColor Green
    } else {
        Write-Host "⚠️  Rejected with HTTP $statusCode (check logs)" -ForegroundColor Yellow
    }
}

# Test 2: Send valid amount (should succeed)
Write-Host "`n=== TEST 2: Alice sends `$50 to Bob (should succeed) ===`n"

$transfer2 = @{
    senderId = [string]$aliceInfo.id
    receiverId = [string]$bobInfo.id
    amount = 50
} | ConvertTo-Json

try {
    $result = Invoke-RestMethod -Uri "http://localhost:8080/api/transaction/transfer" `
        -Method POST -ContentType "application/json" -Body $transfer2 -WebSession $aliceSession
    Write-Host "✅ PASS: Transaction successful!" -ForegroundColor Green
    Write-Host "   Transaction ID: $($result.id)"
    Write-Host "   Amount: `$$($result.amount)"
    Write-Host "   Status: $($result.status)"
} catch {
    Write-Host "❌ FAIL: Valid transaction rejected with $($_.Exception.Response.StatusCode)" -ForegroundColor Red
}

Write-Host "`n=== Checking logs for gRPC activity ===`n"
Start-Sleep -Seconds 2
docker logs findash-transaction 2>&1 | Select-String -Pattern "Received transfer|Starting gRPC|gRPC balance check|Insufficient balance|Transaction completed" | Select-Object -Last 10
