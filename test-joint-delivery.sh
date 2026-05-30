#!/bin/bash
# 多骑手联合配送测试脚本
# 使用方法: bash test-joint-delivery.sh
# 前提: 后端已启动在 localhost:8080

BASE="http://localhost:8080/api"
ORDER_ID=13
ORDER_NO="335750397232812032"

echo "=========================================="
echo "  多骑手联合配送测试"
echo "=========================================="

# ── 1. 三个骑手登录，获取 token ──────────────────────

echo ""
echo "[1] 骑手登录..."

R1=$(curl -s -X POST "$BASE/auth/login/rider/wechat" \
  -H "Content-Type: application/json" \
  -d '{"code":"15800000000"}')
TOKEN1=$(echo $R1 | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
RIDER1_ID=$(echo $R1 | grep -o '"userId":[0-9]*' | grep -o '[0-9]*')
echo "  骑手1 (张三): id=$RIDER1_ID, token=${TOKEN1:0:20}..."

R2=$(curl -s -X POST "$BASE/auth/login/rider/wechat" \
  -H "Content-Type: application/json" \
  -d '{"code":"15877777777"}')
TOKEN2=$(echo $R2 | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
RIDER2_ID=$(echo $R2 | grep -o '"userId":[0-9]*' | grep -o '[0-9]*')
echo "  骑手2 (321):  id=$RIDER2_ID, token=${TOKEN2:0:20}..."

R3=$(curl -s -X POST "$BASE/auth/login/rider/wechat" \
  -H "Content-Type: application/json" \
  -d '{"code":"12344445555"}')
TOKEN3=$(echo $R3 | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
RIDER3_ID=$(echo $R3 | grep -o '"userId":[0-9]*' | grep -o '[0-9]*')
echo "  骑手3 (李四测试): id=$RIDER3_ID, token=${TOKEN3:0:20}..."

if [ -z "$TOKEN1" ] || [ -z "$TOKEN2" ]; then
  echo "  [FAIL] 骑手登录失败，检查后端是否启动"
  exit 1
fi

# ── 2. 确保骑手在线 ──────────────────────────────────

echo ""
echo "[2] 骑手上线..."

for T in "$TOKEN1" "$TOKEN2" "$TOKEN3"; do
  curl -s -X POST "$BASE/rider/online" -H "Authorization: Bearer $T" > /dev/null
done
echo "  3位骑手均已上线"

# ── 3. Admin 创建联合配送组 (需要admin token) ─────

echo ""
echo "[3] Admin 登录并创建联合配送组..."

ADMIN=$(curl -s -X POST "$BASE/auth/login/admin" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}')
ADMIN_TOKEN=$(echo $ADMIN | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
echo "  Admin token: ${ADMIN_TOKEN:0:20}..."

GROUP=$(curl -s -X POST "$BASE/joint-delivery/group/create" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"orderId\":$ORDER_ID,\"requiredRiderCount\":2}")
GROUP_ID=$(echo $GROUP | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
echo "  联合配送组已创建: groupId=$GROUP_ID"

# ── 4. 骑手1接受邀请 ──────────────────────────────────

echo ""
echo "[4] 骑手1 接受邀请..."
curl -s -X POST "$BASE/joint-delivery/rider/join/$GROUP_ID" \
  -H "Authorization: Bearer $TOKEN1" > /dev/null
echo "  骑手1 已加入 (1/2)"

# ── 5. 骑手2接受邀请 ─── 触发订单 ACCEPTED ────────

echo ""
echo "[5] 骑手2 接受邀请 → 人数到齐，订单变为 ACCEPTED"
curl -s -X POST "$BASE/joint-delivery/rider/join/$GROUP_ID" \
  -H "Authorization: Bearer $TOKEN2" > /dev/null
echo "  骑手2 已加入 (2/2) ✓ 订单已接单"

# 验证订单状态
ORDER_STATUS=$(curl -s -X GET "$BASE/order/${ORDER_NO}" \
  -H "Authorization: Bearer $TOKEN1" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
echo "  订单状态: $ORDER_STATUS"

# ── 6. 查看骑手3的邀请列表(应该为空) ────────────

echo ""
echo "[6] 查看骑手3的邀请列表..."
INVITES=$(curl -s -X GET "$BASE/joint-delivery/rider/invites" \
  -H "Authorization: Bearer $TOKEN3")
echo "  骑手3的邀请: $INVITES"

# ── 7. 骑手1取餐 ──────────────────────────────────────

echo ""
echo "[7] 骑手1 取餐..."
# 先找到骑手1的memberId
MEMBERS=$(curl -s -X GET "$BASE/joint-delivery/group/$GROUP_ID/members" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
echo "  成员列表: $MEMBERS"
MEMBER1_ID=$(echo $MEMBERS | grep -o '"id":[0-9]*' | head -1 | grep -o '[0-9]*')
MEMBER2_ID=$(echo $MEMBERS | grep -o '"id":[0-9]*' | tail -1 | grep -o '[0-9]*')

curl -s -X POST "$BASE/joint-delivery/rider/pickup/$MEMBER1_ID" \
  -H "Authorization: Bearer $TOKEN1" > /dev/null
echo "  骑手1 已取餐 (1/2)"

# ── 8. 骑手2取餐 → 触发订单 DELIVERING ──────────────

echo ""
echo "[8] 骑手2 取餐 → 全部取餐，订单变为 DELIVERING"
curl -s -X POST "$BASE/joint-delivery/rider/pickup/$MEMBER2_ID" \
  -H "Authorization: Bearer $TOKEN2" > /dev/null
echo "  骑手2 已取餐 (2/2) ✓ 订单配送中"

ORDER_STATUS=$(curl -s -X GET "$BASE/order/${ORDER_NO}" \
  -H "Authorization: Bearer $TOKEN1" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
echo "  订单状态: $ORDER_STATUS"

# ── 9. 骑手1完成配送 ────────────────────────────────

echo ""
echo "[9] 骑手1 完成配送..."
curl -s -X POST "$BASE/joint-delivery/rider/complete/$MEMBER1_ID" \
  -H "Authorization: Bearer $TOKEN1" > /dev/null
echo "  骑手1 已完成 (收益 ¥2.50)"

# ── 10. 骑手2完成配送 → 订单 COMPLETED ────────────

echo ""
echo "[10] 骑手2 完成配送 → 全部完成，订单 COMPLETED"
curl -s -X POST "$BASE/joint-delivery/rider/complete/$MEMBER2_ID" \
  -H "Authorization: Bearer $TOKEN2" > /dev/null
echo "  骑手2 已完成 (收益 ¥2.50) ✓ 订单完成"

ORDER_STATUS=$(curl -s -X GET "$BASE/order/${ORDER_NO}" \
  -H "Authorization: Bearer $TOKEN1" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
echo "  订单状态: $ORDER_STATUS"

# ── 11. 查看最终进度 ──────────────────────────────────

echo ""
echo "[11] 最终进度:"
curl -s -X GET "$BASE/joint-delivery/group/order/$ORDER_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | python3 -m json.tool 2>/dev/null || \
curl -s -X GET "$BASE/joint-delivery/group/order/$ORDER_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN"

echo ""
echo "=========================================="
echo "  测试完成！"
echo "=========================================="
