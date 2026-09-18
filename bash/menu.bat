@echo off
setlocal
set GIT_ROOT=C:\Users\ahmetemin.saglik\AppData\Local\Programs\Git
set PATH=%GIT_ROOT%\usr\bin;%GIT_ROOT%\bin;%PATH%
set BASH_EXE=%GIT_ROOT%\usr\bin\bash.exe
set BASH_DIR=%~dp0

:menu
echo ============================================
echo  PathExplorer DB Araclari
echo ============================================
echo  1) En guncel cozumu goster (ENTER=yenile, exit=cik)
echo  2) Tum checkpoint'leri txt'ye dok (her grid boyutu icin)
echo  3) Sira araligina gore checkpoint cikar (checkpoint-N-M.txt)
echo  0) Cikis
echo ============================================
set /p CHOICE=Secim:

if "%CHOICE%"=="1" goto :opt1
if "%CHOICE%"=="2" goto :opt2
if "%CHOICE%"=="3" goto :opt3
if "%CHOICE%"=="0" goto :end
echo Gecersiz secim.
echo.
goto :menu

:opt1
"%BASH_EXE%" --norc --noprofile "%BASH_DIR%latest-grid.sh"
goto :menu

:opt2
"%BASH_EXE%" --norc --noprofile "%BASH_DIR%extract-checkpoints.sh"
echo.
pause
goto :menu

:opt3
"%BASH_EXE%" --norc --noprofile "%BASH_DIR%checkpoint-range-extract.sh"
goto :menu

:end
echo Kapatiliyor...
