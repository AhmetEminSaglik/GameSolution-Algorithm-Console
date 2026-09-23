@echo off
setlocal
set GIT_ROOT=C:\Users\ahmetemin.saglik\AppData\Local\Programs\Git
set PATH=%GIT_ROOT%\usr\bin;%GIT_ROOT%\bin;%PATH%
set BASH_EXE=%GIT_ROOT%\usr\bin\bash.exe
set SCRIPT=%~dp0backup-import.sh

"%BASH_EXE%" --norc --noprofile "%SCRIPT%" %*
echo.
pause
