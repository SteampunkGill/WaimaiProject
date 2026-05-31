@echo off
cd /d "%~dp0"
title 外卖配送平台 - 一键启动

echo ========================================
echo   外卖配送平台 - 一键启动
echo ========================================
echo.

:: ============================================================
:: 1. Docker 环境
:: ============================================================
echo [1/5] 启动 Docker 环境 (MySQL + Redis + RabbitMQ)...

docker compose up -d >nul 2>&1
if errorlevel 1 (
    echo [警告] Docker 启动可能失败，请确认 Docker Desktop 已运行。
)

echo 等待 Docker 服务就绪（约 40 秒）...
timeout /t 40 /nobreak >nul

echo [通过] Docker 环境就绪。
echo   - MySQL:    localhost:3307  (root / your_mysql_password)
echo   - Redis:    localhost:6379  (密码: your_redis_password)
echo   - RabbitMQ: localhost:5672  (guest / your_rabbitmq_password)
echo.

:: ============================================================
:: 2. 构建后端
:: ============================================================
echo [2/5] Maven 构建后端...
call mvn clean install -DskipTests -q >nul 2>&1
if errorlevel 1 (
    echo [警告] Maven 构建异常，尝试继续...
) else (
    echo [通过] 后端构建成功。
)
echo.

:: ============================================================
:: 3. 安装前端依赖
:: ============================================================
echo [3/5] 检查前端依赖...
for %%d in (web-admin web-customer web-rider web-merchant) do (
    if exist "%%d\package.json" (
        if not exist "%%d\node_modules" (
            echo   安装 %%d 依赖...
            pushd "%%d"
            call npm install >nul 2>&1
            popd
        ) else (
            echo   %%d 已就绪。
        )
    )
)
echo [通过] 前端依赖就绪。
echo.

:: ============================================================
:: 4. 启动后端
:: ============================================================
echo [4/5] 启动后端 Spring Boot (端口 8080)...
start "外卖-后端-8080" /MIN cmd /c "cd /d "%~dp0api" && title 后端 API 8080 && mvn spring-boot:run"
echo 等待后端启动（约 30 秒）...
timeout /t 30 /nobreak >nul
echo [通过] 后端已启动。
echo.

:: ============================================================
:: 5. 启动前端
:: ============================================================
echo [5/5] 启动前端 Vite 开发服务器...

start "外卖-管理后台-5173" /MIN cmd /c "cd /d "%~dp0web-admin" && title 管理后台 5173 && npm run dev"
timeout /t 2 /nobreak >nul
start "外卖-客户端-5174" /MIN cmd /c "cd /d "%~dp0web-customer" && title 客户端 5174 && npm run dev"
timeout /t 2 /nobreak >nul
start "外卖-骑手端-5175" /MIN cmd /c "cd /d "%~dp0web-rider" && title 骑手端 5175 && npm run dev"
timeout /t 2 /nobreak >nul
start "外卖-商户端-5176" /MIN cmd /c "cd /d "%~dp0web-merchant" && title 商户端 5176 && npm run dev"
timeout /t 2 /nobreak >nul

echo [通过] 前端已启动。
echo 等待前端编译（约 10 秒）...
timeout /t 10 /nobreak >nul

start "" http://localhost:5173
timeout /t 1 /nobreak >nul
start "" http://localhost:5174
timeout /t 1 /nobreak >nul
start "" http://localhost:5175
timeout /t 1 /nobreak >nul
start "" http://localhost:5176

echo.
echo ========================================
echo   启动完成！
echo ========================================
echo.
echo   后端:  http://localhost:8080
echo         Swagger: http://localhost:8080/swagger-ui.html
echo.
echo   前端:
echo     管理后台:  http://localhost:5173
echo     客户端:    http://localhost:5174
echo     骑手端:    http://localhost:5175
echo     商户端:    http://localhost:5176
echo.
echo   Docker 环境:
echo     MySQL:    localhost:3307  (root / your_mysql_password)
echo     Redis:    localhost:6379  (密码: your_redis_password)
echo     RabbitMQ: localhost:5672  (管理后台: http://localhost:15672)
echo.
echo   关闭对应窗口即可停止服务。
echo   停止 Docker: 运行 stop-docker-env.bat
echo.
echo ========================================
pause