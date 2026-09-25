@echo off
setlocal
set GIT_ROOT=C:\Users\ahmetemin.saglik\AppData\Local\Programs\Git
set PATH=%GIT_ROOT%\usr\bin;%GIT_ROOT%\bin;%PATH%
set BASH_EXE=%GIT_ROOT%\usr\bin\bash.exe
set BASH_DIR=%~dp0
rem Secili grid (orn. 7x7) burada saklanir; menu kapanip acilinca da hatirlanir.
rem Grid isteyen araclara GRID ortam degiskeni olarak gecer.
set GRID_FILE=%BASH_DIR%.selected-grid

:menu
call :load_grid
set "GRID_LABEL=(grid secilmedi)"
if defined GRID set "GRID_LABEL=%GRID%"
echo ============================================
echo  PathExplorer DB Araclari  %GRID_LABEL%
echo ============================================
echo  1) En guncel cozumu goster (ENTER=yenile, exit=cik)
echo  2) Tum checkpoint'leri txt'ye dok (her grid boyutu icin)
echo  3) Sira araligina gore checkpoint cikar (checkpoint-N-M.txt)
echo  4) Grid map degistir
echo  0) Cikis
echo ============================================
set CHOICE=
set /p CHOICE=Secim:

if "%CHOICE%"=="1" goto :opt1
if "%CHOICE%"=="2" goto :opt2
if "%CHOICE%"=="3" goto :opt3
if "%CHOICE%"=="4" goto :opt4
if "%CHOICE%"=="0" goto :end
echo Gecersiz secim.
echo.
goto :menu

:opt1
call :ensure_grid
if errorlevel 1 goto :menu
"%BASH_EXE%" --norc --noprofile "%BASH_DIR%latest-grid.sh"
goto :menu

:opt2
"%BASH_EXE%" --norc --noprofile "%BASH_DIR%extract-checkpoints.sh"
echo.
pause
goto :menu

:opt3
call :ensure_grid
if errorlevel 1 goto :menu
"%BASH_EXE%" --norc --noprofile "%BASH_DIR%checkpoint-range-extract.sh"
goto :menu

:opt4
"%BASH_EXE%" --norc --noprofile "%BASH_DIR%select-grid.sh"
echo.
goto :menu

rem --- .selected-grid dosyasindan GRID'i okur (yoksa GRID tanimsiz kalir) ---
:load_grid
set GRID=
if exist "%GRID_FILE%" set /p GRID=<"%GRID_FILE%"
exit /b 0

rem --- Grid secili degilse SADECE bu sefer sectirir; iptal edilirse 1 doner ---
:ensure_grid
if defined GRID exit /b 0
echo Grid secilmedi, once grid sec:
"%BASH_EXE%" --norc --noprofile "%BASH_DIR%select-grid.sh"
call :load_grid
if defined GRID exit /b 0
echo Grid secilmedi, islem iptal.
echo.
exit /b 1

:end
echo Kapatiliyor...
