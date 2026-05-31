@echo off
chcp 65001 >nul
title 启动 Docker 环境 — MySQL / Redis / RabbitMQ

echo ========================================
echo   外卖配送平台 - Docker 环境启动
echo ========================================
echo.
echo 正在启动 MySQL + Redis + RabbitMQ ...
echo.

cd /d "%~dp0"

docker compose up -d

echo.
echo ========================================
echo   等待服务就绪...
echo ========================================
echo.

:: 等待 MySQL 健康检查通过
echo [1/3] 等待 MySQL ...
:wait_mysql
docker compose ps mysql 2>nul | findstr "healthy" >nul
if %errorlevel% neq 0 (
    timeout /t 3 /nobreak >nul
    goto :wait_mysql
)
echo   [成功] MySQL 就绪

:: 等待 Redis 健康检查通过
echo [2/3] 等待 Redis ...
:wait_redis
docker compose ps redis 2>nul | findstr "healthy" >nul
if %errorlevel% neq 0 (
    timeout /t 2 /nobreak >nul
    goto :wait_redis
)
echo   [成功] Redis 就绪

:: 等待 RabbitMQ 健康检查通过
echo [3/3] 等待 RabbitMQ ...
:wait_rabbit
docker compose ps rabbitmq 2>nul | findstr "healthy" >nul
if %errorlevel% neq 0 (
    timeout /t 3 /nobreak >nul
    goto :wait_rabbit
)
echo   [成功] RabbitMQ 就绪

echo.
echo ========================================
echo   所有服务已就绪！
echo ========================================
echo.
echo   MySQL:     localhost:3307   (root / your_mysql_password)
echo   Redis:     localhost:6379   (密码: your_redis_password)
echo   RabbitMQ:  localhost:5672   (AMQP, guest / your_rabbitmq_password)
echo   RabbitMQ 管理后台: http://localhost:15672
echo.
echo   数据库 waimai 已通过 init.sql 自动初始化。
echo.
echo ========================================
echo   现在可以运行 start-all.bat 启动前后端项目
echo ========================================
echo.
pause