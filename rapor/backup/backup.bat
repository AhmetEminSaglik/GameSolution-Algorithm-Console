@echo off
REM pathexplorer DB yedegi. Asil is backup.sh'ta; bu dosya onu Git Bash ile calistirir.
REM Ciktilar (rapor\backup\ altinda):
REM   pgdump\pathexplorer_<tarih>.dump        tam yedek (git'e girmez)
REM   csv\<tablo>_<R>x<C>.csv                  grid bazli CSV (orn. solving_checkpoint_8x8.csv)
REM   sql-insert\<tablo>_<R>x<C>_insert.txt    grid bazli INSERT (orn. solving_checkpoint_8x8_insert.txt)
REM csv\ ve sql-insert\ her calistirmada silinip yeniden yazilir; bitmeden commit atma.
setlocal
REM Git Bash'i bul: once bu makinedeki kullanici kurulumu, sonra Program Files.
set GIT_ROOT=%LOCALAPPDATA%\Programs\Git
if not exist "%GIT_ROOT%\usr\bin\bash.exe" set GIT_ROOT=%ProgramFiles%\Git
if not exist "%GIT_ROOT%\usr\bin\bash.exe" (
  echo [hata] Git Bash bulunamadi. Git for Windows kur ya da GIT_ROOT'u bu dosyada duzelt.
  pause
  exit /b 1
)
set PATH=%GIT_ROOT%\usr\bin;%GIT_ROOT%\bin;%PATH%
set BASH_EXE=%GIT_ROOT%\usr\bin\bash.exe
set SCRIPT=%~dp0backup.sh

"%BASH_EXE%" --norc --noprofile "%SCRIPT%" %*
echo.
echo Bitti: csv\ ve sql-insert\ grid bazli dosyalar yazildi. Artik commit atilabilir.
pause
