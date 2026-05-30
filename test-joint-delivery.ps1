# Multi-Rider Joint Delivery Test Script
# Usage: .\test-joint-delivery.ps1
# Prereq: backend running on localhost:8080

$BASE = "http://localhost:8080/api"
$ORDER_ID = 13
$ORDER_NO = "335750397232812032"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  Joint Delivery Test" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# ── Helper: login as rider, return token ──────────
function Login-Rider($code) {
    $body = "{""code"":""$code""}"
    $resp = Invoke-RestMethod -Uri "$BASE/auth/login/rider/wechat" -Method Post -ContentType "application/json; charset=utf-8" -Body $body
    return $resp.data.accessToken
}

# ── Helper: call API with auth ────────────────────
function Call-API($method, $path, $token, $body) {
    $headers = @{Authorization="Bearer $token"}
    if ($body) {
        return Invoke-RestMethod -Uri "$BASE$path" -Method $method -Headers $headers -ContentType "application/json; charset=utf-8" -Body $body
    } else {
        return Invoke-RestMethod -Uri "$BASE$path" -Method $method -Headers $headers
    }
}

# ── 1. Login all riders ──────────────────────────

Write-Host ""
Write-Host "[1] Rider login..." -ForegroundColor Yellow

$T1 = Login-Rider "15800000000"
Write-Host "  Rider 1: token_len=$($T1.Length) prefix=$($T1.Substring(0, [Math]::Min(20,$T1.Length)))..."

$T2 = Login-Rider "15877777777"
Write-Host "  Rider 2: token_len=$($T2.Length) prefix=$($T2.Substring(0, [Math]::Min(20,$T2.Length)))..."

$T3 = Login-Rider "12344445555"
Write-Host "  Rider 3: token_len=$($T3.Length) prefix=$($T3.Substring(0, [Math]::Min(20,$T3.Length)))..."

# ── 2. Riders go online + set GPS location ────────

Write-Host ""
Write-Host "[2] Riders go online + set location..." -ForegroundColor Yellow
Call-API "Post" "/rider/online" $T1 | Out-Null
Call-API "Post" "/rider/online" $T2 | Out-Null
Call-API "Post" "/rider/online" $T3 | Out-Null

# Set all riders near merchant 3 (106.6727, 26.4475)
$loc1 = '{"longitude":106.6730,"latitude":26.4475}'
$loc2 = '{"longitude":106.6725,"latitude":26.4470}'
$loc3 = '{"longitude":106.6728,"latitude":26.4478}'
Call-API "Post" "/rider/location" $T1 $loc1 | Out-Null
Call-API "Post" "/rider/location" $T2 $loc2 | Out-Null
Call-API "Post" "/rider/location" $T3 $loc3 | Out-Null
Write-Host "  All 3 riders online with GPS near merchant"

# ── 3. Admin login + create group ─────────────────

Write-Host ""
Write-Host "[3] Admin login + create group..." -ForegroundColor Yellow
$adminResp = Invoke-RestMethod -Uri "$BASE/auth/login/admin" -Method Post -ContentType "application/json; charset=utf-8" -Body '{"username":"admin","password":"admin123"}'
$AT = $adminResp.data.accessToken
Write-Host "  Admin token: $($AT.Substring(0, [Math]::Min(20,$AT.Length)))..."

$createBody = "{""orderId"":$ORDER_ID,""requiredRiderCount"":2}"
$groupResp = Call-API "Post" "/joint-delivery/group/create" $AT $createBody
$GID = $groupResp.data.id
Write-Host "  Group created: groupId=$GID" -ForegroundColor Green

# ── 4. Rider 1 accepts ────────────────────────────

Write-Host ""
Write-Host "[4] Rider 1 joins..." -ForegroundColor Yellow
Call-API "Post" "/joint-delivery/rider/join/$GID" $T1 | Out-Null
Write-Host "  Rider 1 joined (1/2)" -ForegroundColor Green

# ── 5. Rider 2 accepts -> order ACCEPTED ──────────

Write-Host ""
Write-Host "[5] Rider 2 joins -> all joined, order ACCEPTED" -ForegroundColor Yellow
Call-API "Post" "/joint-delivery/rider/join/$GID" $T2 | Out-Null
Write-Host "  Rider 2 joined (2/2) order accepted!" -ForegroundColor Green

$orderResp = Call-API "Get" "/order/$ORDER_NO" $T1
Write-Host "  Order status: $($orderResp.data.status)"

# ── 6. Check rider 3 invites (should be 0) ────────

Write-Host ""
Write-Host "[6] Rider 3 invites..." -ForegroundColor Yellow
$invResp = Call-API "Get" "/joint-delivery/rider/invites" $T3
$invCount = if ($invResp.data) { $invResp.data.Count } else { 0 }
Write-Host "  Rider 3 invites: $invCount (expect 0)"

# ── 7. Get member IDs ─────────────────────────────

Write-Host ""
Write-Host "[7] Get member list..." -ForegroundColor Yellow
$memResp = Call-API "Get" "/joint-delivery/group/$GID/members" $AT
$M1 = $memResp.data[0].id
$M2 = $memResp.data[1].id
Write-Host "  member1_id=$M1, member2_id=$M2"

# ── 8. Rider 1 pickup ─────────────────────────────

Write-Host ""
Write-Host "[8] Rider 1 pickup..." -ForegroundColor Yellow
Call-API "Post" "/joint-delivery/rider/pickup/$M1" $T1 | Out-Null
Write-Host "  Rider 1 picked up (1/2)" -ForegroundColor Green

# ── 9. Rider 2 pickup -> order DELIVERING ─────────

Write-Host ""
Write-Host "[9] Rider 2 pickup -> all picked up, order DELIVERING" -ForegroundColor Yellow
Call-API "Post" "/joint-delivery/rider/pickup/$M2" $T2 | Out-Null
Write-Host "  Rider 2 picked up (2/2) delivering!" -ForegroundColor Green

$orderResp = Call-API "Get" "/order/$ORDER_NO" $T1
Write-Host "  Order status: $($orderResp.data.status)"

# ── 10. Rider 1 complete ──────────────────────────

Write-Host ""
Write-Host "[10] Rider 1 complete..." -ForegroundColor Yellow
Call-API "Post" "/joint-delivery/rider/complete/$M1" $T1 | Out-Null
Write-Host "  Rider 1 completed (earnings ~2.50)" -ForegroundColor Green

# ── 11. Rider 2 complete -> order COMPLETED ───────

Write-Host ""
Write-Host "[11] Rider 2 complete -> all done, order COMPLETED" -ForegroundColor Yellow
Call-API "Post" "/joint-delivery/rider/complete/$M2" $T2 | Out-Null
Write-Host "  Rider 2 completed (earnings ~2.50) order complete!" -ForegroundColor Green

$orderResp = Call-API "Get" "/order/$ORDER_NO" $T1
Write-Host "  Order status: $($orderResp.data.status)"

# ── 12. Final progress ────────────────────────────

Write-Host ""
Write-Host "[12] Final progress:" -ForegroundColor Yellow
$progResp = Call-API "Get" "/joint-delivery/group/order/$ORDER_ID" $AT
$g = $progResp.data.group
Write-Host "  Group status: $($g.status)"
Write-Host "  Progress: $($g.completedRiderCount) / $($g.requiredRiderCount)"
Write-Host "  Members:"
foreach ($m in $progResp.data.members) {
    Write-Host "    riderId=$($m.riderId) name=$($m.riderName) status=$($m.status) earnings=$($m.earnings)"
}

Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  Test Complete!" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Cyan
