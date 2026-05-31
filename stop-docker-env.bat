@echo off
chcp 65001 >nul
title 停止 Docker 环境

echo ========================================
echo   外卖配送平台 - 停止 Docker 环境
echo ========================================
echo.

cd /d "%~dp0"

echo 正在停止所有容器...
docker compose down

echo.
echo ========================================
echo   Docker 环境已停止。
echo ========================================
echo.
echo   容器已关闭，数据卷保留（再次启动可恢复数据）。
echo   如需彻底删除数据卷，请运行: docker compose down -v
echo.
pause